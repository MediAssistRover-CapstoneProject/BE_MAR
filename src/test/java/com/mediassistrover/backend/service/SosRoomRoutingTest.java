package com.mediassistrover.backend.service;

import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.repository.RoomRepository;
import com.mediassistrover.backend.repository.ShiftRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.mediassistrover.backend.entity.EmergencyIncident.SosRoutingStatus.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SosRoomRoutingTest {
    private final RuntimeSettingService settings = mock(RuntimeSettingService.class);
    private final RoomRepository rooms = mock(RoomRepository.class);
    private final ShiftRepository shifts = mock(ShiftRepository.class);
    private final SosResponderRouting routing = new SosResponderRouting(settings, rooms, shifts);
    private final Instant now = Instant.parse("2026-11-12T04:00:00Z");
    private final UUID roomId = UUID.randomUUID();
    private final Account responder = Account.builder().accountId(UUID.randomUUID()).build();
    private final Room room = Room.builder().roomId(roomId).roomType("GENERAL").active(true)
            .floor(Floor.builder().active(true).build()).build();

    @BeforeEach void configureRoom() {
        makeOperational(room);
        when(settings.getString(SosResponderRouting.MONITORING_ROOM_SETTING, "")).thenReturn(roomId.toString());
        when(rooms.findById(roomId)).thenReturn(Optional.of(room));
        when(shifts.findOnDutySosResponders(eq(roomId), eq(now), any(Pageable.class))).thenReturn(List.of(responder));
    }

    @Test void changingRoomTypeNameVisibilityAndDepartmentDoesNotChangeSosRecipient() {
        for (String type : List.of("GENERAL", "WARD", "SOS", "PHARMACY")) {
            room.setRoomType(type);
            room.setRoomName("Renamed " + type);
            room.setDepartmentCode("EMERGENCY");
            room.setPublicVisible(false);
            var incident = new EmergencyIncident();
            routing.assign(incident, now);
            assertEquals(ASSIGNED, incident.getSosRoutingStatus(), type);
            assertSame(responder, incident.getSosResponder(), type);
        }
    }

    @Test void sosTypeAloneDoesNotConfigureRouting() {
        room.setRoomType("SOS");
        when(settings.getString(SosResponderRouting.MONITORING_ROOM_SETTING, "")).thenReturn("");
        var incident = new EmergencyIncident();
        routing.assign(incident, now);
        assertEquals(NO_MONITORING_ROOM, incident.getSosRoutingStatus());
        assertNull(incident.getSosResponder());
        verifyNoInteractions(rooms, shifts);
    }

    @Test void deactivatingConfiguredRoomMakesNewSosUnavailable() {
        room.setActive(false);
        var incident = new EmergencyIncident();
        routing.assign(incident, now);
        assertEquals(ROOM_UNAVAILABLE, incident.getSosRoutingStatus());
        assertNull(incident.getSosResponder());
        verifyNoInteractions(shifts);
    }

    @Test void deactivatingConfiguredFloorMakesNewSosUnavailable() {
        room.getFloor().setActive(false);
        var incident = new EmergencyIncident();
        routing.assign(incident, now);
        assertEquals(ROOM_UNAVAILABLE, incident.getSosRoutingStatus());
        verifyNoInteractions(shifts);
    }

    @Test void invalidConfiguredIdIsDetectedOnlyWhenRouting() {
        when(settings.getString(SosResponderRouting.MONITORING_ROOM_SETTING, "")).thenReturn("not-a-room-id");
        var incident = new EmergencyIncident();
        routing.assign(incident, now);
        assertEquals(ROOM_UNAVAILABLE, incident.getSosRoutingStatus());
        verifyNoInteractions(rooms, shifts);
    }

    @Test void switchingConfiguredRoomAffectsNewIncidentsAndKeepsPreviousRecipient() {
        var original = new EmergencyIncident();
        routing.assign(original, now);
        var nextRoomId = UUID.randomUUID();
        var nextRoom = Room.builder().roomId(nextRoomId).active(true).floor(room.getFloor()).build();
        makeOperational(nextRoom);
        var nextResponder = Account.builder().accountId(UUID.randomUUID()).build();
        when(settings.getString(SosResponderRouting.MONITORING_ROOM_SETTING, "")).thenReturn(nextRoomId.toString());
        when(rooms.findById(nextRoomId)).thenReturn(Optional.of(nextRoom));
        when(shifts.findOnDutySosResponders(eq(nextRoomId), eq(now), any(Pageable.class))).thenReturn(List.of(nextResponder));
        var next = new EmergencyIncident();
        routing.assign(next, now);
        assertSame(nextResponder, next.getSosResponder());
        assertSame(responder, original.getSosResponder());
        assertEquals(ASSIGNED, original.getSosRoutingStatus());
    }

    @Test void switchingAwayFromRoomMapReportsMissingPublishedEndpointBeforeCheckingRoster() {
        room.getEndpoints().getFirst().getMap().setStatus(com.mediassistrover.backend.enums.MapStatus.ARCHIVED);
        var assignment = routing.resolve(null, now, null);
        assertEquals(ROOM_UNAVAILABLE, assignment.status());
        assertEquals("NO_ENDPOINT_ON_ACTIVE_MAP", assignment.reason());
        assertEquals(roomId, assignment.roomId());
        verifyNoInteractions(shifts);
    }

    @Test void diagnosticDistinguishesMissingCheckedInStaffAndConflictingRoster() {
        when(shifts.findOnDutySosResponders(eq(roomId), eq(now), any(Pageable.class))).thenReturn(List.of());
        assertEquals("NO_CHECKED_IN_STAFF", routing.resolve(null, now, null).reason());
        when(shifts.findOnDutySosResponders(eq(roomId), eq(now), any(Pageable.class)))
                .thenReturn(List.of(responder, Account.builder().accountId(UUID.randomUUID()).build()));
        var assignment = routing.resolve(null, now, null);
        assertEquals(AMBIGUOUS_SHIFT, assignment.status());
        assertEquals("MULTIPLE_CHECKED_IN_STAFF", assignment.reason());
        assertNull(assignment.responder());
    }

    @Test void aRoomOnAnotherActiveMapCannotReceiveThisMapsSos() {
        var hospital = HospitalInformation.builder().hospitalInformationId(UUID.randomUUID()).build();
        room.getFloor().setHospitalInformation(hospital);
        var other = FacilityMap.builder().mapId(UUID.randomUUID()).floor(room.getFloor())
                .status(com.mediassistrover.backend.enums.MapStatus.ACTIVE).build();
        var assignment = routing.resolve(other, now, null);
        assertEquals(ROOM_UNAVAILABLE, assignment.status());
        assertEquals("NO_ENDPOINT_ON_INCIDENT_MAP", assignment.reason());
        verifyNoInteractions(shifts);
        assertEquals(ASSIGNED, routing.resolve(room.getEndpoints().getFirst().getMap(), now, null).status());
    }
    private void makeOperational(Room target) {
        if(target.getFloor().getFloorId()==null)target.getFloor().setFloorId(UUID.randomUUID());
        var map=FacilityMap.builder().mapId(UUID.randomUUID()).floor(target.getFloor())
                .status(com.mediassistrover.backend.enums.MapStatus.ACTIVE).build();
        target.getEndpoints().add(Endpoint.builder().map(map).room(target).active(true).build());
    }
}
