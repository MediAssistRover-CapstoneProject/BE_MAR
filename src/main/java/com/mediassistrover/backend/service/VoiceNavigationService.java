package com.mediassistrover.backend.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediassistrover.backend.dto.request.request.ServiceRequestRequests.NavigationCreateRequest;
import com.mediassistrover.backend.dto.request.response.ServiceRequestResponses.DeviceRequestDetailResponse;
import com.mediassistrover.backend.entity.Endpoint;
import com.mediassistrover.backend.entity.EndpointGroup;
import com.mediassistrover.backend.entity.EndpointGroupMember;
import com.mediassistrover.backend.entity.Workflow;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.exception.*;
import com.mediassistrover.backend.mqtt.MqttWire;
import com.mediassistrover.backend.repository.*;
import com.mediassistrover.backend.security.DevicePrincipal;
import com.mediassistrover.backend.validation.DeviceResourceScopePolicy;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Voice selects a real endpoint; the published workflow owns execution. */
@Service @RequiredArgsConstructor(onConstructor_=@org.springframework.beans.factory.annotation.Autowired) @Transactional(readOnly=true)
public class VoiceNavigationService {
    public static final String WORKFLOW_CODE="WF_VOICE_ENDPOINT_V1";
    public static final double DEFAULT_SPEED_MPS = 0.35;
    public record Create(@NotNull UUID navigationSessionId,@NotNull UUID endpointId,
        @DecimalMin("0.10") @DecimalMax("0.35") Double maxSpeedMps,UUID voiceInteractionId,
        Boolean workflowOwnsInvite) { }
    private final NavigationSessionService sessions;
    private final DeviceResourceScopePolicy scope;
    private final EndpointRepository endpoints;
    private final WorkflowRepository workflows;
    private final ServiceRequestRepository rows;
    private final ServiceRequestService requests;
    private final JobRepository jobs;
    private final MqttWire wire;
    private final com.mediassistrover.backend.speech.SpeechSettingsService speechSettings;
    private final VoiceAdmissionService admission;
    private final EndpointGroupMemberRepository members;

    // Retain the constructor used by existing integrations that only read the voice catalog.
    public VoiceNavigationService(NavigationSessionService sessions,DeviceResourceScopePolicy scope,EndpointRepository endpoints,
            WorkflowRepository workflows,ServiceRequestRepository rows,ServiceRequestService requests,JobRepository jobs,
            MqttWire wire,com.mediassistrover.backend.speech.SpeechSettingsService speechSettings,VoiceAdmissionService admission) {
        this(sessions,scope,endpoints,workflows,rows,requests,jobs,wire,speechSettings,admission,null);
    }

    public ObjectNode destinations(DevicePrincipal device) {
        scope.requireCurrentDevice(device);
        var session=sessions.device(device);UUID mapId=UUID.fromString(session.path("mapId").asText());
        var result=wire.object();result.set("navigationSession",session);
        // Queried only on wake/explicit confirmation, never in a polling loop.
        // Include server-side steps and gaps between Pi commands in this job fence.
        result.put("robotAvailable",admission.current(device,null).conversationReady());
        result.put("workflowCode",WORKFLOW_CODE);
        var workflow=workflows.findFirstByWorkflowCodeIgnoreCaseAndStatusAndAcceptsNewJobsTrueOrderByVersionNoDesc(WORKFLOW_CODE,WorkflowStatus.PUBLISHED);
        result.put("workflowPrepared",workflow.isPresent());
        result.put("workflowOwnsInvite",workflow.map(VoiceNavigationService::ownsInvite).orElse(false));
        result.set("speechProfile",wire.tree(speechSettings.current()));
        var points=result.putArray("endpoints");
        endpoints.findByMapMapIdOrderByEndpointCodeAsc(mapId).stream().filter(e->e.isActive()&&e.isAccessible()).forEach(e->{
            var p=points.addObject().put("endpointId",e.getEndpointId().toString()).put("mapId",mapId.toString())
                .put("endpointCode",e.getEndpointCode()).put("endpointName",e.getEndpointName())
                .put("spokenLabel",spokenDestination(e));
            if(e.getRoom()!=null&&e.getRoom().isActive())p.put("roomCode",e.getRoom().getRoomCode()).put("roomName",e.getRoom().getRoomName());
        });
        // Clients can reuse labels/profile within this map/session. READY, expiry,
        // job availability and request authorization remain live checks above/in create().
        ObjectNode catalog=wire.object().set("endpoints",points);
        catalog.set("speechProfile",result.path("speechProfile"));
        catalog.put("sessionId",session.path("sessionId").asText());
        catalog.put("mapId",mapId.toString());
        catalog.put("mapAssetHash",session.path("mapAssetHash").asText());
        catalog.put("initializationRevision",session.path("initialization").path("revision").asLong());
        catalog.put("workflowId",workflow.map(w->w.getWorkflowId().toString()).orElse(""));
        catalog.put("workflowOwnsInvite",result.path("workflowOwnsInvite").asBoolean());
        result.put("catalogRevision",wire.canonicalHash(catalog));
        return result;
    }

