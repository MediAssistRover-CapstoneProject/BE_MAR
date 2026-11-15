package com.mediassistrover.backend.ai;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediassistrover.backend.ai.PatrolFallEventService.DetectionKind;
import com.mediassistrover.backend.ai.PatrolFallEventService.PatientLocation;
import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.exception.BusinessRuleException;
import com.mediassistrover.backend.exception.ConflictException;
import com.mediassistrover.backend.mqtt.MqttSessionRegistry;
import com.mediassistrover.backend.mqtt.MqttWire;
import com.mediassistrover.backend.repository.*;
import com.mediassistrover.backend.security.DevicePrincipal;
import com.mediassistrover.backend.service.*;
import com.mediassistrover.backend.validation.DeviceResourceScopePolicy;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** Independent observations never complete, cancel or replace the current robot Job. */
@Service
@RequiredArgsConstructor
public class RobotFallEventService {
    public record Request(@NotNull UUID eventId, @NotNull UUID monitoringSessionId, @NotNull Instant detectedAt,
                          @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal confidence,
                          @NotBlank @Size(max = 100) String algorithm, @NotNull DetectionKind detectionKind,
                          PatientLocation patientLocation) { }
    public record Result(UUID detectionEventId, UUID incidentId, boolean duplicate) { }
    private final DeviceResourceScopePolicy scope;
    private final RobotRepository robots;
    private final RobotMqttSessionRepository links;
    private final MqttSessionRegistry registry;
    private final RobotNavigationSessionRepository sessions;
    private final NavigationSessionService navigation;
    private final PatrolFallEventService locationEvidence;
    private final DetectionEventRepository detections;
    private final EmergencyIncidentRepository incidents;
    private final MonitoringReceiptService receipts;
    private final EmergencyFlowService emergencies;
    private final RuntimeSettingService settings;
    private final MqttWire wire;
    private final Clock clock;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Result record(Request request, DevicePrincipal device, RequestMetadata metadata) {
        var authenticated = scope.requireCurrentDevice(device);
        var robot = robots.findByIdForUpdate(authenticated.getRobotId())
                .orElseThrow(() -> new AccessDeniedException("Robot is unavailable"));
        if (robot.getDeviceSecretHash() == null || robot.getDeviceTokenVersion() != device.tokenVersion()
                || !robot.getRobotCode().equals(device.robotCode()))
            throw new AccessDeniedException("Device session is no longer valid");
        var payload = wire.object().put("source", "CONTINUOUS_VISION_AI")
                .put("event_id", request.eventId().toString())
                .put("monitoring_session_id", request.monitoringSessionId().toString())
                .put("detected_at", request.detectedAt().toString()).put("confidence", request.confidence())
                .put("algorithm", request.algorithm()).put("detection_kind", request.detectionKind().name());
        if (request.patientLocation() != null) payload.set("patient_location_candidate", PatrolFallEventService.patientPayload(request.patientLocation(), wire));
        var replay = receipts.duplicateContinuousFall(request.eventId(), robot, payload);
        if (replay.isPresent()) {
            var value = replay.get();
            return new Result(UUID.fromString(value.path("detection_event_id").asText()),
                    UUID.fromString(value.path("incident_id").asText()), true);
        }
        var now = Instant.now(clock);
        if (request.detectedAt().isBefore(now.minusSeconds(15)) || request.detectedAt().isAfter(now.plusSeconds(2)))
            throw new BusinessRuleException("Fall observation timestamp must be fresh");
        var link = links.findById(robot.getRobotId()).orElse(null);
        if (robot.getStatus() == RobotStatus.RETIRED || link == null || link.isClosed()
                || link.getHelloJson() == null || link.getHelloJson().path("simulation").asBoolean()
                || !registry.fresh(link) || !continuousCapable(link))
            throw new BusinessRuleException("Continuous fall observation requires a live opted-in Pi");
        var map = robot.getCurrentMap();
        if (map == null) throw new BusinessRuleException("Fall observation requires an assigned robot map");

        // A map projection is optional: unknown location must not suppress a real fall transition.
        var session = sessions.findById(robot.getRobotId()).orElse(null);
        PatrolFallEventService.Viewpoint viewpoint = null;
        PatientLocation patient = null;
        if (session != null && session.open(now) && map.getStatus() == MapStatus.ACTIVE
                && session.getMap().getMapId().equals(map.getMapId())) {
            try {
                navigation.requireReadyContext(robot.getRobotId(), session.getSessionId(), map.getMapId());
                viewpoint = locationEvidence.viewpoint(session, request.detectedAt());
                patient = PatrolFallEventService.verifiedPatientLocation(request.detectedAt(), request.patientLocation(),
                        session, map, viewpoint);
            } catch (BusinessRuleException unavailable) {
                // The suspicion still reaches staff; invalid map data is not presented as a patient location.
            }
        }
        PatrolFallEventService.requireFloorEvidence(request.detectionKind(), patient);

        var previous = incidents.findFirstByRobotRobotIdAndIncidentTypeAndArchivedAtIsNullOrderByOccurredAtDesc(
                robot.getRobotId(), EmergencyIncident.SUSPECTED_FALL_TYPE).orElse(null);
        long cooldown = settings.getLong("fall.continuous-alert-cooldown-seconds", 30, 1, 300);
        double distanceM = settings.getLong("fall.continuous-dedup-distance-cm", 100, 20, 300) / 100.;
        if (coalesces(previous, patient, map, now, cooldown, distanceM)) {
            var result = new Result(previous.getDetectionEvent().getDetectionEventId(), previous.getIncidentId(), true);
            remember(request, robot, payload, result);
            return result;
        }
        if (detections.existsById(request.eventId())) throw new ConflictException("Detection event ID already exists");
        var data = payload.deepCopy().put("staff_review_required", true);
        data.remove("patient_location_candidate");
        data.put("patient_location_status", patient != null ? "AVAILABLE"
                : request.patientLocation() == null ? "UNAVAILABLE" : "REJECTED");
        if (viewpoint != null) data.putObject("robot_viewpoint").put("x_m", viewpoint.x()).put("y_m", viewpoint.y())
                .put("captured_at", viewpoint.capturedAt().toString()).put("map_asset_hash", session.getMapAssetHash());
        BigDecimal x = null, y = null;
        if (patient != null) {
            x = patient.xM().setScale(4, RoundingMode.HALF_UP);
            y = patient.yM().setScale(4, RoundingMode.HALF_UP);
            var position = data.putObject("patient_location").put("basis", "PATIENT_RGBD")
                    .put("x_m", x).put("y_m", y).put("range_m", patient.rangeM())
                    .put("captured_at", patient.capturedAt().toString()).put("map_id", map.getMapId().toString())
                    .put("map_asset_hash", patient.mapAssetHash())
                    .put("navigation_session_id", patient.navigationSessionId().toString())
                    .put("initialization_revision", patient.initializationRevision())
                    .put("depth_skew_ms", patient.depthSkewMs());
            if (patient.floorHeightM() != null) position.put("floor_height_m", patient.floorHeightM());
        }
        var basis = patient == null ? DetectionLocationBasis.UNKNOWN : DetectionLocationBasis.OBJECT;
        data.put("location_basis", basis.name());
        var detection = detections.saveAndFlush(DetectionEvent.builder().detectionEventId(request.eventId())
                .robot(robot).map(map).eventType(DetectionEventType.SUSPECTED_FALL)
                .confidenceScore(request.confidence().setScale(4, RoundingMode.HALF_UP))
                .xM(x).yM(y).locationBasis(basis).modelVersion(request.algorithm())
                .eventPayloadJson(data.deepCopy()).detectedAt(request.detectedAt()).receivedAt(now).build());
        String description = request.detectionKind() == DetectionKind.PERSON_ON_FLOOR
                ? "Robot phát hiện người có tư thế nằm gần sàn. Vui lòng xác minh qua camera."
                : "Robot phát hiện người nghi té ngã. Vui lòng xác minh qua camera.";
        var incident = emergencies.createForStaffReview(new EmergencyFlowService.Command(
                "CONTINUOUS_FALL:" + request.eventId(), robot, map, null, detection,
                EmergencyIncident.SUSPECTED_FALL_TYPE, EmergencyIncidentSeverity.WARNING, description,
                x, y, request.detectedAt(), RequestSourceType.DETECTOR, data, "CONTINUOUS_FALL_REVIEW_REQUESTED", metadata));
        var result = new Result(request.eventId(), incident.incident().getIncidentId(), false);
        remember(request, robot, payload, result);
        return result;
    }

