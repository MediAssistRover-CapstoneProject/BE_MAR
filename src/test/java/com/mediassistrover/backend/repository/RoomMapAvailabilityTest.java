package com.mediassistrover.backend.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.mapper.FacilityMapper;
import com.mediassistrover.backend.service.impl.FacilityServiceImpl;
import com.mediassistrover.backend.service.AuditService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.util.ReflectionTestUtils;
import org.mapstruct.factory.Mappers;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties={"spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect","spring.jpa.properties.hibernate.globally_quoted_identifiers=true",
    "spring.jpa.properties.hibernate.globally_quoted_identifiers_skip_column_definitions=true",
    "spring.jpa.properties.hibernate.hbm2ddl.halt_on_error=true","logging.level.org.hibernate.SQL=WARN","logging.level.org.springframework.jdbc=WARN"})
@ContextConfiguration(classes=PatrolPersistenceTest.Config.class)
class RoomMapAvailabilityTest {
    @Autowired EntityManager em;
    @Autowired RoomRepository rooms;
    @Autowired FacilityMapRepository maps;
    @Autowired FloorRepository floors;
    @Autowired AccountRepository accounts;
    final Instant now=Instant.now();

    @Test void activationSwitchUpdatesRoomQueriesAndResponsesWithoutReenablingManuallyDisabledRooms() {
        var actor=Account.builder().accountCode("ADMIN").username("admin").fullName("Admin").role(Role.ADMIN)
            .authProvider(AuthProvider.LOCAL).status(AccountStatus.ACTIVE).createdAt(now).updatedAt(now).build();em.persist(actor);
        var hospital=HospitalInformation.builder().updatedBy(actor).infoCategory("HOSPITAL_PROFILE").title("Hospital")
            .content("Hospital").languageCode("vi").keywordsJson(new ObjectMapper().createArrayNode())
            .status(HospitalInformationStatus.PUBLISHED).createdAt(now).updatedAt(now).build();em.persist(hospital);
        var floor=Floor.builder().hospitalInformation(hospital).floorCode("F1").floorName("Floor 1")
            .active(true).createdAt(now).updatedAt(now).build();em.persist(floor);
        var a=map(floor,"A",MapStatus.ACTIVE);var b=map(floor,"B",MapStatus.INACTIVE);
        var oldRoom=room(floor,"OLD",true);var newRoom=room(floor,"NEW",true);
        var shared=room(floor,"SHARED",true);var disabled=room(floor,"DISABLED",false);
        var unlinked=room(floor,"UNLINKED",true);var disabledPoint=room(floor,"DISABLED_POINT",true);
        endpoint(a,oldRoom,true);endpoint(b,newRoom,true);endpoint(a,shared,true);endpoint(b,shared,true);
        endpoint(b,disabled,true);endpoint(b,disabledPoint,false);
        em.flush();em.clear();
        assertEquals(Set.of("OLD","SHARED"),activeCodes());
        assertTrue(rooms.findById(oldRoom.getRoomId()).orElseThrow().isOperational());
        assertFalse(rooms.findById(newRoom.getRoomId()).orElseThrow().isOperational());
        var service=mock(FacilityServiceImpl.class,CALLS_REAL_METHODS);
        Map.of("mapRepository",maps,"floorRepository",floors,"accountRepository",accounts,
            "mapper",Mappers.getMapper(FacilityMapper.class),"clock",Clock.systemUTC(),"auditService",mock(AuditService.class))
            .forEach((key,value)->ReflectionTestUtils.setField(service,key,value));
        service.activateMap(b.getMapId(),actor.getAccountId(),null);
        em.flush();em.clear();
        assertEquals(Set.of("NEW","SHARED"),activeCodes());
        assertFalse(rooms.findById(oldRoom.getRoomId()).orElseThrow().isOperational());
        var current=rooms.findById(newRoom.getRoomId()).orElseThrow();
        assertTrue(current.isOperationalOn(maps.findById(b.getMapId()).orElseThrow()));
        assertFalse(current.isOperationalOn(maps.findById(a.getMapId()).orElseThrow()));
        assertTrue(Mappers.getMapper(FacilityMapper.class).toResponse(current).operational());
        assertFalse(rooms.findById(disabled.getRoomId()).orElseThrow().isActive());
        assertFalse(rooms.findById(unlinked.getRoomId()).orElseThrow().isOperational());
        assertFalse(rooms.findById(disabledPoint.getRoomId()).orElseThrow().isOperational());
        assertEquals(Set.of("NEW","SHARED"),rooms.searchPublic(null,null,null,null,null,Pageable.unpaged())
            .stream().map(Room::getRoomCode).collect(java.util.stream.Collectors.toSet()));
        assertTrue(rooms.findByRoomIdAndActiveTrueAndPublicVisibleTrue(oldRoom.getRoomId()).isEmpty());
        assertEquals(6,rooms.findAll().size());
        service.activateMap(a.getMapId(),actor.getAccountId(),null);em.flush();em.clear();
        assertEquals(Set.of("OLD","SHARED"),activeCodes());
        var active=maps.findById(a.getMapId()).orElseThrow();active.setStatus(MapStatus.ARCHIVED);em.flush();em.clear();
        assertTrue(activeCodes().isEmpty());
    }

    Set<String> activeCodes() {return rooms.findByActiveTrue(Pageable.unpaged()).stream().map(Room::getRoomCode).collect(java.util.stream.Collectors.toSet());}
    FacilityMap map(Floor floor,String code,MapStatus status) {
        var value=FacilityMap.builder().floor(floor).mapCode(code).mapName(code).versionNo(1).status(status)
            .widthM(BigDecimal.TEN).heightM(BigDecimal.TEN).resolutionMPerPixel(new BigDecimal("0.05"))
            .originXM(BigDecimal.ZERO).originYM(BigDecimal.ZERO).originYawRad(BigDecimal.ZERO)
            .frameId("map").mapFileUri("test.zip").assetHash("a".repeat(64)).createdAt(now).updatedAt(now).build();em.persist(value);return value;
    }
    Room room(Floor floor,String code,boolean enabled) {
        var value=Room.builder().floor(floor).roomCode(code).roomName(code).roomType("GENERAL").active(enabled)
            .publicVisible(true).createdAt(now).updatedAt(now).build();em.persist(value);return value;
    }
    void endpoint(FacilityMap map,Room room,boolean enabled) {
        var value=Endpoint.builder().map(map).room(room).endpointCode(room.getRoomCode()).endpointName(room.getRoomName())
            .endpointType(EndpointType.WAYPOINT).xM(BigDecimal.ONE).yM(BigDecimal.ONE).yawRad(BigDecimal.ZERO)
            .active(enabled).accessible(true).publicVisible(true).createdAt(now).updatedAt(now).build();em.persist(value);
    }
}
