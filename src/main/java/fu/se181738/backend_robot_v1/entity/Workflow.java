package fu.se181738.backend_robot_v1.entity;

import fu.se181738.backend_robot_v1.enums.WorkflowStatus;
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
 * Workflow mapped from docs/logicalDiagram.md.
 * Source constraints (cross-row/policy rules are not enforced by JPA alone):
 * - Khóa chính: `PK(workflow_id)`.
 * - UQ(workflow_code, version_no). start_step phải thuộc chính workflow này.
 * - PUBLISHED bất biến; đổi cấu hình tạo version mới; compiled JSON là artifact, không là bản cấu hình song song.
 * - Task/StepTransition là nguồn graph; không lưu thêm transitions_json độc lập trong Step.
 * 
 * Nguồn: S0, S1, S3
 */
@Entity
@Table(name = "Workflow", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"workflow_code", "version_no"})
})
@Getter
@Setter
@NoArgsConstructor
@Check(constraints = "version_no >= 1")
@Check(constraints = "max_duration_sec > 0")
@Check(constraints = "max_total_visits > 0")
public class Workflow {

    /** ID của phiên bản workflow. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Id
    @Column(name = "workflow_id", nullable = false)
    private UUID workflow_id;

    /** Bước bắt đầu; bắt buộc khi publish. Tham chiếu Step.step_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "start_step_id", referencedColumnName = "step_id", nullable = true)
    private Step start_step_id;

    /** Người công bố. Tham chiếu Account.account_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "published_by_account_id", referencedColumnName = "account_id", nullable = true)
    private Account published_by_account_id;

    /** Mã nghiệp vụ của workflow. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Column(name = "workflow_code", nullable = false, length = 80)
    private String workflow_code;

    /** Phiên bản định nghĩa >= 1. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Column(name = "version_no", nullable = false)
    private Integer version_no;

    /** Tên hiển thị. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "workflow_name", nullable = false, length = 160)
    private String workflow_name;

    /** Loại yêu cầu/workflow phục vụ. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "service_type", nullable = false, length = 64)
    private String service_type;

    /** Mô tả. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "description", nullable = true, columnDefinition = "text")
    private String description;

    /** Schema input; tương đương validation_schema của WorkflowParam trong tài liệu. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    // Structure not specified in ERD: retain raw JSON (object/array/scalar/null).
    // TODO: Define the JSON schema once the physical design is clarified.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_schema_json", nullable = false)
    private String input_schema_json;

    /** Cấu hình form; tương đương ui_config của WorkflowParam. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    // Structure not specified in ERD: retain raw JSON (object/array/scalar/null).
    // TODO: Define the JSON schema once the physical design is clarified.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_ui_json", nullable = false)
    private String input_ui_json;

    /** Giá trị mặc định đã kiểm schema. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    // Structure not specified in ERD: retain raw JSON (object/array/scalar/null).
    // TODO: Define the JSON schema once the physical design is clarified.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "default_inputs_json", nullable = false)
    private String default_inputs_json;

    /** Ánh xạ kết quả cuối workflow. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    // Structure not specified in ERD: retain raw JSON (object/array/scalar/null).
    // TODO: Define the JSON schema once the physical design is clarified.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "output_mapping_json", nullable = false)
    private String output_mapping_json;

    /** Danh sách capability trước dispatch. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    // Structure not specified in ERD: retain raw JSON (object/array/scalar/null).
    // TODO: Define the JSON schema once the physical design is clarified.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "required_capabilities_json", nullable = false)
    private String required_capabilities_json;

    /** Giới hạn toàn job > 0. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "max_duration_sec", nullable = false)
    private Integer max_duration_sec;

    /** Giới hạn tổng lượt ghé bước > 0. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "max_total_visits", nullable = false)
    private Integer max_total_visits;

    /** Định dạng graph hiện tại = 3. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "graph_version", nullable = false)
    private Integer graph_version;

    /** Định dạng I/O step hiện tại = 2. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "schema_version", nullable = false)
    private Integer schema_version;

    /** Artifact được biên dịch từ Task/Step/StepTransition. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    // Structure not specified in ERD: retain raw JSON (object/array/scalar/null).
    // TODO: Define the JSON schema once the physical design is clarified.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "compiled_definition_json", nullable = true)
    private String compiled_definition_json;

    /** SHA-256 artifact; bắt buộc khi publish. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "definition_hash", nullable = true, length = 64)
    private String definition_hash;

    /** DRAFT, PUBLISHED, RETIRED. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private WorkflowStatus status;

    /** Được tạo job mới hay không. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "accepts_new_jobs", nullable = false)
    private Boolean accepts_new_jobs;

    /** Thời điểm publish. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "published_at", nullable = true)
    private Instant published_at;

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
