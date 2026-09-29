package fu.se181738.backend_robot_v1.entity;

import fu.se181738.backend_robot_v1.enums.RobotStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * RobotStatusHistory mapped from docs/logicalDiagram.md.
 * Source constraints (cross-row/policy rules are not enforced by JPA alone):
 * - Khóa chính: `PK(robot_status_history_id)`.
 * - Append-only; cùng transaction với cập nhật Robot.status.
 * 
 * Nguồn: S0, S3
 */
@Entity
@Table(name = "RobotStatusHistory")
@Getter
@Setter
@NoArgsConstructor
public class RobotStatusHistory {

    /** ID bản ghi. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Id
    @Column(name = "robot_status_history_id", nullable = false)
    private UUID robot_status_history_id;

    /** Robot liên quan. Tham chiếu Robot.robot_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "robot_id", referencedColumnName = "robot_id", nullable = false)
    private Robot robot_id;

    /** Job liên quan nếu có. Tham chiếu Job.job_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "job_id", referencedColumnName = "job_id", nullable = true)
    private Job job_id;

    /** RobotStatus cũ; NULL với bản ghi đầu. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", nullable = true)
    private RobotStatus previous_status;

    /** RobotStatus mới. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false)
    private RobotStatus new_status;

    /** Lý do chuyển trạng thái. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "reason", nullable = true, columnDefinition = "text")
    private String reason;

    /** Mã lỗi liên quan. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "error_code", nullable = true, length = 100)
    private String error_code;

    /** Thời điểm ghi nhận. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "recorded_at", nullable = false)
    private Instant recorded_at;
}
