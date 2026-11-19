package com.mediassistrover.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.exception.BusinessRuleException;
import com.mediassistrover.backend.mapping.*;
import com.mediassistrover.backend.repository.*;
import com.mediassistrover.backend.validation.StaffOperationalScopePolicy;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SosResponseRouteServiceTest {
    @Test void responseUsesCentimetresAndReportsMissingCoordinates() {
        var incidents = mock(EmergencyIncidentRepository.class);
        var scope = mock(StaffOperationalScopePolicy.class);
        var settings = mock(RuntimeSettingService.class);
        var rooms = mock(RoomRepository.class);
        var endpoints = mock(EndpointRepository.class);
        var maps = mock(MapBundleService.class);
        var service = new SosResponseRouteService(incidents,scope,settings,rooms,endpoints,maps);
        var floor = Floor.builder().floorId(UUID.randomUUID()).active(true).build();
        var map = FacilityMap.builder().mapId(UUID.randomUUID()).floor(floor).status(MapStatus.ACTIVE).assetHash("test-hash").build();
        var room = Room.builder().roomId(UUID.randomUUID()).floor(floor).active(true).build();
        room.getEndpoints().add(Endpoint.builder().map(map).room(room).active(true).build());
        var incident = EmergencyIncident.builder().incidentId(UUID.randomUUID()).incidentType("SOS")
                .status(EmergencyIncidentStatus.IN_PROGRESS).map(map)
                .xM(new BigDecimal(".25")).yM(new BigDecimal(".05")).build();
        when(incidents.findById(incident.getIncidentId())).thenReturn(Optional.of(incident));
        when(settings.getString(SosResponderRouting.MONITORING_ROOM_SETTING,"")).thenReturn(room.getRoomId().toString());
        when(rooms.findById(room.getRoomId())).thenReturn(Optional.of(room));
        when(endpoints.findByRoomRoomIdOrderByEndpointCodeAsc(room.getRoomId())).thenReturn(List.of(
                Endpoint.builder().map(map).active(true).accessible(true).xM(new BigDecimal(".05")).yM(new BigDecimal(".05")).build()));
        when(maps.responseRouteGrid(map.getMapId())).thenReturn(new SosRouteGrid(3,1,.1,0,0,0,new boolean[]{true,true,true}));
        var response = service.route(incident.getIncidentId(),null);
        assertEquals("FOUND",response.status());
        assertEquals("cm",response.unit());
        assertEquals(25,response.destination().xCm(),1e-9);
        assertEquals(20,response.distanceCm(),1e-9);
        verify(scope).requireIncidentAccess(incident,null);
        incident.setXM(null);
        assertEquals("SOS_POSITION_MISSING",service.route(incident.getIncidentId(),null).reason());

        var robot = Robot.builder().robotId(UUID.randomUUID()).build();
        var observedAt = Instant.parse("2026-11-12T03:00:00Z");
        var evidence = new ObjectMapper().createObjectNode().put("source", "PATROL_VISION_AI");
        var viewpoint = evidence.putObject("robot_viewpoint").put("x_m", new BigDecimal(".25"))
                .put("y_m", new BigDecimal(".05")).put("captured_at", observedAt.toString())
                .put("map_asset_hash", map.getAssetHash());
        incident.setIncidentType(EmergencyIncident.SUSPECTED_FALL_TYPE);
        incident.setRobot(robot);
        incident.setXM(new BigDecimal(".25"));
        incident.setDetectionEvent(DetectionEvent.builder().robot(robot).map(map).detectedAt(observedAt)
                .eventType(DetectionEventType.SUSPECTED_FALL).locationBasis(DetectionLocationBasis.ROBOT)
                .eventPayloadJson(evidence).build());
        incident.setStatus(EmergencyIncidentStatus.OPEN);
        assertThrows(BusinessRuleException.class, () -> service.route(incident.getIncidentId(), null));
        incident.setStatus(EmergencyIncidentStatus.IN_PROGRESS);
        var fallRoute = service.route(incident.getIncidentId(), null);
        assertEquals("FOUND", fallRoute.status());
        assertEquals("ROBOT_VIEWPOINT", fallRoute.destinationBasis());
        assertEquals(observedAt, fallRoute.destinationCapturedAt());

        // Continuous observations can carry a verified robot viewpoint without patient coordinates.
        evidence.put("source", "CONTINUOUS_VISION_AI");
        incident.getDetectionEvent().setLocationBasis(DetectionLocationBasis.UNKNOWN);
        incident.setXM(null);
        incident.setYM(null);
        var observedRoute = service.route(incident.getIncidentId(), null);
        assertEquals("FOUND", observedRoute.status());
        assertEquals("ROBOT_VIEWPOINT", observedRoute.destinationBasis());
        assertEquals(observedRoute.robotObservationLocation(), observedRoute.destination());
        assertNull(observedRoute.patientLocation());

        viewpoint.put("captured_at", observedAt.minusSeconds(5).toString());
        assertEquals("FALL_VIEWPOINT_UNAVAILABLE", service.route(incident.getIncidentId(), null).reason());
        viewpoint.put("captured_at", observedAt.toString()).put("map_asset_hash", "changed");
        assertNull(service.route(incident.getIncidentId(), null).destination());
        viewpoint.remove("map_asset_hash");
        assertNull(service.route(incident.getIncidentId(), null).destination());
    }

    @Test void rgbdSeparatesPatientFromViewpointAndOnlyProposesVisibleStandOff() {
        var incidents = mock(EmergencyIncidentRepository.class);
        var settings = mock(RuntimeSettingService.class);
        var rooms = mock(RoomRepository.class);
        var endpoints = mock(EndpointRepository.class);
        var maps = mock(MapBundleService.class);
        var service = new SosResponseRouteService(incidents, mock(StaffOperationalScopePolicy.class),
                settings, rooms, endpoints, maps);
        var floor = Floor.builder().floorId(UUID.randomUUID()).active(true).build();
        var map = FacilityMap.builder().mapId(UUID.randomUUID()).floor(floor).status(MapStatus.ACTIVE).assetHash("depth-hash").build();
        var room = Room.builder().roomId(UUID.randomUUID()).floor(floor).active(true).build();
        room.getEndpoints().add(Endpoint.builder().map(map).room(room).active(true).build());
        var robot = Robot.builder().robotId(UUID.randomUUID()).build();
        var observedAt = Instant.parse("2026-11-15T15:00:00Z");
        var evidence = new ObjectMapper().createObjectNode().put("source", "PATROL_VISION_AI");
        var patient = evidence.putObject("patient_location").put("basis", "PATIENT_RGBD")
                .put("x_m", 2.15).put("y_m", .10).put("range_m", 1.9)
                .put("captured_at", observedAt.toString()).put("map_id", map.getMapId().toString())
                .put("map_asset_hash", map.getAssetHash());
        evidence.putObject("robot_viewpoint").put("x_m", .15).put("y_m", .10)
                .put("captured_at", observedAt.toString()).put("map_asset_hash", map.getAssetHash());
        var detection = DetectionEvent.builder().robot(robot).map(map).detectedAt(observedAt)
                .eventType(DetectionEventType.SUSPECTED_FALL).locationBasis(DetectionLocationBasis.OBJECT)
                .eventPayloadJson(evidence).build();
        var incident = EmergencyIncident.builder().incidentId(UUID.randomUUID()).incidentType(EmergencyIncident.SUSPECTED_FALL_TYPE)
                .status(EmergencyIncidentStatus.IN_PROGRESS).robot(robot).map(map).detectionEvent(detection)
                .xM(BigDecimal.valueOf(2.15)).yM(BigDecimal.valueOf(.10)).build();
        when(incidents.findById(incident.getIncidentId())).thenReturn(Optional.of(incident));
        when(settings.getString(SosResponderRouting.MONITORING_ROOM_SETTING, "")).thenReturn(room.getRoomId().toString());
        when(rooms.findById(room.getRoomId())).thenReturn(Optional.of(room));
        var origin = Endpoint.builder().map(map).active(true).accessible(true)
                .xM(BigDecimal.valueOf(.10)).yM(BigDecimal.valueOf(.10)).build();
        var approach = Endpoint.builder().endpointId(UUID.randomUUID()).endpointCode("APPROACH_A").map(map)
                .active(true).accessible(true).xM(BigDecimal.valueOf(.65)).yM(BigDecimal.valueOf(.10)).build();
        var tooClose = Endpoint.builder().endpointId(UUID.randomUUID()).map(map).active(true).accessible(true)
                .xM(BigDecimal.valueOf(1.9)).yM(BigDecimal.valueOf(.10)).build();
        when(endpoints.findByRoomRoomIdOrderByEndpointCodeAsc(room.getRoomId())).thenReturn(List.of(origin));
        when(endpoints.findByMapMapIdOrderByEndpointCodeAsc(map.getMapId())).thenReturn(List.of(tooClose, approach));
        var open = new boolean[12];
        Arrays.fill(open, true);
        when(maps.responseRouteGrid(map.getMapId())).thenReturn(new SosRouteGrid(12, 1, .25, 0, 0, 0, open));

        incident.setStatus(EmergencyIncidentStatus.OPEN);
        var preview = service.preview(incident.getIncidentId(), null);
        assertEquals("PREVIEW", preview.status());
        assertEquals("PATIENT_RGBD", preview.destinationBasis());
        assertEquals(215, preview.patientLocation().xCm(), 1e-9);
        assertEquals(preview.patientLocation(), preview.destination());
        assertTrue(preview.points().isEmpty());
        assertNull(preview.origin());
        assertNull(preview.robotApproach());
        verifyNoInteractions(maps);
        assertThrows(BusinessRuleException.class, () -> service.route(incident.getIncidentId(), null));
        incident.setStatus(EmergencyIncidentStatus.IN_PROGRESS);

        var direct = service.route(incident.getIncidentId(), null);
        assertEquals("PATIENT_RGBD", direct.destinationBasis());
        assertEquals(215, direct.patientLocation().xCm(), 1e-9);
        assertEquals(15, direct.robotObservationLocation().xCm(), 1e-9);
        assertEquals(1.9, direct.cameraRangeM(), 1e-9);
        assertEquals(approach.getEndpointId(), direct.robotApproach().endpointId());
        assertEquals(direct.patientLocation(), direct.destination());
        evidence.put("source", "CONTINUOUS_VISION_AI");
        assertEquals("PATIENT_RGBD", service.route(incident.getIncidentId(), null).destinationBasis());

        open[8] = false;
        when(endpoints.findByMapMapIdOrderByEndpointCodeAsc(map.getMapId())).thenReturn(List.of());
        var nearby = service.route(incident.getIncidentId(), null);
        assertEquals("FOUND", nearby.status());
        assertEquals("PATIENT_RGBD_NEARBY_WALKABLE", nearby.destinationBasis());
        assertEquals(187.5, nearby.destination().xCm(), 1e-9);
        assertEquals(nearby.destination(), nearby.points().getLast());
        assertEquals(215, nearby.patientLocation().xCm(), 1e-9);
        assertNull(nearby.robotApproach());
        when(endpoints.findByRoomRoomIdOrderByEndpointCodeAsc(room.getRoomId())).thenReturn(List.of(
                Endpoint.builder().map(map).active(true).accessible(true)
                        .xM(BigDecimal.valueOf(2.9)).yM(BigDecimal.valueOf(.10)).build()));
        var blocked = service.route(incident.getIncidentId(), null);
        assertEquals("UNAVAILABLE", blocked.status());
        assertTrue(blocked.points().isEmpty());
        assertNull(blocked.robotApproach());
        assertEquals(215, blocked.patientLocation().xCm(), 1e-9);

        patient.put("map_asset_hash", "different-asset");
        assertNull(service.route(incident.getIncidentId(), null).patientLocation());
    }
}
