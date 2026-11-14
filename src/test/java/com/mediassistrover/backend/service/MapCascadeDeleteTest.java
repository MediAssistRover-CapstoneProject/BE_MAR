package com.mediassistrover.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.exception.*;
import com.mediassistrover.backend.repository.RoomRepository;
import com.mediassistrover.backend.service.impl.MapHardDeleteService;
import jakarta.persistence.EntityManager;
import org.h2.api.Trigger;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.ByteBuffer;
import java.sql.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual SQL/FK/rollback tests on an isolated database; never connects to the application database. */
public class MapCascadeDeleteTest {
    final UUID floor = UUID.randomUUID(), map = UUID.randomUUID(), other = UUID.randomUUID();
    final UUID room = UUID.randomUUID(), unrelatedRoom = UUID.randomUUID(), point = UUID.randomUUID(), group = UUID.randomUUID(), zone = UUID.randomUUID(), robot = UUID.randomUUID();
    final RoomRepository rooms = mock(RoomRepository.class);
    final SosMonitoringRoomService sos = mock(SosMonitoringRoomService.class);
    final EntityManager em = mock(EntityManager.class);
    final Account actor = Account.builder().accountId(UUID.randomUUID()).build();
    final Clock clock = Clock.fixed(Instant.parse("2026-11-14T00:00:00Z"), ZoneOffset.UTC);
    JdbcTemplate jdbc;
    TransactionTemplate tx;
    MapHardDeleteService service;

