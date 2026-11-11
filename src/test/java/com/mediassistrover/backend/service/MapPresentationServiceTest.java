package com.mediassistrover.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediassistrover.backend.dto.facility.MapPresentationContracts.*;
import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.exception.*;
import com.mediassistrover.backend.mapping.SavedMapImageService;
import com.mediassistrover.backend.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.validation.Validation;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MapPresentationServiceTest {
    final UUID id=UUID.randomUUID();
    final String hash="a".repeat(64);
    final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
    final MapPresentationRepository rows=mock(MapPresentationRepository.class);
    final FacilityMapRepository maps=mock(FacilityMapRepository.class);
    final EndpointRepository endpoints=mock(EndpointRepository.class);
    final RoomRepository rooms=mock(RoomRepository.class);
    final SavedMapImageService images=mock(SavedMapImageService.class);
    final EntityManager em=mock(EntityManager.class);
    final SosMonitoringRoomService sosRooms=mock(SosMonitoringRoomService.class);
    final Clock clock=Clock.fixed(Instant.parse("2026-11-06T00:00:00Z"),ZoneOffset.UTC);
    final MapPresentationService service=new MapPresentationService(rows,maps,endpoints,rooms,images,json,em,clock,sosRooms);
    FacilityMap map;
    Source source;
    Feature room;
    Document draft;

    @BeforeEach void setup() {
        map=FacilityMap.builder().mapId(id).mapCode("MAP_A").floor(Floor.builder().floorId(UUID.randomUUID()).build())
            .assetHash(hash).widthM(BigDecimal.TEN).heightM(BigDecimal.TEN).build();
        source=new Source(id,hash,10,10,.05,-2,-3,.5,"map",200,200);
        room=new Feature("room-1",FeatureType.ROOM,"Phòng A","#dbeafe",List.of(new Point(1.,1.),new Point(3.,1.),new Point(3.,3.),new Point(1.,3.)),.05,.3,null);
        draft=new Document(1,"#ffffff",List.of(room));
        when(maps.findById(id)).thenReturn(Optional.of(map));
        when(em.find(FacilityMap.class,id,LockModeType.PESSIMISTIC_WRITE)).thenReturn(map);
        when(rows.findById(id)).thenReturn(Optional.empty());
        when(endpoints.findAllById(any())).thenReturn(List.of());
        when(images.metadata(id)).thenReturn(json.createObjectNode().put("asset_hash",hash).put("width_m",10).put("height_m",10)
            .put("resolution_m_per_pixel",.05).put("origin_x_m",-2).put("origin_y_m",-3).put("origin_yaw_rad",.5)
            .put("frame_id","map").put("width_px",200).put("height_px",200));
    }
    MapPresentation row(long revision) {
        var row=new MapPresentation(); row.setMapId(id); row.setRevision(revision); row.setSourceAssetHash(hash);
        row.setSourceJson(json.valueToTree(source)); row.setDraftJson(json.valueToTree(draft));
        when(rows.findById(id)).thenReturn(Optional.of(row)); return row;
    }
    @Test void newEditorHasNoPublishedDocument() {
        var result=service.editor(id); assertEquals(0,result.revision()); assertNull(result.published()); assertEquals(.5,result.source().originYawRad());
    }
    @Test void saveDoesNotPublishOrChangeNavigation() {
        var result=service.save(id,new Save(0,hash,draft));
        assertEquals(1,result.revision()); assertNull(result.published()); assertEquals(hash,map.getAssetHash());
        verify(maps,never()).save(any());
    }
    @Test void rotationSurvivesSaveReadAndPublishWithoutChangingNavigationOrGeometry() {
        map.setOriginXM(BigDecimal.valueOf(-2)); map.setOriginYM(BigDecimal.valueOf(-3));
        map.setOriginYawRad(BigDecimal.valueOf(.5)); map.setResolutionMPerPixel(BigDecimal.valueOf(.05));
        map.setMapFileUri("s3://maps/original.yaml");
        var row=row(1);
        var rotated=new Document(1,"#ffffff",List.of(room),-19.5);
        var result=service.save(id,new Save(1,hash,rotated));
        assertEquals(rotated,result.draft()); assertEquals(-19.5,row.getDraftJson().get("rotationDegrees").asDouble());
        assertEquals(rotated,service.editor(id).draft());
        result=service.publish(id,new Change(2));
        assertEquals(-19.5,result.published().rotationDegrees());
        assertEquals(rotated,service.published(id).document());
        assertEquals(room.points(),result.published().features().getFirst().points());
        assertEquals(source,result.source());
        assertEquals(hash,map.getAssetHash()); assertEquals(BigDecimal.TEN,map.getWidthM()); assertEquals(BigDecimal.TEN,map.getHeightM());
        assertEquals(BigDecimal.valueOf(-2),map.getOriginXM()); assertEquals(BigDecimal.valueOf(-3),map.getOriginYM());
        assertEquals(BigDecimal.valueOf(.5),map.getOriginYawRad()); assertEquals(BigDecimal.valueOf(.05),map.getResolutionMPerPixel());
        assertEquals("s3://maps/original.yaml",map.getMapFileUri());
        verify(maps,never()).save(any()); verify(endpoints,never()).save(any());
    }
    @Test void laterRotationChangeDoesNotChangePublishedOrientation() {
        draft=new Document(1,"#ffffff",List.of(room),-19.5);
        row(1); service.publish(id,new Change(1));
        var changed=new Document(1,"#ffffff",List.of(room),45.);
        var result=service.save(id,new Save(2,hash,changed));
        assertEquals(45.,result.draft().rotationDegrees());
        assertEquals(-19.5,result.published().rotationDegrees());
        assertEquals(-19.5,service.published(id).document().rotationDegrees());
    }
    @Test void rotationOnlyPresentationCanBePublishedBeforeDrawing() {
        draft=new Document(1,"#ffffff",List.of(),-19.5);
        row(1);
        var result=service.publish(id,new Change(1));
        assertEquals(2,result.revision()); assertEquals(draft,result.published());
        assertEquals(-19.5,service.published(id).document().rotationDegrees());
        assertTrue(result.published().features().isEmpty());
        assertEquals(source,result.source()); verify(maps,never()).save(any());
    }
    @Test void emptyDefaultPresentationStillCannotBePublished() {
        draft=new Document(1,"#ffffff",List.of());
        var row=row(1);
        assertThrows(BusinessRuleException.class,()->service.publish(id,new Change(1)));
        assertEquals(1,row.getRevision()); assertNull(row.getPublishedJson());
    }
    @Test void publishedRotationOnlyMapCanBeResetToZeroAndPublishedAgain() {
        draft=new Document(1,"#ffffff",List.of(),-19.5);
        row(1); service.publish(id,new Change(1));
        var reset=new Document(1,"#ffffff",List.of(),0.);
        service.save(id,new Save(2,hash,reset));
        var result=service.publish(id,new Change(3));
        assertEquals(4,result.revision()); assertEquals(reset,result.published());
        assertEquals(reset,service.published(id).document());
        assertEquals(source,result.source()); verify(maps,never()).save(any());
    }
    @Test void missingOrNullLegacyRotationDefaultsToZero() throws Exception {
        var oldJson=json.valueToTree(draft);
        ((com.fasterxml.jackson.databind.node.ObjectNode)oldJson).remove("rotationDegrees");
        assertEquals(0.,json.treeToValue(oldJson,Document.class).rotationDegrees());
        var row=row(2); row.setDraftJson(oldJson); row.setPublishedJson(oldJson.deepCopy()); row.setPublishedRevision(2L);
        assertEquals(0.,service.editor(id).draft().rotationDegrees());
        assertEquals(0.,service.published(id).document().rotationDegrees());
        ((com.fasterxml.jackson.databind.node.ObjectNode)oldJson).putNull("rotationDegrees");
        assertEquals(0.,json.treeToValue(oldJson,Document.class).rotationDegrees());
        assertEquals(0.,new Document(1,"#ffffff",List.of(room)).rotationDegrees());
    }
    @Test void nonFiniteAndOutOfRangeRotationCannotBeSavedEvenWithoutBeanValidation() {
        for(double angle:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,-180.01,180.01}) {
            var invalid=new Document(1,"#ffffff",List.of(room),angle);
            assertThrows(BusinessRuleException.class,()->service.save(id,new Save(0,hash,invalid)));
        }
        verify(rows,never()).save(any());
        assertDoesNotThrow(()->service.validate(new Document(1,"#ffffff",List.of(room),-180.),source));
        assertDoesNotThrow(()->service.validate(new Document(1,"#ffffff",List.of(room),180.),source));
    }
    @Test void publishedSnapshotSurvivesLaterDraftSave() {
        var row=row(1); var result=service.publish(id,new Change(1));
        assertEquals(draft,result.published()); assertEquals(2,result.revision());
        var changed=new Document(1,"#f1f5f9",List.of(room));
        result=service.save(id,new Save(2,hash,changed));
        assertEquals(3,result.revision()); assertEquals(draft,result.published()); assertEquals(changed,result.draft());
        assertEquals(draft,service.published(id).document()); assertEquals(2,service.published(id).revision());
        assertNotSame(row.getPublishedJson(),row.getDraftJson());
    }
    @Test void concurrentWriteIsRejected() {
        row(5); assertThrows(ConflictException.class,()->service.save(id,new Save(4,hash,draft)));
        assertThrows(ConflictException.class,()->service.publish(id,new Change(4)));
        verify(rows,never()).save(any());
    }
    @Test void wrongSourceHashIsRejected() {
        assertThrows(ConflictException.class,()->service.save(id,new Save(0,"b".repeat(64),draft)));
    }
    @Test void stalePublishedGeometryIsNeverServed() {
        var row=row(3); row.setPublishedJson(json.valueToTree(draft)); map.setAssetHash("b".repeat(64));
        assertThrows(ConflictException.class,()->service.published(id));
    }
    @Test void unpublishKeepsDraft() {
        var row=row(3); row.setPublishedJson(json.valueToTree(draft));
        var result=service.unpublish(id,new Change(3));
        assertEquals(draft,result.draft()); assertNull(result.published()); assertEquals(4,result.revision());
        assertThrows(ResourceNotFoundException.class,()->service.published(id));
    }
    @Test void geometryCannotLeaveMapOrContainDuplicateIds() {
        assertThrows(BusinessRuleException.class,()->service.validate(new Document(1,"#ffffff",List.of(room,room)),source));
        var wall=new Feature("wall",FeatureType.WALL,"Tường","#334155",List.of(new Point(1.,1.),new Point(11.,1.)),.1,.3,null);
        assertThrows(BusinessRuleException.class,()->service.validate(new Document(1,"#ffffff",List.of(wall)),source));
        var invalid=new Feature("wall",FeatureType.WALL,"Tường","#334155",List.of(new Point(Double.NaN,1.),new Point(2.,1.)),.1,.3,null);
        assertThrows(BusinessRuleException.class,()->service.validate(new Document(1,"#ffffff",List.of(invalid)),source));
    }
    @Test void endpointFromAnotherMapCannotBeLinked() {
        UUID endpointId=UUID.randomUUID();
        var feature=new Feature("room",FeatureType.ROOM,"Phòng","#ffffff",room.points(),.1,.3,endpointId);
        var endpoint=Endpoint.builder().endpointId(endpointId).map(FacilityMap.builder().mapId(UUID.randomUUID()).build()).active(true).accessible(true).build();
        when(endpoints.findAllById(Set.of(endpointId))).thenReturn(List.of(endpoint));
        assertThrows(BusinessRuleException.class,()->service.validate(new Document(1,"#ffffff",List.of(feature)),source));
    }
    @Test void publishCreatesRoomAndLinksEndpointInSameDocument() {
        UUID endpointId=UUID.randomUUID(),roomId=UUID.randomUUID();
        var endpoint=Endpoint.builder().endpointId(endpointId).map(map).endpointName("Cửa phòng 01").active(true).accessible(true).build();
        var feature=new Feature("room-01",FeatureType.ROOM,"Phòng 01","#ffffff",room.points(),.1,.3,endpointId,
            null,"ROOM_01","Phòng 01","PATIENT_ROOM","WARD_A","Phòng nội trú",true);
        draft=new Document(1,"#ffffff",List.of(feature)); row(1);
        when(endpoints.findAllById(Set.of(endpointId))).thenReturn(List.of(endpoint));
        when(endpoints.findById(endpointId)).thenReturn(Optional.of(endpoint));
        when(rooms.findByRoomCodeIgnoreCase("ROOM_01")).thenReturn(Optional.empty());
        when(rooms.save(any(Room.class))).thenAnswer(invocation->{Room value=invocation.getArgument(0);value.setRoomId(roomId);return value;});

        var result=service.publish(id,new Change(1));

        assertEquals(roomId,result.published().features().getFirst().roomId());
        assertEquals(roomId,endpoint.getRoom().getRoomId());
        assertTrue(endpoint.isPublicVisible());
        verify(endpoints).save(endpoint);
    }
    @Test void validationRejectsUnboundedStyleAndExecutableColorText() {
        try(var factory=Validation.buildDefaultValidatorFactory()) {
            var invalid=new Feature("label",FeatureType.LABEL,"Nhãn","url(https://example.com)",List.of(new Point(1.,1.)),99.,.3,null);
            assertFalse(factory.getValidator().validate(new Save(0,hash,new Document(1,"#ffffff",List.of(invalid)))).isEmpty());
            assertFalse(factory.getValidator().validate(new Save(0,hash,new Document(1,"#ffffff",List.of(room),180.01))).isEmpty());
            assertFalse(factory.getValidator().validate(new Save(0,hash,new Document(1,"#ffffff",List.of(room),-180.01))).isEmpty());
        }
    }
}