    @Transactional public DeviceRequestDetailResponse create(Create input,String key,DevicePrincipal device,RequestMetadata metadata) {
        scope.requireCurrentDevice(device);
        if(key==null||key.isBlank()||key.length()>150)throw new BusinessRuleException("Idempotency-Key is required, at most 150 characters");
        double speed = input.maxSpeedMps() == null ? DEFAULT_SPEED_MPS : input.maxSpeedMps();
        if (!Double.isFinite(speed) || speed < 0.10 || speed > 0.35)
            throw new BusinessRuleException("Voice navigation speed must be 0.10..0.35 m/s");
        var prior=rows.findByIdempotencyKey(key).orElse(null);
        if(prior!=null){
            if(prior.getRequestedRobot()==null||!prior.getRequestedRobot().getRobotId().equals(device.robotId())
                ||prior.getRequestType()!=ServiceRequestType.NAVIGATION||!prior.getRequestDataJson().path("voice_navigation").asBoolean()
                ||!input.endpointId().toString().equals(prior.getRequestDataJson().path("voice_requested_endpoint_id")
                    .asText(prior.getRequestDataJson().path("endpoint_id").asText()))
                ||!input.navigationSessionId().toString().equals(prior.getRequestDataJson().path("navigation_session_id").asText())
                ||!Objects.equals(input.voiceInteractionId()==null?null:input.voiceInteractionId().toString(),
                    prior.getRequestDataJson().hasNonNull("voice_interaction_id")?prior.getRequestDataJson().path("voice_interaction_id").asText():null)
                ||!Objects.equals(input.workflowOwnsInvite(),prior.getRequestDataJson().hasNonNull("workflow_owns_invite")
                    ?prior.getRequestDataJson().path("workflow_owns_invite").asBoolean():null)
                ||Double.compare(speed,prior.getRequestDataJson().path("max_speed_mps").asDouble())!=0)
                throw new ConflictException("Idempotency-Key belongs to another voice navigation request");
            return requests.getDeviceRequest(prior.getServiceRequestId(),device);
        }
        if(!admission.current(device,input.voiceInteractionId()).conversationReady())
            throw new BusinessRuleException("VOICE_ROBOT_NOT_READY: wait for confirmed patrol suspension or the current foreground task");
        var session=sessions.device(device);
        if(!input.navigationSessionId().toString().equals(session.path("sessionId").asText())||!"READY".equals(session.path("state").asText()))
            throw new BusinessRuleException("VOICE_NAVIGATION_SESSION_NOT_READY");
        UUID mapId=UUID.fromString(session.path("mapId").asText());
        var target=endpoints.findById(input.endpointId()).orElseThrow(()->new ResourceNotFoundException("Endpoint not found"));
        if(!target.isActive()||!target.isAccessible()||!mapId.equals(target.getMap().getMapId()))
            throw new BusinessRuleException("Voice destination must be an active accessible endpoint in this session map");
        // Older Pi clients speak the invitation themselves. Keep their dispatch on the MOVE-first workflow.
        var selected=input.workflowOwnsInvite()==null
                ?workflows.findFirstByWorkflowCodeIgnoreCaseAndStatusAndAcceptsNewJobsTrueAndStartStepStepTypeOrderByVersionNoDesc(
                    WORKFLOW_CODE,WorkflowStatus.PUBLISHED,StepType.MOVE)
                :workflows.findFirstByWorkflowCodeIgnoreCaseAndStatusAndAcceptsNewJobsTrueOrderByVersionNoDesc(
                    WORKFLOW_CODE,WorkflowStatus.PUBLISHED);
        var workflow=selected.orElseThrow(()->new BusinessRuleException(
                "VOICE_NAVIGATION_WORKFLOW_NOT_PREPARED: prepare WF_VOICE_ENDPOINT_V1 on mapping.html"));
        if(input.workflowOwnsInvite()!=null && input.workflowOwnsInvite()!=ownsInvite(workflow))
            throw new ConflictException("VOICE_NAVIGATION_WORKFLOW_CHANGED: refresh the catalog and confirm again");
        EndpointGroup group=input.workflowOwnsInvite()==null?null:fallbackGroup(target,mapId);
        if(group!=null && (!workflow.getInputSchemaJson().path("properties").has("endpoint_group_id")
                || !java.util.stream.StreamSupport.stream(workflow.getInputSchemaJson().path("properties")
                    .path("target_mode").path("enum").spliterator(),false).anyMatch(value->"ENDPOINT_GROUP".equals(value.asText()))))
            throw new BusinessRuleException("VOICE_NAVIGATION_WORKFLOW_NOT_PREPARED: prepare the new endpoint-group workflow on mapping.html");
        var data=wire.object().put("voice_navigation",true).put("max_speed_mps",speed)
            .put("arrival_text",arrivalText(target)).put("voice_requested_endpoint_id",target.getEndpointId().toString());
        if(input.workflowOwnsInvite()!=null)data.put("workflow_owns_invite",input.workflowOwnsInvite());
        if(input.voiceInteractionId()!=null)data.put("voice_interaction_id",input.voiceInteractionId().toString());
        var body=new NavigationCreateRequest(mapId,workflow.getWorkflowId(),group==null?null:group.getRoom().getRoomId(),null,
            group==null?target.getEndpointId():null,group==null?null:group.getEndpointGroupId(),
            group==null?TargetMode.ENDPOINT:TargetMode.ENDPOINT_GROUP,NavigationMode.STANDARD,null,1,null,50,"Confirmed voice destination: "+target.getEndpointCode(),
            data,device.robotId(),input.navigationSessionId());
        var created=requests.createDeviceNavigation(body,key,device,metadata);
        return requests.getDeviceRequest(created.request().serviceRequestId(),device);
    }

