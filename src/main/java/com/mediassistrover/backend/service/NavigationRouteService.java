package com.mediassistrover.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediassistrover.backend.dto.request.request.ServiceRequestRequests.NavigationCreateRequest;
import com.mediassistrover.backend.dto.request.response.ServiceRequestResponses.ServiceRequestResponse;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.exception.*;
import com.mediassistrover.backend.repository.*;
import com.mediassistrover.backend.security.AccountPrincipal;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service @RequiredArgsConstructor
public class NavigationRouteService {
    public record Create(@NotNull UUID robotId,@NotNull UUID mapId,@NotNull UUID navigationSessionId,
        @NotEmpty @Size(max=20) List<@NotNull UUID> endpointIds,
        @DecimalMin("0.05") @DecimalMax("0.60") Double maxSpeedMps,
        Boolean submit,@Size(max=4000) String note) { }
    public record Result(UUID workflowId,List<UUID> endpointIds,ServiceRequestResponse request) { }
    private final NavigationRouteWorkflowFactory factory;
    private final WorkflowService workflows;
    private final ServiceRequestService requests;
    private final ServiceRequestRepository requestRows;
    private final EndpointRepository endpoints;
    private final RobotRepository robots;
    private final NavigationSessionService sessions;
    private final ObjectMapper json;
    private final com.mediassistrover.backend.mqtt.MqttRolloutService rollout;

    @Transactional public Result create(Create r,String key,AccountPrincipal actor,RequestMetadata metadata) {
        if(key==null||key.isBlank()||key.length()>150)throw new BusinessRuleException("Idempotency-Key is required, at most 150 characters");
        if(r.endpointIds()==null||r.endpointIds().isEmpty()||r.endpointIds().size()>20||r.endpointIds().stream().anyMatch(Objects::isNull))
            throw new BusinessRuleException("Route requires 1..20 endpoint IDs");
        double speed=r.maxSpeedMps()==null?.6:r.maxSpeedMps();
        if(!Double.isFinite(speed)||speed<.05||speed>.6)throw new BusinessRuleException("Route speed must be 0.05..0.60 m/s");
        // Match runtime lock ordering: rollout, then robot. Prevent concurrent
        // route creation from changing ownership or submitting duplicate work.
        rollout.lockForRuntimeCreation();
        robots.findByIdForUpdate(r.robotId()).orElseThrow(()->new ResourceNotFoundException("Robot not found"));
        var existing=requestRows.findByIdempotencyKey(key).orElse(null);UUID workflowId;
        if(existing!=null) {
            if(existing.getRequestType()!=ServiceRequestType.NAVIGATION||existing.getRequestedByAccount()==null
                ||!existing.getRequestedByAccount().getAccountId().equals(actor.accountId())
                ||!existing.getRequestDataJson().has("route_endpoint_ids"))
                throw new ConflictException("Idempotency-Key belongs to another request");
            workflowId=UUID.fromString(existing.getRequestDataJson().path("workflow_id").asText());
        }else {
            sessions.requireReadyContext(r.robotId(),r.navigationSessionId(),r.mapId());
            for(UUID id:r.endpointIds()) {
                var e=endpoints.findById(id).orElseThrow(()->new ResourceNotFoundException("Route endpoint not found: "+id));
                if(!e.isActive()||!e.isAccessible()||!e.getMap().getMapId().equals(r.mapId()))
                    throw new BusinessRuleException("Every route endpoint must be active, accessible and on the selected map");
            }
            var definition=workflows.createGraph(factory.build(r.endpointIds().size(),speed),actor.accountId(),metadata);
            workflowId=definition.workflow().workflowId();
            workflows.validate(workflowId,actor.accountId(),metadata);workflows.publish(workflowId,actor.accountId(),metadata);
        }
        var data=json.createObjectNode();data.set("route_endpoint_ids",json.valueToTree(r.endpointIds()));
        data.set("route_waypoint_ids",json.valueToTree(r.endpointIds().subList(0,r.endpointIds().size()-1)));
        boolean submit=r.submit()==null||r.submit();data.put("route_speed_mps",speed).put("route_auto_submit",submit);
        var body=new NavigationCreateRequest(r.mapId(),workflowId,null,null,r.endpointIds().getLast(),null,
            TargetMode.ENDPOINT,NavigationMode.STANDARD,null,1,null,50,r.note(),data,r.robotId(),r.navigationSessionId());
        var created=requests.createNavigation(body,key,actor,metadata);
        var response=submit&&created.request().status()==ServiceRequestStatus.DRAFT
            ?requests.submit(created.request().serviceRequestId(),actor,metadata):created.request();
        return new Result(workflowId,List.copyOf(r.endpointIds()),response);
    }
}
