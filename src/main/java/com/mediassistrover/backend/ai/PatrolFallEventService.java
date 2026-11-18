package com.mediassistrover.backend.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.exception.BusinessRuleException;
import com.mediassistrover.backend.exception.ConflictException;
import com.mediassistrover.backend.mqtt.MqttSessionRegistry;
import com.mediassistrover.backend.mqtt.MqttWire;
import com.mediassistrover.backend.mqtt.NavigationLivePoseService;
import com.mediassistrover.backend.repository.*;
import com.mediassistrover.backend.security.DevicePrincipal;
import com.mediassistrover.backend.service.EmergencyFlowService;
import com.mediassistrover.backend.service.NavigationSessionService;
import com.mediassistrover.backend.service.PatrolWorkflowService;
import com.mediassistrover.backend.service.RequestMetadata;
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
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A checkpoint reports a suspicion and a camera viewpoint for staff review. */
@Service
@RequiredArgsConstructor
public class PatrolFallEventService {
    public record Request(@NotNull UUID eventId, @NotNull UUID jobStepId, @NotNull Instant detectedAt,
                          @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal confidence,
                          @NotBlank @Size(max = 100) String algorithm, PatientLocation patientLocation, DetectionKind detectionKind) {
        public Request(UUID eventId, UUID jobStepId, Instant detectedAt, BigDecimal confidence,
                       String algorithm, PatientLocation patientLocation) {
            this(eventId, jobStepId, detectedAt, confidence, algorithm, patientLocation, null);
        }
        public DetectionKind kind() { return detectionKind == null ? DetectionKind.FALL_TRANSITION : detectionKind; }
    }
    public enum DetectionKind { FALL_TRANSITION, PERSON_ON_FLOOR }
    /** Optional map projection from the RGB frame used for detection and its registered depth frame. */
    public record PatientLocation(BigDecimal xM, BigDecimal yM, BigDecimal rangeM, Instant capturedAt,
                                  UUID navigationSessionId, UUID mapId, String mapAssetHash,
                                  Long initializationRevision, BigDecimal depthSkewMs, BigDecimal floorHeightM) {
        public PatientLocation(BigDecimal xM, BigDecimal yM, BigDecimal rangeM, Instant capturedAt,
                               UUID navigationSessionId, UUID mapId, String mapAssetHash,
                               Long initializationRevision, BigDecimal depthSkewMs) {
            this(xM, yM, rangeM, capturedAt, navigationSessionId, mapId, mapAssetHash,
                    initializationRevision, depthSkewMs, null);
        }
    }
    public record Result(UUID detectionEventId, UUID incidentId, boolean duplicate) { }

