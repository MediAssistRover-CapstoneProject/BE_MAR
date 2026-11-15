package com.mediassistrover.backend.service;

import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.EmergencyIncidentStatus;
import com.mediassistrover.backend.exception.*;
import com.mediassistrover.backend.mapping.*;
import com.mediassistrover.backend.repository.*;
import com.mediassistrover.backend.security.AccountPrincipal;
import com.mediassistrover.backend.validation.StaffOperationalScopePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SosResponseRouteService {
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
                           String unit, Point origin, Point destination, List<Point> points, Double distanceCm) { }

    public Response route(UUID id, AccountPrincipal actor) {
        EmergencyIncident incident = incidents.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found"));
        scope.requireIncidentAccess(incident, actor);
        if (incident.getArchivedAt() != null) throw new ResourceNotFoundException("Incident not found");
        if (!"SOS".equals(incident.getIncidentType()) || incident.getStatus() != EmergencyIncidentStatus.IN_PROGRESS)
            throw new BusinessRuleException("An IN_PROGRESS SOS incident is required");
        FacilityMap map = incident.getMap();
        if (map == null) return unavailable(incident, "MAP_MISSING");
        if (incident.getXM() == null || incident.getYM() == null)
            return unavailable(incident, "SOS_POSITION_MISSING");
        String roomSetting = settings.getString(SosResponderRouting.MONITORING_ROOM_SETTING, "").trim();
        if (roomSetting.isEmpty()) return unavailable(incident, "MONITORING_ROOM_MISSING");
        UUID roomId;
        try {
            roomId = UUID.fromString(roomSetting);
        } catch (IllegalArgumentException error) {
            return unavailable(incident, "MONITORING_ROOM_INVALID");
        }
        Room room = rooms.findById(roomId).orElse(null);
        if (room == null || !room.isActive() || !room.getFloor().isActive())
            return unavailable(incident, "MONITORING_ROOM_UNAVAILABLE");
        if (!room.getFloor().getFloorId().equals(map.getFloor().getFloorId()))
            return unavailable(incident, "DIFFERENT_FLOOR");
        List<Endpoint> candidates = endpoints.findByRoomRoomIdOrderByEndpointCodeAsc(roomId).stream()
                .filter(endpoint -> endpoint.isActive() && endpoint.isAccessible()
                        && endpoint.getMap().getMapId().equals(map.getMapId())).toList();
        if (candidates.isEmpty()) return unavailable(incident, "ORIGIN_ENDPOINT_MISSING_ON_MAP");
        SosRouteGrid grid;
        try {
            grid = maps.responseRouteGrid(map.getMapId());
        } catch (BusinessRuleException | ResourceNotFoundException error) {
            return unavailable(incident, "MAP_DATA_UNAVAILABLE");
        }
        var destination = new SosRouteGrid.Point(incident.getXM().doubleValue(), incident.getYM().doubleValue());
        if (grid.cell(destination) < 0) return unavailable(incident, "SOS_POSITION_OUTSIDE_MAP");
        if (!grid.walkable(destination)) return unavailable(incident, "SOS_POSITION_NOT_FREE");
        List<SosRouteGrid.Point> origins = candidates.stream()
                .filter(endpoint -> endpoint.getXM() != null && endpoint.getYM() != null)
                .map(endpoint -> new SosRouteGrid.Point(endpoint.getXM().doubleValue(), endpoint.getYM().doubleValue()))
                .filter(grid::walkable).toList();
        if (origins.isEmpty()) return unavailable(incident, "ORIGIN_POSITION_NOT_FREE");
        var route = grid.shortest(origins, destination).orElse(null);
        if (route == null) return unavailable(incident, "NO_PATH");
        List<Point> points = route.points().stream().map(Point::centimetres).toList();
        return new Response("FOUND", null, map.getMapId(), map.getAssetHash(), "cm",
                points.getFirst(), Point.centimetres(destination), points, route.distance() * 100);
    }

    private Response unavailable(EmergencyIncident incident, String reason) {
        FacilityMap map = incident.getMap();
        Point destination = incident.getXM() == null || incident.getYM() == null ? null
                : new Point(incident.getXM().doubleValue() * 100, incident.getYM().doubleValue() * 100);
        return new Response("UNAVAILABLE", reason, map == null ? null : map.getMapId(),
                map == null ? null : map.getAssetHash(), "cm", null, destination, List.of(), null);
    }
}
