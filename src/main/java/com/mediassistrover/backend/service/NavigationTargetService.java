package com.mediassistrover.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.exception.BusinessRuleException;
import com.mediassistrover.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class NavigationTargetService {
    private final EndpointGroupRepository groups;
    private final EndpointGroupMemberRepository members;
    private final EndpointRepository endpoints;
    private final JobStepRepository steps;
    private final JobNavigationSelectionRepository selections;
    private final RobotCommandOutboxRepository commands;
    private final RobotLocationHistoryRepository locations;
    private final DeliveryRequestRepository deliveries;
    private final NavigationRequestRepository navigations;
    private final EscortRequestRepository escorts;
    private final ObjectMapper json;
    private final Clock clock;

    public ArrayNode resolve(JobStep current,UUID groupId) {
        Job job=requireServer(current);EndpointGroup group=group(job,groupId);
        List<Endpoint> candidates=new ArrayList<>();
        var orderedMembers=members.findByEndpointGroupEndpointGroupIdOrderBySequenceNo(groupId);
        for(EndpointGroupMember member:orderedMembers) {
            if(!member.getMap().getMapId().equals(group.getMap().getMapId()) || !member.getEndpoint().getMap().getMapId().equals(group.getMap().getMapId()))
                fail("Endpoint group member crosses map scope");
            if(member.isActive() && group.getRoom()!=null && !sameRoom(member.getEndpoint(),group))
                fail("Room endpoint group member crosses room scope");
            if(member.isActive() && usable(member.getEndpoint(),job)) candidates.add(member.getEndpoint());
        }
        if(group.getRoom()!=null && (group.getSelectionPolicy()!=SelectionPolicy.PRIORITY || candidates.size()!=2
                || !orderedMembers.stream().filter(EndpointGroupMember::isActive).map(EndpointGroupMember::getSequenceNo).toList().equals(List.of(1,2))))
            fail("Room fallback requires two active accessible endpoints in priority order");
        if(group.getSelectionPolicy()==SelectionPolicy.NEAREST_REACHABLE) {
            if(job.getRobot()==null) fail("Nearest-target selection requires a robot");
            RobotLocationHistory pose=locations.findFirstByRobotRobotIdAndMapMapIdAndFreshTrueOrderByCapturedAtDesc(job.getRobot().getRobotId(),job.getMap().getMapId())
                    .filter(p->p.getCapturedAt().isAfter(Instant.now(clock).minusSeconds(2)) && !p.getCapturedAt().isAfter(Instant.now(clock)))
                    .orElseThrow(()->new BusinessRuleException("Nearest-target selection requires a fresh pose in this map"));
            candidates.sort(Comparator.comparing(e->distance(e,pose))); // Stable sort retains member priority for equal distance.
        }
        ArrayNode result=json.createArrayNode();candidates.forEach(e->result.add(target(e,group)));return result;
    }

    @Transactional
    public ObjectNode select(JobStep current,JsonNode targets,int index) {
        Job job=requireServer(current);
        JobStep resolved=steps.findByJobId(job.getJobId()).stream().filter(s->s.getSelectedAt()!=null && s.getStatus()==JobStepStatus.SUCCEEDED
                && s.getExecutionSequenceNo()<current.getExecutionSequenceNo() && s.getStep().getStepType()==StepType.DATA_SERVICE
                && "RESOLVE_TARGETS".equals(s.getResolvedInputsJson().path("service_key").asText()))
                .max(Comparator.comparingLong(JobStep::getExecutionSequenceNo)).orElseThrow(()->new BusinessRuleException("SELECT_TARGET requires a selected RESOLVE_TARGETS visit"));
        if(resolved.getResultJson()==null || !resolved.getResultJson().path("data").path("targets").equals(targets)) fail("Targets differ from the BE RESOLVE_TARGETS snapshot");
        EndpointGroup group=group(job,UUID.fromString(resolved.getResolvedInputsJson().path("endpoint_group_id").asText()));
        // A fresh RESOLVE visit starts a new checkpoint/round, including repeated patrols.
        List<JobNavigationSelection> previous=selections.history(job.getJobId(),group.getEndpointGroupId()).stream()
                .filter(s->s.getResolveJobStep().getJobStepId().equals(resolved.getJobStepId())).toList();
        if(!previous.isEmpty()) {
            if(!group.isAllowFallback()) fail("Endpoint group does not allow fallback");
            JobStep attempted=previous.getFirst().getMoveJobStep();
            if(attempted==null) fail("A selected endpoint must be executed before changing candidates");
            RobotCommandOutbox command=commands.findByJobStepId(attempted.getJobStepId()).orElse(null);
            if(attempted.getStatus()!=JobStepStatus.FAILED || attempted.getInterruptionReason()!=null || !"PATH_UNAVAILABLE".equals(attempted.getErrorCode())
                    || command==null || command.getSendAttempts()<1 || !"COMPLETED".equals(command.getState())
                    || !Boolean.TRUE.equals(command.getPhysicalStopConfirmed()) || !command.getOperationKey().equals(attempted.getOperationKey())
                    || !command.getJobId().equals(job.getJobId())
                    || job.getRobot()==null || !command.getRobotId().equals(job.getRobot().getRobotId()))
                fail("Fallback requires PATH_UNAVAILABLE and committed physical stop evidence");
        }
        ObjectNode result=json.createObjectNode().put("has_target",false);
        if(!targets.isArray() || index<0) fail("Targets/index are invalid");
        if(index!=(previous.isEmpty()?0:previous.getFirst().getTargetIndex()+1)) fail("Candidate index must advance once through the frozen target order");
        if(index>=targets.size()) return result;
        UUID endpointId=UUID.fromString(targets.get(index).path("endpoint_id").asText());
        if(previous.stream().anyMatch(p->p.getEndpoint().getEndpointId().equals(endpointId))) fail("Endpoint was already attempted in this Job/group");
        Endpoint candidate=endpoints.findById(endpointId).orElseThrow(()->new BusinessRuleException("Selected endpoint no longer exists"));
        if(!usable(candidate,job) || members.findById(new EndpointGroupMemberId(group.getEndpointGroupId(),endpointId)).filter(EndpointGroupMember::isActive).isEmpty())
            fail("Selected endpoint is outside the active accessible group/map");
        if(group.getRoom()!=null) {
            if(!sameRoom(candidate,group)) fail("Selected endpoint differs from the requested room");
            if(!previous.isEmpty()) requireDoorObstruction(previous.getFirst(),candidate,group);
        }
        ObjectNode canonical=target(candidate,group);
        if(!sameTarget(canonical,targets.get(index))) fail("Endpoint changed after RESOLVE_TARGETS; do not reinterpret a frozen target");
        if(selections.findBySelectorJobStepJobStepId(current.getJobStepId()).isPresent()) fail("Selection visit is already persisted");
        selections.saveAndFlush(JobNavigationSelection.builder().resolveJobStep(resolved).selectorJobStep(current).endpointGroup(group).endpoint(candidate)
                .targetSnapshotJson(canonical.deepCopy()).targetIndex(index).createdAt(Instant.now(clock)).build());
        result.put("has_target",true).put("endpoint_id",endpointId.toString())
                .put("arrival_text",index>0 && group.getRoom()!=null
                    ?"Robot đã tới điểm đến dự phòng bên ngoài "+group.getRoom().getRoomName()+". Lối vào đang bị chặn."
                    :VoiceNavigationService.arrivalText(candidate)).set("target",canonical);return result;
    }
    public ObjectNode check(JobStep current,UUID endpointId) {
        Job job=requireServer(current);Endpoint candidate=endpoints.findById(endpointId).orElse(null);
        ObjectNode result=json.createObjectNode().put("is_valid",candidate!=null && usable(candidate,job));
        if(result.path("is_valid").asBoolean()) result.set("endpoint",endpoint(candidate));return result;
    }

    /** Bind the selected output to exactly one MOVE visit; Pi only sees its concrete endpoint ID. */
    @Transactional
    public void bindMove(JobStep current,JsonNode inputs) {
        Job job=current.getJobTask().getJob();
        if(!job.isBeOrchestrated() || job.getSchemaVersion()!=3) return;
        String reference=current.getStep().getInputMappingJson().path("endpoint_id").path("$ref").asText();
        var match=java.util.regex.Pattern.compile("^/outputs/([^/]+)/data/endpoint_id$").matcher(reference);
        UUID requestedGroup=requestedGroup(job,inputs);
        if(!match.matches()) {
            if(requestedGroup!=null) fail("Group MOVE must use a committed SELECT_TARGET output directly");
            return;
        }
        JobStep selector=steps.findByJobId(job.getJobId()).stream().filter(s->s.getSelectedAt()!=null && s.getStatus()==JobStepStatus.SUCCEEDED
                && s.getExecutionSequenceNo()<current.getExecutionSequenceNo() && s.getStepCodeSnapshot().equals(match.group(1)))
                .max(Comparator.comparingLong(JobStep::getExecutionSequenceNo)).orElseThrow(()->new BusinessRuleException("MOVE has no committed target-selection output"));
        if(!"SELECT_TARGET".equals(selector.getResolvedInputsJson().path("service_key").asText())) {
            if(requestedGroup!=null) fail("Group MOVE requires SELECT_TARGET authority");
            return;
        }
        JobNavigationSelection selection=selections.findBySelectorJobStepJobStepId(selector.getJobStepId()).orElseThrow();
        if(requestedGroup!=null && !requestedGroup.equals(selection.getEndpointGroup().getEndpointGroupId())) fail("Selected group differs from the requested destination group");
        if(!selection.getEndpoint().getEndpointId().toString().equals(inputs.path("endpoint_id").asText())
                || !selection.getEndpointGroup().getMap().getMapId().toString().equals(inputs.path("map_id").asText())) fail("MOVE does not match the selected endpoint/map");
        if(selection.getMoveJobStep()!=null && !selection.getMoveJobStep().getJobStepId().equals(current.getJobStepId())
                && !stoppedContinuation(selection.getMoveJobStep(),current,job)) fail("Target selection is already bound to another MOVE visit");
        if(!sameTarget(selection.getTargetSnapshotJson(),target(selection.getEndpoint(),selection.getEndpointGroup()))) fail("Selected endpoint changed before MOVE");
        selection.setMoveJobStep(current);
    }
    private boolean stoppedContinuation(JobStep previous,JobStep current,Job job) {
        if(!JobOccupancyPolicy.patrol(job) || !previous.getJobStepId().equals(current.getResumedFromJobStepId())
                || !JobSuspensionService.suspendedExecution(previous) || previous.getPatrolRound()!=current.getPatrolRound()
                || !previous.getJobTask().getJob().getJobId().equals(job.getJobId()))return false;
        var command=commands.findByJobStepId(previous.getJobStepId()).orElse(null);
        return command!=null && Boolean.TRUE.equals(command.getPhysicalStopConfirmed())
                && ("COMPLETED".equals(command.getState()) || command.getSendAttempts()==0 && "ABANDONED".equals(command.getState()))
                && previous.getOperationKey().equals(command.getOperationKey()) && job.getJobId().equals(command.getJobId())
                && job.getRobot()!=null && job.getRobot().getRobotId().equals(command.getRobotId());
    }
    /** Called with the Job lock after a validated stopped MOVE result. */
    public void reached(JobStep move) {
        Job job=move.getJobTask().getJob();
        if(job.getServiceRequest()==null) return;
        JobNavigationSelection selected=selections.findByMoveJobStepJobStepId(move.getJobStepId()).orElse(null);
        UUID requestId=job.getServiceRequest().getServiceRequestId();
        if(job.getServiceRequest().getRequestType()==ServiceRequestType.DELIVERY) {
            if(selected==null) return;
            DeliveryRequest delivery=deliveries.lockById(requestId).orElseThrow();
            if(delivery.getDeliveryGroup()==null || !delivery.getDeliveryGroup().getEndpointGroupId().equals(selected.getEndpointGroup().getEndpointGroupId())) fail("Delivery group differs from navigation selection");
            delivery.setResolvedDeliveryEndpoint(selected.getEndpoint());
        } else if(job.getServiceRequest().getRequestType()==ServiceRequestType.NAVIGATION) {
            NavigationRequest navigation=navigations.findById(requestId).orElseThrow();
            Endpoint reached=arrivalEndpoint(move,selected,navigation.getDestinationGroup(),navigation.getDestinationEndpoint());
            if(reached!=null) navigation.setResolvedEndpoint(reached);
        } else if(job.getServiceRequest().getRequestType()==ServiceRequestType.ESCORT) {
            EscortRequest escort=escorts.findById(requestId).orElseThrow();
            Endpoint reached=arrivalEndpoint(move,selected,escort.getDestinationGroup(),escort.getDestinationEndpoint());
            if(reached!=null) escort.setResolvedEndpoint(reached);
        }
    }
    private Endpoint arrivalEndpoint(JobStep move,JobNavigationSelection selected,EndpointGroup group,Endpoint direct) {
        if(selected!=null) {
            if(group==null || !group.getEndpointGroupId().equals(selected.getEndpointGroup().getEndpointGroupId()))
                fail("Arrival group differs from requested destination");
            return selected.getEndpoint();
        }
        return direct!=null && direct.getEndpointId().toString().equals(move.getResolvedInputsJson().path("endpoint_id").asText())?direct:null;
    }
    private Job requireServer(JobStep current) {
        Job job=current.getJobTask().getJob();
        if(!job.isBeOrchestrated() || job.getSchemaVersion()!=3 || job.getStatus()!=JobStatus.RUNNING || job.getCancelRequestedAt()!=null || job.getMap()==null
                || job.getMap().getStatus()!=MapStatus.ACTIVE || current.getStatus()!=JobStepStatus.RUNNING || job.getSelectedJobStep()==null
                || !job.getSelectedJobStep().getJobStepId().equals(current.getJobStepId())) fail("Target service requires the selected RUNNING BE step in an ACTIVE map");
        return job;
    }
    private EndpointGroup group(Job job,UUID id) {
        EndpointGroup group=groups.findById(id).orElseThrow(()->new BusinessRuleException("Endpoint group does not exist"));
        if(!group.isActive() || !group.getMap().getMapId().equals(job.getMap().getMapId())) fail("Endpoint group is inactive or outside the Job map");
        if(group.getRoom()!=null) {
            if(!group.getRoom().isActive()) fail("Destination room is inactive");
            Room requested=null;
            if(job.getServiceRequest()!=null) {
                UUID requestId=job.getServiceRequest().getServiceRequestId();
                if(job.getServiceRequest().getRequestType()==ServiceRequestType.NAVIGATION)
                    requested=navigations.findById(requestId).map(NavigationRequest::getRoom).orElse(null);
                else if(job.getServiceRequest().getRequestType()==ServiceRequestType.ESCORT)
                    requested=escorts.findById(requestId).map(EscortRequest::getDestinationRoom).orElse(null);
                else if(job.getServiceRequest().getRequestType()==ServiceRequestType.DELIVERY)
                    requested=deliveries.findById(requestId).map(DeliveryRequest::getDeliveryRoom).orElse(null);
            }
            if(requested!=null && !requested.getRoomId().equals(group.getRoom().getRoomId())) fail("Destination group belongs to another room");
        }
        return group;
    }
    private boolean sameRoom(Endpoint endpoint,EndpointGroup group) {
        return endpoint.getRoom()!=null && endpoint.getRoom().getRoomId().equals(group.getRoom().getRoomId());
    }
    /** Infrastructure failures never authorize substituting a room's destination. */
    private void requireDoorObstruction(JobNavigationSelection previous,Endpoint next,EndpointGroup group) {
        JsonNode error=previous.getMoveJobStep().getErrorDetailsJson();
        JsonNode evidence=error==null?json.missingNode():error.path("details").path("navigationFailure");
        if(!"OBSTACLE_BLOCKED".equals(evidence.path("category").asText())
                || !evidence.path("recoveryExhausted").asBoolean() || !evidence.path("poseFresh").asBoolean()
                || !evidence.path("scanFresh").asBoolean() || !evidence.path("tfFresh").asBoolean()
                || !previous.getEndpoint().getEndpointId().toString().equals(evidence.path("endpointId").asText())
                || !Set.of("NAV_GATE_BLOCKED","BRAKING_OBSTACLE","LIDAR_BRAKING_STOP","TURN_ENVELOPE_BLOCKED","REAR_OBSTACLE","LIVE_ROUTE_OBSTACLE")
                    .contains(evidence.path("obstacleReason").asText()))
            fail("Room fallback requires fresh obstacle evidence after navigation recovery is exhausted");
        JsonNode pose=evidence.path("pose");
        for(String field:List.of("xM","yM","yawRad"))
            if(!pose.path(field).isNumber() || !Double.isFinite(pose.path(field).asDouble())) fail("Fallback obstacle pose is invalid");
        double distance=Math.hypot(pose.path("xM").asDouble()-next.getXM().doubleValue(),pose.path("yM").asDouble()-next.getYM().doubleValue());
        if(group.getFallbackRadiusM()==null || distance>group.getFallbackRadiusM().doubleValue())
            fail("Robot must reach the room's door area before switching to its outside endpoint");
    }
    private boolean usable(Endpoint e,Job job) {return e.isActive()&&e.isAccessible()&&e.getMap().getMapId().equals(job.getMap().getMapId());}
    private UUID requestedGroup(Job job,JsonNode inputs) {
        if(!"ENDPOINT_GROUP".equals(job.getInputJson().path("target_mode").asText())) return null;
        JsonNode group=job.getInputJson().get("endpoint_group_id");
        if(job.getServiceRequest()!=null && job.getServiceRequest().getRequestType()==ServiceRequestType.DELIVERY) {
            // Pickup is an explicit endpoint; only the destination MOVE needs the delivery-group authority.
            if(job.getInputJson().path("pickup_endpoint_id").asText().equals(inputs.path("endpoint_id").asText())
                    && deliveries.findById(job.getServiceRequest().getServiceRequestId()).filter(d->d.getPickedUpAt()==null).isPresent()) return null;
            group=job.getInputJson().get("delivery_group_id");
        }
        if(group==null || !group.isTextual()) fail("Group target request is missing its canonical destination group");
        try{return UUID.fromString(group.asText());}catch(IllegalArgumentException ex){throw new BusinessRuleException("Destination group must be a UUID");}
    }
    public ObjectNode endpoint(Endpoint e) {return json.createObjectNode().put("endpoint_id",e.getEndpointId().toString()).put("endpoint_code",e.getEndpointCode()).put("endpoint_name",e.getEndpointName()).put("x_m",e.getXM()).put("y_m",e.getYM()).put("yaw_rad",e.getYawRad());}
    private ObjectNode target(Endpoint endpoint,EndpointGroup group) {
        ObjectNode result=endpoint(endpoint);
        if(group.getRoom()!=null) result.put("room_id",group.getRoom().getRoomId().toString()).put("fallback_radius_m",group.getFallbackRadiusM());
        return result;
    }
    private BigDecimal distance(Endpoint e,RobotLocationHistory p) {BigDecimal x=e.getXM().subtract(p.getXM()),y=e.getYM().subtract(p.getYM());return x.multiply(x).add(y.multiply(y));}
    private boolean sameTarget(JsonNode a,JsonNode b) {
        if(!a.isObject() || !b.isObject() || a.size()!=b.size())return false;
        for(String field:List.of("endpoint_id","endpoint_code","endpoint_name"))if(!a.path(field).equals(b.path(field)))return false;
        if(!a.path("room_id").equals(b.path("room_id")))return false;
        if(a.has("fallback_radius_m") && (!b.path("fallback_radius_m").isNumber()
                || a.path("fallback_radius_m").decimalValue().compareTo(b.path("fallback_radius_m").decimalValue())!=0))return false;
        // MySQL JSON normalizes decimal scales; compare business coordinates, not Jackson numeric node classes.
        for(String field:List.of("x_m","y_m","yaw_rad"))if(!a.path(field).isNumber() || !b.path(field).isNumber()
                || a.path(field).decimalValue().compareTo(b.path(field).decimalValue())!=0)return false;
        return true;
    }
    private void fail(String message) {throw new BusinessRuleException(message);}
}
