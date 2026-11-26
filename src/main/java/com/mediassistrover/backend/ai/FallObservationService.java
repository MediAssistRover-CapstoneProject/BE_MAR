package com.mediassistrover.backend.ai;

import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.entity.FallObservation.State;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.exception.*;
import com.mediassistrover.backend.mqtt.MqttSessionRegistry;
import com.mediassistrover.backend.repository.*;
import com.mediassistrover.backend.security.*;
import com.mediassistrover.backend.service.*;
import com.mediassistrover.backend.validation.*;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.access.AccessDeniedException;
import java.time.*;
import java.util.*;

/** Early observation only suspends the exact active patrol. RGBD evidence is checked later. */
@Service @RequiredArgsConstructor @Slf4j
public class FallObservationService {
    public static final String SOURCE="FALL_OBSERVATION";
    private static final Set<String> REASONS=Set.of("LOW_HORIZONTAL_POSTURE","PARTIAL_LOW_HORIZONTAL_POSTURE","END_ON_LOWER_BODY");
    private static final Set<EmergencyIncidentStatus> OPEN_INCIDENTS=Set.of(EmergencyIncidentStatus.OPEN,EmergencyIncidentStatus.ACKNOWLEDGED,EmergencyIncidentStatus.IN_PROGRESS);
    public record Request(@NotNull UUID observationId,@NotNull UUID monitoringSessionId,@NotNull UUID trackingRevision,@NotNull UUID jobId,
                          @NotNull UUID jobStepId,@NotBlank @Size(max=150) String operationKey,
                          @NotNull Instant detectedAt,@Min(0) int trackId,
                          @NotBlank @Size(max=64) String candidateReason,
                          @DecimalMin("0.5") @DecimalMax("30.0") double continuousDurationSec,
                          @NotBlank @Size(max=100) String captureId) { }
    public enum Outcome { CLEAR, INCONCLUSIVE, INCIDENT }
    public record Complete(@NotNull Outcome outcome,UUID incidentId) { }
    public record Result(UUID observationId,UUID robotId,UUID jobId,UUID jobStepId,String operationKey,
                         UUID monitoringSessionId,UUID trackingRevision,int trackId,String candidateReason,State state,
                         double settleSeconds,double observeSeconds,Instant stopConfirmedAt,Instant observeAfter,
                         Instant observeUntil,Instant expiresAt,UUID incidentId,String completionReason) { }
    private final FallObservationRepository observations;
    private final JobRepository jobs;
    private final RobotRepository robots;
    private final RobotCommandOutboxRepository commands;
    private final RobotMqttSessionRepository links;
    private final EmergencyIncidentRepository incidents;
    private final DeviceResourceScopePolicy deviceScope;
    private final StaffOperationalScopePolicy staffScope;
    private final JobSuspensionService suspensions;
    private final PatrolInterruptionEvents interruptions;
    private final MqttSessionRegistry registry;
    private final PlatformTransactionManager transactions;
    private final Clock clock;
    @org.springframework.beans.factory.annotation.Value("${app.operations.scheduler-enabled:true}") private boolean schedulerEnabled=true;