    static boolean coalesces(EmergencyIncident previous, PatientLocation patient, FacilityMap map,
                              Instant now, long cooldown, double maximumDistanceM) {
        if (previous == null || previous.getDetectionEvent() == null || previous.getMap() == null
                || !map.getMapId().equals(previous.getMap().getMapId()) || previous.getOccurredAt() == null
                || !previous.getOccurredAt().isAfter(now.minusSeconds(cooldown))) return false;
        // A review may remain OPEN for hours; that must not hide a later patient in another location.
        var detected = previous.getDetectionEvent();
        if (patient == null || detected.getLocationBasis() != DetectionLocationBasis.OBJECT
                || detected.getXM() == null || detected.getYM() == null) return true;
        return Math.hypot(patient.xM().subtract(detected.getXM()).doubleValue(),
                patient.yM().subtract(detected.getYM()).doubleValue()) <= maximumDistanceM;
    }

    private void remember(Request request, Robot robot, ObjectNode payload, Result result) {
        receipts.recordContinuousFall(request.eventId(), robot, payload, wire.object().put("transport", "HTTPS")
                .put("detection_event_id", result.detectionEventId().toString())
                .put("incident_id", result.incidentId().toString()));
    }

    private static boolean continuousCapable(RobotMqttSession link) {
        for (var item : link.getHelloJson().path("capabilities"))
            if ("continuous-fall-v1".equals(item.asText())) return true;
        return false;
    }
}
