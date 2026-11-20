package com.mediassistrover.backend.service;

import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.media.*;
import com.mediassistrover.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Durable task admission: a queued foreground job may request a patrol's confirmed handoff. */
@Service @RequiredArgsConstructor
public class RobotTaskArbitrationService {
    private static final Set<EmergencyIncidentStatus> OPEN_INCIDENTS = Set.of(EmergencyIncidentStatus.OPEN,
            EmergencyIncidentStatus.ACKNOWLEDGED, EmergencyIncidentStatus.IN_PROGRESS);
    private final JobRepository jobs;
    private final JobSuspensionHoldRepository holds;
    private final JobSuspensionService suspension;
    private final VoiceInteractionRepository voices;
    private final EmergencyIncidentRepository incidents;
    private final MediaSessionRepository media;
    private final OrchestrationWorkService wakeups;
    private final RobotRepository robots;
    private final NavigationSessionService navigation;
    private final AlertRepository alerts;

    /** Caller already owns the selected job row and robot lock; this method is read-only. */
    public boolean canStart(Job candidate) {
        if (candidate.getRobot() == null || candidate.getStatus() != JobStatus.QUEUED) return false;
        UUID robotId = candidate.getRobot().getRobotId();
        // Caller owns the Job row; serialize admission on the Robot before checking all owners.
        Robot robot=robots.findByIdForUpdate(robotId).orElse(null);
        if(robot==null || robot.getStatus()!=RobotStatus.IDLE)return false;
        if(candidate.getInputJson()!=null && candidate.getInputJson().hasNonNull("navigation_session_id")) {
            if(candidate.getMap()==null)return false;
            try {
                navigation.requireReadyContext(robotId,UUID.fromString(candidate.getInputJson().path("navigation_session_id").asText()),candidate.getMap().getMapId());
            } catch(com.mediassistrover.backend.exception.BusinessRuleException | IllegalArgumentException unavailable) {return false;}
        }
        var owners = jobs.findByRobotRobotIdAndStatusIn(robotId, JobOccupancyPolicy.MOTION_OWNERS);
        if (owners.stream().anyMatch(j -> !j.getJobId().equals(candidate.getJobId())
                && j.getStatus() != JobStatus.QUEUED)) return false;
        if (owners.stream().filter(j -> j.getStatus() == JobStatus.QUEUED && j.getCancelRequestedAt() == null)
                .min(priorityOrder()).filter(j -> !j.getJobId().equals(candidate.getJobId())).isPresent()) return false;
        if (media.findByActiveRobotId(robotId).filter(s -> s.getMode() == MediaSession.Mode.ASSIST).isPresent()) return false;
        boolean emergency = emergency(candidate);
        if (!emergency && !voices.findByRobotRobotIdAndStatus(robotId, VoiceInteractionStatus.ACTIVE).isEmpty()) return false;
        if (!emergency && !incidents.findByRobotRobotIdAndStatusInAndArchivedAtIsNull(robotId, OPEN_INCIDENTS).isEmpty()) return false;
        if (!emergency && alerts.existsActiveRestrictedByRobotId(robotId)) return false;
        return !JobOccupancyPolicy.patrol(candidate) || !suspension.hasHolds(candidate);
    }

    public boolean yieldingPatrol(Robot robot, int priority) {
        if (robot == null || !Set.of(RobotStatus.IDLE, RobotStatus.BUSY).contains(robot.getStatus())) return false;
        var owners = jobs.findByRobotRobotIdAndStatusIn(robot.getRobotId(), JobOccupancyPolicy.NON_TERMINAL);
        boolean patrol=owners.stream().anyMatch(j -> JobOccupancyPolicy.yieldable(j, priority));
        return patrol && owners.stream().allMatch(j -> JobOccupancyPolicy.yieldable(j, priority)
                || !JobOccupancyPolicy.patrol(j) && j.getStatus()==JobStatus.QUEUED);
    }

    @Transactional
    public void reconcileRobot(UUID robotId) {
        // Lock all relevant primary rows in the repository's stable order before the robot is locked.
        var all = jobs.lockRobotJobs(robotId, JobOccupancyPolicy.NON_TERMINAL);
        var patrols = all.stream().filter(JobOccupancyPolicy::patrol).toList();
        if (patrols.isEmpty()) return;
        for (Job patrol : patrols) {
            if (patrol.getCancelRequestedAt() != null || patrol.getStatus() == JobStatus.RECOVERY_REQUIRED) continue;
            for (Job foreground : all) {
                if (foreground.getJobId().equals(patrol.getJobId()) || JobOccupancyPolicy.patrol(foreground)) continue;
                if (foreground.getPriority() > patrol.getPriority() && foreground.getCancelRequestedAt() == null)
                    suspension.request(patrol, "JOB", foreground.getJobId());
            }
            for (var hold : holds.findByJobJobIdOrderByCreatedAt(patrol.getJobId())) {
                if (hold.getReleasedAt() != null) continue;
                boolean released = switch (hold.getSource()) {
                    case "JOB" -> jobs.findById(hold.getSourceId()).map(j -> !JobOccupancyPolicy.NON_TERMINAL.contains(j.getStatus())).orElse(true);
                    default -> false; // Event-source reconciliation and authorized requests own other reasons.
                };
                if (released) suspension.release(patrol, hold.getSource(), hold.getSourceId());
            }
            // Event facts are checked here as well as via durable source holds; an event callback may still be pending.
            if (patrol.getStatus() == JobStatus.SUSPENDED && !robotEventHeld(robotId)) suspension.tryResume(patrol);
        }
        all.stream().filter(j -> j.getStatus() == JobStatus.QUEUED).sorted(priorityOrder()).forEach(wakeups::request);
    }

    public boolean robotEventHeld(UUID robotId) {
        return media.findByActiveRobotId(robotId).filter(s -> s.getMode() == MediaSession.Mode.ASSIST).isPresent()
                || !voices.findByRobotRobotIdAndStatus(robotId, VoiceInteractionStatus.ACTIVE).isEmpty()
                || !incidents.findByRobotRobotIdAndStatusInAndArchivedAtIsNull(robotId, OPEN_INCIDENTS).isEmpty()
                || alerts.existsActiveRestrictedByRobotId(robotId);
    }

    /** Recheck at publication, including source commits whose after-commit listener is waiting on this Job. */
    public boolean patrolPublicationBlocked(Job job) {
        if(!JobOccupancyPolicy.patrol(job) || job.getRobot()==null)return false;
        UUID robotId=job.getRobot().getRobotId();
        return robotEventHeld(robotId) || jobs.findByRobotRobotIdAndStatusIn(robotId,Set.of(JobStatus.QUEUED)).stream()
                .anyMatch(other->!other.getJobId().equals(job.getJobId()) && other.getCancelRequestedAt()==null && other.getPriority()>job.getPriority());
    }

    static Comparator<Job> priorityOrder() {
        return Comparator.comparingInt(Job::getPriority).reversed()
                .thenComparing(Job::getQueuedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Job::getJobId);
    }
    private static boolean emergency(Job job) {
        return job.getWorkflow() != null && Set.of("EMERGENCY", "FALL_DETECTION_RESPONSE")
                .contains(job.getWorkflow().getServiceType().toUpperCase(Locale.ROOT));
    }
}