    @Transactional
    public Result request(Request input,DevicePrincipal device) {
        UUID robotId=deviceScope.requireCurrentDevice(device).getRobotId();
        // Preserve the orchestration lock order: Job -> Robot -> source row.
        Job job=jobs.lockById(input.jobId()).orElseThrow(()->new ResourceNotFoundException("Job not found"));
        Robot robot=robots.findByIdForUpdate(robotId).orElseThrow();
        requireOwner(job,robotId);
        var replay=observations.lock(input.observationId()).orElse(null);
        if(replay!=null){requireReplay(replay,input,robotId);refresh(replay,job);return result(replay);}
        Instant now=clock.instant();
        if(!Double.isFinite(input.continuousDurationSec()) || input.continuousDurationSec()<.5 || input.continuousDurationSec()>30.
                || !REASONS.contains(input.candidateReason()))fail("FALL_OBSERVATION_INVALID_CANDIDATE");
        if(input.detectedAt().isBefore(now.minusSeconds(10)) || input.detectedAt().isAfter(now.plusSeconds(2)))fail("FALL_OBSERVATION_STALE_FRAME");
        RobotMqttSession link=links.findById(robotId).orElse(null);
        if(robot.getStatus()==RobotStatus.RETIRED || link==null || link.isClosed() || !registry.observationFresh(link)
                || link.getHelloJson()==null || link.getHelloJson().path("simulation").asBoolean()
                || !capable(link))fail("FALL_OBSERVATION_CONNECTION_NOT_READY");
        JobStep step=job.getSelectedJobStep();
        if(!suspensions.isPatrol(job) || job.getStatus()!=JobStatus.RUNNING || job.getCancelRequestedAt()!=null
                || step==null || !input.jobStepId().equals(step.getJobStepId()) || step.getStatus()!=JobStepStatus.RUNNING
                || !"MOVE".equals(step.getStepTypeSnapshot()) || !input.operationKey().equals(step.getOperationKey()))
            fail("FALL_OBSERVATION_ACTIVE_PATROL_MOVE_REQUIRED");
        var command=commands.findByJobStepId(step.getJobStepId()).orElse(null);
        if(command==null || !input.operationKey().equals(command.getOperationKey()) || command.getSendAttempts()==0
                || !Set.of("PUBLISHED","ACKNOWLEDGED","STARTED").contains(command.getState())
                || command.getInterruptionReason()!=null || Boolean.TRUE.equals(command.getPhysicalStopConfirmed()))
            fail("FALL_OBSERVATION_OPERATION_CHANGED");
        if(job.getMap()==null || robot.getCurrentMap()==null || job.getMap().getStatus()!=MapStatus.ACTIVE
                || !job.getMap().getMapId().equals(robot.getCurrentMap().getMapId())
                || !Objects.equals(job.getMap().getAssetHash(),robot.getMapAssetHash()))fail("FALL_OBSERVATION_MAP_CHANGED");
        if(!observations.findByRobotIdAndStateIn(robotId,FallObservation.ACTIVE).isEmpty())fail("FALL_OBSERVATION_ALREADY_ACTIVE");
        var recent=observations.findFirstByRobotIdOrderByCreatedAtDesc(robotId).orElse(null);
        if(recent!=null && recent.getCreatedAt().isAfter(now.minusSeconds(10)))fail("FALL_OBSERVATION_RATE_LIMITED");
        var person=observations.findFirstByRobotIdAndMonitoringSessionIdAndTrackingRevisionAndTrackIdOrderByCreatedAtDesc(robotId,input.monitoringSessionId(),input.trackingRevision(),input.trackId()).orElse(null);
        if(person!=null && person.getCreatedAt().isAfter(now.minusSeconds(30)))fail("FALL_OBSERVATION_PERSON_COOLDOWN");
        FallObservation observation=FallObservation.builder().observationId(input.observationId()).robotId(robotId)
                .jobId(job.getJobId()).jobStepId(step.getJobStepId()).operationKey(step.getOperationKey())
                .monitoringSessionId(input.monitoringSessionId()).trackingRevision(input.trackingRevision()).transportSessionId(link.getTransportSessionId())
                .mapId(job.getMap().getMapId()).mapAssetHash(job.getMap().getAssetHash())
                .navigationProfileHash(command.getNavigationProfileHash()).trackId(input.trackId())
                .candidateReason(input.candidateReason()).continuousDurationSec(input.continuousDurationSec())
                .captureId(input.captureId()).detectedAt(input.detectedAt().truncatedTo(java.time.temporal.ChronoUnit.MICROS)).createdAt(now).expiresAt(now.plusSeconds(20))
                .state(State.SUSPENDING).build();
        observations.saveAndFlush(observation);
        suspensions.request(job,SOURCE,observation.getObservationId());
        log.info("FALL_OBSERVATION_REQUESTED observation={} robot={} job={} track={}",observation.getObservationId(),robotId,job.getJobId(),input.trackId());
        return result(observation);
    }

    @Transactional public Result get(UUID id,DevicePrincipal device) {
        UUID robotId=deviceScope.requireCurrentDevice(device).getRobotId();
        var initial=observations.findById(id).orElseThrow(()->new ResourceNotFoundException("Fall observation not found"));
        if(!initial.getRobotId().equals(robotId))throw new AccessDeniedException("Fall observation is outside Device scope");
        var job=jobs.lockById(initial.getJobId()).orElseThrow();
        robots.findByIdForUpdate(robotId).orElseThrow();
        var observation=observations.lock(id).orElseThrow();refresh(observation,job);return result(observation);
    }

