package com.mediassistrover.backend.service;

import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.mapping.*;
import com.mediassistrover.backend.repository.*;
import com.mediassistrover.backend.validation.StaffOperationalScopePolicy;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
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
        var map = FacilityMap.builder().mapId(UUID.randomUUID()).floor(floor).assetHash("test-hash").build();
        var room = Room.builder().roomId(UUID.randomUUID()).floor(floor).active(true).build();
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
    }
}
