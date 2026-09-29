package fu.se181738.backend_robot_v1.entity;

import fu.se181738.backend_robot_v1.enums.ZoneZoneType;
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
 * Zone mapped from docs/logicalDiagram.md.
 * Source constraints (cross-row/policy rules are not enforced by JPA alone):
 * - Khóa chính: `PK(zone_id)`.
 * - UQ(map_id, zone_code); UQ(map_id, zone_id) để hỗ trợ FK ghép.
 * - Zone giám sát ảnh phải có ánh xạ camera và thời điểm khung hình.
 * 
 * Nguồn: S0, S3
 */
@Entity
@Table(name = "Zone", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"map_id", "zone_code"}),
    @UniqueConstraint(columnNames = {"map_id", "zone_id"})
})
@Getter
@Setter
@NoArgsConstructor
@Check(constraints = "speed_limit_mps > 0")
@Check(constraints = "capacity_limit >= 0")
@Check(constraints = "calibrated_area_m2 > 0")
public class Zone {

    /** ID vùng. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Id
    @Column(name = "zone_id", nullable = false)
    private UUID zone_id;

    /** Phiên bản bản đồ chứa vùng. Tham chiếu Map.map_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "map_id", referencedColumnName = "map_id", nullable = false)
    private Map map_id;

    /** Mã vùng trong Map. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Column(name = "zone_code", nullable = false, length = 64)
    private String zone_code;

    /** Tên vùng. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "zone_name", nullable = false, length = 160)
    private String zone_name;

    /** WAITING, CORRIDOR, RESTRICTED, SERVICE, OTHER. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Enumerated(EnumType.STRING)
    @Column(name = "zone_type", nullable = false)
    private ZoneZoneType zone_type;

    /** Đường bao trong frame Map, đơn vị mét. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    // Structure not specified in ERD: retain raw JSON (object/array/scalar/null).
    // TODO: Define the JSON schema once the physical design is clarified.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "polygon_json", nullable = false)
    private String polygon_json;

    /** Trần tốc độ vùng > 0; không vượt chính sách Pi. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "speed_limit_mps", nullable = true, precision = 5, scale = 3)
    private BigDecimal speed_limit_mps;

    /** Ngưỡng số người; >= 0. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "capacity_limit", nullable = true)
    private Integer capacity_limit;

    /** Diện tích đã hiệu chuẩn; > 0 khi có. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "calibrated_area_m2", nullable = true, precision = 12, scale = 4)
    private BigDecimal calibrated_area_m2;

    /** Mã camera/nguồn ảnh do dịch vụ quản lý. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "camera_source_ref", nullable = true, length = 255)
    private String camera_source_ref;

    /** Vùng quan sát được hiệu chuẩn trên ảnh. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    // Structure not specified in ERD: retain raw JSON (object/array/scalar/null).
    // TODO: Define the JSON schema once the physical design is clarified.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "camera_roi_json", nullable = true)
    private String camera_roi_json;

    /** Còn dùng hay không. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "is_active", nullable = false)
    private Boolean is_active;

    /** Mô tả. */
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
