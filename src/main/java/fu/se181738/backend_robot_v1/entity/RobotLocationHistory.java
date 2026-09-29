package fu.se181738.backend_robot_v1.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;

/**
 * RobotLocationHistory mapped from docs/logicalDiagram.md.
 * Source constraints (cross-row/policy rules are not enforced by JPA alone):
 * - Khóa chính: `PK(robot_location_history_id)`.
 * - Index(robot_id, captured_at); map_id gắn phiên bản bản đồ đã dùng khi đo.
 * 
 * Nguồn: S0, S3
 */
@Entity
@Table(name = "RobotLocationHistory", indexes = @Index(columnList = "robot_id, captured_at"))
@Getter
@Setter
@NoArgsConstructor
@Check(constraints = "localization_confidence BETWEEN 0 AND 1")
public class RobotLocationHistory {

    /** ID bản ghi vị trí. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Id
    @Column(name = "robot_location_history_id", nullable = false)
    private UUID robot_location_history_id;

    /** Robot được đo. Tham chiếu Robot.robot_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "robot_id", referencedColumnName = "robot_id", nullable = false)
    private Robot robot_id;

    /** Map dùng cho tọa độ. Tham chiếu Map.map_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "map_id", referencedColumnName = "map_id", nullable = false)
    private Map map_id;

    /** Job đang chạy nếu có. Tham chiếu Job.job_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "job_id", referencedColumnName = "job_id", nullable = true)
    private Job job_id;

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

    /** Hướng, radian. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "yaw_rad", nullable = false, precision = 10, scale = 6)
    private BigDecimal yaw_rad;

    /** Độ tin cậy [0,1]. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "localization_confidence", nullable = true, precision = 5, scale = 4)
    private BigDecimal localization_confidence;

    /** Mẫu còn hợp lệ theo policy. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "is_fresh", nullable = false)
    private Boolean is_fresh;

    /** Thời điểm đo tại robot. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "captured_at", nullable = false)
    private Instant captured_at;

    /** Thời điểm backend nhận. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "received_at", nullable = false)
    private Instant received_at;
}
