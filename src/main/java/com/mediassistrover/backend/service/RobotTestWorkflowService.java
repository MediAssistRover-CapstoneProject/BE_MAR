package com.mediassistrover.backend.service;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.mediassistrover.backend.dto.workflow.request.WorkflowRequests.GraphCreateRequest;
import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.exception.*;
import com.mediassistrover.backend.mqtt.*;
import com.mediassistrover.backend.repository.*;
import com.mediassistrover.backend.security.AccountPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.time.Clock;
import java.time.Instant;

/** Administrator commissioning flows use the ordinary job/step/MQTT runtime. */
@Service @RequiredArgsConstructor
public class RobotTestWorkflowService {
    public enum Kind { MOVE, WAIT, SPEAK, VOICE_CAPTURE, AUDIO_LOOPBACK, SPEAK_LAST_TRANSCRIPT, OPEN_SERVO, CLOSE_SERVO, WAIT_CONFIRM }
    public record TestStep(@NotNull Kind kind, UUID endpointId,
            @DecimalMin("1") @DecimalMax("300") Double durationSec,
            @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) @Size(max=500) String text,
            @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) @Pattern(regexp="vi|en") String languageCode,
            @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) @Min(0) @Max(100) Integer volume) {
        public TestStep(Kind kind,UUID endpointId,Double durationSec){this(kind,endpointId,durationSec,null,null,null);}
    }
    public record Create(@NotNull UUID robotId,UUID mapId,UUID navigationSessionId,
            @DecimalMin("0.05") @DecimalMax("0.60") Double maxSpeedMps,
            @Pattern(regexp="[A-Za-z0-9_-]{1,64}") String compartmentId,
            @NotEmpty @Size(max=20) List<@NotNull @Valid TestStep> steps) { }
    public record AudioLoopback(@Min(1) @Max(15) Integer durationSec,
            @Min(0) @Max(100) Integer volume) { }
    public record Voice(@Min(1) @Max(15) Integer durationSec,
            @Pattern(regexp="vi|en") String languageCode, Boolean playback,
            @Min(0) @Max(100) Integer volume) { }
    public record Servo(@NotNull @Pattern(regexp="OPEN|CLOSE") String action,
            @Pattern(regexp="[A-Za-z0-9_-]{1,64}") String compartmentId) { }
    public record Result(UUID jobId,UUID workflowId,String status,boolean reused) { }
    private final ObjectMapper json;
    private final WorkflowService workflows;
    private final WorkflowRepository workflowRows;
    private final JobRepository jobs;
    private final RobotRepository robots;
    private final RobotMqttSessionRepository links;
    private final EndpointRepository endpoints;
    private final JobExecutionService execution;
    private final NavigationSessionService navigation;
    private final MqttRolloutService rollout;
    private final MqttProperties mqtt;
    private final RobotTestSubmissionRepository submissions;
    private final Clock clock;

    private double speed(Create r){return r.maxSpeedMps()==null?.4:r.maxSpeedMps();}
    private String compartment(Create r){return r.compartmentId()==null?"CART_MAIN":r.compartmentId();}
    private boolean moves(Create r){return r.steps().stream().anyMatch(s->s.kind()==Kind.MOVE);}
    private boolean hardware(Create r){return r.steps().stream().anyMatch(s->s.kind()==Kind.OPEN_SERVO||s.kind()==Kind.CLOSE_SERVO);}
    private void validate(Create r){
        if(r.steps()==null||r.steps().isEmpty()||r.steps().size()>20||r.steps().stream().anyMatch(s->s==null||s.kind()==null))
            throw new BusinessRuleException("Test workflow requires 1..20 basic steps");
        if(!Double.isFinite(speed(r))||speed(r)<.05||speed(r)>.6)throw new BusinessRuleException("Test MOVE speed must be 0.05..0.60 m/s");
        if(!compartment(r).matches("[A-Za-z0-9_-]{1,64}"))throw new BusinessRuleException("Invalid compartment ID");
        if(moves(r)&&r.mapId()==null)throw new BusinessRuleException("MOVE requires mapId");
        for(var step:r.steps()){
            if(step.kind()==Kind.MOVE&&step.endpointId()==null)throw new BusinessRuleException("MOVE requires endpointId");
            if(step.kind()!=Kind.MOVE&&step.endpointId()!=null)throw new BusinessRuleException("Only MOVE accepts endpointId");
            if(step.kind()==Kind.WAIT||step.kind()==Kind.VOICE_CAPTURE||step.kind()==Kind.AUDIO_LOOPBACK){
                if(step.durationSec()==null||!Double.isFinite(step.durationSec())||step.durationSec()<1||step.durationSec()>300)
                    throw new BusinessRuleException("WAIT requires durationSec 1..300");
            }else if(step.durationSec()!=null)throw new BusinessRuleException("Only WAIT/VOICE_CAPTURE/AUDIO_LOOPBACK accept durationSec");
            if((step.kind()==Kind.VOICE_CAPTURE||step.kind()==Kind.AUDIO_LOOPBACK) && (step.durationSec()>15 || step.durationSec()!=Math.rint(step.durationSec())))
                throw new BusinessRuleException("Audio recording requires an integer durationSec 1..15");
            if(step.kind()==Kind.SPEAK_LAST_TRANSCRIPT && r.steps().subList(0,r.steps().indexOf(step)).stream().noneMatch(s->s.kind()==Kind.VOICE_CAPTURE))
                throw new BusinessRuleException("SPEAK_LAST_TRANSCRIPT requires a preceding VOICE_CAPTURE");
            if(step.kind()==Kind.SPEAK){
                if(step.text()==null||step.text().isBlank()||step.text().length()>500)
                    throw new BusinessRuleException("SPEAK requires text 1..500 characters");
                if(step.languageCode()!=null&&!Set.of("vi","en").contains(step.languageCode()))throw new BusinessRuleException("SPEAK languageCode must be vi or en");
                if(step.volume()!=null&&(step.volume()<0||step.volume()>100))throw new BusinessRuleException("SPEAK volume must be 0..100");
            }else if(step.kind()==Kind.AUDIO_LOOPBACK){
                if(step.text()!=null||step.languageCode()!=null)throw new BusinessRuleException("AUDIO_LOOPBACK replays the original WAV; no text or languageCode");
                if(step.volume()!=null&&(step.volume()<0||step.volume()>100))throw new BusinessRuleException("Playback volume must be 0..100");
            }else if(step.kind()==Kind.VOICE_CAPTURE||step.kind()==Kind.SPEAK_LAST_TRANSCRIPT){
                if(step.text()!=null)throw new BusinessRuleException("Voice capture/echo does not accept supplied text");
                if(step.languageCode()!=null&&!Set.of("vi","en").contains(step.languageCode()))throw new BusinessRuleException("Voice languageCode must be vi or en");
                if(step.volume()!=null&&(step.kind()==Kind.VOICE_CAPTURE||step.volume()<0||step.volume()>100))throw new BusinessRuleException("Only speech playback accepts volume 0..100");
            }else if(step.text()!=null||step.languageCode()!=null||step.volume()!=null)
                throw new BusinessRuleException("Speech fields require a voice step");
        }
    }
    /** Pure preview: no workflow is saved, published or dispatched. */
    public GraphCreateRequest preview(Create r){
        validate(r);
        ObjectNode root=json.createObjectNode(),w=root.putObject("workflow"),g=root.putObject("graph");
        w.put("workflowCode","WF_ROBOT_TEST_PREVIEW").put("versionNo",1).put("workflowName","Robot basic step test")
                .put("serviceType","ROBOT_TEST").put("description","Administrator commissioning: MOVE, WAIT, servo and operator confirmation")
                .put("maxTotalVisits",r.steps().size()).put("graphVersion",3).put("schemaVersion",3);
        var schema=w.putObject("inputSchemaJson").put("type","object").put("additionalProperties",true);
        schema.putObject("properties").putObject("test_confirm_keys").put("type","object")
                .putObject("additionalProperties").put("type","string");
        w.putObject("inputUiJson");w.putObject("defaultInputsJson").put("hardware_test_mode","COMMISSIONING_ONLY");
        w.putObject("outputMappingJson");w.putArray("requiredCapabilitiesJson");
        g.put("startStepCode","TEST_01");g.putArray("tasks").addObject().put("taskCode","TEST_FLOW")
                .put("taskName","Basic step sequence").put("sequenceNo",1).putObject("configJson");
        ArrayNode steps=g.putArray("steps"),edges=g.putArray("transitions");int seconds=30;
        for(int i=0;i<r.steps().size();i++){
            var item=r.steps().get(i);String code=String.format(Locale.ROOT,"TEST_%02d",i+1);
            ObjectNode s=steps.addObject();s.put("taskCode","TEST_FLOW").put("stepCode",code).put("stepName",item.kind().name())
                    .put("sequenceNo",i+1).put("maxAttempts",1).put("retryDelaySec",0).put("maxVisits",1).put("auditLevel","FULL");
            s.putArray("retryCodesJson");s.putObject("inputMappingJson");ObjectNode p=s.putObject("paramsJson");int timeout;
            switch(item.kind()){
                case MOVE -> {s.put("stepType","MOVE");p.put("mode","NORMAL").put("map_id",r.mapId().toString())
                        .put("endpoint_id",item.endpointId().toString()).put("max_speed_mps",speed(r));timeout=180;}
                case WAIT -> {s.put("stepType","WAIT");p.put("wait_type","DURATION").put("duration_sec",item.durationSec());timeout=(int)Math.ceil(item.durationSec())+10;}
                case SPEAK -> {s.put("stepType","INTERACT");p.put("action","SPEAK").put("prompt_key","TEST_SPEAK_"+(i+1));
                        p.putObject("data").put("text",item.text()).put("language_code",item.languageCode()==null?"vi":item.languageCode())
                            .put("volume",item.volume()==null?70:item.volume()).put("speed_wpm",150);timeout=120;}
                case AUDIO_LOOPBACK -> {s.put("stepType","EXECUTE_SENSING");p.put("sensing_type","AUDIO_LOOPBACK")
                        .put("duration_sec",item.durationSec().intValue()).put("volume",item.volume()==null?70:item.volume());timeout=45;}
                case VOICE_CAPTURE -> {s.put("stepType","EXECUTE_SENSING");p.put("sensing_type","VOICE_CAPTURE")
                        .put("duration_sec",item.durationSec().intValue()).put("language_code",item.languageCode()==null?"vi":item.languageCode());timeout=85;}
                case SPEAK_LAST_TRANSCRIPT -> {s.put("stepType","INTERACT");p.put("action","SPEAK").put("prompt_key","TEST_ECHO_"+(i+1));
                        int source=i-1;while(r.steps().get(source).kind()!=Kind.VOICE_CAPTURE)source--;
                        String pointer="/outputs/"+String.format(Locale.ROOT,"TEST_%02d",source+1)+"/metrics/playback_text";
                        var data=p.putObject("data").put("language_code",item.languageCode()==null?"vi":item.languageCode())
                            .put("volume",item.volume()==null?70:item.volume()).put("speed_wpm",150);
                        data.set("text",json.createObjectNode().put("$ref",pointer));timeout=120;}
                case WAIT_CONFIRM -> {s.put("stepType","WAIT");p.put("wait_type","EVENT").put("event_type","TEST_OPERATOR_CONFIRMED");
                        // Scope to this job's operation, never a reusable workflow-wide key.
                        ((ObjectNode)s.path("inputMappingJson")).set("correlation_key",json.createObjectNode().put("$ref","/inputs/test_confirm_keys/"+code));timeout=600;}
                case OPEN_SERVO,CLOSE_SERVO -> {s.put("stepType","CONTROL_HARDWARE");p.put("compartment_id",compartment(r))
                        .put("action",item.kind()==Kind.OPEN_SERVO?"UNLOCK_COMPARTMENT":"LOCK_COMPARTMENT");timeout=20;}
                default -> throw new IllegalStateException();
            }
            s.put("timeoutSec",timeout);seconds+=timeout;
            var ok=edge(edges,code,"SUCCESS");
            if(i+1==r.steps().size())ok.put("terminalStatus","SUCCEEDED");else ok.put("toStepCode",String.format(Locale.ROOT,"TEST_%02d",i+2));
            edge(edges,code,"FAILURE").put("terminalStatus","FAILED");
            edge(edges,code,"TIMEOUT").put("terminalStatus","RECOVERY_REQUIRED");
        }
        w.put("maxDurationSec",seconds);
        try{return json.treeToValue(root,GraphCreateRequest.class);}catch(Exception e){throw new IllegalStateException("Invalid test graph",e);}
    }
    private ObjectNode edge(ArrayNode edges,String code,String outcome){return edges.addObject().put("fromStepCode",code).put("outcome",outcome).put("priority",1).put("defaultTransition",true);}
    private String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}

    @Transactional
    public Result run(Create r,String key,AccountPrincipal actor,RequestMetadata metadata){
        validate(r);
        if(key==null||!key.matches("[A-Za-z0-9._:-]{1,150}"))throw new BusinessRuleException("Idempotency-Key is required (1..150 safe characters)");
        rollout.lockForRuntimeCreation();
        String requestKey=hash(actor.accountId()+":"+key),fingerprint=hash(json.valueToTree(r).toString());
        submissions.reserve(requestKey,actor.accountId(),fingerprint,Instant.now(clock));
        var submission=submissions.lockByKey(requestKey).orElseThrow();
        if(!actor.accountId().equals(submission.getActorAccountId())||!fingerprint.equals(submission.getRequestFingerprint()))
            throw new ConflictException("Idempotency-Key was used for a different test sequence");
        if(submission.getJobId()!=null){
            var previous=jobs.findById(submission.getJobId()).orElseThrow(()->new ResourceNotFoundException("Original test job was removed; this key cannot replay physical actions"));
            return new Result(previous.getJobId(),previous.getWorkflow().getWorkflowId(),previous.getStatus().name(),true);
        }
        if(hardware(r)&&!mqtt.getEnabledStepTypes().contains("CONTROL_HARDWARE"))throw new BusinessRuleException("CONTROL_HARDWARE is not enabled on BE");
        if(r.steps().stream().anyMatch(s->s.kind()==Kind.SPEAK||s.kind()==Kind.SPEAK_LAST_TRANSCRIPT)&&!mqtt.getEnabledStepTypes().contains("INTERACT"))
            throw new BusinessRuleException("INTERACT/SPEAK is not enabled on BE");
        if(r.steps().stream().anyMatch(s->s.kind()==Kind.VOICE_CAPTURE||s.kind()==Kind.AUDIO_LOOPBACK)&&!mqtt.getEnabledStepTypes().contains("EXECUTE_SENSING"))
            throw new BusinessRuleException("EXECUTE_SENSING/VOICE_CAPTURE is not enabled on BE");
        if(r.steps().stream().anyMatch(s->s.kind()==Kind.OPEN_SERVO)&&!mqtt.isAllowCommissioningUnlock())
            throw new BusinessRuleException("BE commissioning unlock is disabled");
        if(moves(r)){
            if(r.navigationSessionId()==null)throw new BusinessRuleException("MOVE requires navigationSessionId");
            navigation.requirePreflightReady(r.robotId(),r.navigationSessionId(),r.mapId());
            var ids=r.steps().stream().filter(s->s.kind()==Kind.MOVE).map(TestStep::endpointId).distinct().toList();
            var points=new HashMap<UUID,Endpoint>();endpoints.findAllById(ids).forEach(e->points.put(e.getEndpointId(),e));
            for(UUID id:ids){var p=points.get(id);if(p==null||!p.isActive()||!p.isAccessible()||!p.getMap().getMapId().equals(r.mapId()))
                throw new BusinessRuleException("MOVE endpoint must be active, accessible and on the selected map");}
        }
        var graph=preview(r);String code="WF_ROBOT_TEST_"+hash(json.valueToTree(graph).toString()).substring(0,32).toUpperCase(Locale.ROOT);
        var wf=workflowRows.findFirstByWorkflowCodeIgnoreCaseAndStatusAndAcceptsNewJobsTrueOrderByVersionNoDesc(code,WorkflowStatus.PUBLISHED).orElse(null);
        UUID workflowId;
        if(wf!=null)workflowId=wf.getWorkflowId();
        else{
            ObjectNode tree=json.valueToTree(graph);
            if(workflowRows.existsByWorkflowCodeIgnoreCaseAndVersionNo(code,1))code="WF_ROBOT_TEST_"+UUID.randomUUID().toString().replace("-","").toUpperCase(Locale.ROOT);
            ((ObjectNode)tree.path("workflow")).put("workflowCode",code);
            try{graph=json.treeToValue(tree,GraphCreateRequest.class);}catch(Exception e){throw new IllegalStateException(e);}
            workflowId=workflows.createGraph(graph,actor.accountId(),metadata).workflow().workflowId();
            workflows.validate(workflowId,actor.accountId(),metadata);workflows.publish(workflowId,actor.accountId(),metadata);
        }
        ObjectNode input=json.createObjectNode().put("test_request_key",requestKey).put("test_request_fingerprint",fingerprint)
                .put("requested_by_account_id",actor.accountId().toString());input.set("test_sequence",json.valueToTree(r.steps()));
        ObjectNode confirmations=input.putObject("test_confirm_keys");
        for(int i=0;i<r.steps().size();i++)if(r.steps().get(i).kind()==Kind.WAIT_CONFIRM)
            confirmations.put(String.format(Locale.ROOT,"TEST_%02d",i+1),UUID.randomUUID().toString());
        if(moves(r))input.put("navigation_session_id",r.navigationSessionId().toString()).put("map_id",r.mapId().toString());
        var job=execution.createForTest(workflowId,r.robotId(),moves(r)?r.mapId():null,moves(r)?r.navigationSessionId():null,input);
        submission.setJobId(job.getJobId());
        return new Result(job.getJobId(),workflowId,job.getStatus().name(),false);
    }
    @Transactional(readOnly=true)
    public Result lookup(String key,AccountPrincipal actor){
        if(key==null||!key.matches("[A-Za-z0-9._:-]{1,150}"))throw new BusinessRuleException("Invalid Idempotency-Key");
        var submission=submissions.findById(hash(actor.accountId()+":"+key)).orElse(null);
        var previous=submission==null||submission.getJobId()==null?null:jobs.findById(submission.getJobId()).orElse(null);
        return previous==null?null:new Result(previous.getJobId(),previous.getWorkflow().getWorkflowId(),previous.getStatus().name(),true);
    }
    @Transactional(readOnly=true)
    public ObjectNode configuration(UUID robotId){
        robots.findById(robotId).orElseThrow(()->new ResourceNotFoundException("Robot not found"));
        var link=links.findById(robotId).orElse(null);JsonNode hello=link==null||link.getHelloJson()==null?json.createObjectNode():link.getHelloJson();
        ObjectNode result=json.createObjectNode().put("robotId",robotId.toString()).put("commissioningUnlockEnabled",mqtt.isAllowCommissioningUnlock())
                .put("piUnlockPolicy",hello.path("unlockPolicy").asText("UNKNOWN"));
        result.set("beEnabledStepTypes",json.valueToTree(mqtt.getEnabledStepTypes()));result.set("piEnabledStepTypes",hello.path("enabledStepTypes"));
        result.putObject("servoContract").put("model","MG996R").put("port","S6").put("rosId",4)
                .put("openRaw",2400).put("closeRaw",1500).put("physicalFeedbackAvailable",false);
        return result;
    }
}
