package com.mediassistrover.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.exception.*;
import com.mediassistrover.backend.mapping.MapBundleService;
import com.mediassistrover.backend.mqtt.MqttWire;
import com.mediassistrover.backend.repository.*;
import com.mediassistrover.backend.security.DevicePrincipal;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class NavigationSessionService {
    private final RobotNavigationSessionRepository rows;
    private final RobotRepository robots;
    private final FacilityMapRepository maps;
    private final RobotMqttSessionRepository links;
    private final RobotNavigationProfileRepository profiles;
    private final JobRepository jobs;
    private final EndpointRepository endpoints;
    private final MapBundleService bundles;
    private final MqttWire wire;
    private final Clock clock;
    private final jakarta.persistence.EntityManager entityManager;
    private final org.springframework.context.ApplicationEventPublisher updates;
    private static final List<JobStatus> HELD=List.of(JobStatus.QUEUED,JobStatus.ACCEPTED,JobStatus.RUNNING,JobStatus.RECOVERY_REQUIRED);
    private static final Object ROUTE_READY_RESOURCE=new Object();
    private record RouteReady(UUID robot,UUID session,UUID map,long revision,UUID transport,UUID producer) { }
    public record Create(@NotNull UUID mapId,@Min(60) @Max(28800) int durationSec) { }
    public record Initialize(@NotNull UUID sessionId,@NotBlank @Pattern(regexp="SEED|GLOBAL") String mode,
                             Double xM,Double yM,Double yawRad) { }
    public record Close(@NotNull UUID sessionId) { }
    public record PlanPoint(double xM,double yM) { }
    public record PlannedPath(@NotNull UUID sessionId,@NotNull UUID mapId,@NotNull UUID jobId,
                              @NotNull UUID jobStepId,@NotNull UUID endpointId,
                              @NotBlank String mapAssetHash,long initializationRevision,
                              @NotNull List<PlanPoint> points) { }

    public RobotNavigationSession current(UUID robotId){return rows.findById(robotId).filter(r->r.open(Instant.now(clock))).orElse(null);}
    @Transactional
    public ObjectNode create(UUID robotId,Create request){
        idle(robotId);Robot robot=robot(robotId);
        if(current(robotId)!=null)throw new BusinessRuleException("Close the existing navigation session before selecting another map");
        var map=maps.findById(request.mapId()).orElseThrow(()->new ResourceNotFoundException("Map not found"));
        if(map.getStatus()!=MapStatus.ACTIVE)throw new BusinessRuleException("Activate the saved map first");
        if(!bundles.navigationSafe(map.getMapId()))throw new BusinessRuleException("MAP_UNKNOWN_RELOAD_UNSAFE: create a navigation copy before starting this map");
        RobotNavigationSession row=new RobotNavigationSession();row.setRobotId(robotId);row.setSessionId(UUID.randomUUID());row.setMap(map);
        row.setMapAssetHash(map.getAssetHash().toLowerCase(Locale.ROOT));row.setCreatedAt(Instant.now(clock));
        row.setExpiresAt(row.getCreatedAt().plusSeconds(request.durationSec()));row.setInitializationMode("MANUAL");row.setInitializationRevision(0);
        rows.saveAndFlush(row);profiles.deleteById(robotId);
        return describe(row);
    }
    @Transactional(readOnly=true)
    public ObjectNode status(UUID robotId){if(!robots.existsById(robotId))throw new ResourceNotFoundException("Robot not found");var row=rows.findById(robotId).orElse(null);return row==null?wire.object().put("state","NONE"):describe(row);}
    @Transactional(readOnly=true)
    public ObjectNode device(DevicePrincipal principal){requireDevice(principal);var row=current(principal.robotId());
        if(row==null)throw new BusinessRuleException("NAVIGATION_SESSION_CLOSED_OR_EXPIRED");return describe(row);}
    @Transactional
    public ObjectNode initialize(UUID robotId,Initialize request){
        idle(robotId);var row=required(robotId,request.sessionId());
        if("SEED".equals(request.mode())) {
            if(request.xM()==null||request.yM()==null||request.yawRad()==null||!Double.isFinite(request.xM())||!Double.isFinite(request.yM())
                    ||!Double.isFinite(request.yawRad())||Math.abs(request.yawRad())>Math.PI)throw new BusinessRuleException("SEED requires finite xM/yM and yawRad within +/-pi");
            bundles.requireFreePoint(row.getMap().getMapId(),request.xM(),request.yM());
            row.setInitialPoseJson(wire.object().put("x_m",request.xM()).put("y_m",request.yM()).put("yaw_rad",request.yawRad()));
        } else {
            if(request.xM()!=null||request.yM()!=null||request.yawRad()!=null)throw new BusinessRuleException("GLOBAL does not accept a guessed pose");
            row.setInitialPoseJson(null);
        }
        row.setInitializationMode(request.mode());row.setInitializationRevision(row.getInitializationRevision()+1);
        row.setPlannedPathJson(null);profiles.deleteById(robotId);return describe(row);
    }
    @Transactional
    public ObjectNode close(UUID robotId,UUID id){idle(robotId);var row=rows.findById(robotId).orElseThrow();if(!row.getSessionId().equals(id))throw new BusinessRuleException("Navigation session changed");
        if(row.getClosedAt()==null)row.setClosedAt(Instant.now(clock));row.setPlannedPathJson(null);profiles.deleteById(robotId);return describe(row);}
    @Transactional
    public ObjectNode closeDevice(DevicePrincipal device,UUID id){requireDevice(device);return close(device.robotId(),id);}

    /** Observation of Pi's validated Nav2 path; this upload never authorizes motion. */
    @Transactional
    public ObjectNode publishPlan(DevicePrincipal device,PlannedPath request){
        requireDevice(device);
        var row=required(device.robotId(),request.sessionId());
        var link=links.findById(device.robotId()).orElse(null);
        if(link==null||link.isClosed()||!peer(device.robotId(),link)
                ||!row.getMap().getMapId().equals(request.mapId())
                ||!row.getMapAssetHash().equalsIgnoreCase(request.mapAssetHash())
                ||row.getInitializationRevision()!=request.initializationRevision())
            throw new BusinessRuleException("Navigation plan session or localization changed");
        var job=jobs.findById(request.jobId()).orElseThrow(()->new ResourceNotFoundException("Job not found"));
        var step=job.getSelectedJobStep();
        if(job.getStatus()!=JobStatus.RUNNING||job.getRobot()==null||!job.getRobot().getRobotId().equals(device.robotId())
                ||job.getMap()==null||!job.getMap().getMapId().equals(request.mapId())
                ||step==null||!step.getJobStepId().equals(request.jobStepId())
                ||!"MOVE".equals(step.getStepTypeSnapshot())||step.getStatus()!=JobStepStatus.RUNNING)
            throw new BusinessRuleException("Navigation plan does not belong to the active MOVE step");
        var point=endpoints.findById(request.endpointId()).orElseThrow(()->new ResourceNotFoundException("Endpoint not found"));
        boolean selectedTarget=request.endpointId().toString().equals(step.getResolvedInputsJson().path("endpoint_id").asText());
        for(var candidate:step.getResolvedInputsJson().path("candidate_endpoint_ids"))
            if(request.endpointId().toString().equals(candidate.asText()))selectedTarget=true;
        if(!point.getMap().getMapId().equals(request.mapId())||!selectedTarget)
            throw new BusinessRuleException("Navigation plan endpoint differs from MOVE target");
        if(request.points()==null||request.points().size()<2||request.points().size()>256)
            throw new BusinessRuleException("Navigation plan requires 2..256 points");
        var path=wire.object().put("sessionId",request.sessionId().toString()).put("mapId",request.mapId().toString())
                .put("mapAssetHash",row.getMapAssetHash()).put("initializationRevision",row.getInitializationRevision())
                .put("jobId",request.jobId().toString()).put("jobStepId",request.jobStepId().toString())
                .put("endpointId",request.endpointId().toString()).put("frameId",row.getMap().getFrameId())
                .put("plannedAt",Instant.now(clock).toString());
        var points=path.putArray("points");
        for(var p:request.points()){
            if(p==null||!Double.isFinite(p.xM())||!Double.isFinite(p.yM())
                    ||Math.abs(p.xM())>100000||Math.abs(p.yM())>100000)
                throw new BusinessRuleException("Navigation plan contains invalid coordinates");
            points.addObject().put("xM",p.xM()).put("yM",p.yM());
        }
        path.put("endpointName",point.getEndpointName());
        // Retried identical uploads need no UI event. Changed paths replace the preview.
        var initial=row.getPlannedPathJson();
        if(initial!=null&&request.jobId().toString().equals(initial.path("jobId").asText())
                &&request.jobStepId().toString().equals(initial.path("jobStepId").asText())
                &&request.sessionId().toString().equals(initial.path("sessionId").asText())
                &&row.getInitializationRevision()==initial.path("initializationRevision").asLong(-1)
                &&points.equals(initial.path("points")))
            return wire.object().put("accepted",true).put("jobStepId",request.jobStepId().toString());
        row.setPlannedPathJson(path);
        updates.publishEvent(new RobotRealtimeEventStream.Changed(device.robotId()));
        return wire.object().put("accepted",true).put("jobStepId",request.jobStepId().toString());
    }

    @Transactional(readOnly=true)
    public JsonNode currentPlan(UUID robotId,Job active){
        if(active==null||active.getStatus()!=JobStatus.RUNNING||active.getSelectedJobStep()==null
                ||active.getSelectedJobStep().getStatus()!=JobStepStatus.RUNNING)return null;
        var row=current(robotId);if(row==null||row.getPlannedPathJson()==null)return null;
        var plan=row.getPlannedPathJson();
        if(!active.getJobId().toString().equals(plan.path("jobId").asText())
                ||!active.getSelectedJobStep().getJobStepId().toString().equals(plan.path("jobStepId").asText())
                ||!row.getSessionId().toString().equals(plan.path("sessionId").asText())
                ||row.getInitializationRevision()!=plan.path("initializationRevision").asLong(-1))return null;
        return plan;
    }

    /** Called after the caller has locked Robot. Never accepts a context from another process. */
    public void hello(UUID robotId,UUID producer,JsonNode context){
        var row=current(robotId);
        if(context==null||context.isNull()||context.isMissingNode()) {
            // Returning to ordinary mapping explicitly ends a previous temporary navigation context.
            if(row!=null && row.getProducerSessionId()!=null)row.setClosedAt(Instant.now(clock));return;
        }
        if(row==null||!matches(row,context))throw new BusinessRuleException("NAVIGATION_SESSION_MAP_OR_ID_MISMATCH");
        if(row.getProducerSessionId()!=null&&!row.getProducerSessionId().equals(producer))throw new BusinessRuleException("NAVIGATION_SESSION_PROCESS_CHANGED: open a new session after restarting Pi navigation");
        row.setProducerSessionId(producer);
    }
    public boolean matches(RobotNavigationSession row,JsonNode context){return context!=null&&row.getSessionId().toString().equals(context.path("session_id").asText())
            &&row.getMap().getMapId().toString().equals(context.path("map_id").asText())&&row.getMapAssetHash().equals(context.path("map_asset_hash").asText());}
    /** Carry authorization on the existing heartbeat, independent of pose freshness.
     * Never return READY here: the Pi's live ROS evidence owns localization.
     * One row read replaces the separate HTTPS polling of that same context.
     */
    @Transactional(readOnly=true)
    public ObjectNode mqttAuthorization(RobotMqttSession link){
        if(link.getHelloJson()==null||!link.getHelloJson().hasNonNull("navigationSession"))return null;
        var context=link.getHelloJson().path("navigationSession");
        var row=rows.findById(link.getRobotId()).orElse(null);Instant now=Instant.now(clock);
        var out=wire.object().put("robotId",link.getRobotId().toString()).put("observedAt",now.toString());
        if(row==null||!matches(row,context)||!Objects.equals(row.getProducerSessionId(),link.getProducerSessionId()))
            return out.put("sessionId",context.path("session_id").asText()).put("state","CLOSED");
        out.put("sessionId",row.getSessionId().toString()).put("mapId",row.getMap().getMapId().toString())
            .put("mapAssetHash",row.getMapAssetHash()).put("expiresAt",row.getExpiresAt().toString())
            .put("state",!row.open(now)?row.getClosedAt()!=null?"CLOSED":"EXPIRED":"OPEN");
        out.putObject("initialization").put("mode",row.getInitializationMode()).put("revision",row.getInitializationRevision())
            .set("pose",row.getInitialPoseJson()==null?wire.object().nullNode():row.getInitialPoseJson());
        return out;
    }
    public boolean peer(UUID robotId,RobotMqttSession link){var row=current(robotId);return row!=null&&link!=null
            &&Objects.equals(row.getProducerSessionId(),link.getProducerSessionId())&&matches(row,link.getHelloJson().path("navigationSession"));}
    public boolean localized(UUID robotId,RobotMqttSession link){
        if(link==null||link.getHelloJson()==null)return false;
        var row=current(robotId);if(row==null)return !link.getHelloJson().hasNonNull("navigationSession");
        if(!peer(robotId,link)||link.getHeartbeatJson()==null)return false;
        JsonNode l=link.getHeartbeatJson().path("telemetry").path("localization"),pose=link.getHeartbeatJson().path("telemetry").path("map_pose");
        return l.path("ready").asBoolean()&&l.path("session_id").asText().equals(row.getSessionId().toString())
                &&l.path("age_sec").asDouble(99)<=.75&&pose.path("age_sec").asDouble(99)<=1.
                &&l.path("initialization_revision").asLong(-1)==row.getInitializationRevision()&&row.getInitializationRevision()>0
                &&l.path("map_id").asText().equals(row.getMap().getMapId().toString())&&l.path("map_asset_hash").asText().equals(row.getMapAssetHash())
                &&pose.path("fresh").asBoolean()&&pose.path("value").path("navigation_session_id").asText().equals(row.getSessionId().toString())
                &&pose.path("value").path("initialization_revision").asLong(-1)==row.getInitializationRevision();
    }
    public ObjectNode commandContext(UUID robotId,String profileHash){
        var row=current(robotId);if(row==null)return null;
        return wire.object().put("session_id",row.getSessionId().toString()).put("map_id",row.getMap().getMapId().toString())
                .put("map_asset_hash",row.getMapAssetHash()).put("initialization_revision",row.getInitializationRevision()).put("profile_hash",profileHash);
    }
    /** Do not attach an old heartbeat to the long graph-building transaction. */
    @Transactional(readOnly=true)
    public void requirePreflightReady(UUID robotId,UUID id,UUID mapId){
        requireReadyContext(robotId,id,mapId);
        // These are read-only preflight entities. Retaining them through graph
        // compilation can reuse an old heartbeat or flush stale JSON later.
        // Detach only these two, never clear the caller's pending graph/job work.
        links.findById(robotId).ifPresent(entityManager::detach);
        rows.findById(robotId).ifPresent(entityManager::detach);
    }
    public void requireReadyContext(UUID robotId,UUID id,UUID mapId){
        var row=required(robotId,id);var link=links.findById(robotId).orElse(null);Instant now=Instant.now(clock);
        boolean checkedInThisLockedRouteTransaction=link!=null&&Objects.equals(
            TransactionSynchronizationManager.getResource(ROUTE_READY_RESOURCE),
            new RouteReady(robotId,id,mapId,row.getInitializationRevision(),link.getTransportSessionId(),link.getProducerSessionId()));
        if(!row.getMap().getMapId().equals(mapId)||link==null||link.isClosed()||!link.isDispatchReady()
                ||!checkedInThisLockedRouteTransaction&&(link.getLastHeartbeatAt()==null
                ||!link.getLastHeartbeatAt().isAfter(now.minusSeconds(5))||!localized(robotId,link)))
            throw new BusinessRuleException("NAVIGATION_SESSION_NOT_LOCALIZED: wait for a verified fresh robot pose in this map");
    }
    /** Route caller already holds Robot FOR UPDATE until transaction completion.
     * A heartbeat cannot acquire that lock while the graph is being published.
     * Reuse only this transaction's verified identity/revision, never a global
     * ready flag. Other requests, closed/expired sessions and changed identities
     * still require new evidence; Pi verifies live sensors before physical MOVE.
     */
    public void pinReadyContextForLockedRoute(UUID robotId,UUID id,UUID mapId){
        // Graph preparation may outlive the first heartbeat read. Read current
        // committed evidence under Robot, instead of a cached managed entity
        // (or the transaction's older REPEATABLE_READ snapshot).
        var currentRow=rows.findById(robotId).orElseThrow();
        entityManager.refresh(currentRow,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        var currentLink=links.lock(robotId).orElseThrow();
        entityManager.refresh(currentLink,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        requireReadyContext(robotId,id,mapId);
        if(!TransactionSynchronizationManager.isActualTransactionActive()
                ||!TransactionSynchronizationManager.isSynchronizationActive())return;
        var row=required(robotId,id);var link=links.findById(robotId).orElseThrow();
        var proof=new RouteReady(robotId,id,mapId,row.getInitializationRevision(),link.getTransportSessionId(),link.getProducerSessionId());
        if(TransactionSynchronizationManager.hasResource(ROUTE_READY_RESOURCE))return;
        TransactionSynchronizationManager.bindResource(ROUTE_READY_RESOURCE,proof);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
                @Override public void suspend(){TransactionSynchronizationManager.unbindResourceIfPossible(ROUTE_READY_RESOURCE);}
                @Override public void resume(){TransactionSynchronizationManager.bindResource(ROUTE_READY_RESOURCE,proof);}
                @Override public void afterCompletion(int status){
                    TransactionSynchronizationManager.unbindResourceIfPossible(ROUTE_READY_RESOURCE);
                }
            });
    }
    public boolean commandMatches(UUID robotId,JsonNode context){var row=current(robotId);
        return row==null?context==null||context.isMissingNode()||context.isNull():matches(row,context)&&context.path("initialization_revision").asLong(-1)==row.getInitializationRevision();}
    private ObjectNode describe(RobotNavigationSession row){
        Instant now=Instant.now(clock);var link=links.findById(row.getRobotId()).orElse(null);
        boolean fresh=link!=null&&!link.isClosed()&&link.getLastHeartbeatAt()!=null&&link.getLastHeartbeatAt().isAfter(now.minusSeconds(5));
        String state=!row.open(now)?row.getClosedAt()!=null?"CLOSED":"EXPIRED":!fresh||!peer(row.getRobotId(),link)?"WAITING_FOR_PI":localized(row.getRobotId(),link)?"READY":"LOCALIZING";
        ObjectNode out=wire.object().put("sessionId",row.getSessionId().toString()).put("robotId",row.getRobotId().toString())
                .put("mapId",row.getMap().getMapId().toString()).put("mapAssetHash",row.getMapAssetHash()).put("frameId",row.getMap().getFrameId())
                .put("state",state).put("expiresAt",row.getExpiresAt().toString()).put("observedAt",now.toString());
        out.putObject("initialization").put("mode",row.getInitializationMode()).put("revision",row.getInitializationRevision())
                .set("pose",row.getInitialPoseJson()==null?wire.object().nullNode():row.getInitialPoseJson());
        if(fresh&&peer(row.getRobotId(),link))out.set("localization",link.getHeartbeatJson().path("telemetry").path("localization"));
        return out;
    }
    private RobotNavigationSession required(UUID robotId,UUID id){var row=current(robotId);if(row==null||!row.getSessionId().equals(id))throw new BusinessRuleException("NAVIGATION_SESSION_CLOSED_OR_CHANGED");return row;}
    private Robot robot(UUID id){return robots.findByIdForUpdate(id).orElseThrow(()->new ResourceNotFoundException("Robot not found"));}
    private void idle(UUID id){if(!jobs.lockRobotJobs(id,HELD).isEmpty())throw new BusinessRuleException("End/cancel the robot's current Job before changing navigation context or initial pose");
        var robot=robot(id);if(robot.getStatus()==RobotStatus.BUSY)throw new BusinessRuleException("Stop manual movement first");}
    private void requireDevice(DevicePrincipal p){if(p==null)throw new org.springframework.security.access.AccessDeniedException("DEVICE identity required");var r=robots.findById(p.robotId()).orElseThrow();if(r.getDeviceTokenVersion()!=p.tokenVersion()||!r.getRobotCode().equals(p.robotCode())||p.expiresAt()==null||!p.expiresAt().isAfter(Instant.now(clock)))throw new org.springframework.security.access.AccessDeniedException("Current DEVICE token required");}
}
