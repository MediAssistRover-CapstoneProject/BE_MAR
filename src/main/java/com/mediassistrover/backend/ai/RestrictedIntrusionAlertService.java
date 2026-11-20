package com.mediassistrover.backend.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediassistrover.backend.entity.Alert;
import com.mediassistrover.backend.entity.AlertRecipient;
import com.mediassistrover.backend.entity.DetectionEvent;
import com.mediassistrover.backend.entity.JobStep;
import com.mediassistrover.backend.enums.AlertAckPolicy;
import com.mediassistrover.backend.enums.AlertChannel;
import com.mediassistrover.backend.enums.AlertDeliveryStatus;
import com.mediassistrover.backend.enums.AlertSeverity;
import com.mediassistrover.backend.enums.AlertStatus;
import com.mediassistrover.backend.repository.AlertRecipientRepository;
import com.mediassistrover.backend.repository.AlertRepository;
import com.mediassistrover.backend.service.SosResponderRouting;
import com.mediassistrover.backend.service.PatrolInterruptionEvents;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class RestrictedIntrusionAlertService {
    public static final String TYPE = "PATROL_RESTRICTED_INTRUSION";

    private final AiProperties properties;
    private final AlertRepository alerts;
    private final AlertRecipientRepository recipients;
    private final SosResponderRouting routing;
    private final ObjectMapper json;
    private final Clock clock;
    private final PatrolInterruptionEvents interruptions;

    public static boolean isRoutedAlert(Alert alert) {
        return alert != null && TYPE.equals(alert.getNotificationType()) && alert.getDetectionEvent() != null;
    }

    public static boolean isReviewOpen(Alert alert) {
        return isRoutedAlert(alert) && alert.getArchivedAt() == null
                && (alert.getStatus() == AlertStatus.OPEN || alert.getStatus() == AlertStatus.ACKNOWLEDGED);
    }

    /** The sensing result transaction holds the Zone row lock before persisting the DetectionEvent. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Alert record(DetectionEvent event, JobStep step) {
        return record(event, step, false);
    }

    /** A verified live patrol observation always gets a review, even after an older review was resolved. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Alert recordTracked(DetectionEvent event, JobStep step) {
        return record(event, step, true);
    }

    private Alert record(DetectionEvent event, JobStep step, boolean tracked) {
        var zone = event.getZone();
        Instant now = Instant.now(clock);
        var active = alerts.findActiveRestrictedByRobotId(event.getRobot().getRobotId()).stream()
                .filter(a -> a.getZone() != null && zone.getZoneId().equals(a.getZone().getZoneId())).findFirst();
        if (active.isPresent()) {
            var alert = active.get();
            interruptions.hold(event.getRobot().getRobotId(), "RESTRICTED_ALERT", alert.getAlertId());
            return alert;
        }
        Instant cutoff = now.minusSeconds(properties.getRestrictedAlertCooldownSeconds());
        if (!tracked && !alerts.findRecentZoneAlerts(zone.getZoneId(), TYPE, cutoff, PageRequest.of(0, 1)).isEmpty()) return null;

        var assignment = routing.resolve(event.getMap(), now, properties.getRestrictedAlertRoomId());
        var responder = assignment.responder();
        var data = json.createObjectNode()
                .put("detection_event_id", event.getDetectionEventId().toString())
                .put("map_id", event.getMap().getMapId().toString())
                .put("zone_id", zone.getZoneId().toString())
                .put("zone_name", zone.getZoneName())
                .put("robot_id", event.getRobot().getRobotId().toString())
                .put("detected_at", event.getDetectedAt().toString())
                .put("routing_status", assignment.status().name())
                .put("staff_review_required", true).put("camera_review_mode", "MONITOR")
                .put("location_basis", event.getLocationBasis().name());
        if (event.getEventPayloadJson().has("person_location"))
            data.set("person_location", event.getEventPayloadJson().path("person_location").deepCopy());
        if (event.getEventPayloadJson().has("track_id"))
            data.set("track_id", event.getEventPayloadJson().path("track_id"));
        var requested = json.createArrayNode();
        if (responder != null) requested.add(responder.getAccountId().toString());

        Alert alert = alerts.saveAndFlush(Alert.builder().detectionEvent(event).jobStep(step).zone(zone)
                .notificationType(TYPE).severity(AlertSeverity.WARNING).status(AlertStatus.OPEN)
                .message("Phát hiện người trong vùng cấm " + zone.getZoneName())
                .dataJson(data).requestedRecipientsJson(requested).requireAck(true).ackPolicy(AlertAckPolicy.ANY)
                .operationKey(TYPE + ":" + event.getDetectionEventId()).createdAt(now).build());
        if (responder != null) {
            recipients.save(AlertRecipient.builder().alert(alert).account(responder).channel(AlertChannel.WEB)
                    .recipientAddressSnapshot(responder.getUsername()).resolvedRoleSnapshot(responder.getRole().name())
                    .deliveryStatus(AlertDeliveryStatus.PENDING).createdAt(now).build());
        }
        interruptions.hold(event.getRobot().getRobotId(), "RESTRICTED_ALERT", alert.getAlertId());
        return alert;
    }
}
