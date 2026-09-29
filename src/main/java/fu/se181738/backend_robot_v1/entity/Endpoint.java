package fu.se181738.backend_robot_v1.entity;

import fu.se181738.backend_robot_v1.enums.EndpointEndpointType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Endpoint mapped from docs/logicalDiagram.md.
 * Source constraints (cross-row/policy rules are not enforced by JPA alone):
 * - Khóa chính: `PK(endpoint_id)`.
 * - UQ(map_id, endpoint_code); UQ(map_id, endpoint_id).
 * - FK ghép (map_id, zone_id) -> Zone(map_id, zone_id), khi zone_id có giá trị.
 * - Không khai báo docking thành tính năng đã hỗ trợ; sạc hiện do nhân viên thực hiện.
 * 
 * Nguồn: S0, S3, S4
 */
@Entity
@Table(name = "Endpoint", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"map_id", "endpoint_code"}),
    @UniqueConstraint(columnNames = {"map_id", "endpoint_id"})
})
@Getter
@Setter
@NoArgsConstructor
public class Endpoint {

    /** ID điểm đích. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Id
    @Column(name = "endpoint_id", nullable = false)
    private UUID endpoint_id;

    /** Phiên bản bản đồ. Tham chiếu Map.map_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "map_id", referencedColumnName = "map_id", nullable = false)
    private Map map_id;

    /** Tối đa một vùng chính. Tham chiếu Zone.zone_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "zone_id", nullable = true)
    private UUID zone_id;

    // Read through the existing (map_id, zone_id) columns; write using map_id and zone_id.
    // Read-only joins avoid two writers for map_id. Refresh after changing either key.
    // map_id NOT NULL is declared on its owning mapping; this optional read view uses nullable joins.
    @Setter(lombok.AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumns(value = {
        @JoinColumn(name = "map_id", referencedColumnName = "map_id", nullable = true, insertable = false, updatable = false),
        @JoinColumn(name = "zone_id", referencedColumnName = "zone_id", nullable = true, insertable = false, updatable = false)
    }, foreignKey = @ForeignKey(name = "fk_endpoint_map_zone"))
    private Zone zone;

    /** Phòng liên quan; NULL với checkpoint/hành lang. Tham chiếu Room.room_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "room_id", referencedColumnName = "room_id", nullable = true)
    private Room room_id;

    /** Mã điểm trong Map. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Column(name = "endpoint_code", nullable = false, length = 64)
    private String endpoint_code;

    /** Tên điểm. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "endpoint_name", nullable = false, length = 160)
    private String endpoint_name;

    /** ROOM_STOP, PICKUP, DROPOFF, WAITING, CHECKPOINT, MANUAL_CHARGE. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Enumerated(EnumType.STRING)
    @Column(name = "endpoint_type", nullable = false)
    private EndpointEndpointType endpoint_type;

    /** Tọa độ X, mét. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "x_m", nullable = false, precision = 12, scale = 4)
    private BigDecimal x_m;

    /** Tọa độ Y, mét. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "y_m", nullable = false, precision = 12, scale = 4)
    private BigDecimal y_m;

    /** Hướng đến đích, radian trong [-pi,pi]. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "yaw_rad", nullable = false, precision = 10, scale = 6)
    private BigDecimal yaw_rad;

    /** Cho phép chọn hay không. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "is_active", nullable = false)
    private Boolean is_active;

    /** Đã xác nhận khả năng tiếp cận theo cấu hình. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "is_accessible", nullable = false)
    private Boolean is_accessible;

    /** Ghi chú điểm. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "description", nullable = true, columnDefinition = "text")
    private String description;

    /** Thời điểm tạo. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "created_at", nullable = false)
    private Instant created_at;

    /** Thời điểm cập nhật. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "updated_at", nullable = false)
    private Instant updated_at;
}
