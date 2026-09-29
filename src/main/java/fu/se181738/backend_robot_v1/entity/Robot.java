package fu.se181738.backend_robot_v1.entity;

import fu.se181738.backend_robot_v1.enums.RobotStatus;
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
 * Robot mapped from docs/logicalDiagram.md.
 * Source constraints (cross-row/policy rules are not enforced by JPA alone):
 * - Khóa chính: `PK(robot_id)`.
 * - Không lưu active_job_id lặp; tra Job có trạng thái đang chiếm robot.
 * - Snapshot không thay lịch sử. Không giả định sensors_ready nghĩa mọi capability đã sẵn sàng.
 * 
 * Nguồn: S0, S2, S3
 */
@Entity
@Table(name = "Robot")
@Getter
@Setter
@NoArgsConstructor
@Check(constraints = "battery_raw BETWEEN 0 AND 65535")
public class Robot {

    /** ID robot. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Id
    @Column(name = "robot_id", nullable = false)
    private UUID robot_id;

    /** Map đang được triển khai trên Pi. Tham chiếu Map.map_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "current_map_id", referencedColumnName = "map_id", nullable = true)
    private Map current_map_id;

    /** Mã robot công khai. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Column(name = "robot_code", nullable = false, unique = true, length = 64)
    private String robot_code;

    /** Tên robot. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "robot_name", nullable = false, length = 160)
    private String robot_name;

    /** MentorPi M1 cho phần cứng hiện tại. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "model", nullable = false, length = 100)
    private String model;

    /** Số sê-ri nếu có. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Column(name = "serial_number", nullable = true, unique = true, length = 100)
    private String serial_number;

    /** Phiên bản runtime Pi. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "runtime_version", nullable = true, length = 64)
    private String runtime_version;

    /** Phiên bản firmware. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "firmware_version", nullable = true, length = 64)
    private String firmware_version;

    /** Giao thức hiện tại = 1. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "protocol_version", nullable = false)
    private Integer protocol_version;

    /** Capability từ HELLO đã xác minh. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    // Structure not specified in ERD: retain raw JSON (object/array/scalar/null).
    // TODO: Define the JSON schema once the physical design is clarified.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "capabilities_json", nullable = false)
    private String capabilities_json;

    /** Hash catalog đã triển khai; kiểm trước dispatch. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "deployed_catalog_hash", nullable = true, length = 64)
    private String deployed_catalog_hash;

    /** Hash Map trên Pi; phải khớp Map.asset_hash. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "map_asset_hash", nullable = true, length = 64)
    private String map_asset_hash;

    /** OFFLINE, STARTING, IDLE, BUSY, EMERGENCY_STOP, ERROR, MAINTENANCE. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RobotStatus status;

    /** UInt16 từ driver, [0,65535]. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "battery_raw", nullable = true)
    private Integer battery_raw;

    /** UNKNOWN cho tới khi xác minh đơn vị. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "battery_unit", nullable = true, length = 32)
    private String battery_unit;

    /** Hiện phải NULL; không suy từ raw khi chưa hiệu chuẩn. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "battery_percent", nullable = true, precision = 5, scale = 2)
    private BigDecimal battery_percent;

    /** Tuổi mẫu pin được suy từ thời điểm này. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "battery_captured_at", nullable = true)
    private Instant battery_captured_at;

    /** Heartbeat gần nhất. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "last_heartbeat_at", nullable = true)
    private Instant last_heartbeat_at;

    /** Thời điểm nghiệm thu. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "commissioned_at", nullable = true)
    private Instant commissioned_at;

    /** Thời điểm tạo. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "created_at", nullable = false)
    private Instant created_at;

    /** Thời điểm cập nhật snapshot. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "updated_at", nullable = false)
    private Instant updated_at;
}
