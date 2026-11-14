package com.mediassistrover.backend.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediassistrover.backend.dto.common.DeletionContracts.*;
import com.mediassistrover.backend.entity.Account;
import com.mediassistrover.backend.exception.*;
import com.mediassistrover.backend.repository.RoomRepository;
import com.mediassistrover.backend.service.SosMonitoringRoomService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.*;

/** Map-owned catalog data is deleted together. Operational references outside it block the transaction. */
@Service
@RequiredArgsConstructor
public class MapHardDeleteService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final RoomRepository rooms;
    private final SosMonitoringRoomService sos;
    private final EntityManager em;
    private final Clock clock;

    private record Scope(String table, String column, List<UUID> ids) {
        String where(String alias) {
            return ids.isEmpty() ? "1=0" : (alias.isEmpty() ? "" : alias + ".") + q(column)
                    + " IN (" + String.join(",", Collections.nCopies(ids.size(), "?")) + ")";
        }
        Object[] args() { return ids.stream().map(MapHardDeleteService::key).toArray(); }
    }
    private record Column(String child, String parent) { }
    private record Reference(String child, List<Column> columns) { }
    private record Plan(LinkedHashMap<String, Scope> scopes, List<UUID> rooms, int sharedRooms) { }

    @Transactional(readOnly = true)
    public DeletionImpactResponse impact(UUID id) {
        UUID floor = requireMap(id, false);
        return impact(id, plan(id, floor, false));
    }

    @Transactional
    public HardDeleteResponse delete(UUID id, Account actor) {
        // Use the same map-then-floor lock order as MapPresentationService.publish.
        em.flush();
        UUID floor = requireMap(id, true);
        jdbc.queryForList("SELECT floor_id FROM Floor WHERE floor_id=? FOR UPDATE", key(floor));
        Plan plan = plan(id, floor, true);
        for (Scope scope : plan.scopes().values())
            jdbc.queryForList("SELECT " + q(scope.column()) + " FROM " + q(scope.table())
                    + " WHERE " + scope.where("") + " FOR UPDATE", scope.args());
        DeletionImpactResponse impact = impact(id, plan);
        if (!impact.canHardDelete()) throw new ConflictException(String.join("; ", impact.blockers()));

        for (UUID roomId : plan.rooms()) rooms.lockById(roomId).ifPresent(room -> sos.releaseIfConfigured(room, actor));
        em.flush();
        var deleted = new LinkedHashMap<String, Long>();
        deleted.put("RobotMapUnlinked", (long) jdbc.update("UPDATE Robot SET current_map_id=NULL,map_asset_hash=NULL WHERE current_map_id=?", key(id)));
        // Shared rooms survive; their default group cannot point at the map being removed.
        deleted.put("RoomDefaultGroupUnlinked", (long) jdbc.update("UPDATE Room SET default_endpoint_group_id=NULL WHERE default_endpoint_group_id IN (SELECT endpoint_group_id FROM EndpointGroup WHERE map_id=?)", key(id)));
        for (Scope scope : plan.scopes().values())
            deleted.put(scope.table(), (long) jdbc.update("DELETE FROM " + q(scope.table()) + " WHERE " + scope.where(""), scope.args()));
        em.clear(); // JDBC deletes must not leave stale Room/Endpoint instances in the persistence context.
        return new HardDeleteResponse(id, "MAP", Map.copyOf(deleted));
    }

    private DeletionImpactResponse impact(UUID id, Plan plan) {
        var counts = new LinkedHashMap<String, Long>();
        var blockers = new LinkedHashSet<String>();
        for (Scope scope : plan.scopes().values()) {
            long n = count("SELECT COUNT(*) FROM " + q(scope.table()) + " WHERE " + scope.where(""), scope.args());
            counts.put(scope.table(), n);
            if (n == 0) continue;
            for (Reference reference : references(scope.table())) {
                if (detachable(reference)) continue;
                Scope child = plan.scopes().values().stream().filter(s -> s.table().equalsIgnoreCase(reference.child())).findFirst().orElse(null);
                String join = reference.columns().stream().map(c -> "c." + q(c.child()) + "=p." + q(c.parent()))
                        .reduce((a, b) -> a + " AND " + b).orElseThrow();
                String sql = "SELECT COUNT(*) FROM " + q(reference.child()) + " c WHERE EXISTS (SELECT 1 FROM "
                        + q(scope.table()) + " p WHERE " + join + " AND " + scope.where("p") + ")";
                var args = new ArrayList<>(Arrays.asList(scope.args()));
                if (child != null) { sql += " AND NOT (" + child.where("c") + ")"; args.addAll(Arrays.asList(child.args())); }
                long external = count(sql, args.toArray());
                if (external == 0) continue;
                String name = reference.child() + "." + reference.columns().stream().map(Column::child).reduce((a, b) -> a + "," + b).orElseThrow();
                counts.put(name, external);
                blockers.add(name + " còn " + external + " bản ghi tham chiếu. Cần xử lý trước khi xóa bản đồ.");
            }
        }
        counts.put("sharedRooms", (long) plan.sharedRooms());
        if (count("SELECT COUNT(*) FROM Robot WHERE current_map_id=? AND status IN ('BUSY','STARTING')", key(id)) > 0)
            blockers.add("Robot đang sử dụng bản đồ. Dừng công việc trước khi xóa.");
        if (count("SELECT COUNT(*) FROM RobotNavigationSession WHERE map_id=? AND closed_at IS NULL AND expires_at>?", key(id), Timestamp.from(clock.instant())) > 0)
            blockers.add("Bản đồ còn phiên điều hướng đang mở. Đóng phiên trước khi xóa.");
        return new DeletionImpactResponse(id, "MAP", blockers.isEmpty(), List.copyOf(blockers), Map.copyOf(counts));
    }

    private Plan plan(UUID id, UUID floor, boolean lock) {
        var candidates = new TreeSet<UUID>(ids("SELECT DISTINCT r.room_id FROM Room r WHERE r.floor_id=? AND (EXISTS (SELECT 1 FROM Endpoint e WHERE e.room_id=r.room_id AND e.map_id=?) OR EXISTS (SELECT 1 FROM EndpointGroup g WHERE g.endpoint_group_id=r.default_endpoint_group_id AND g.map_id=?))", key(floor), key(id), key(id)));
        for (var row : jdbc.queryForList("SELECT draft_json,published_json FROM MapPresentation WHERE map_id=?", key(id)))
            row.values().forEach(document -> candidates.addAll(drawingRooms(document, id)));
        // Ignore deleted/stale IDs and never touch a room on another floor.
        candidates.retainAll(new HashSet<>(ids("SELECT room_id FROM Room WHERE floor_id=?", key(floor))));
        Scope candidateRooms = new Scope("Room", "room_id", List.copyOf(candidates));
        if (lock && !candidates.isEmpty())
            jdbc.queryForList("SELECT room_id FROM Room WHERE " + candidateRooms.where("") + " ORDER BY room_id FOR UPDATE", candidateRooms.args());

        var shared = new HashSet<>(ids("SELECT DISTINCT room_id FROM Endpoint WHERE map_id<>? AND room_id IS NOT NULL", key(id)));
        shared.addAll(ids("SELECT r.room_id FROM Room r JOIN EndpointGroup g ON g.endpoint_group_id=r.default_endpoint_group_id WHERE g.map_id<>?", key(id)));
        // Drafts and archived maps also own links. Preserve a room still referenced by either.
        for (var row : jdbc.queryForList("SELECT p.map_id,p.draft_json,p.published_json FROM MapPresentation p JOIN Map m ON m.map_id=p.map_id WHERE m.floor_id=? AND p.map_id<>?", key(floor), key(id))) {
            UUID other = uuid((byte[]) row.get("map_id"));
            shared.addAll(drawingRooms(row.get("draft_json"), other));
            shared.addAll(drawingRooms(row.get("published_json"), other));
        }
        shared.retainAll(candidates);
        candidates.removeAll(shared);
        var zones = ids("SELECT zone_id FROM Zone WHERE map_id=?", key(id));
        // Child-first order handles composite group/endpoint FKs without disabling constraints.
        var scopes = new LinkedHashMap<String, Scope>();
        add(scopes, "MapPresentation", "map_id", id);
        add(scopes, "MapArtifact", "map_id", id);
        add(scopes, "MapBinaryAsset", "map_id", id);
        add(scopes, "RobotNavigationSession", "map_id", id);
        add(scopes, "RobotNavigationProfile", "map_id", id);
        scopes.put("AiSensingProfile", new Scope("AiSensingProfile", "zone_id", zones));
        add(scopes, "HeatmapRecord", "map_id", id);
        scopes.put("CrowdDensityRecord", new Scope("CrowdDensityRecord", "zone_id", zones));
        add(scopes, "DetectionEvent", "map_id", id);
        add(scopes, "RobotLocationHistory", "map_id", id);
        add(scopes, "EndpointGroupMember", "map_id", id);
        add(scopes, "Endpoint", "map_id", id);
        scopes.put("Room", new Scope("Room", "room_id", List.copyOf(candidates)));
        add(scopes, "EndpointGroup", "map_id", id);
        add(scopes, "Zone", "map_id", id);
        add(scopes, "Map", "map_id", id);
        return new Plan(scopes, List.copyOf(candidates), shared.size());
    }

    private Set<UUID> drawingRooms(Object value, UUID mapId) {
        var result = new HashSet<UUID>();
        if (value == null) return result;
        JsonNode document;
        try {
            document = json.readTree(value instanceof byte[] bytes ? new String(bytes, StandardCharsets.UTF_8) : value.toString());
        } catch (Exception e) { throw new ConflictException("Không đọc được bản vẽ. Cần kiểm tra dữ liệu trước khi xóa."); }
        if (document == null || document.isNull()) return result;
        for (JsonNode feature : document.path("features")) {
            if (!"ROOM".equals(feature.path("type").asText())) continue;
            String room = feature.path("roomId").asText("");
            String point = feature.path("endpointId").asText("");
            try {
                if (!room.isBlank()) result.add(UUID.fromString(room));
                else if (!point.isBlank()) result.addAll(ids("SELECT room_id FROM Endpoint WHERE endpoint_id=? AND map_id=? AND room_id IS NOT NULL", key(UUID.fromString(point)), key(mapId)));
            } catch (IllegalArgumentException e) { throw new ConflictException("Liên kết phòng trong bản vẽ không hợp lệ. Cần kiểm tra trước khi xóa."); }
        }
        return result;
    }

    private UUID requireMap(UUID id, boolean lock) {
        var rows = ids("SELECT floor_id FROM Map WHERE map_id=?" + (lock ? " FOR UPDATE" : ""), key(id));
        if (rows.isEmpty()) throw new ResourceNotFoundException("Map not found: " + id);
        return rows.getFirst();
    }

    private List<Reference> references(String table) {
        var connection = DataSourceUtils.getConnection(Objects.requireNonNull(jdbc.getDataSource()));
        try (var keys = connection.getMetaData().getExportedKeys(connection.getCatalog(), null, table)) {
            var tables = new LinkedHashMap<String, String>();
            var columns = new LinkedHashMap<String, List<Column>>();
            while (keys.next()) {
                String child = keys.getString("FKTABLE_NAME"), name = child + ":" + keys.getString("FK_NAME");
                tables.put(name, child);
                columns.computeIfAbsent(name, ignored -> new ArrayList<>()).add(new Column(keys.getString("FKCOLUMN_NAME"), keys.getString("PKCOLUMN_NAME")));
            }
            return columns.entrySet().stream().map(e -> new Reference(tables.get(e.getKey()), e.getValue())).toList();
        } catch (SQLException e) { throw new IllegalStateException("Cannot inspect map references", e); }
        finally { DataSourceUtils.releaseConnection(connection, jdbc.getDataSource()); }
    }

    private boolean detachable(Reference reference) {
        if (reference.columns().size() != 1) return false;
        String column = reference.columns().getFirst().child();
        return reference.child().equalsIgnoreCase("Robot") && column.equalsIgnoreCase("current_map_id")
                || reference.child().equalsIgnoreCase("Room") && column.equalsIgnoreCase("default_endpoint_group_id");
    }
    private List<UUID> ids(String sql, Object... args) { return jdbc.query(sql, (rs, n) -> uuid(rs.getBytes(1)), args); }
    private long count(String sql, Object... args) { return Optional.ofNullable(jdbc.queryForObject(sql, Long.class, args)).orElse(0L); }
    private static void add(LinkedHashMap<String, Scope> scopes, String table, String column, UUID id) { scopes.put(table, new Scope(table, column, List.of(id))); }
    private static String q(String name) { return "`" + name.replace("`", "``") + "`"; }
    private static byte[] key(UUID id) { return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array(); }
    private static UUID uuid(byte[] value) { var bytes = ByteBuffer.wrap(value); return new UUID(bytes.getLong(), bytes.getLong()); }
}
