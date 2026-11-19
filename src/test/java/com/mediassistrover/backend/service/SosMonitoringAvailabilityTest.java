package com.mediassistrover.backend.service;

import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.MapStatus;
import com.mediassistrover.backend.exception.BusinessRuleException;
import com.mediassistrover.backend.repository.*;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SosMonitoringAvailabilityTest {
    private final SystemSettingRepository settings = mock(SystemSettingRepository.class);
    private final RoomRepository rooms = mock(RoomRepository.class);
    private final EndpointRepository endpoints = mock(EndpointRepository.class);
    private final SosMonitoringRoomService service = new SosMonitoringRoomService(settings, rooms, endpoints, Clock.systemUTC());
    private final Floor floor = Floor.builder().floorId(UUID.randomUUID()).active(true).build();
    private final FacilityMap map = FacilityMap.builder().mapId(UUID.randomUUID()).floor(floor).status(MapStatus.INACTIVE).build();
    private final Room candidate = Room.builder().roomId(UUID.randomUUID()).floor(floor).roomName("SOS same name")
            .roomType("SOS").active(true).build();
    private final Endpoint point = Endpoint.builder().room(candidate).map(map).active(true).build();

    SosMonitoringAvailabilityTest() {
        when(endpoints.findByRoomRoomIdOrderByEndpointCodeAsc(candidate.getRoomId())).thenReturn(List.of(point));
    }

    @Test void inactiveMapCannotTakeOverMonitoringRoomEvenWhenRoomAndEndpointAreEnabled() {
        assertThrows(BusinessRuleException.class, () -> service.reconcile(candidate, "SOS same name", "GENERAL", null));
        verifyNoInteractions(settings, rooms);
    }

    @Test void onlyEndpointWithTheCorrectRoomIdAndActiveMapCanConfigureSos() {
        map.setStatus(MapStatus.ACTIVE);
        var setting = SystemSetting.builder().active(true).settingValue(Map.of("value", "")).build();
        when(settings.lockBySettingKey(SosMonitoringRoomService.SETTING_KEY)).thenReturn(Optional.of(setting));
        // Collection can be empty immediately after a map publication links its owning endpoint.
        assertTrue(candidate.getEndpoints().isEmpty());
        service.reconcile(candidate, candidate.getRoomName(), "GENERAL", null);
        assertEquals(candidate.getRoomId().toString(), setting.getSettingValue().get("value"));
        assertEquals("GENERAL", candidate.getSosOriginalRoomType());
        verify(endpoints, atLeastOnce()).findByRoomRoomIdOrderByEndpointCodeAsc(candidate.getRoomId());
    }

    @Test void disabledPointOrWrongFloorCannotConfigureSos() {
        map.setStatus(MapStatus.ACTIVE);
        point.setActive(false);
        assertThrows(BusinessRuleException.class, () -> service.reconcile(candidate, candidate.getRoomName(), "GENERAL", null));
        point.setActive(true);
        map.setFloor(Floor.builder().floorId(UUID.randomUUID()).active(true).build());
        assertThrows(BusinessRuleException.class, () -> service.reconcile(candidate, candidate.getRoomName(), "GENERAL", null));
        verifyNoInteractions(settings, rooms);
    }
}
