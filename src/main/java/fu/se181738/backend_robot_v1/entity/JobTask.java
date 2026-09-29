package fu.se181738.backend_robot_v1.entity;

import fu.se181738.backend_robot_v1.enums.JobTaskStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * JobTask mapped from docs/logicalDiagram.md.
 * Source constraints (cross-row/policy rules are not enforced by JPA alone):
 * - Khóa chính: `PK(job_task_id)`.
 * - UQ(job_id, task_id); task.workflow_id phải bằng job.workflow_id.
 * - Một JobTask chứa nhiều JobStep/lượt ghé; không ghi đè lượt lặp cũ.
 * 
 * Nguồn: S0, S1, S3
 */
@Entity
@Table(name = "JobTask", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"job_id", "task_id"})
})
@Getter
@Setter
@NoArgsConstructor
@Check(constraints = "sequence_no >= 1")
public class JobTask {

    /** ID task thực thi. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Id
    @Column(name = "job_task_id", nullable = false)
    private UUID job_task_id;

    /** Lần chạy chứa task. Tham chiếu Job.job_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", referencedColumnName = "job_id", nullable = false)
    private Job job_id;

    /** Task cấu hình. Tham chiếu Task.task_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", referencedColumnName = "task_id", nullable = false)
    private Task task_id;

    /** Thứ tự hiển thị >= 1. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "sequence_no", nullable = false)
    private Integer sequence_no;

    /** Tên task tại lúc tạo job. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "task_name_snapshot", nullable = false, length = 160)
    private String task_name_snapshot;

    /** Cấu hình task tại lúc tạo job. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    // Structure not specified in ERD: retain raw JSON (object/array/scalar/null).
    // TODO: Define the JSON schema once the physical design is clarified.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config_snapshot_json", nullable = false)
    private String config_snapshot_json;

    /** PENDING, RUNNING, SUCCEEDED, FAILED, CANCELLED, NOT_VISITED. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private JobTaskStatus status;

    /** Lần bắt đầu đầu tiên. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "started_at", nullable = true)
    private Instant started_at;

    /** Thời điểm kết thúc theo lần chạy. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "ended_at", nullable = true)
    private Instant ended_at;

    /** Mã lỗi nếu có. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "error_code", nullable = true, length = 100)
    private String error_code;

    /** Mô tả lỗi. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "error_message", nullable = true, columnDefinition = "text")
    private String error_message;
}
