package com.mediassistrover.backend.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediassistrover.backend.dto.request.request.ServiceRequestRequests.NavigationCreateRequest;
import com.mediassistrover.backend.dto.request.response.ServiceRequestResponses.DeviceRequestDetailResponse;
import com.mediassistrover.backend.entity.Endpoint;
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

/** Voice selects a real endpoint; a trusted MOVE -> SPEAK workflow owns execution. */
@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class VoiceNavigationService {
    public static final String WORKFLOW_CODE="WF_VOICE_ENDPOINT_V1";
    public static final double DEFAULT_SPEED_MPS = 0.35;
    public record Create(@NotNull UUID navigationSessionId,@NotNull UUID endpointId,
        @DecimalMin("0.10") @DecimalMax("0.35") Double maxSpeedMps,UUID voiceInteractionId) { }
    private final NavigationSessionService sessions;
    private final DeviceResourceScopePolicy scope;
    private final EndpointRepository endpoints;
    private final WorkflowRepository workflows;
    private final ServiceRequestRepository rows;
    private final ServiceRequestService requests;
    private final JobRepository jobs;
    private final MqttWire wire;

    public ObjectNode destinations(DevicePrincipal device) {
        scope.requireCurrentDevice(device);
        var session=sessions.device(device);UUID mapId=UUID.fromString(session.path("mapId").asText());
        var result=wire.object();result.set("navigationSession",session);
        // Queried only on wake/explicit confirmation, never in a polling loop.
        // Include server-side steps and gaps between Pi commands in this job fence.
        result.put("robotAvailable",jobs.findRobotJobIds(device.robotId(),List.of(
            JobStatus.QUEUED,JobStatus.ACCEPTED,JobStatus.RUNNING,JobStatus.RECOVERY_REQUIRED)).isEmpty());
        result.put("workflowCode",WORKFLOW_CODE);
        var workflow=workflows.findFirstByWorkflowCodeIgnoreCaseAndStatusAndAcceptsNewJobsTrueOrderByVersionNoDesc(WORKFLOW_CODE,WorkflowStatus.PUBLISHED);
        result.put("workflowPrepared",workflow.isPresent());
        var points=result.putArray("endpoints");
        endpoints.findByMapMapIdOrderByEndpointCodeAsc(mapId).stream().filter(e->e.isActive()&&e.isAccessible()).forEach(e->{
            var p=points.addObject().put("endpointId",e.getEndpointId().toString()).put("mapId",mapId.toString())
                .put("endpointCode",e.getEndpointCode()).put("endpointName",e.getEndpointName())
                .put("spokenLabel",spokenDestination(e));
            if(e.getRoom()!=null&&e.getRoom().isActive())p.put("roomCode",e.getRoom().getRoomCode()).put("roomName",e.getRoom().getRoomName());
        });
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
                ||!input.endpointId().toString().equals(prior.getRequestDataJson().path("endpoint_id").asText())
                ||!input.navigationSessionId().toString().equals(prior.getRequestDataJson().path("navigation_session_id").asText())
                ||!Objects.equals(input.voiceInteractionId()==null?null:input.voiceInteractionId().toString(),
                    prior.getRequestDataJson().hasNonNull("voice_interaction_id")?prior.getRequestDataJson().path("voice_interaction_id").asText():null)
                ||Double.compare(speed,prior.getRequestDataJson().path("max_speed_mps").asDouble())!=0)
                throw new ConflictException("Idempotency-Key belongs to another voice navigation request");
            return requests.getDeviceRequest(prior.getServiceRequestId(),device);
        }
        var session=sessions.device(device);
        if(!input.navigationSessionId().toString().equals(session.path("sessionId").asText())||!"READY".equals(session.path("state").asText()))
            throw new BusinessRuleException("VOICE_NAVIGATION_SESSION_NOT_READY");
        UUID mapId=UUID.fromString(session.path("mapId").asText());
        var target=endpoints.findById(input.endpointId()).orElseThrow(()->new ResourceNotFoundException("Endpoint not found"));
        if(!target.isActive()||!target.isAccessible()||!mapId.equals(target.getMap().getMapId()))
            throw new BusinessRuleException("Voice destination must be an active accessible endpoint in this session map");
        var workflow=workflows.findFirstByWorkflowCodeIgnoreCaseAndStatusAndAcceptsNewJobsTrueOrderByVersionNoDesc(WORKFLOW_CODE,WorkflowStatus.PUBLISHED)
            .orElseThrow(()->new BusinessRuleException("VOICE_NAVIGATION_WORKFLOW_NOT_PREPARED: prepare WF_VOICE_ENDPOINT_V1 on mapping.html"));
        var data=wire.object().put("voice_navigation",true).put("max_speed_mps",speed)
            .put("arrival_text",arrivalText(target));
        if(input.voiceInteractionId()!=null)data.put("voice_interaction_id",input.voiceInteractionId().toString());
        var body=new NavigationCreateRequest(mapId,workflow.getWorkflowId(),null,null,target.getEndpointId(),null,
            TargetMode.ENDPOINT,NavigationMode.STANDARD,null,1,null,50,"Confirmed voice destination: "+target.getEndpointCode(),
            data,device.robotId(),input.navigationSessionId());
        var created=requests.createDeviceNavigation(body,key,device,metadata);
        return requests.getDeviceRequest(created.request().serviceRequestId(),device);
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