    @BeforeEach void setup() {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=FALSE;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(ds);
        tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        service = new MapHardDeleteService(jdbc, new ObjectMapper(), rooms, sos, em, clock);
        String ddl = """
            CREATE TABLE Floor(floor_id BINARY(16) PRIMARY KEY);
            CREATE TABLE Map(map_id BINARY(16) PRIMARY KEY,floor_id BINARY(16) NOT NULL REFERENCES Floor(floor_id));
            CREATE TABLE Zone(zone_id BINARY(16) PRIMARY KEY,map_id BINARY(16) NOT NULL REFERENCES Map(map_id));
            CREATE TABLE EndpointGroup(endpoint_group_id BINARY(16) PRIMARY KEY,map_id BINARY(16) NOT NULL REFERENCES Map(map_id),UNIQUE(endpoint_group_id,map_id));
            CREATE TABLE Room(room_id BINARY(16) PRIMARY KEY,floor_id BINARY(16) NOT NULL REFERENCES Floor(floor_id),default_endpoint_group_id BINARY(16) REFERENCES EndpointGroup(endpoint_group_id));
            CREATE TABLE Endpoint(endpoint_id BINARY(16) PRIMARY KEY,map_id BINARY(16) NOT NULL REFERENCES Map(map_id),room_id BINARY(16) REFERENCES Room(room_id),zone_id BINARY(16) REFERENCES Zone(zone_id),UNIQUE(endpoint_id,map_id));
            CREATE TABLE EndpointGroupMember(endpoint_group_id BINARY(16),endpoint_id BINARY(16),map_id BINARY(16),PRIMARY KEY(endpoint_group_id,endpoint_id),FOREIGN KEY(endpoint_group_id,map_id) REFERENCES EndpointGroup(endpoint_group_id,map_id),FOREIGN KEY(endpoint_id,map_id) REFERENCES Endpoint(endpoint_id,map_id));
            CREATE TABLE MapPresentation(map_id BINARY(16) PRIMARY KEY REFERENCES Map(map_id),draft_json LONGVARCHAR,published_json LONGVARCHAR);
            CREATE TABLE MapBinaryAsset(map_id BINARY(16) PRIMARY KEY REFERENCES Map(map_id));
            CREATE TABLE MapArtifact(map_id BINARY(16) PRIMARY KEY REFERENCES Map(map_id));
            CREATE TABLE Robot(robot_id BINARY(16) PRIMARY KEY,current_map_id BINARY(16) REFERENCES Map(map_id),map_asset_hash VARCHAR(64),status VARCHAR(32));
            CREATE TABLE RobotNavigationProfile(robot_id BINARY(16) PRIMARY KEY,map_id BINARY(16) REFERENCES Map(map_id));
            CREATE TABLE RobotNavigationSession(robot_id BINARY(16) PRIMARY KEY,map_id BINARY(16) REFERENCES Map(map_id),closed_at TIMESTAMP,expires_at TIMESTAMP);
            CREATE TABLE AiSensingProfile(profile_id BINARY(16) PRIMARY KEY,zone_id BINARY(16) REFERENCES Zone(zone_id));
            CREATE TABLE HeatmapRecord(record_id BINARY(16) PRIMARY KEY,map_id BINARY(16) REFERENCES Map(map_id));
            CREATE TABLE CrowdDensityRecord(record_id BINARY(16) PRIMARY KEY,zone_id BINARY(16) REFERENCES Zone(zone_id),checkpoint_id BINARY(16) REFERENCES Endpoint(endpoint_id));
            CREATE TABLE DetectionEvent(record_id BINARY(16) PRIMARY KEY,map_id BINARY(16) REFERENCES Map(map_id));
            CREATE TABLE RobotLocationHistory(record_id BINARY(16) PRIMARY KEY,map_id BINARY(16) REFERENCES Map(map_id));
            CREATE TABLE Shift(shift_id BINARY(16) PRIMARY KEY,room_id BINARY(16) REFERENCES Room(room_id));
            CREATE TABLE Job(job_id BINARY(16) PRIMARY KEY,map_id BINARY(16) REFERENCES Map(map_id));
            """;
        for (String sql : ddl.split(";")) if (!sql.isBlank()) jdbc.execute(sql);
        jdbc.update("INSERT INTO Floor VALUES(?)", key(floor));
        jdbc.update("INSERT INTO Map VALUES(?,?),(?,?)", key(map), key(floor), key(other), key(floor));
        jdbc.update("INSERT INTO Zone VALUES(?,?)", key(zone), key(map));
        jdbc.update("INSERT INTO EndpointGroup VALUES(?,?)", key(group), key(map));
        jdbc.update("INSERT INTO Room VALUES(?,?,?),(?,?,NULL)", key(room), key(floor), key(group), key(unrelatedRoom), key(floor));
        jdbc.update("INSERT INTO Endpoint VALUES(?,?,?,?)", key(point), key(map), key(room), key(zone));
        jdbc.update("INSERT INTO EndpointGroupMember VALUES(?,?,?)", key(group), key(point), key(map));
        jdbc.update("INSERT INTO MapPresentation VALUES(?,NULL,?)", key(map), drawing(room));
        jdbc.update("INSERT INTO MapBinaryAsset VALUES(?)", key(map));
        jdbc.update("INSERT INTO MapArtifact VALUES(?)", key(map));
        jdbc.update("INSERT INTO Robot VALUES(?,?,'hash','IDLE')", key(robot), key(map));
        jdbc.update("INSERT INTO RobotNavigationProfile VALUES(?,?)", key(robot), key(map));
        jdbc.update("INSERT INTO RobotNavigationSession VALUES(?,?,?,?)", key(robot), key(map), Timestamp.from(clock.instant()), Timestamp.from(clock.instant().plusSeconds(100)));
        jdbc.update("INSERT INTO AiSensingProfile VALUES(?,?)", key(UUID.randomUUID()), key(zone));
        jdbc.update("INSERT INTO HeatmapRecord VALUES(?,?)", key(UUID.randomUUID()), key(map));
        jdbc.update("INSERT INTO CrowdDensityRecord VALUES(?,?,?)", key(UUID.randomUUID()), key(zone), key(point));
        jdbc.update("INSERT INTO DetectionEvent VALUES(?,?)", key(UUID.randomUUID()), key(map));
        jdbc.update("INSERT INTO RobotLocationHistory VALUES(?,?)", key(UUID.randomUUID()), key(map));
        when(rooms.lockById(room)).thenReturn(Optional.of(Room.builder().roomId(room).build()));
    }

