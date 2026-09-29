package fu.se181738.backend_robot_v1.entity;

import fu.se181738.backend_robot_v1.enums.StepTransitionOutcome;
import fu.se181738.backend_robot_v1.enums.StepTransitionTerminalStatus;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * StepTransition mapped from docs/logicalDiagram.md.
 * Source constraints (cross-row/policy rules are not enforced by JPA alone):
 * - Khóa chính: `PK(transition_id)`.
 * - UQ(from_step_id, outcome, priority); tối đa một default mỗi (from_step_id,outcome).
 * - XOR(to_step_id, terminal_status); default không condition, nhánh khác phải có condition.
 * - FK ghép buộc from/to cùng workflow; compiler kiểm start, reachable, fallback và giới hạn loop.
 * 
 * Nguồn: S1, S3 + chuẩn hóa đề xuất
 */
@Entity
@Table(name = "StepTransition", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"from_step_id", "outcome", "priority"})
})
@Getter
@Setter
@NoArgsConstructor
@Check(constraints = "(to_step_id IS NULL AND terminal_status IS NOT NULL) OR (to_step_id IS NOT NULL AND terminal_status IS NULL)")
@Check(constraints = "(is_default = true AND condition_json IS NULL) OR (is_default = false AND condition_json IS NOT NULL)")
@Check(constraints = "priority >= 1")
public class StepTransition {

    // TODO: Clarify from physical database design
    // At most one is_default=true per (from_step_id, outcome); requires conditional uniqueness.

    // TODO: Clarify from physical database design
    // Composite FK tuples are incomplete in Composite FK notes; keep explicit single FKs only.

    /** ID cạnh graph. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Id
    @Column(name = "transition_id", nullable = false)
    private UUID transition_id;

    /** Workflow của cạnh. Tham chiếu Workflow.workflow_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workflow_id", referencedColumnName = "workflow_id", nullable = false)
    private Workflow workflow_id;

    /** Bước nguồn. Tham chiếu Step.step_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_step_id", referencedColumnName = "step_id", nullable = false)
    private Step from_step_id;

    /** Bước đích; NULL nếu kết thúc job. Tham chiếu Step.step_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "to_step_id", referencedColumnName = "step_id", nullable = true)
    private Step to_step_id;

    /** SUCCESS, FAILURE, TIMEOUT. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false)
    private StepTransitionOutcome outcome;

    /** SAFE_EXPR_V1; xét output, ví dụ confirmed=true. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    // Structure not specified in ERD: retain raw JSON (object/array/scalar/null).
    // TODO: Define the JSON schema once the physical design is clarified.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "condition_json", nullable = true)
    private String condition_json;

    /** Số nhỏ xét trước; >= 1. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Column(name = "priority", nullable = false)
    private Integer priority;

    /** Nhánh cuối không điều kiện của outcome. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "is_default", nullable = false)
    private Boolean is_default;

    /** SUCCEEDED, FAILED, CANCELLED, RECOVERY_REQUIRED. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Enumerated(EnumType.STRING)
    @Column(name = "terminal_status", nullable = true)
    private StepTransitionTerminalStatus terminal_status;
}