    private final DeviceResourceScopePolicy scope;
    private final JobStepRepository steps;
    private final JobRepository jobs;
    private final RobotRepository robots;
    private final RobotMqttSessionRepository links;
    private final MqttSessionRegistry registry;
    private final RobotNavigationSessionRepository sessions;
    private final NavigationSessionService navigation;
    private final NavigationLivePoseService livePoses;
    private final DetectionEventRepository detections;
    private final EmergencyIncidentRepository incidents;
    private final ServiceRequestRepository requests;
    private final MonitoringReceiptService receipts;
    private final EmergencyFlowService emergencies;
    private final MqttWire wire;
    private final Clock clock;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Result record(Request request, DevicePrincipal device, RequestMetadata metadata) {
        var authenticated = scope.requireCurrentDevice(device);
        var jobId = steps.findJobId(request.jobStepId()).orElseThrow(PatrolFallEventService::outsidePatrol);
        var job = jobs.lockById(jobId).orElseThrow(PatrolFallEventService::outsidePatrol);
        if (job.getRobot() == null || !job.getRobot().getRobotId().equals(authenticated.getRobotId()))
            throw outsidePatrol();
        var robotId = authenticated.getRobotId();
        var robot = robots.findByIdForUpdate(robotId).orElseThrow(PatrolFallEventService::outsidePatrol);
        if (robot.getDeviceSecretHash() == null || robot.getDeviceTokenVersion() != device.tokenVersion()
                || !robot.getRobotCode().equals(device.robotCode())) throw outsidePatrol();

        var payload = wire.object().put("event_id", request.eventId().toString()).put("job_id", jobId.toString())
                .put("job_step_id", request.jobStepId().toString()).put("source", "PATROL_VISION_AI")
                .put("detected_at", request.detectedAt().toString()).put("confidence", request.confidence())
                .put("algorithm", request.algorithm());
        if (request.detectionKind() != null) payload.put("detection_kind", request.kind().name());
        if (request.patientLocation() != null) {
            var candidate = patientPayload(request.patientLocation(), wire);
            if (request.patientLocation().floorHeightM() == null) candidate.remove("floorHeightM");
            payload.set("patient_location_candidate", candidate);
        }
        // A lost HTTP response may be retried after this sensing step has ended.
        if (receipts.duplicatePatrolFall(request.eventId(), robot, payload)) {
            var existing = incidents.findByDetectionEventDetectionEventId(request.eventId()).orElseThrow();
            return new Result(request.eventId(), existing.getIncidentId(), true);
        }

        var step = job.getSelectedJobStep();
        if (job.getStatus() != JobStatus.RUNNING || job.getCancelRequestedAt() != null
                || !PatrolWorkflowService.isPatrol(job.getWorkflow()) || step == null
                || !step.getJobStepId().equals(request.jobStepId()) || step.getStatus() != JobStepStatus.RUNNING
                || step.getInterruptionReason() != null || !"EXECUTE_SENSING".equals(step.getStepTypeSnapshot())
                || !step.getResolvedInputsJson().path("fall_detection").asBoolean())
            throw new BusinessRuleException("Patrol fall event requires an active opted-in sensing step");
        var now = Instant.now(clock);
        if (step.getStartedAt() == null || request.detectedAt().isBefore(step.getStartedAt().minusSeconds(1))
                || request.detectedAt().isBefore(now.minusSeconds(15)) || request.detectedAt().isAfter(now.plusSeconds(2)))
            throw new BusinessRuleException("Patrol fall event timestamp must be fresh and within the sensing operation");

        var map = job.getMap();
        var session = sessions.findById(robotId).orElse(null);
        var link = links.findById(robotId).orElse(null);
        if (map == null || map.getStatus() != MapStatus.ACTIVE || robot.getCurrentMap() == null
                || !map.getMapId().equals(robot.getCurrentMap().getMapId()) || session == null
                || link == null || link.getHelloJson() == null || link.getHelloJson().path("simulation").asBoolean()
                || !PatrolWorkflowService.fallCapable(link) || !registry.fresh(link))
            throw new BusinessRuleException("Patrol fall event requires a commissioned live Pi on the patrol map");
        navigation.requireReadyContext(robotId, session.getSessionId(), map.getMapId());

        String operationKey = "PATROL_FALL:" + request.jobStepId();
        if (requests.findByIdempotencyKey(operationKey).isPresent())
            throw new ConflictException("This patrol checkpoint already reported a suspected fall");
        if (detections.existsById(request.eventId())) throw new ConflictException("Detection event ID already exists");
        var viewpoint = viewpoint(session, request.detectedAt());
        var patient = verifiedPatientLocation(request, session, map, viewpoint);
        requireFloorEvidence(request.kind(), patient);
        var basis = patient != null ? DetectionLocationBasis.OBJECT
                : viewpoint == null ? DetectionLocationBasis.UNKNOWN : DetectionLocationBasis.ROBOT;
        var data = payload.deepCopy().put("location_basis", basis.name()).put("staff_review_required", true)
                .put("detection_kind", request.kind().name());
        data.remove("patient_location_candidate");
        data.put("patient_location_status", patient != null ? "AVAILABLE"
                : request.patientLocation() == null ? "UNAVAILABLE" : "REJECTED");
        BigDecimal x = viewpoint == null ? null : viewpoint.x();
        BigDecimal y = viewpoint == null ? null : viewpoint.y();
        if (viewpoint != null) data.putObject("robot_viewpoint").put("x_m", viewpoint.x()).put("y_m", viewpoint.y())
                .put("captured_at", viewpoint.capturedAt().toString())
                .put("map_asset_hash", session.getMapAssetHash());
        if (patient != null) {
            x = patient.xM().setScale(4, RoundingMode.HALF_UP);
            y = patient.yM().setScale(4, RoundingMode.HALF_UP);
            data.putObject("patient_location").put("basis", "PATIENT_RGBD")
                    .put("x_m", x).put("y_m", y).put("range_m", patient.rangeM())
                    .put("captured_at", patient.capturedAt().toString()).put("map_id", map.getMapId().toString())
                    .put("map_asset_hash", patient.mapAssetHash())
                    .put("navigation_session_id", patient.navigationSessionId().toString())
                    .put("initialization_revision", patient.initializationRevision())
                    .put("depth_skew_ms", patient.depthSkewMs());
            if (patient.floorHeightM() != null)
                ((com.fasterxml.jackson.databind.node.ObjectNode) data.path("patient_location"))
                        .put("floor_height_m", patient.floorHeightM());
        }
        var detection = detections.saveAndFlush(DetectionEvent.builder().detectionEventId(request.eventId())
                .robot(robot).map(map).eventType(DetectionEventType.SUSPECTED_FALL)
                .confidenceScore(request.confidence().setScale(4, RoundingMode.HALF_UP))
                .xM(x).yM(y).locationBasis(basis).modelVersion(request.algorithm())
                .eventPayloadJson(data.deepCopy()).detectedAt(request.detectedAt()).receivedAt(now).build());
        receipts.recordPatrolFall(request.eventId(), robot, payload, wire.object().put("transport", "HTTPS")
                .put("navigation_session_id", session.getSessionId().toString()));
        String description = request.kind() == DetectionKind.PERSON_ON_FLOOR
                ? "Robot phát hiện người có tư thế nằm gần sàn. Vui lòng xác minh qua camera."
                : "Robot tuần tra phát hiện người nghi té ngã. Vui lòng xác minh qua camera.";
        if (patient != null) description += " Vị trí người được ước tính từ camera độ sâu tại thời điểm quan sát.";
        else if (viewpoint != null) description += " Điểm trên bản đồ là vị trí robot lúc quan sát.";
        var result = emergencies.createForStaffReview(new EmergencyFlowService.Command(operationKey,
                robot, map, null, detection, EmergencyIncident.SUSPECTED_FALL_TYPE, EmergencyIncidentSeverity.WARNING,
                description,
                x, y, request.detectedAt(), RequestSourceType.DETECTOR, data, "PATROL_FALL_REVIEW_REQUESTED", metadata));
        return new Result(request.eventId(), result.incident().getIncidentId(), false);
    }

