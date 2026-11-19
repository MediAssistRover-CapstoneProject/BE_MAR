package com.mediassistrover.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.DetectionEventType;
import com.mediassistrover.backend.enums.DetectionLocationBasis;
import com.mediassistrover.backend.enums.EmergencyIncidentStatus;
import com.mediassistrover.backend.enums.ZoneType;
import com.mediassistrover.backend.exception.*;
import com.mediassistrover.backend.mapping.*;
import com.mediassistrover.backend.repository.*;
import com.mediassistrover.backend.security.AccountPrincipal;
import com.mediassistrover.backend.validation.StaffOperationalScopePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SosResponseRouteService {
    private static final double PATIENT_WALKING_OFFSET_M = .75;
    private final EmergencyIncidentRepository incidents;
    private final StaffOperationalScopePolicy scope;
    private final RuntimeSettingService settings;
    private final RoomRepository rooms;
    private final EndpointRepository endpoints;
    private final MapBundleService maps;

    public record Point(double xCm, double yCm) {
        static Point centimetres(SosRouteGrid.Point point) {
            return new Point(point.x() * 100, point.y() * 100);
        }
    }
    public record Response(String status, String reason, UUID mapId, String mapAssetHash,
                           String unit, Point origin, Point destination, List<Point> points, Double distanceCm,
                           String destinationBasis, Instant destinationCapturedAt, Point patientLocation,
                           Point robotObservationLocation, Double cameraRangeM, RobotApproach robotApproach) { }

    public record RobotApproach(UUID endpointId, String endpointCode, String endpointName,
                                Point position, double distanceToPatientCm) { }

    private record Destination(SosRouteGrid.Point point, String basis, Instant capturedAt,
                               SosRouteGrid.Point patient, SosRouteGrid.Point observation, Double cameraRangeM) { }

    private record Candidate(Endpoint endpoint, SosRouteGrid.Point point, double metres) {
        RobotApproach proposal() {
            return new RobotApproach(endpoint.getEndpointId(), endpoint.getEndpointCode(), endpoint.getEndpointName(),
                    Point.centimetres(point), metres * 100);
        }
    }

    public Response preview(UUID id, AccountPrincipal actor) {
        EmergencyIncident incident = incidentForView(id, actor);
        if (!incident.isSuspectedFall() || !Set.of(EmergencyIncidentStatus.OPEN,
                EmergencyIncidentStatus.ACKNOWLEDGED, EmergencyIncidentStatus.IN_PROGRESS).contains(incident.getStatus()))
            throw new BusinessRuleException("An active suspected fall incident is required");
        FacilityMap map = incident.getMap();
        if (map == null) return unavailable(null, null, "MAP_MISSING");
        Destination destination = destination(incident, map);
        if (destination == null) return unavailable(map, null, "FALL_VIEWPOINT_UNAVAILABLE");
        return response("PREVIEW", null, map, null, Point.centimetres(destination.point()), List.of(), null,
                destination.basis(), destination, null);
    }

    public Response route(UUID id, AccountPrincipal actor) {
        EmergencyIncident incident = incidentForView(id, actor);
        if (!incident.supportsResponderConfirmation() || incident.getStatus() != EmergencyIncidentStatus.IN_PROGRESS)
            throw new BusinessRuleException("An IN_PROGRESS SOS or confirmed fall incident is required");
        FacilityMap map = incident.getMap();
        if (map == null) return unavailable(null, null, "MAP_MISSING");
        Destination destination = destination(incident, map);
        if (destination == null) return unavailable(map, null,
                incident.isSuspectedFall() ? "FALL_VIEWPOINT_UNAVAILABLE" : "SOS_POSITION_MISSING");
        String roomSetting = settings.getString(SosResponderRouting.MONITORING_ROOM_SETTING, "").trim();
        if (roomSetting.isEmpty()) return unavailable(map, destination, "MONITORING_ROOM_MISSING");
        UUID roomId;
        try {
            roomId = UUID.fromString(roomSetting);
        } catch (IllegalArgumentException error) {
            return unavailable(map, destination, "MONITORING_ROOM_INVALID");
        }
        Room room = rooms.findById(roomId).orElse(null);
        if (room == null || !room.isOperationalOn(map))
            return unavailable(map, destination, "MONITORING_ROOM_UNAVAILABLE");
        if (!room.getFloor().getFloorId().equals(map.getFloor().getFloorId()))
            return unavailable(map, destination, "DIFFERENT_FLOOR");
        List<Endpoint> candidates = endpoints.findByRoomRoomIdOrderByEndpointCodeAsc(roomId).stream()
                .filter(endpoint -> endpoint.isActive() && endpoint.isAccessible()
                        && endpoint.getMap().getMapId().equals(map.getMapId())).toList();
        if (candidates.isEmpty()) return unavailable(map, destination, "ORIGIN_ENDPOINT_MISSING_ON_MAP");
        SosRouteGrid grid;
        try {
            grid = maps.responseRouteGrid(map.getMapId());
        } catch (BusinessRuleException | ResourceNotFoundException error) {
            return unavailable(map, destination, "MAP_DATA_UNAVAILABLE");
        }
        if (grid.cell(destination.point()) < 0) return unavailable(map, destination,
                destination.patient() == null ? "SOS_POSITION_OUTSIDE_MAP" : "PATIENT_POSITION_OUTSIDE_MAP");
        List<SosRouteGrid.Point> origins = candidates.stream()
                .filter(endpoint -> endpoint.getXM() != null && endpoint.getYM() != null)
                .map(SosResponseRouteService::endpointPoint)
                .filter(grid::walkable).toList();
        if (origins.isEmpty()) return unavailable(map, destination, "ORIGIN_POSITION_NOT_FREE");
        var approach = approach(map, grid, destination);
        var target = destination.point();
        var basis = destination.basis();
        var route = grid.shortest(origins, target).orElse(null);
        if (route == null && destination.patient() != null && !grid.walkable(target)) {
            route = grid.shortestToVisibleNeighbor(origins, target, destination.observation(), PATIENT_WALKING_OFFSET_M)
                    .orElse(null);
            if (route != null) {
                target = route.points().getLast();
                basis = "PATIENT_RGBD_NEARBY_WALKABLE";
            }
        }
        if (route == null) {
            String reason = grid.walkable(destination.point()) ? "NO_PATH"
                    : destination.patient() == null ? "SOS_POSITION_NOT_FREE" : "PATIENT_POSITION_NOT_FREE";
            return response("UNAVAILABLE", reason, map, null, Point.centimetres(destination.point()), List.of(), null,
                    destination.basis(), destination, approach);
        }
        List<Point> points = route.points().stream().map(Point::centimetres).toList();
        return response("FOUND", null, map, points.getFirst(), Point.centimetres(target), points,
                route.distance() * 100, basis, destination, approach);
    }

    private EmergencyIncident incidentForView(UUID id, AccountPrincipal actor) {
        EmergencyIncident incident = incidents.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found"));
        scope.requireIncidentAccess(incident, actor);
        if (incident.getArchivedAt() != null) throw new ResourceNotFoundException("Incident not found");
        return incident;
    }

    private Candidate approach(FacilityMap map, SosRouteGrid grid, Destination destination) {
        if (destination.patient() == null || destination.observation() == null
                || !grid.hasClearView(destination.observation(), destination.patient())) return null;
        return endpoints.findByMapMapIdOrderByEndpointCodeAsc(map.getMapId()).stream()
                .filter(endpoint -> eligibleApproachEndpoint(endpoint, map))
                .map(endpoint -> {
                    var point = endpointPoint(endpoint);
                    var patient = destination.patient();
                    return new Candidate(endpoint, point, Math.hypot(point.x() - patient.x(), point.y() - patient.y()));
                })
                .filter(candidate -> candidate.metres() >= 1.2 && candidate.metres() <= 2.0)
                .filter(candidate -> grid.hasClearView(candidate.point(), destination.patient()))
                .min(Comparator.comparingDouble(Candidate::metres)).orElse(null);
    }

    private static boolean eligibleApproachEndpoint(Endpoint endpoint, FacilityMap map) {
        Zone zone = endpoint.getZone();
        return endpoint.getEndpointId() != null && endpoint.isActive() && endpoint.isAccessible()
                && endpoint.getMap() != null && map.getMapId().equals(endpoint.getMap().getMapId())
                && endpoint.getXM() != null && endpoint.getYM() != null
                && (zone == null || !zone.isActive()
                    || (zone.getZoneType() != ZoneType.KEEP_OUT && zone.getZoneType() != ZoneType.NO_STOP));
    }

    private static SosRouteGrid.Point endpointPoint(Endpoint endpoint) {
        return new SosRouteGrid.Point(endpoint.getXM().doubleValue(), endpoint.getYM().doubleValue());
    }

    private Destination destination(EmergencyIncident incident, FacilityMap map) {
        BigDecimal x = incident.getXM(), y = incident.getYM();
        var point = x == null || y == null ? null : new SosRouteGrid.Point(x.doubleValue(), y.doubleValue());
        if (!incident.isSuspectedFall()) return point == null ? null : new Destination(point, "INCIDENT_POSITION",
                incident.getOccurredAt(), null, null, null);

        // Both the estimated patient position and robot viewpoint are frozen at detection time.
        DetectionEvent detection = incident.getDetectionEvent();
        if (detection == null || detection.getEventType() != DetectionEventType.SUSPECTED_FALL
                || detection.getDetectedAt() == null
                || detection.getMap() == null || !map.getMapId().equals(detection.getMap().getMapId())
                || detection.getRobot() == null || incident.getRobot() == null
                || !incident.getRobot().getRobotId().equals(detection.getRobot().getRobotId())) return null;
        JsonNode data = detection.getEventPayloadJson();
        if (data == null || !("PATROL_VISION_AI".equals(data.path("source").asText())
                || "CONTINUOUS_VISION_AI".equals(data.path("source").asText()))) return null;
        JsonNode viewpoint = data.path("robot_viewpoint");
        if (detection.getLocationBasis() == DetectionLocationBasis.OBJECT) {
            return point == null ? null : patientDestination(point, x, y, detection, map, data.path("patient_location"), viewpoint);
        }
        if (detection.getLocationBasis() == DetectionLocationBasis.ROBOT) {
            if (point == null || !sameCoordinate(viewpoint.path("x_m"), x) || !sameCoordinate(viewpoint.path("y_m"), y))
                return null;
        } else if (detection.getLocationBasis() != DetectionLocationBasis.UNKNOWN || x != null || y != null
                || !viewpoint.hasNonNull("map_asset_hash")) {
            return null;
        }
        Instant captured = capture(viewpoint, detection, map);
        var observed = storedPoint(viewpoint);
        return captured == null || observed == null ? null
                : new Destination(observed, "ROBOT_VIEWPOINT", captured, null, observed, null);
    }

    private Destination patientDestination(SosRouteGrid.Point point, BigDecimal x, BigDecimal y,
                                           DetectionEvent detection, FacilityMap map, JsonNode patient, JsonNode viewpoint) {
        if (!"PATIENT_RGBD".equals(patient.path("basis").asText())
                || !map.getMapId().toString().equals(patient.path("map_id").asText())
                || !patient.hasNonNull("map_asset_hash") || !patient.path("range_m").isNumber()
                || !sameCoordinate(patient.path("x_m"), x) || !sameCoordinate(patient.path("y_m"), y)) return null;
        Instant captured = capture(patient, detection, map);
        double range = patient.path("range_m").asDouble(Double.NaN);
        if (!detection.getDetectedAt().equals(captured) || !(range >= .2 && range <= 5.)) return null;
        var observed = capture(viewpoint, detection, map) == null ? null : storedPoint(viewpoint);
        return new Destination(point, "PATIENT_RGBD", captured, point, observed, range);
    }

    private Instant capture(JsonNode data, DetectionEvent detection, FacilityMap map) {
        if (data.has("map_asset_hash")
                && !Objects.equals(map.getAssetHash(), data.path("map_asset_hash").asText())) return null;
        try {
            Instant captured = Instant.parse(data.path("captured_at").asText());
            return captured.isBefore(detection.getDetectedAt().minusSeconds(2))
                    || captured.isAfter(detection.getDetectedAt().plusSeconds(2)) ? null : captured;
        } catch (DateTimeParseException invalid) {
            return null;
        }
    }

    private boolean sameCoordinate(JsonNode stored, BigDecimal coordinate) {
        return stored.isNumber() && Double.isFinite(stored.doubleValue()) && stored.decimalValue().compareTo(coordinate) == 0;
    }

    private Response unavailable(FacilityMap map, Destination destination, String reason) {
        return response("UNAVAILABLE", reason, map, null, destination == null ? null : Point.centimetres(destination.point()),
                List.of(), null, destination == null ? null : destination.basis(), destination, null);
    }

    private Response response(String status, String reason, FacilityMap map, Point origin, Point target,
                              List<Point> points, Double distance, String basis, Destination destination,
                              Candidate approach) {
        return new Response(status, reason, map == null ? null : map.getMapId(),
                map == null ? null : map.getAssetHash(), "cm", origin, target, points, distance, basis,
                destination == null ? null : destination.capturedAt(),
                destination == null || destination.patient() == null ? null : Point.centimetres(destination.patient()),
                destination == null || destination.observation() == null ? null : Point.centimetres(destination.observation()),
                destination == null ? null : destination.cameraRangeM(), approach == null ? null : approach.proposal());
    }

    private static SosRouteGrid.Point storedPoint(JsonNode data) {
        JsonNode x = data.path("x_m"), y = data.path("y_m");
        return x.isNumber() && y.isNumber() && Double.isFinite(x.asDouble()) && Double.isFinite(y.asDouble())
                ? new SosRouteGrid.Point(x.asDouble(), y.asDouble()) : null;
    }
}
