package fu.se181738.backend_robot_v1.entity;

import fu.se181738.backend_robot_v1.enums.StepTypeExecutorKind;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * StepType mapped from docs/logicalDiagram.md.
 * Source constraints (cross-row/policy rules are not enforced by JPA alone):
 * - Khóa chính: `PK(step_type_code)`.
 * - Không có CALL_WORKFLOW, FACE_ID hoặc tự docking trong catalog này.
 * - Không sửa nghĩa contract v2 tại chỗ; phiên bản mới phải có quy trình nâng catalog.
 * 
 * Nguồn: S3
 */
@Entity
@Table(name = "StepType")
@Getter
@Setter
@NoArgsConstructor
public class StepType {

    /** MOVE, INTERACT, DATA_SERVICE, AUTHENTICATE, CONTROL_HARDWARE, HANDLE_PAYLOAD, EXECUTE_SENSING, NOTIFY, WAIT, CHECK_CONDITION, SET_CONTEXT. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Id
    @Column(name = "step_type_code", nullable = false, length = 40)
    private String step_type_code;

    /** Tên hiển thị handler. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "display_name", nullable = false, length = 120)
    private String display_name;

    /** PI, SERVER, MIXED. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Enumerated(EnumType.STRING)
    @Column(name = "executor_kind", nullable = false)
    private StepTypeExecutorKind executor_kind;

    /** Hợp đồng hiện tại = 2. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "schema_version", nullable = false)
    private Integer schema_version;

    /** JSON Schema kiểm input, cả quy tắc điều kiện. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    // Structure not specified in ERD: retain raw JSON (object/array/scalar/null).
    // TODO: Define the JSON schema once the physical design is clarified.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_schema_json", nullable = false)
    private String input_schema_json;

    /** JSON Schema kiểm data output; field null vẫn phải có khóa. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    // Structure not specified in ERD: retain raw JSON (object/array/scalar/null).
    // TODO: Define the JSON schema once the physical design is clarified.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "output_schema_json", nullable = false)
    private String output_schema_json;

    /** Danh sách capability cần hỗ trợ. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    // Structure not specified in ERD: retain raw JSON (object/array/scalar/null).
    // TODO: Define the JSON schema once the physical design is clarified.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "required_capabilities_json", nullable = false)
    private String required_capabilities_json;

    /** Handler được phép cấu hình/publish. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "is_enabled", nullable = false)
    private Boolean is_enabled;

    /** Ý nghĩa và giới hạn handler. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;
}