    record Viewpoint(BigDecimal x, BigDecimal y, Instant capturedAt) { }

    static PatientLocation verifiedPatientLocation(Request request, RobotNavigationSession session,
                                                   FacilityMap map, Viewpoint viewpoint) {
        return verifiedPatientLocation(request.detectedAt(), request.patientLocation(), session, map, viewpoint);
    }

    static PatientLocation verifiedPatientLocation(Instant detectedAt, PatientLocation location,
                                                   RobotNavigationSession session, FacilityMap map, Viewpoint viewpoint) {
        if (viewpoint == null || location == null || !detectedAt.equals(location.capturedAt())
                || !session.getSessionId().equals(location.navigationSessionId())
                || !map.getMapId().equals(location.mapId()) || location.mapAssetHash() == null
                || !location.mapAssetHash().equals(session.getMapAssetHash())
                || !location.mapAssetHash().equals(map.getAssetHash()) || location.initializationRevision() == null
                || location.initializationRevision() < 1
                || session.getInitializationRevision() != location.initializationRevision()) return null;

        double x = number(location.xM()), y = number(location.yM()), depth = number(location.rangeM());
        double skew = number(location.depthSkewMs());
        if (!Double.isFinite(x) || !Double.isFinite(y) || !(depth >= .20 && depth <= 5.)
                || !(Math.abs(skew) <= 40.)) return null;
        double deltaX = x - number(viewpoint.x()), deltaY = y - number(viewpoint.y());
        // Optical-axis depth differs from horizontal range for off-axis rays.
        if (!(Math.hypot(deltaX, deltaY) <= 1.8 * depth + .75)) return null;

        double dx = x - number(map.getOriginXM()), dy = y - number(map.getOriginYM());
        double yaw = number(map.getOriginYawRad());
        double mapX = Math.cos(yaw) * dx + Math.sin(yaw) * dy;
        double mapY = -Math.sin(yaw) * dx + Math.cos(yaw) * dy;
        return mapX >= 0 && mapX < number(map.getWidthM()) && mapY >= 0 && mapY < number(map.getHeightM())
                ? location : null;
    }

