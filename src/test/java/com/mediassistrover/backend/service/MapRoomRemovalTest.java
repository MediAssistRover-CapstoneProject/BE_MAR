package com.mediassistrover.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediassistrover.backend.dto.facility.MapPresentationContracts.*;
import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.SystemSettingDataType;
import com.mediassistrover.backend.exception.*;
import com.mediassistrover.backend.mapping.SavedMapImageService;
import com.mediassistrover.backend.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MapRoomRemovalTest {
    final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    final MapPresentationRepository presentations = mock(MapPresentationRepository.class);
    final FacilityMapRepository maps = mock(FacilityMapRepository.class);
    final EndpointRepository endpoints = mock(EndpointRepository.class);
    final RoomRepository rooms = mock(RoomRepository.class);
    final SavedMapImageService images = mock(SavedMapImageService.class);
    final EntityManager em = mock(EntityManager.class);
    final SosMonitoringRoomService sos = mock(SosMonitoringRoomService.class);
    final Clock clock = Clock.fixed(Instant.parse("2026-11-12T00:00:00Z"), ZoneOffset.UTC);
    final UUID mapId = UUID.randomUUID(), roomId = UUID.randomUUID(), endpointId = UUID.randomUUID();
    final String hash = "a".repeat(64);
    final Floor floor = Floor.builder().floorId(UUID.randomUUID()).active(true).build();
    final FacilityMap map = FacilityMap.builder().mapId(mapId).floor(floor).assetHash(hash).mapCode("M1").build();
    final Room room = Room.builder().roomId(roomId).floor(floor).roomCode("ROOM_05").roomName("Phòng 05")
            .roomType("EXAMINATION").active(true).publicVisible(true).build();
    final Endpoint point = Endpoint.builder().endpointId(endpointId).map(map).room(room).endpointCode("P05")
            .endpointName("Cửa phòng 05").active(true).accessible(true).publicVisible(true).build();
    final Feature feature = new Feature("room-5", FeatureType.ROOM, "Phòng 05", "#ffffff",
            List.of(new Point(1., 1.), new Point(3., 1.), new Point(3., 3.)), .05, .3, endpointId,
            roomId, "ROOM_05", "Phòng 05", "EXAMINATION", null, null, true);
    final Document empty = new Document(1, "#ffffff", List.of());
    final MapPresentation row = new MapPresentation();
    final MapPresentationService service = service(sos);

    MapPresentationService service(SosMonitoringRoomService routing) {
        return new MapPresentationService(presentations, maps, endpoints, rooms, images, json, em, clock, routing);
    }

    @BeforeEach void setup() {
        row.setMapId(mapId); row.setRevision(4); row.setSourceAssetHash(hash);
        row.setDraftJson(json.valueToTree(empty));
        row.setPublishedJson(json.valueToTree(new Document(1, "#ffffff", List.of(feature))));
        when(em.find(FacilityMap.class, mapId, LockModeType.PESSIMISTIC_WRITE)).thenReturn(map);
        when(presentations.findById(mapId)).thenReturn(Optional.of(row));
        when(images.metadata(mapId)).thenReturn(json.createObjectNode().put("asset_hash", hash)
                .put("width_m", 10).put("height_m", 10));
        when(rooms.lockById(roomId)).thenReturn(Optional.of(room));
        when(rooms.findById(roomId)).thenReturn(Optional.of(room));
        when(rooms.findByRoomCodeIgnoreCase("ROOM_05")).thenReturn(Optional.of(room));
        when(rooms.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(endpoints.findById(endpointId)).thenReturn(Optional.of(point));
        when(endpoints.findByRoomRoomIdOrderByEndpointCodeAsc(roomId)).thenReturn(List.of(point));
        when(endpoints.findAllById(any())).thenAnswer(invocation -> {
            Iterable<UUID> ids = invocation.getArgument(0);
            for (UUID id : ids) if (endpointId.equals(id)) return List.of(point);
            return List.of();
        });
    }

    @Test void savingDeletionOrUndoDoesNotRetireRoomOrEndpoint() {
        service.save(mapId, new Save(4, hash, empty));
        assertTrue(room.isActive()); assertTrue(point.isActive());
        assertNotNull(row.getPublishedJson());
        var restored = new Document(1, "#ffffff", List.of(feature));
        service.save(mapId, new Save(5, hash, restored));
        service.publish(mapId, new Change(6));
        assertTrue(room.isActive()); assertTrue(point.isActive());
        verify(rooms, never()).lockById(any()); verify(sos, never()).releaseIfConfigured(any(), any());
    }

    @Test void applyingRemovalOfLastRoomRetiresItAndAllItsLocalEndpointsWithoutDeletingHistory() {
        var extra = Endpoint.builder().endpointId(UUID.randomUUID()).map(map).room(room).active(true).accessible(true).build();
        when(endpoints.findByRoomRoomIdOrderByEndpointCodeAsc(roomId)).thenReturn(List.of(point, extra));
        var result = service.publish(mapId, new Change(4));
        assertFalse(room.isActive()); assertFalse(point.isActive()); assertFalse(extra.isActive());
        assertSame(room, point.getRoom()); assertTrue(point.isAccessible());
        assertTrue(result.published().features().isEmpty()); assertEquals(5, result.revision());
        verify(sos).releaseIfConfigured(room, null);
        verify(rooms, never()).delete(any()); verify(endpoints, never()).delete(any());
        verify(em).lock(floor, LockModeType.PESSIMISTIC_WRITE);
    }

    @Test void roomStillDrawnOnAnotherPublishedMapStaysActiveAndOnlyLocalPointsStop() {
        var other = new MapPresentation(); other.setMapId(UUID.randomUUID());
        other.setPublishedJson(json.valueToTree(new Document(1, "#ffffff", List.of(feature))));
        when(presentations.findOtherPublishedOnFloor(floor.getFloorId(), mapId)).thenReturn(List.of(other));
        service.publish(mapId, new Change(4));
        assertTrue(room.isActive()); assertFalse(point.isActive()); verifyNoInteractions(sos);
    }

    @Test void roomWithAnActiveEndpointOnAnotherMapStaysActiveEvenWithoutPublishedDrawing() {
        var otherMap = FacilityMap.builder().mapId(UUID.randomUUID()).floor(floor).build();
        var otherPoint = Endpoint.builder().endpointId(UUID.randomUUID()).map(otherMap).room(room).active(true).build();
        when(endpoints.findByRoomRoomIdOrderByEndpointCodeAsc(roomId)).thenReturn(List.of(point, otherPoint));
        service.publish(mapId, new Change(4));
        assertTrue(room.isActive()); assertTrue(otherPoint.isActive()); assertFalse(point.isActive());
        verifyNoInteractions(sos);
    }

    @Test void inactivePointsOnAnotherMapDoNotKeepAnUnusedRoomActive() {
        var otherMap = FacilityMap.builder().mapId(UUID.randomUUID()).floor(floor).build();
        var otherPoint = Endpoint.builder().endpointId(UUID.randomUUID()).map(otherMap).room(room).active(false).build();
        when(endpoints.findByRoomRoomIdOrderByEndpointCodeAsc(roomId)).thenReturn(List.of(point, otherPoint));
        service.publish(mapId, new Change(4));
        assertFalse(room.isActive()); assertFalse(otherPoint.isActive()); assertFalse(point.isActive());
    }

    @Test void deletingLegacyShapeResolvesItsRoomThroughTheEndpoint() {
        var legacy = new Feature("legacy", FeatureType.ROOM, "Phòng 05", "#ffffff", feature.points(), .05, .3, endpointId);
        row.setPublishedJson(json.valueToTree(new Document(1, "#ffffff", List.of(legacy))));
        service.publish(mapId, new Change(4));
        assertFalse(room.isActive()); assertFalse(point.isActive());
    }

    @Test void redrawingTheSameRoomWithADifferentShapeIdDoesNotRetireIt() {
        var redrawn = new Feature("new-shape", FeatureType.ROOM, feature.label(), feature.color(), feature.points(),
                .05, .3, endpointId, roomId, room.getRoomCode(), room.getRoomName(), room.getRoomType(), null, null, true);
        row.setDraftJson(json.valueToTree(new Document(1, "#ffffff", List.of(redrawn))));
        service.publish(mapId, new Change(4));
        assertTrue(room.isActive()); assertTrue(point.isActive()); verify(sos, never()).releaseIfConfigured(any(), any());
    }

    @Test void roomsNotPreviouslyPublishedHereAreNotRetiredJustBecauseTheyHaveNoShape() {
        row.setPublishedJson(null);
        row.setDraftJson(json.valueToTree(new Document(1, "#ffffff", List.of(), 10.)));
        service.publish(mapId, new Change(4));
        assertTrue(room.isActive()); assertTrue(point.isActive());
        verify(rooms, never()).lockById(any()); verifyNoInteractions(sos);
    }

    @Test void staleRevisionAndInvalidDraftLeaveRoomAvailabilityUnchanged() {
        assertThrows(ConflictException.class, () -> service.publish(mapId, new Change(3)));
        var invalid = new Feature("label", FeatureType.LABEL, "Ngoài map", "#ffffff", List.of(new Point(100., 0.)), .05, .3, null);
        row.setDraftJson(json.valueToTree(new Document(1, "#ffffff", List.of(invalid))));
        assertThrows(BusinessRuleException.class, () -> service.publish(mapId, new Change(4)));
        assertTrue(room.isActive()); assertTrue(point.isActive()); assertEquals(4, row.getRevision());
        verify(rooms, never()).lockById(any()); verifyNoInteractions(sos);
    }

    @Test void aRemainingLabelMustUnlinkTheRemovedRoomsEndpointBeforePublish() {
        var label = new Feature("label", FeatureType.LABEL, "Chỉ dẫn", "#ffffff", List.of(new Point(1., 1.)), .05, .3, endpointId);
        row.setDraftJson(json.valueToTree(new Document(1, "#ffffff", List.of(label))));
        assertThrows(BusinessRuleException.class, () -> service.publish(mapId, new Change(4)));
        assertTrue(room.isActive()); assertTrue(point.isActive()); assertEquals(4, row.getRevision());
        verifyNoInteractions(sos);
    }

    @Test void retiringSosRoomClearsTheSettingAndRestoresItsOriginalType() {
        var settings = mock(SystemSettingRepository.class);
        var setting = SystemSetting.builder().settingKey(SosMonitoringRoomService.SETTING_KEY)
                .settingValue(Map.of("value", roomId.toString())).dataType(SystemSettingDataType.STRING).active(true).build();
        when(settings.lockBySettingKey(SosMonitoringRoomService.SETTING_KEY)).thenReturn(Optional.of(setting));
        room.setRoomType("SOS"); room.setSosOriginalRoomName("Phòng 05"); room.setSosOriginalRoomType("EXAMINATION");
        point.setSosOriginalEndpointName("Điểm ban đầu");
        service(new SosMonitoringRoomService(settings, rooms, endpoints, clock)).publish(mapId, new Change(4));
        assertFalse(room.isActive()); assertFalse(point.isActive());
        assertEquals("", setting.getSettingValue().get("value"));
        assertEquals("EXAMINATION", room.getRoomType()); assertEquals("Điểm ban đầu", point.getEndpointName());
    }

    @Test void unpublishingTheDrawingDoesNotRetireRooms() {
        service.unpublish(mapId, new Change(4));
        assertTrue(room.isActive()); assertTrue(point.isActive()); verifyNoInteractions(sos);
    }
}
