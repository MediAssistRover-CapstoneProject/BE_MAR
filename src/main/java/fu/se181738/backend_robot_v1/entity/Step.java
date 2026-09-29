package fu.se181738.backend_robot_v1.entity;

import fu.se181738.backend_robot_v1.enums.StepAuditLevel;
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
 * Step mapped from docs/logicalDiagram.md.
 * Source constraints (cross-row/policy rules are not enforced by JPA alone):
 * - Khóa chính: `PK(step_id)`.
 * - UQ(workflow_id, step_code); UQ(workflow_id, step_id).
 * - FK(workflow_id, task_id) -> Task(workflow_id, task_id).
 * - Vòng lặp do StepTransition; không dùng sequence_no thay graph.
 * - Không tự retry hành động vật lý khi mất ACK; mặc định max_attempts=1.
 * 
 * Nguồn: S0, S1, S3
 */
@Entity
@Table(name = "Step", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"workflow_id", "step_code"}),
    @UniqueConstraint(columnNames = {"workflow_id", "step_id"})
})
@Getter
@Setter
@NoArgsConstructor
@Check(constraints = "timeout_sec > 0")
@Check(constraints = "retry_delay_sec >= 0")
@Check(constraints = "max_visits > 0")
public class Step {

    /** ID bước định nghĩa. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Id
    @Column(name = "step_id", nullable = false)
    private UUID step_id;

    /** Workflow chứa bước; hỗ trợ FK ghép. Tham chiếu Workflow.workflow_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workflow_id", referencedColumnName = "workflow_id", nullable = false)
    private Workflow workflow_id;

    /** Nhóm task của bước. Tham chiếu Task.task_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "task_id", nullable = false)
    private UUID task_id;

    // Read through the existing (workflow_id, task_id) columns; write using workflow_id and task_id.
    // Read-only joins avoid two writers for workflow_id. Refresh after changing either key.
    @Setter(lombok.AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns(value = {
        @JoinColumn(name = "workflow_id", referencedColumnName = "workflow_id", nullable = false, insertable = false, updatable = false),
        @JoinColumn(name = "task_id", referencedColumnName = "task_id", nullable = false, insertable = false, updatable = false)
    }, foreignKey = @ForeignKey(name = "fk_step_workflow_task"))
    private Task task;

    /** Một trong 11 handler. Tham chiếu StepType.step_type_code. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "step_type_code", referencedColumnName = "step_type_code", nullable = false)
    private StepType step_type_code;

    /** Mã duy nhất trong Workflow; dùng trong /outputs/STEP_CODE. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Column(name = "step_code", nullable = false, length = 80)
    private String step_code;

    /** Tên hiển thị. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "step_name", nullable = false, length = 160)
    private String step_name;

    /** Thứ tự hiển thị trong task. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "sequence_no", nullable = false)
    private Integer sequence_no;

    /** Mô tả hành động. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "description", nullable = true, columnDefinition = "text")
    private String description;

    /** Chỉ được thay đổi khi workflow là DRAFT. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "is_enabled", nullable = false)
    private Boolean is_enabled;

    /** Hằng và $ref được đăng ký; hợp đồng params của handler. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    // Structure not specified in ERD: retain raw JSON (object/array/scalar/null).
    // TODO: Define the JSON schema once the physical design is clarified.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "params_json", nullable = false)
    private String params_json;

    /** Binding input/outputs/vars; ưu tiên binding đè hằng theo compiler. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    // Structure not specified in ERD: retain raw JSON (object/array/scalar/null).
    // TODO: Define the JSON schema once the physical design is clarified.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_mapping_json", nullable = false)
    private String input_mapping_json;

    /** Timeout một lượt thực thi > 0. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "timeout_sec", nullable = false)
    private Integer timeout_sec;

    /** Tổng số lần thử, gồm lần đầu; bản mặc định = 1. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique. No inferred constraint/default.
    // ERD default: 1.
    // TODO: Clarify from physical database design
    // The ERD does not assign default generation to database/application/compiler.
    @Column(name = "max_attempts", nullable = false)
    private Integer max_attempts;

    /** Mặc định = 0; >= 0. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique. No inferred constraint/default.
    // ERD default: 0.
    // TODO: Clarify from physical database design
    // The ERD does not assign default generation to database/application/compiler.
    @Column(name = "retry_delay_sec", nullable = false)
    private Integer retry_delay_sec;

    /** Mảng mã lỗi được phép retry; bản mặc định []. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique. No inferred constraint/default.
    // ERD default: [].
    // TODO: Clarify from physical database design
    // The ERD does not assign default generation to database/application/compiler.
    // Structure not specified in ERD: retain raw JSON (object/array/scalar/null).
    // TODO: Define the JSON schema once the physical design is clarified.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "retry_codes_json", nullable = false)
    private String retry_codes_json;

    /** Giới hạn vòng lặp ghé bước; > 0. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "max_visits", nullable = false)
    private Integer max_visits;

    /** BASIC, FULL; bước xác thực/giao nhận FULL. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Enumerated(EnumType.STRING)
    @Column(name = "audit_level", nullable = false)
    private StepAuditLevel audit_level;

    /** Thời điểm tạo. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "created_at", nullable = false)
    private Instant created_at;
}