    @Transactional public Result complete(UUID id,Complete input,DevicePrincipal device) {
        UUID robotId=deviceScope.requireCurrentDevice(device).getRobotId();
        var initial=observations.findById(id).orElseThrow(()->new ResourceNotFoundException("Fall observation not found"));
        if(!initial.getRobotId().equals(robotId))throw new AccessDeniedException("Fall observation is outside Device scope");
        var job=jobs.lockById(initial.getJobId()).orElseThrow();
        robots.findByIdForUpdate(robotId).orElseThrow();
        var observation=observations.lock(id).orElseThrow();refresh(observation,job);
        State target=State.valueOf(input.outcome().name());
        if(!FallObservation.ACTIVE.contains(observation.getState())) {
            if(observation.getState()==target && Objects.equals(observation.getIncidentId(),input.incidentId()))return result(observation);
            // A delayed retry must not revive or overwrite an expired observation.
            return result(observation);
        }
        if(input.outcome()==Outcome.INCIDENT) {
            if(input.incidentId()==null)fail("FALL_OBSERVATION_INCIDENT_REQUIRED");
            var incident=incidents.findById(input.incidentId()).orElseThrow(()->new ResourceNotFoundException("Incident not found"));
            requireIncident(observation,incident);interruptions.hold(robotId,"INCIDENT",incident.getIncidentId());
            observation.setIncidentId(incident.getIncidentId());
        } else {
            if(input.incidentId()!=null)fail("FALL_OBSERVATION_UNEXPECTED_INCIDENT");
            if(observation.getStopConfirmedAt()==null)fail("FALL_OBSERVATION_STOP_NOT_CONFIRMED");
            if(input.outcome()==Outcome.INCONCLUSIVE && clock.instant().isBefore(observation.getObserveUntil()))
                fail("FALL_OBSERVATION_WINDOW_NOT_FINISHED");
        }
        finish(observation,target,input.outcome().name());
        suspensions.release(job,SOURCE,id);
        return result(observation);
    }

    /** Called inside the existing Robot-locked event transaction, never acquire a Job lock here. */
    public void incidentCommitted(UUID observationId,UUID robotId,UUID monitoringSessionId,Instant detectedAt,EmergencyIncident incident) {
        if(observationId==null)return;
        var observation=observations.lock(observationId).orElseThrow(()->new BusinessRuleException("FALL_OBSERVATION_NOT_FOUND"));
        if(!observation.getRobotId().equals(robotId) || !observation.getMonitoringSessionId().equals(monitoringSessionId))
            throw new AccessDeniedException("Fall observation event scope changed");
        requireIncident(observation,incident);
        if(observation.getState()==State.INCIDENT && incident.getIncidentId().equals(observation.getIncidentId()))return;
        // A valid fall event remains authoritative if its optional observation expired
        // or an independent transition was detected before the stop completed.
        if(!observation.active(clock.instant()) || observation.getStopConfirmedAt()==null
                || detectedAt.isBefore(observation.getObserveAfter()))return;
        observation.setIncidentId(incident.getIncidentId());
        // Establish the durable incident first. Source callbacks inspect persisted current state.
        interruptions.hold(robotId,"INCIDENT",incident.getIncidentId());
        finish(observation,State.INCIDENT,"INCIDENT_RECORDED");
        interruptions.release(robotId,SOURCE,observationId);
    }

    @Transactional(readOnly=true) public Optional<Result> latest(UUID robotId,AccountPrincipal actor) {
        staffScope.requireRobotRead(actor);
        if(!robots.existsById(robotId))throw new ResourceNotFoundException("Robot not found");
        return observations.findFirstByRobotIdOrderByCreatedAtDesc(robotId).map(this::result);
    }

    @Scheduled(fixedDelayString="${app.fall.observation-reconcile-ms:500}")
    public void reconcile() {
        if(!schedulerEnabled)return;
        for(UUID id:observations.activeIds(FallObservation.ACTIVE,PageRequest.of(0,100))) {
            try {
                var tx=new TransactionTemplate(transactions);tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                tx.executeWithoutResult(status->{
                    var initial=observations.findById(id).orElse(null);if(initial==null)return;
                    var job=jobs.lockById(initial.getJobId()).orElse(null);if(job==null)return;
                    if(robots.findByIdForUpdate(initial.getRobotId()).isEmpty())return;
                    var observation=observations.lock(id).orElseThrow();refresh(observation,job);
                });
            } catch(RuntimeException unavailable){log.warn("Fall observation reconciliation deferred observation={}",id);}
        }
    }

