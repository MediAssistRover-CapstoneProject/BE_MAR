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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service @RequiredArgsConstructor @Slf4j
public class NavigationRouteService {
    public record Create(@NotNull UUID robotId,@NotNull UUID mapId,@NotNull UUID navigationSessionId,
        @NotEmpty @Size(max=20) List<@NotNull UUID> endpointIds,
        @DecimalMin("0.05") @DecimalMax("0.60") Double maxSpeedMps,
        Boolean submit,@Size(max=4000) String note) { }
    public record Result(UUID workflowId,List<UUID> endpointIds,ServiceRequestResponse request) { }
    private final NavigationRouteWorkflowFactory factory;
    private final WorkflowService workflows;
    private final WorkflowRepository workflowRows;
    private final ServiceRequestService requests;
    private final ServiceRequestRepository requestRows;
    private final EndpointRepository endpoints;
    private final RobotRepository robots;
    private final NavigationSessionService sessions;
    private final ObjectMapper json;
    private final com.mediassistrover.backend.mqtt.MqttRolloutService rollout;

    @Transactional public Result create(Create r,String key,AccountPrincipal actor,RequestMetadata metadata) {
        long started=System.nanoTime();boolean reused=false;
        if(key==null||key.isBlank()||key.length()>150)throw new BusinessRuleException("Idempotency-Key is required, at most 150 characters");
        if(r.endpointIds()==null||r.endpointIds().isEmpty()||r.endpointIds().size()>20||r.endpointIds().stream().anyMatch(Objects::isNull))
            throw new BusinessRuleException("Route requires 1..20 endpoint IDs");
        double speed=r.maxSpeedMps()==null?.6:r.maxSpeedMps();
        if(!Double.isFinite(speed)||speed<.05||speed>.6)throw new BusinessRuleException("Route speed must be 0.05..0.60 m/s");
        // Serialize template creation through rollout, but do not hold Robot
        // while compiling/publishing a new graph: heartbeats need that lock.
        // Submission below still acquires rollout then robot in runtime order.
        rollout.lockForRuntimeCreation();
        var existing=requestRows.findByIdempotencyKey(key).orElse(null);UUID workflowId;
        if(existing!=null) {
            if(existing.getRequestType()!=ServiceRequestType.NAVIGATION||existing.getRequestedByAccount()==null
                ||!existing.getRequestedByAccount().getAccountId().equals(actor.accountId())
                ||!existing.getRequestDataJson().has("route_endpoint_ids"))
                throw new ConflictException("Idempotency-Key belongs to another request");
            workflowId=UUID.fromString(existing.getRequestDataJson().path("workflow_id").asText());
        }else {
            sessions.requireReadyContext(r.robotId(),r.navigationSessionId(),r.mapId());
            var points=new HashMap<UUID,com.mediassistrover.backend.entity.Endpoint>();
            endpoints.findAllById(new HashSet<>(r.endpointIds())).forEach(e->points.put(e.getEndpointId(),e));
            for(UUID id:r.endpointIds()) {
                var e=points.get(id);
                if(e==null)throw new ResourceNotFoundException("Route endpoint not found: "+id);
                if(!e.isActive()||!e.isAccessible()||!e.getMap().getMapId().equals(r.mapId()))
                    throw new BusinessRuleException("Every route endpoint must be active, accessible and on the selected map");
            }
            var graph=factory.build(r.endpointIds().size(),speed);
            var reusable=workflowRows.findFirstByWorkflowCodeIgnoreCaseAndStatusAndAcceptsNewJobsTrueOrderByVersionNoDesc(
                graph.workflow().workflowCode(),WorkflowStatus.PUBLISHED);
            if(reusable.isPresent()&&reusable.get().getSchemaVersion()==3&&reusable.get().getGraphVersion()==3
                    &&reusable.get().getDefinitionHash()!=null&&reusable.get().getPublishedAt()!=null){
                workflowId=reusable.get().getWorkflowId();
                reused=true;
            }else{
                // Archived/disabled definitions are never re-enabled implicitly.
                // Use a new identity if an administrator retired this template.
                if(workflowRows.existsByWorkflowCodeIgnoreCaseAndVersionNo(graph.workflow().workflowCode(),1)){
                    var w=graph.workflow();
                    graph=new com.mediassistrover.backend.dto.workflow.request.WorkflowRequests.GraphCreateRequest(
                        new com.mediassistrover.backend.dto.workflow.request.WorkflowRequests.WorkflowUpsertRequest(
                            "WF_NAV_ROUTE_"+UUID.randomUUID().toString().replace("-","").toUpperCase(),1,
                            w.workflowName(),w.serviceType(),w.description(),w.inputSchemaJson(),w.inputUiJson(),
                            w.defaultInputsJson(),w.outputMappingJson(),w.requiredCapabilitiesJson(),w.maxDurationSec(),
                            w.maxTotalVisits(),w.graphVersion(),w.schemaVersion()),graph.graph());
                }
                var definition=workflows.createGraph(graph,actor.accountId(),metadata);
                workflowId=definition.workflow().workflowId();
                workflows.validate(workflowId,actor.accountId(),metadata);workflows.publish(workflowId,actor.accountId(),metadata);
            }
        }
        robots.findByIdForUpdate(r.robotId()).orElseThrow(()->new ResourceNotFoundException("Robot not found"));
        if(existing==null)sessions.pinReadyContextForLockedRoute(r.robotId(),r.navigationSessionId(),r.mapId());
        long prepared=System.nanoTime();
        var data=json.createObjectNode();data.set("route_endpoint_ids",json.valueToTree(r.endpointIds()));
        data.set("route_waypoint_ids",json.valueToTree(r.endpointIds().subList(0,r.endpointIds().size()-1)));
        boolean submit=r.submit()==null||r.submit();data.put("route_speed_mps",speed).put("route_auto_submit",submit);
        var body=new NavigationCreateRequest(r.mapId(),workflowId,null,null,r.endpointIds().getLast(),null,
            TargetMode.ENDPOINT,NavigationMode.STANDARD,null,1,null,50,r.note(),data,r.robotId(),r.navigationSessionId());
        var created=requests.createNavigation(body,key,actor,metadata);
        var response=submit&&created.request().status()==ServiceRequestStatus.DRAFT
            ?requests.submit(created.request().serviceRequestId(),actor,metadata):created.request();
        log.info("Navigation route prepared: robot={} request={} stops={} reusedWorkflow={} prepareMs={} requestMs={} totalMs={}",
            r.robotId(),response.serviceRequestId(),r.endpointIds().size(),reused,
            (prepared-started)/1_000_000,(System.nanoTime()-prepared)/1_000_000,(System.nanoTime()-started)/1_000_000);
        return new Result(workflowId,List.copyOf(r.endpointIds()),response);
    }
}
