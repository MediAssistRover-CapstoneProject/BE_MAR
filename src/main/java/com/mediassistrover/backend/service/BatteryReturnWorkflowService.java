package com.mediassistrover.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.exception.BusinessRuleException;
import com.mediassistrover.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.UUID;

/** Trusted, compiler-validated system definition using the existing Pi SPEAK and MOVE contracts. */
@Service @RequiredArgsConstructor
public class BatteryReturnWorkflowService {
    public static final String CODE="WF_BATTERY_RETURN_V1";
    public static final String WARNING_CODE="WF_BATTERY_WARNING_V1";
    private final WorkflowRepository workflows;
    private final TaskRepository tasks;
    private final StepRepository steps;
    private final StepTransitionRepository transitions;
    private final WorkflowValidationService compiler;
    private final com.mediassistrover.backend.validation.WorkflowExecutionReadiness readiness;
    private final com.mediassistrover.backend.mqtt.MqttRolloutService rollout;
    private final ObjectMapper json;
    private final Clock clock;

    @Transactional public UUID ensure() {return prepare(false);}
    @Transactional public UUID ensureWarning() {return prepare(true);}
    private UUID prepare(boolean warning) {
        var policy=rollout.lockForRuntimeCreation();rollout.requireSchema(policy,3);
        String code=warning?WARNING_CODE:CODE;
        var prior=workflows.findTopByWorkflowCodeIgnoreCaseOrderByVersionNoDesc(code).orElse(null);
        if(prior!=null) {
            if(prior.getStatus()!=WorkflowStatus.PUBLISHED || !prior.isAcceptsNewJobs()
                    || prior.getSchemaVersion()!=3 || prior.getDefinitionHash()==null)
                throw new BusinessRuleException("Battery return workflow is disabled; publish a reviewed version before returning");
            return prior.getWorkflowId();
        }
        var schema=json.createObjectNode().put("type","object").put("additionalProperties",true);
        var properties=schema.putObject("properties");
        for(String name:new String[]{"map_id","endpoint_id","navigation_session_id","battery_episode_id"})
            properties.putObject(name).put("type","string").put("format","uuid");
        var required=schema.putArray("required");required.add("battery_episode_id");
        if(!warning)required.add("map_id").add("endpoint_id").add("navigation_session_id");
        properties.putObject("announcement_text").put("type","string").put("minLength",1).put("maxLength",500);
        if(warning)required.add("announcement_text");
        Workflow workflow=workflows.saveAndFlush(Workflow.builder().workflowCode(code).versionNo(1)
                .workflowName(warning?"Battery return announcement":"Battery return to charger").serviceType("AUTO_CHARGING")
                .description(warning?"Announce return after confirmed physical stop; no MOVE or localization requirement.":"Navigate to an active CHARGER endpoint after the warning; arrival does not prove charging contacts.")
                .inputSchemaJson(schema).inputUiJson(json.createObjectNode()).defaultInputsJson(json.createObjectNode())
                .outputMappingJson(json.createObjectNode()).requiredCapabilitiesJson(json.createArrayNode().add(warning?"INTERACT":"MOVE"))
                .maxDurationSec(warning?120:360).maxTotalVisits(1).graphVersion(3).schemaVersion(3)
                .status(WorkflowStatus.DRAFT).acceptsNewJobs(false).build());
        Task task=tasks.saveAndFlush(Task.builder().workflow(workflow).taskCode("RETURN_TO_CHARGER")
                .taskName("Return to charger").sequenceNo(1).configJson(json.createObjectNode()).build());
        var speech=json.createObjectNode();
        var data=speech.putObject("data");data.put("language_code","vi").put("speed_wpm",150).put("volume",100);
        data.putObject("text").put("$ref","/inputs/announcement_text");
        var mapping=json.createObjectNode();
        mapping.putObject("map_id").put("$ref","/inputs/map_id");mapping.putObject("endpoint_id").put("$ref","/inputs/endpoint_id");
        Step action=warning?step(workflow,task,StepType.INTERACT,"BATTERY_SPEAK",1,90,
                json.createObjectNode().put("prompt_key","LOW_BATTERY_RETURN").put("action","SPEAK"),speech)
                :step(workflow,task,StepType.MOVE,"MOVE_TO_CHARGER",1,300,
                    json.createObjectNode().put("mode","NORMAL").put("max_speed_mps",0.10),mapping);
        workflow.setStartStep(action);
        for(Step current:new Step[]{action})for(TransitionOutcome outcome:TransitionOutcome.values()) {
            var edge=StepTransition.builder().workflow(workflow).fromStep(current).outcome(outcome).priority(1).defaultTransition(true);
            edge.terminalStatus(outcome==TransitionOutcome.SUCCESS?TerminalStatus.SUCCEEDED:
                    outcome==TransitionOutcome.TIMEOUT?TerminalStatus.RECOVERY_REQUIRED:TerminalStatus.FAILED);
            transitions.save(edge.build());
        }
        transitions.flush();
        var compiled=compiler.validateAndCompile(workflow);readiness.requireReady(workflow);
        workflow.setCompiledDefinitionJson(compiled.compiledDefinition());workflow.setDefinitionHash(compiled.definitionHash());
        workflow.setStatus(WorkflowStatus.PUBLISHED);workflow.setAcceptsNewJobs(true);workflow.setPublishedAt(clock.instant());
        return workflow.getWorkflowId();
    }

    private Step step(Workflow workflow,Task task,StepType type,String code,int order,int timeout,
                      com.fasterxml.jackson.databind.JsonNode params,com.fasterxml.jackson.databind.JsonNode mapping) {
        return steps.saveAndFlush(Step.builder().workflow(workflow).task(task).stepType(type).stepCode(code).stepName(code)
                .sequenceNo(order).enabled(true).paramsJson(params).inputMappingJson(mapping).timeoutSec(timeout)
                .maxAttempts(1).retryDelaySec(0).retryCodesJson(json.createArrayNode()).maxVisits(1).auditLevel(StepAuditLevel.FULL).build());
    }
}
