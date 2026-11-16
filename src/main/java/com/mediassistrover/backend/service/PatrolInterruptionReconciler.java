package com.mediassistrover.backend.service;

import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.media.MediaSession;
import com.mediassistrover.backend.media.MediaSessionRepository;
import com.mediassistrover.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;

/** Rebuilds event holds from durable facts after missed callbacks or a BE restart. */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name="app.operations.scheduler-enabled", havingValue="true", matchIfMissing=true)
public class PatrolInterruptionReconciler {
    private static final Set<String> EVENT_SOURCES = Set.of("VOICE", "MEDIA", "INCIDENT");
    private static final List<EmergencyIncidentStatus> OPEN_INCIDENTS = List.of(
            EmergencyIncidentStatus.OPEN, EmergencyIncidentStatus.ACKNOWLEDGED, EmergencyIncidentStatus.IN_PROGRESS);
    private final JobRepository jobs;
    private final JobSuspensionHoldRepository holds;
    private final VoiceInteractionRepository voice;
    private final MediaSessionRepository media;
    private final EmergencyIncidentRepository incidents;
    private final JobSuspensionService suspensions;
    private final PlatformTransactionManager transactions;

    record Source(String type, UUID id) { }

    @Scheduled(fixedDelayString="${app.patrol.interruption-reconcile-ms:1000}")
    public void reconcile() {
        Set<UUID> candidates = new LinkedHashSet<>();
        for (var status : List.of(JobStatus.QUEUED, JobStatus.ACCEPTED, JobStatus.RUNNING,
                JobStatus.SUSPENDING, JobStatus.SUSPENDED, JobStatus.RECOVERY_REQUIRED)) {
            for (int page = 0; page < 1000; page++) {
                var batch = jobs.findByBeOrchestratedTrueAndStatus(status, PageRequest.of(page, 100));
                // Only identifiers may be read outside the repository transaction.
                // Workflow/robot are LAZY and must be inspected in reconcileOne.
                batch.forEach(job -> candidates.add(job.getJobId()));
                if (batch.isLast()) break;
            }
        }
        for (UUID jobId : candidates) {
            try { reconcileOne(jobId); }
            catch (RuntimeException unavailable) {
                log.warn("Patrol source reconciliation deferred for Job {}", jobId);
            }
        }
    }

    public void reconcileOne(UUID jobId) {
        var transaction = new TransactionTemplate(transactions);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.executeWithoutResult(status -> {
            // Source read transactions do not take Robot locks. Mutation always
            // follows the existing Job -> Robot execution lock order.
            var job = jobs.lockById(jobId).orElse(null);
            if (!suspensions.isPatrol(job) || job.getRobot() == null) return;
            UUID robotId = job.getRobot().getRobotId();
            Set<Source> expected = new LinkedHashSet<>();
            voice.findByRobotRobotIdAndStatus(robotId, VoiceInteractionStatus.ACTIVE)
                    .forEach(row -> expected.add(new Source("VOICE", row.getVoiceInteractionId())));
            media.findByActiveRobotId(robotId).filter(row -> row.getMode() == MediaSession.Mode.ASSIST)
                    .ifPresent(row -> expected.add(new Source("MEDIA", row.getSessionId())));
            incidents.findByRobotRobotIdAndStatusInAndArchivedAtIsNull(robotId, OPEN_INCIDENTS)
                    .forEach(row -> expected.add(new Source("INCIDENT", row.getIncidentId())));
            for (Source source : expected) suspensions.request(job, source.type(), source.id());
            for (var hold : holds.findByJobJobIdOrderByCreatedAt(jobId)) {
                if (hold.getReleasedAt() == null && EVENT_SOURCES.contains(hold.getSource())
                        && !expected.contains(new Source(hold.getSource(), hold.getSourceId())))
                    suspensions.release(job, hold.getSource(), hold.getSourceId());
            }
        });
    }
}