    private static double number(BigDecimal value) { return value == null ? Double.NaN : value.doubleValue(); }

    static com.fasterxml.jackson.databind.node.ObjectNode patientPayload(PatientLocation location, MqttWire wire) {
        var data = wire.object().put("xM", location.xM()).put("yM", location.yM()).put("rangeM", location.rangeM())
                .put("capturedAt", location.capturedAt() == null ? null : location.capturedAt().toString())
                .put("navigationSessionId", location.navigationSessionId() == null ? null : location.navigationSessionId().toString())
                .put("mapId", location.mapId() == null ? null : location.mapId().toString())
                .put("mapAssetHash", location.mapAssetHash()).put("initializationRevision", location.initializationRevision())
                .put("depthSkewMs", location.depthSkewMs());
        if (location.floorHeightM() != null) data.put("floorHeightM", location.floorHeightM());
        return data;
    }

    static void requireFloorEvidence(DetectionKind kind, PatientLocation location) {
        if (kind == DetectionKind.PERSON_ON_FLOOR && (location == null || location.floorHeightM() == null
                || number(location.floorHeightM()) < -.15 || number(location.floorHeightM()) > .45
                || !Double.isFinite(number(location.floorHeightM()))))
            throw new BusinessRuleException("Person-on-floor suspicion requires synchronized registered depth and a low hip position");
    }

    Viewpoint viewpoint(RobotNavigationSession session, Instant detectedAt) {
        JsonNode sample = livePoses.nearest(session.getRobotId(), detectedAt, Duration.ofSeconds(2));
        if (sample == null || !session.getSessionId().toString().equals(sample.path("navigationSessionId").asText())
                || !session.getMap().getMapId().toString().equals(sample.path("mapId").asText())
                || !Objects.equals(session.getMapAssetHash(), sample.path("mapAssetHash").asText())
                || session.getInitializationRevision() != sample.path("initializationRevision").asLong(-1)) return null;
        Instant captured = Instant.parse(sample.path("capturedAt").asText());
        if (captured.isBefore(detectedAt.minusSeconds(2)) || captured.isAfter(detectedAt.plusSeconds(2))) return null;
        JsonNode pose = sample.path("pose"), x = pose.path("xM"), y = pose.path("yM");
        if (!x.isNumber() || !y.isNumber() || Math.abs(x.asDouble()) >= 100_000_000
                || Math.abs(y.asDouble()) >= 100_000_000) return null;
        return new Viewpoint(x.decimalValue().setScale(4, RoundingMode.HALF_UP),
                y.decimalValue().setScale(4, RoundingMode.HALF_UP), captured);
    }

    private static AccessDeniedException outsidePatrol() {
        return new AccessDeniedException("Patrol operation is outside this Device scope");
    }
}
