package com.mediassistrover.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.repository.*;
import com.mediassistrover.backend.media.MediaSession;
import com.mediassistrover.backend.media.MediaSessionRepository;

import java.util.Set;
import java.util.UUID;

/**
 * Interaction transactions often already hold a Robot row. Apply Job -> Robot
 * interruption locks only after that transaction commits. The arbitration
 * reconciler also derives these holds from the persisted source rows, so a
 * crash or a failed callback cannot silently discard an active interaction.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PatrolInterruptionEvents {
    private final ApplicationEventPublisher publisher;
    private final JobSuspensionService suspensions;
    private final PlatformTransactionManager transactions;
    private final VoiceInteractionRepository voices;
    private final MediaSessionRepository media;
    private final EmergencyIncidentRepository incidents;
    private static final Set<EmergencyIncidentStatus> OPEN_INCIDENTS=Set.of(
            EmergencyIncidentStatus.OPEN,EmergencyIncidentStatus.ACKNOWLEDGED,EmergencyIncidentStatus.IN_PROGRESS);

    public record Changed(UUID robotId, String source, UUID sourceId, boolean active) { }

    public void hold(UUID robotId, String source, UUID sourceId) {
        if (robotId != null && sourceId != null) publisher.publishEvent(new Changed(robotId, source, sourceId, true));
    }

    public void release(UUID robotId, String source, UUID sourceId) {
        if (robotId != null && sourceId != null) publisher.publishEvent(new Changed(robotId, source, sourceId, false));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void apply(Changed event) {
        try {
            var transaction = new TransactionTemplate(transactions);
            transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            transaction.executeWithoutResult(status -> {
                // Read durable current state instead of replaying an obsolete start/end
                // callback. Two source transactions can commit while listeners wait.
                if (active(event)) suspensions.holdRobot(event.robotId(), event.source(), event.sourceId());
                else suspensions.releaseRobot(event.robotId(), event.source(), event.sourceId());
            });
        } catch (RuntimeException unavailable) {
            // The source lifecycle is already committed. A durable reconciliation
            // pass retries from that source rather than turning this into a
            // misleading HTTP failure after a successful interaction commit.
            log.warn("Patrol interruption reconciliation deferred for robot {}, source {}", event.robotId(), event.source());
        }
    }

    private boolean active(Changed event) {
        return switch(event.source()) {
            case "VOICE" -> voices.findById(event.sourceId()).filter(row->row.getRobot()!=null
                    && event.robotId().equals(row.getRobot().getRobotId())
                    && row.getStatus()==VoiceInteractionStatus.ACTIVE).isPresent();
            case "MEDIA" -> media.findById(event.sourceId()).filter(row->row.getRobot()!=null
                    && event.robotId().equals(row.getRobot().getRobotId())
                    && row.getMode()==MediaSession.Mode.ASSIST && row.open()).isPresent();
            case "INCIDENT" -> incidents.findById(event.sourceId()).filter(row->row.getRobot()!=null
                    && event.robotId().equals(row.getRobot().getRobotId()) && row.getArchivedAt()==null
                    && OPEN_INCIDENTS.contains(row.getStatus())).isPresent();
            default -> throw new IllegalArgumentException("Unsupported patrol event source");
        };
    }
}
