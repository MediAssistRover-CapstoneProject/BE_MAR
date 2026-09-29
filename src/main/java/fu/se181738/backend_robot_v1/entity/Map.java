package fu.se181738.backend_robot_v1.entity;

import fu.se181738.backend_robot_v1.enums.MapStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Map mapped from docs/logicalDiagram.md.
 * Source constraints (cross-row/policy rules are not enforced by JPA alone):
 * - Khóa chính: `PK(map_id)`.
 * - UQ(map_code, version_no). Bản đã nghiệm thu không sửa tọa độ/hash tại chỗ.
 * - Tọa độ dùng mét, góc dùng radian thống nhất với MOVE; không trộn orientation_deg.
 * 
 * Nguồn: S0, S3
 */
@Entity
@Table(name = "Map", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"map_code", "version_no"})
})
@Getter
@Setter
@NoArgsConstructor
@Check(constraints = "version_no >= 1")
@Check(constraints = "width_m > 0")
@Check(constraints = "height_m > 0")
@Check(constraints = "resolution_m_per_pixel > 0")
public class Map {

    /** ID phiên bản bản đồ; dùng trong hợp đồng map_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Id
    @Column(name = "map_id", nullable = false)
    private UUID map_id;

    /** Mã dòng bản đồ. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Column(name = "map_code", nullable = false, length = 64)
    private String map_code;

    /** Phiên bản >= 1. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Column(name = "version_no", nullable = false)
    private Integer version_no;

    /** Tên bản đồ. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "map_name", nullable = false, length = 160)
    private String map_name;

    /** Tầng/khu vực. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "floor_label", nullable = true, length = 32)
    private String floor_label;

    /** Chiều rộng > 0, mét. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "width_m", nullable = false, precision = 12, scale = 4)
    private BigDecimal width_m;

    /** Chiều cao bản đồ > 0, mét. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "height_m", nullable = false, precision = 12, scale = 4)
    private BigDecimal height_m;

    /** Mét/pixel > 0. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "resolution_m_per_pixel", nullable = false, precision = 10, scale = 6)
    private BigDecimal resolution_m_per_pixel;

    /** Tọa độ gốc X, mét. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "origin_x_m", nullable = false, precision = 12, scale = 4)
    private BigDecimal origin_x_m;

    /** Tọa độ gốc Y, mét. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "origin_y_m", nullable = false, precision = 12, scale = 4)
    private BigDecimal origin_y_m;

    /** Góc gốc, radian. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "origin_yaw_rad", nullable = false, precision = 10, scale = 6)
    private BigDecimal origin_yaw_rad;

    /** Hệ tọa độ, mặc định map. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique. No inferred constraint/default.
    // ERD default: map.
    // TODO: Clarify from physical database design
    // The ERD does not assign default generation to database/application/compiler.
    @Column(name = "frame_id", nullable = false, length = 64)
    private String frame_id;

    /** Tham chiếu bộ tài nguyên bản đồ. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "map_file_uri", nullable = false, columnDefinition = "text")
    private String map_file_uri;

    /** SHA-256 của gói tài nguyên bản đồ. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "asset_hash", nullable = false, length = 64)
    private String asset_hash;

    /** DRAFT, ACTIVE, INACTIVE, RETIRED. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private MapStatus status;

    /** Thời điểm nghiệm thu bản đồ. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "commissioned_at", nullable = true)
    private Instant commissioned_at;

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