    @Test void deletesOwnedCatalogAndBytesTogetherWhilePreservingOtherMapAndUnrelatedRoom() {
        var impact = service.impact(map);
        assertTrue(impact.canHardDelete(), impact.blockers().toString());
        assertEquals(1L, impact.dependencies().get("Endpoint"));
        assertEquals(1L, impact.dependencies().get("Room"));
        var result = tx.execute(status -> service.delete(map, actor));
        assertEquals(1L, result.deleted().get("Map"));
        assertEquals(1L, result.deleted().get("Endpoint"));
        assertEquals(1L, result.deleted().get("Room"));
        assertEquals(1L, result.deleted().get("MapPresentation"));
        for (String table : List.of("Endpoint", "Zone", "EndpointGroup", "EndpointGroupMember", "MapBinaryAsset", "MapArtifact", "MapPresentation", "AiSensingProfile", "HeatmapRecord", "CrowdDensityRecord", "DetectionEvent", "RobotLocationHistory", "RobotNavigationProfile", "RobotNavigationSession")) assertEquals(0, count(table), table);
        assertEquals(1, count("Map")); assertEquals(1, count("Room"));
        assertNotNull(jdbc.queryForObject("SELECT map_id FROM Map", byte[].class));
        assertNull(jdbc.queryForObject("SELECT current_map_id FROM Robot", byte[].class));
        verify(sos).releaseIfConfigured(any(Room.class), eq(actor));
    }

    @Test void roomReferencedByAnotherMapsEndpointSurvivesAndLosesDeletedDefaultGroup() {
        jdbc.update("INSERT INTO Endpoint VALUES(?,?,?,NULL)", key(UUID.randomUUID()), key(other), key(room));
        assertEquals(1L, service.impact(map).dependencies().get("sharedRooms"));
        var result = tx.execute(status -> service.delete(map, actor));
        assertEquals(0L, result.deleted().get("Room")); assertEquals(2, count("Room"));
        assertEquals(1, count("Endpoint"));
        assertNull(jdbc.queryForObject("SELECT default_endpoint_group_id FROM Room WHERE room_id=?", byte[].class, key(room)));
        verifyNoInteractions(sos);
    }

    @Test void sharedRoomInAnotherDraftOrPublishedDrawingIsPreserved() {
        jdbc.update("INSERT INTO MapPresentation VALUES(?,?,?)", key(other), drawing(room), drawing(room));
        tx.execute(status -> service.delete(map, actor));
        assertEquals(2, count("Room")); assertEquals(1, count("MapPresentation"));
        verifyNoInteractions(sos);
    }

    @Test void roomLinkedOnlyByDrawingIsDeleted() {
        jdbc.update("UPDATE Endpoint SET room_id=NULL"); jdbc.update("UPDATE Room SET default_endpoint_group_id=NULL");
        assertEquals(1L, service.impact(map).dependencies().get("Room"));
        tx.execute(status -> service.delete(map, actor));
        assertEquals(1, count("Room"));
    }

    @Test void roomLinkedOnlyByDefaultGroupIsDeleted() {
        jdbc.update("UPDATE Endpoint SET room_id=NULL"); jdbc.update("DELETE FROM MapPresentation");
        tx.execute(status -> service.delete(map, actor));
        assertEquals(1, count("Room"));
    }

    @Test void missingRoomIdsInAnOldDrawingDoNotBlockLegacyEndpointLinks() {
        jdbc.update("UPDATE MapPresentation SET published_json=?", "{\"features\":[{\"type\":\"ROOM\",\"endpointId\":\"" + point + "\"}]}");
        tx.execute(status -> service.delete(map, actor));
        assertEquals(1, count("Room"));
    }

