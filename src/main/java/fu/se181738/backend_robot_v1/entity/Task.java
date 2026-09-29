package fu.se181738.backend_robot_v1.entity;

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
 * Task mapped from docs/logicalDiagram.md.
 * Source constraints (cross-row/policy rules are not enforced by JPA alone):
 * - Khóa chính: `PK(task_id)`.
 * - UQ(workflow_id, task_code); UQ(workflow_id, task_id).
 * - Task không tự retry nguyên nhóm và không tự mở khóa lại; điều khiển theo graph Step.
 * 
 * Nguồn: S0, S1, S3
 */
@Entity
@Table(name = "Task", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"workflow_id", "task_code"}),
    @UniqueConstraint(columnNames = {"workflow_id", "task_id"})
})
@Getter
@Setter
@NoArgsConstructor
@Check(constraints = "sequence_no >= 1")
public class Task {

    /** ID task định nghĩa. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Id
    @Column(name = "task_id", nullable = false)
    private UUID task_id;

    /** Workflow chứa task. Tham chiếu Workflow.workflow_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workflow_id", referencedColumnName = "workflow_id", nullable = false)
    private Workflow workflow_id;

    /** Mã task trong Workflow. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Column(name = "task_code", nullable = false, length = 80)
    private String task_code;

    /** Tên task. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "task_name", nullable = false, length = 160)
    private String task_name;

    /** Thứ tự hiển thị >= 1; không quyết định graph. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "sequence_no", nullable = false)
    private Integer sequence_no;

    /** Mô tả nhóm bước. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "description", nullable = true, columnDefinition = "text")
    private String description;

    /** Cấu hình hiển thị/nghiệp vụ đã đăng ký. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    // Structure not specified in ERD: retain raw JSON (object/array/scalar/null).
    // TODO: Define the JSON schema once the physical design is clarified.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config_json", nullable = false)
    private String config_json;

    /** Thời điểm tạo. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "created_at", nullable = false)
    private Instant created_at;
}
