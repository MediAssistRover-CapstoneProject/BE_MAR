package fu.se181738.backend_robot_v1.entity;

import fu.se181738.backend_robot_v1.enums.DetectionEventEventType;
import fu.se181738.backend_robot_v1.enums.DetectionEventLocationBasis;
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
 * DetectionEvent mapped from docs/logicalDiagram.md.
 * Source constraints (cross-row/policy rules are not enforced by JPA alone):
 * - Khóa chính: `PK(detection_event_id)`.
 * - x_m/y_m cùng có hoặc cùng NULL; zone phải cùng Map.
 * - Một event có thể không tạo incident nếu không đạt ngưỡng.
 * 
 * Nguồn: S0, S1, S3
 */
@Entity
@Table(name = "DetectionEvent")
@Getter
@Setter
@NoArgsConstructor
@Check(constraints = "(x_m IS NULL AND y_m IS NULL) OR (x_m IS NOT NULL AND y_m IS NOT NULL)")
@Check(constraints = "confidence_score BETWEEN 0 AND 1")
public class DetectionEvent {

    /** ID sự kiện detector; dùng chống trùng. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Id
    @Column(name = "detection_event_id", nullable = false)
    private UUID detection_event_id;

    /** Robot nguồn. Tham chiếu Robot.robot_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "robot_id", referencedColumnName = "robot_id", nullable = false)
    private Robot robot_id;

    /** Map nguồn. Tham chiếu Map.map_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "map_id", referencedColumnName = "map_id", nullable = false)
    private Map map_id;

    /** Vùng quan sát nếu xác định. Tham chiếu Zone.zone_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "zone_id", referencedColumnName = "zone_id", nullable = true)
    private Zone zone_id;

    /** SUSPECTED_FALL, RESTRICTED_INTRUSION, SOS_SIGNAL, OTHER. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private DetectionEventEventType event_type;

    /** Độ tin cậy [0,1]. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "confidence_score", nullable = true, precision = 5, scale = 4)
    private BigDecimal confidence_score;

    /** Tọa độ X của đối tượng hoặc robot theo location_basis. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "x_m", nullable = true, precision = 12, scale = 4)
    private BigDecimal x_m;

    /** Tọa độ Y, mét. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "y_m", nullable = true, precision = 12, scale = 4)
    private BigDecimal y_m;

    /** OBJECT, ROBOT, UNKNOWN. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Enumerated(EnumType.STRING)
    @Column(name = "location_basis", nullable = false)
    private DetectionEventLocationBasis location_basis;

    /** Phiên bản mô hình nhận biết. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "model_version", nullable = true, length = 100)
    private String model_version;

    /** Chi tiết kết quả detector. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    // Structure not specified in ERD: retain raw JSON (object/array/scalar/null).
    // TODO: Define the JSON schema once the physical design is clarified.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "event_payload_json", nullable = false)
    private String event_payload_json;

    /** Mã/URI minh chứng, không ảnh base64. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "evidence_ref", nullable = true, columnDefinition = "text")
    private String evidence_ref;

    /** Thời điểm sự kiện. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "detected_at", nullable = false)
    private Instant detected_at;

    /** Thời điểm backend nhận. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "received_at", nullable = false)
    private Instant received_at;
}