    @Test void shiftReferenceBlocksTheWholeDeletionBeforeAnyMutation() {
        jdbc.update("INSERT INTO Shift VALUES(?,?)", key(UUID.randomUUID()), key(room));
        var impact = service.impact(map);
        assertFalse(impact.canHardDelete()); assertTrue(impact.blockers().stream().anyMatch(s -> s.contains("Shift.room_id")));
        assertThrows(ConflictException.class, () -> tx.execute(status -> service.delete(map, actor)));
        assertIntact(); verifyNoInteractions(sos);
    }

    @Test void jobReferenceBlocksTheWholeDeletion() {
        jdbc.update("INSERT INTO Job VALUES(?,?)", key(UUID.randomUUID()), key(map));
        assertFalse(service.impact(map).canHardDelete());
        assertThrows(ConflictException.class, () -> tx.execute(status -> service.delete(map, actor)));
        assertIntact();
    }

    @Test void crossMapZoneReferenceCannotSilentlyRemoveAnotherMapsEndpoint() {
        jdbc.update("INSERT INTO Endpoint VALUES(?,?,NULL,?)", key(UUID.randomUUID()), key(other), key(zone));
        assertFalse(service.impact(map).canHardDelete());
        assertThrows(ConflictException.class, () -> tx.execute(status -> service.delete(map, actor)));
        assertEquals(2, count("Endpoint")); assertEquals(2, count("Map"));
    }

    @Test void runningRobotAndOpenNavigationSessionBlockDeletion() {
        jdbc.update("UPDATE Robot SET status='BUSY'");
        assertFalse(service.impact(map).canHardDelete());
        jdbc.update("UPDATE Robot SET status='IDLE'");
        jdbc.update("UPDATE RobotNavigationSession SET closed_at=NULL");
        assertFalse(service.impact(map).canHardDelete());
        assertThrows(ConflictException.class, () -> tx.execute(status -> service.delete(map, actor)));
        assertIntact();
    }

    @Test void failureOnFinalMapDeleteRollsBackAllPreviousChildDeletesAndUnlinks() {
        jdbc.execute("CREATE TRIGGER reject_map_delete BEFORE DELETE ON Map FOR EACH ROW CALL '" + RejectMapDeletion.class.getName() + "'");
        assertThrows(RuntimeException.class, () -> tx.execute(status -> service.delete(map, actor)));
        assertIntact();
        assertEquals(1, count("EndpointGroupMember")); assertEquals(1, count("MapBinaryAsset"));
        assertArrayEquals(key(map), jdbc.queryForObject("SELECT current_map_id FROM Robot", byte[].class));
        assertArrayEquals(key(group), jdbc.queryForObject("SELECT default_endpoint_group_id FROM Room WHERE room_id=?", byte[].class, key(room)));
    }

    @Test void unknownFutureReferenceAlsoBlocksInsteadOfLeavingOrphans() {
        jdbc.execute("CREATE TABLE FutureMapUse(id INTEGER PRIMARY KEY,map_id BINARY(16) REFERENCES Map(map_id))");
        jdbc.update("INSERT INTO FutureMapUse VALUES(1,?)", key(map));
        assertFalse(service.impact(map).canHardDelete());
        assertThrows(ConflictException.class, () -> tx.execute(status -> service.delete(map, actor)));
        assertIntact();
    }

    @Test void missingMapIsNotReportedAsSuccessfulDeletion() {
        assertThrows(ResourceNotFoundException.class, () -> tx.execute(status -> service.delete(UUID.randomUUID(), actor)));
        assertIntact();
    }

    void assertIntact() { assertEquals(2, count("Map")); assertEquals(2, count("Room")); assertEquals(1, count("Endpoint")); assertEquals(1, count("MapPresentation")); }
    int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM `" + table + "`", Integer.class); }
    String drawing(UUID id) { return "{\"features\":[{\"type\":\"ROOM\",\"roomId\":\"" + id + "\"}]}"; }
    static byte[] key(UUID id) { return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array(); }
    public static class RejectMapDeletion implements Trigger {
        @Override public void fire(Connection connection, Object[] oldRow, Object[] newRow) throws SQLException { throw new SQLException("injected delete failure"); }
    }
}