    private EndpointGroup fallbackGroup(Endpoint primary,UUID mapId) {
        if(members==null || primary.getRoom()==null || !primary.getRoom().isActive())return null;
        var group=primary.getRoom().getDefaultEndpointGroup();
        if(group==null || !group.isActive() || !group.isAllowFallback() || group.getRoom()==null
                || group.getSelectionPolicy()!=SelectionPolicy.PRIORITY || !mapId.equals(group.getMap().getMapId())
                || !primary.getRoom().getRoomId().equals(group.getRoom().getRoomId()))return null;
        var pair=members.findByEndpointGroupEndpointGroupIdOrderBySequenceNo(group.getEndpointGroupId()).stream()
                .filter(EndpointGroupMember::isActive).toList();
        if(pair.size()!=2 || pair.getFirst().getSequenceNo()!=1 || pair.get(1).getSequenceNo()!=2
                || !primary.getEndpointId().equals(pair.getFirst().getEndpoint().getEndpointId())
                || pair.stream().anyMatch(m->!m.getEndpoint().isActive() || !m.getEndpoint().isAccessible()
                    || !mapId.equals(m.getMap().getMapId()) || !mapId.equals(m.getEndpoint().getMap().getMapId())
                    || m.getEndpoint().getRoom()==null || !m.getEndpoint().getRoom().getRoomId().equals(group.getRoom().getRoomId())))return null;
        return group;
    }

    private static boolean ownsInvite(Workflow workflow) {
        var start=workflow.getStartStep();
        return start!=null && start.isEnabled() && "ANNOUNCE_INVITATION".equals(start.getStepCode())
                && start.getStepType()==StepType.INTERACT && "SPEAK".equals(start.getParamsJson().path("action").asText());
    }

    public static String arrivalText(Endpoint endpoint) {
        return "Đã đến " + spokenDestination(endpoint) + " rồi ạ.";
    }

    private static String spokenDestination(Endpoint endpoint){
        String code=endpoint.getEndpointCode().strip();
        String name=endpoint.getEndpointName().isBlank()?code:endpoint.getEndpointName().strip();
        var room=endpoint.getRoom();
        if(room!=null&&room.isActive()&&!room.getRoomName().isBlank()){
            String roomName=room.getRoomName().strip();
            return name.equalsIgnoreCase(code)||name.equalsIgnoreCase(roomName)?roomName:name+", "+roomName;
        }
        return name.matches("[A-Za-z]|[0-9]+")?"điểm "+name.toUpperCase(Locale.ROOT):name;
    }
}
