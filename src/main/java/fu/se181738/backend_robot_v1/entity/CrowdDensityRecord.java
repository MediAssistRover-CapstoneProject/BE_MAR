package fu.se181738.backend_robot_v1.entity;

import fu.se181738.backend_robot_v1.enums.CrowdDensityRecordDensityLevel;
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
 * CrowdDensityRecord mapped from docs/logicalDiagram.md.
 * Source constraints (cross-row/policy rules are not enforced by JPA alone):
 * - Khóa chính: `PK(crowd_density_record_id)`.
 * - Checkpoint và Zone cùng Map; không suy danh tính người từ đếm người.
 * - Không gọi person_count là người/m² khi chưa có diện tích hiệu chuẩn.
 * 
 * Nguồn: S0, S3
 */
@Entity
@Table(name = "CrowdDensityRecord")
@Getter
@Setter
@NoArgsConstructor
@Check(constraints = "person_count >= 0")
@Check(constraints = "confidence_score BETWEEN 0 AND 1")
public class CrowdDensityRecord {

    /** ID lượt đo. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Id
    @Column(name = "crowd_density_record_id", nullable = false)
    private UUID crowd_density_record_id;

    /** Robot quan sát. Tham chiếu Robot.robot_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "robot_id", referencedColumnName = "robot_id", nullable = false)
    private Robot robot_id;

    /** Vùng quan sát. Tham chiếu Zone.zone_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "zone_id", referencedColumnName = "zone_id", nullable = false)
    private Zone zone_id;

    /** Điểm robot đứng quan sát. Tham chiếu Endpoint.endpoint_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "checkpoint_id", referencedColumnName = "endpoint_id", nullable = true)
    private Endpoint checkpoint_id;

    /** Bước sensing tạo mẫu. Tham chiếu JobStep.job_step_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "job_step_id", referencedColumnName = "job_step_id", nullable = true)
    private JobStep job_step_id;

    /** Số người >= 0. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "person_count", nullable = false)
    private Integer person_count;

    /** LOW, MEDIUM, HIGH; theo policy ngưỡng đã cấu hình. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Enumerated(EnumType.STRING)
    @Column(name = "density_level", nullable = true)
    private CrowdDensityRecordDensityLevel density_level;

    /** Độ tin cậy [0,1]. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "confidence_score", nullable = true, precision = 5, scale = 4)
    private BigDecimal confidence_score;

    /** Phiên bản detector. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "model_version", nullable = true, length = 100)
    private String model_version;

    /** Kết quả sensing gốc theo schema. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    // Structure not specified in ERD: retain raw JSON (object/array/scalar/null).
    // TODO: Define the JSON schema once the physical design is clarified.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metrics_json", nullable = false)
    private String metrics_json;

    /** Minh chứng. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "evidence_ref", nullable = true, columnDefinition = "text")
    private String evidence_ref;

    /** Thời điểm khung hình/lượt đo. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "captured_at", nullable = false)
    private Instant captured_at;

    /** Thời điểm lưu. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "recorded_at", nullable = false)
    private Instant recorded_at;
}
