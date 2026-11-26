package com.mediassistrover.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.exception.BusinessRuleException;
import com.mediassistrover.backend.mqtt.MqttSessionRegistry;
import com.mediassistrover.backend.media.MediaSession;
import com.mediassistrover.backend.media.MediaSessionRepository;
import com.mediassistrover.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

/** Business suspension is independent of a cancelled physical operation. Callers lock Job first. */
@Service @RequiredArgsConstructor
public class JobSuspensionService {
    private final JobRepository jobs;
    private final RobotRepository robots;
    private final JobSuspensionHoldRepository holds;
    private final JobEventRepository events;
    private final RobotCommandOutboxRepository commands;
    private final RobotMqttSessionRepository sessions;
    private final MqttSessionRegistry registry;
    private final NavigationProfileService navigation;
    private final NavigationSessionService navigationSessions;
    private final OrchestrationWorkService wakeups;
    private final ApplicationEventPublisher updates;
    private final ObjectMapper json;
    private final Clock clock;
    private final VoiceInteractionRepository voices;
    private final EmergencyIncidentRepository incidents;
    private final MediaSessionRepository media;
    private final AlertRepository alerts;
    @org.springframework.beans.factory.annotation.Autowired private FallObservationRepository fallObservations;

    public boolean isPatrol(Job job) {
        return job!=null && job.isBeOrchestrated() && job.getSchemaVersion()==3 && job.getWorkflow()!=null
                && "PATROL".equalsIgnoreCase(job.getWorkflow().getServiceType());
    }
    @Transactional public void holdRobot(UUID robotId,String source,UUID sourceId) {
        for(UUID id:jobs.findRobotJobIds(robotId,List.of(JobStatus.QUEUED,JobStatus.ACCEPTED,JobStatus.RUNNING,JobStatus.SUSPENDING,JobStatus.SUSPENDED))) {
            Job job=jobs.lockById(id).orElse(null);
            if(isPatrol(job))request(job,source,sourceId);
        }
    }
    @Transactional public void releaseRobot(UUID robotId,String source,UUID sourceId) {
        for(UUID id:jobs.findRobotJobIds(robotId,List.of(JobStatus.QUEUED,JobStatus.SUSPENDING,JobStatus.SUSPENDED,JobStatus.RUNNING,JobStatus.RECOVERY_REQUIRED))) {
            Job job=jobs.lockById(id).orElse(null);
            if(isPatrol(job))release(job,source,sourceId);
        }
    }
    public void request(Job job,String source,UUID sourceId) {
        if(!isPatrol(job))throw new BusinessRuleException("Only backend schema 3 patrol jobs can be suspended");
        if(source==null || !source.matches("[A-Z][A-Z0-9_]{0,31}") || sourceId==null)throw new BusinessRuleException("A scoped suspension source is required");
        if(!List.of(JobStatus.QUEUED,JobStatus.ACCEPTED,JobStatus.RUNNING,JobStatus.SUSPENDING,JobStatus.SUSPENDED).contains(job.getStatus()))return;
        if(job.getCancelRequestedAt()!=null)return;
        JobSuspensionHold existing=holds.findByJobJobIdAndSourceAndSourceId(job.getJobId(),source,sourceId).orElse(null);
        if(existing!=null && existing.getReleasedAt()!=null && !"MANUAL".equals(source))return;
        if(existing==null)holds.save(JobSuspensionHold.builder().job(job).source(source).sourceId(sourceId).createdAt(clock.instant()).build());
        else if(existing.getReleasedAt()!=null){existing.setCreatedAt(clock.instant());existing.setReleasedAt(null);}
        job.setResumeRequested(true);
        // A future patrol holds no motor operation to cancel or settle. Keep its queue identity
        // while recording the same scoped holds that admission checks before starting it.
        if(JobOccupancyPolicy.unstartedPatrolIntent(job)) {
            if(existing==null || "MANUAL".equals(source))event(job,"JOB_SUSPENSION_HOLD_ADDED",source,sourceId);
            wakeups.request(job);
            return;
        }
        if(job.getStatus()!=JobStatus.SUSPENDING && job.getStatus()!=JobStatus.SUSPENDED) {
            job.setSuspendRequestedAt(clock.instant());
            job.setSuspendedMapAssetHash(job.getMap()==null?null:job.getMap().getAssetHash());
            job.setStatus(JobStatus.SUSPENDING);
            event(job,"JOB_SUSPEND_REQUESTED",source,sourceId);
        }
        wakeups.request(job);
    }
    public void release(Job job,String source,UUID sourceId) {
        holds.findByJobJobIdAndSourceAndSourceId(job.getJobId(),source,sourceId).ifPresent(hold->{
            if(hold.getReleasedAt()==null){hold.setReleasedAt(clock.instant());event(job,"JOB_SUSPENSION_HOLD_RELEASED",source,sourceId);}
        });
        wakeups.request(job);
    }
    public boolean hasHolds(Job job){return holds.existsByJobJobIdAndReleasedAtIsNull(job.getJobId());}
    public void settled(Job job) {
        if(job.getStatus()!=JobStatus.SUSPENDING)return;
        if(job.getSelectedJobStep()!=null) {
            var execute=commands.findByJobStepId(job.getSelectedJobStep().getJobStepId()).orElse(null);
            if(execute!=null && execute.getSendAttempts()>0 && !Boolean.TRUE.equals(execute.getPhysicalStopConfirmed()))
                throw new BusinessRuleException("Suspension requires physical stop evidence for the selected command");
        }
        job.setStatus(JobStatus.SUSPENDED);job.setSuspendedAt(clock.instant());
        if(job.getRobot()!=null) {
            Robot robot=robots.findByIdForUpdate(job.getRobot().getRobotId()).orElseThrow();
            boolean occupied=jobs.findByRobotRobotIdAndStatusIn(robot.getRobotId(),List.of(JobStatus.ACCEPTED,JobStatus.RUNNING,JobStatus.SUSPENDING,JobStatus.RECOVERY_REQUIRED))
                    .stream().anyMatch(other->!other.getJobId().equals(job.getJobId()));
            if(!occupied && robot.getStatus()==RobotStatus.BUSY)robot.setStatus(RobotStatus.IDLE);
        }
        event(job,"JOB_SUSPENDED",null,null);wakeups.request(job);
    }
    public boolean tryResume(Job job) {
        if(job.getRobot()==null)return false;
        robots.findByIdForUpdate(job.getRobot().getRobotId()).orElseThrow();
        if(resumeBlockReason(job)!=null)return false;
        Robot robot=job.getRobot();
        Instant now=clock.instant();
        if(job.getSuspendedAt()!=null)job.setSuspendedDurationMs(Math.addExact(job.getSuspendedDurationMs(),Math.max(0,Duration.between(job.getSuspendedAt(),now).toMillis())));
        job.setSuspendedAt(null);job.setSuspendRequestedAt(null);
        if(job.getStartedAt()==null)job.setStatus(JobStatus.QUEUED);
        else {job.setStatus(JobStatus.RUNNING);robot.setStatus(RobotStatus.BUSY);}
        event(job,"JOB_RESUMED",null,null);wakeups.request(job);return true;
    }
    /** Read-only eligibility; callers that change ownership must lock Job then Robot and recheck. */
    public String resumeBlockReason(Job job) {
        if(!isPatrol(job))return "NOT_A_PATROL_JOB";
        if(job.getStatus()!=JobStatus.SUSPENDED)return "NOT_SUSPENDED";
        if(!job.isResumeRequested() || job.getCancelRequestedAt()!=null)return "RESUME_DISABLED";
        if(hasHolds(job))return "ACTIVE_SUSPENSION_HOLDS";
        if(job.getRobot()==null)return "ROBOT_NOT_ASSIGNED";
        Robot robot=job.getRobot();
        if(robot.getStatus()!=RobotStatus.IDLE)return "ROBOT_NOT_IDLE";
        // Source records are authoritative even if an after-commit hold callback
        // has not run yet. Never resume in that callback/reconciliation gap.
        if(!voices.findByRobotRobotIdAndStatus(robot.getRobotId(),VoiceInteractionStatus.ACTIVE).isEmpty())return "VOICE_INTERACTION_ACTIVE";
        if(media.findByActiveRobotId(robot.getRobotId()).filter(row->row.getMode()==MediaSession.Mode.ASSIST).isPresent())return "MEDIA_ASSIST_ACTIVE";
        if(!incidents.findByRobotRobotIdAndStatusInAndArchivedAtIsNull(robot.getRobotId(),
                List.of(EmergencyIncidentStatus.OPEN,EmergencyIncidentStatus.ACKNOWLEDGED,EmergencyIncidentStatus.IN_PROGRESS)).isEmpty())return "INCIDENT_ACTIVE";
        if(alerts.existsActiveRestrictedByRobotId(robot.getRobotId()))return "RESTRICTED_REVIEW_ACTIVE";
        if(fallObservations!=null && fallObservations.existsByJobIdAndStateInAndExpiresAtAfter(job.getJobId(),FallObservation.ACTIVE,clock.instant()))return "FALL_OBSERVATION_ACTIVE";
        if(jobs.findByRobotRobotIdAndStatusIn(robot.getRobotId(),List.of(JobStatus.QUEUED,JobStatus.ACCEPTED,JobStatus.RUNNING,JobStatus.SUSPENDING,JobStatus.RECOVERY_REQUIRED))
                .stream().anyMatch(other->!other.getJobId().equals(job.getJobId())))return "FOREGROUND_JOB_PENDING";
        if(jobs.existsHigherPriorityQueuedForMap(job.getMap()==null?null:job.getMap().getMapId(),job.getPriority()))return "HIGHER_PRIORITY_JOB_PENDING";
        if(!commands.findByRobotIdAndStateIn(robot.getRobotId(),List.of("PENDING","PUBLISHING","PUBLISHED","ACKNOWLEDGED","STARTED","RECOVERY_REQUIRED")).isEmpty())return "UNRESOLVED_PI_OPERATION";
        RobotMqttSession link=sessions.findById(robot.getRobotId()).orElse(null);
        if(link==null || !registry.fresh(link) || link.getHeartbeatJson()==null || !"IDLE".equals(link.getHeartbeatJson().path("status").asText())
                || !link.getHeartbeatJson().path("activeJobStepId").isNull())return "MQTT_ROBOT_NOT_READY";
        if(job.getMap()==null || robot.getCurrentMap()==null || job.getMap().getStatus()!=MapStatus.ACTIVE
                || !job.getMap().getMapId().equals(robot.getCurrentMap().getMapId())
                || !Objects.equals(job.getSuspendedMapAssetHash(),job.getMap().getAssetHash())
                || !Objects.equals(job.getSuspendedMapAssetHash(),robot.getMapAssetHash()))return "PATROL_MAP_CHANGED";
        if(!navigationSessions.localized(robot.getRobotId(),link))return "LOCALIZATION_NOT_READY";
        var target=job.getSelectedJobStep()==null?job.getWorkflow().getStartStep():job.getSelectedJobStep().getStep();
        if(target!=null && target.getStepType()==StepType.MOVE) {
            var params=job.getSelectedJobStep()!=null && job.getSelectedJobStep().getResolvedInputsJson()!=null
                    && job.getSelectedJobStep().getResolvedInputsJson().has("endpoint_id")?job.getSelectedJobStep().getResolvedInputsJson():target.getParamsJson();
            try {if(navigation.ready(robot,link,params)==null)return "NAVIGATION_PROFILE_NOT_READY";}catch(BusinessRuleException e){return "NAVIGATION_PROFILE_NOT_READY";}
        }
        return null;
    }
    public Instant deadline(Job job){return effectiveDeadline(job);}
    public static Instant effectiveDeadline(Job job) {
        if(job.getStartedAt()==null)return Instant.MAX;
        Instant base=job.getPatrolRoundStartedAt()==null?job.getStartedAt():job.getPatrolRoundStartedAt();
        return base.plusSeconds(job.getWorkflow().getMaxDurationSec()).plusMillis(Math.max(0,job.getSuspendedDurationMs()-job.getPatrolRoundSuspendedBaseMs()));
    }
    public static boolean suspendedExecution(JobStep step) {
        String reason = step.getInterruptionReason();
        return "JOB_SUSPEND".equals(reason) && step.getStatus() == JobStepStatus.CANCELLED
                || stoppedForFallReview(step);
    }