    private void refresh(FallObservation observation,Job job) {
        if(!FallObservation.ACTIVE.contains(observation.getState()))return;
        Instant now=clock.instant();
        var link=links.findById(observation.getRobotId()).orElse(null);
        var command=commands.findByJobStepId(observation.getJobStepId()).orElse(null);
        String invalid=null;
        if(!observation.getExpiresAt().isAfter(now))invalid="LEASE_EXPIRED";
        else if(job.getCancelRequestedAt()!=null || !Set.of(JobStatus.RUNNING,JobStatus.SUSPENDING,JobStatus.SUSPENDED).contains(job.getStatus()))invalid="JOB_ENDED_OR_CANCELLED";
        else if(job.getMap()==null || !observation.getMapId().equals(job.getMap().getMapId())
                || !Objects.equals(observation.getMapAssetHash(),job.getMap().getAssetHash()))invalid="MAP_CHANGED";
        else if(link==null || link.isClosed() || !Objects.equals(link.getTransportSessionId(),observation.getTransportSessionId()))invalid="CONNECTION_CHANGED";
        else if(observation.getStopConfirmedAt()!=null && link.getLastHeartbeatAt()!=null
                && !link.getLastHeartbeatAt().isBefore(observation.getStopConfirmedAt())
                && link.getHeartbeatJson()!=null && link.getHeartbeatJson().path("activeJobStepId").isTextual()
                && !observation.getJobStepId().toString().equals(link.getHeartbeatJson().path("activeJobStepId").asText()))invalid="FOREGROUND_OPERATION";
        else if(command==null || !observation.getOperationKey().equals(command.getOperationKey())
                || !Objects.equals(observation.getNavigationProfileHash(),command.getNavigationProfileHash()))invalid="OPERATION_CHANGED";
        if(invalid!=null){finish(observation,State.EXPIRED,invalid);suspensions.release(job,SOURCE,observation.getObservationId());return;}
        if(observation.getStopConfirmedAt()==null && job.getStatus()==JobStatus.SUSPENDED
                && Boolean.TRUE.equals(command.getPhysicalStopConfirmed()) && job.getSuspendedAt()!=null) {
            Instant stopped=job.getSuspendedAt();
            observation.setStopConfirmedAt(stopped);observation.setObserveAfter(stopped.plusMillis(500));
            observation.setObserveUntil(stopped.plusMillis(3500));
            Instant grace=observation.getObserveUntil().plusSeconds(12);
            if(grace.isBefore(observation.getExpiresAt()))observation.setExpiresAt(grace);
            observation.setState(State.STOP_CONFIRMED);
        }
        if(!observation.getExpiresAt().isAfter(now)){
            finish(observation,State.EXPIRED,"OBSERVATION_WINDOW_EXPIRED");suspensions.release(job,SOURCE,observation.getObservationId());return;
        }
        if(observation.getObserveAfter()!=null && !now.isBefore(observation.getObserveAfter()))observation.setState(State.OBSERVING);
    }

    private void finish(FallObservation observation,State state,String reason){
        observation.setState(state);observation.setCompletedAt(clock.instant());observation.setCompletionReason(reason);
        log.info("FALL_OBSERVATION_COMPLETED observation={} state={} reason={}",observation.getObservationId(),state,reason);
    }
    private void requireIncident(FallObservation observation,EmergencyIncident incident){
        if(incident.getRobot()==null || !observation.getRobotId().equals(incident.getRobot().getRobotId())
                || incident.getMap()==null || !observation.getMapId().equals(incident.getMap().getMapId())
                || !incident.isSuspectedFall() || incident.getArchivedAt()!=null || !OPEN_INCIDENTS.contains(incident.getStatus()))
            fail("FALL_OBSERVATION_INCIDENT_SCOPE_CHANGED");
    }
    private void requireOwner(Job job,UUID robotId){if(job.getRobot()==null || !robotId.equals(job.getRobot().getRobotId()))throw new AccessDeniedException("Job is outside Device scope");}
    private void requireReplay(FallObservation row,Request input,UUID robotId){
        if(!row.getRobotId().equals(robotId) || !row.getJobId().equals(input.jobId()) || !row.getJobStepId().equals(input.jobStepId())
                || !row.getOperationKey().equals(input.operationKey()) || !row.getMonitoringSessionId().equals(input.monitoringSessionId())
                || !row.getTrackingRevision().equals(input.trackingRevision())
                || row.getTrackId()!=input.trackId() || !row.getCaptureId().equals(input.captureId())
                || !row.getDetectedAt().equals(input.detectedAt().truncatedTo(java.time.temporal.ChronoUnit.MICROS)) || !row.getCandidateReason().equals(input.candidateReason())
                || Double.compare(row.getContinuousDurationSec(),input.continuousDurationSec())!=0)
            throw new ConflictException("Fall observation ID was already used for a different candidate");
    }
    private boolean capable(RobotMqttSession link){for(var c:link.getHelloJson().path("capabilities"))if("fall-observation-v1".equals(c.asText()))return true;return false;}
    private Result result(FallObservation row){return new Result(row.getObservationId(),row.getRobotId(),row.getJobId(),row.getJobStepId(),row.getOperationKey(),row.getMonitoringSessionId(),row.getTrackingRevision(),row.getTrackId(),row.getCandidateReason(),row.getState(),.5,3.,row.getStopConfirmedAt(),row.getObserveAfter(),row.getObserveUntil(),row.getExpiresAt(),row.getIncidentId(),row.getCompletionReason());}
    private static void fail(String message){throw new BusinessRuleException(message);}
}
