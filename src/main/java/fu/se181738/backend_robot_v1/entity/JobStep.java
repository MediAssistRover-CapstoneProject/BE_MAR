package fu.se181738.backend_robot_v1.entity;

import fu.se181738.backend_robot_v1.enums.JobStepStatus;
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
 * JobStep mapped from docs/logicalDiagram.md.
 * Source constraints (cross-row/policy rules are not enforced by JPA alone):
 * - Khóa chính: `PK(job_step_id)`.
 * - UQ(job_task_id, step_id, visit_no); UQ(job_task_id, execution_sequence_no).
 * - Step.task_id phải bằng JobTask.task_id; task và job cùng phiên bản Workflow.
 * - Không UNIQUE(job_id, step_id): vòng lặp phải giữ nhiều lượt.
 * - Mỗi attempt có event riêng; default max_attempts=1, không giả định retry đồng nghĩa chạy lại servo.
 * 
 * Nguồn: S0, S1, S3
 */
@Entity
@Table(name = "JobStep", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"job_task_id", "step_id", "visit_no"}),
    @UniqueConstraint(columnNames = {"job_task_id", "execution_sequence_no"})
})
@Getter
@Setter
@NoArgsConstructor
@Check(constraints = "visit_no >= 1")
@Check(constraints = "execution_sequence_no >= 1")
@Check(constraints = "attempt_no >= 1")
@Check(constraints = "timeout_sec_snapshot > 0")
public class JobStep {

    /** ID lượt ghé; là operation_key mặc định của dịch vụ bước. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Id
    @Column(name = "job_step_id", nullable = false)
    private UUID job_step_id;

    /** Task thực thi chứa bước. Tham chiếu JobTask.job_task_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_task_id", referencedColumnName = "job_task_id", nullable = false)
    private JobTask job_task_id;

    /** Bước định nghĩa. Tham chiếu Step.step_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "step_id", referencedColumnName = "step_id", nullable = false)
    private Step step_id;

    /** Lần ghé step trong Job >= 1. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Column(name = "visit_no", nullable = false)
    private Integer visit_no;

    /** Thứ tự phát sinh lượt thực thi trong JobTask >= 1. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Column(name = "execution_sequence_no", nullable = false)
    private Integer execution_sequence_no;

    /** Mã Step lúc chạy. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "step_code_snapshot", nullable = false, length = 80)
    private String step_code_snapshot;

    /** Một trong 11 handler tại lúc chạy. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "step_type_snapshot", nullable = false, length = 40)
    private String step_type_snapshot;

    /** Params thực tế đã resolve; immutable khi bước bắt đầu. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    // Structure not specified in ERD: retain raw JSON (object/array/scalar/null).
    // TODO: Define the JSON schema once the physical design is clarified.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "resolved_inputs_json", nullable = false)
    private String resolved_inputs_json;

    /** Data output nguyên hợp đồng, gồm cả các khóa có giá trị null. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    // Structure not specified in ERD: retain raw JSON (object/array/scalar/null).
    // TODO: Define the JSON schema once the physical design is clarified.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_json", nullable = true)
    private String result_json;

    /** PENDING, RUNNING, SUCCEEDED, FAILED, TIMED_OUT, CANCELLED, RETRYING. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private JobStepStatus status;

    /** Số lần thử hiện tại trong lượt ghé, tính từ 1. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "attempt_no", nullable = false)
    private Integer attempt_no;

    /** Ổn định qua retry của cùng lượt ghé; mặc định job_step_id. */
    // ERD default: job_step_id.
    // TODO: Clarify from physical database design
    // The ERD does not assign default generation to database/application/compiler.
    @Column(name = "operation_key", nullable = false, unique = true, length = 150)
    private String operation_key;

    /** Timeout đã chốt > 0. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "timeout_sec_snapshot", nullable = false)
    private Integer timeout_sec_snapshot;

    /** Bắt đầu lượt. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "started_at", nullable = true)
    private Instant started_at;

    /** Kết thúc lượt. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "ended_at", nullable = true)
    private Instant ended_at;

    /** Mã lỗi máy đọc. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "error_code", nullable = true, length = 100)
    private String error_code;

    /** Thông tin tra cứu; không dùng để rẽ nhánh. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "error_message", nullable = true, columnDefinition = "text")
    private String error_message;

    /** Chi tiết lỗi hợp đồng. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    // Structure not specified in ERD: retain raw JSON (object/array/scalar/null).
    // TODO: Define the JSON schema once the physical design is clarified.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "error_details_json", nullable = true)
    private String error_details_json;
}