    static boolean stoppedForFallReview(JobStep step) {
        String reason = step.getInterruptionReason();
        var inputs = step.getResolvedInputsJson();
        var receipt = step.getErrorDetailsJson();
        if ((reason != null && !"JOB_SUSPEND".equals(reason)) || step.getStatus() != JobStepStatus.FAILED
                || !"EXECUTE_SENSING".equals(step.getStepTypeSnapshot())
                || inputs == null || !inputs.path("fall_detection").asBoolean() || receipt == null)
            return false;
        // A cancel ACK can deliver the same completed Pi journal before its STEP_ERROR.
        var snapshot = "FAILED".equals(receipt.path("result").path("status").asText())
                ? receipt.path("result") : receipt;
        return "PATROL_FALL_REVIEW_REQUIRED".equals(snapshot.path("errorCode").asText())
                && (receipt.path("physicalStopConfirmed").asBoolean() || snapshot.path("physicalStopConfirmed").asBoolean());
    }
    private void event(Job job,String type,String source,UUID sourceId) {
        Instant now=clock.instant();var data=json.createObjectNode();
        if(source!=null)data.put("source",source);if(sourceId!=null)data.put("source_id",sourceId.toString());
        events.save(JobEvent.builder().jobEventId(UUID.randomUUID()).job(job).robot(job.getRobot()).producerType(JobEventProducerType.SERVER)
                .producerSessionId(UUID.randomUUID()).sequenceNo(1).eventType(type).statusScope("JOB").newStatus(job.getStatus().name())
                .eventDataJson(data).occurredAt(now).receivedAt(now).persistedAt(now).build());
        updates.publishEvent(new RuntimeChanged(job.getJobId()));
    }
}
