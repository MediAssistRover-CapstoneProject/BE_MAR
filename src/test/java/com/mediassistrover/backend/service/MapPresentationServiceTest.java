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
    final SavedMapImageService images=mock(SavedMapImageService.class);
    final EntityManager em=mock(EntityManager.class);
    final Clock clock=Clock.fixed(Instant.parse("2026-11-06T00:00:00Z"),ZoneOffset.UTC);
    final MapPresentationService service=new MapPresentationService(rows,maps,endpoints,images,json,em,clock);
    FacilityMap map;
    Source source;
    Feature room;
    Document draft;

    @BeforeEach void setup() {
        map=FacilityMap.builder().mapId(id).assetHash(hash).widthM(BigDecimal.TEN).heightM(BigDecimal.TEN).build();
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
    @Test void validationRejectsUnboundedStyleAndExecutableColorText() {
        try(var factory=Validation.buildDefaultValidatorFactory()) {
            var invalid=new Feature("label",FeatureType.LABEL,"Nhãn","url(https://example.com)",List.of(new Point(1.,1.)),99.,.3,null);
            assertFalse(factory.getValidator().validate(new Save(0,hash,new Document(1,"#ffffff",List.of(invalid)))).isEmpty());
        }
    }
}
