package fu.se181738.backend_robot_v1.entity;

import fu.se181738.backend_robot_v1.enums.EmergencyIncidentSeverity;
import fu.se181738.backend_robot_v1.enums.EmergencyIncidentStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;

/**
 * EmergencyIncident mapped from docs/logicalDiagram.md.
 * Source constraints (cross-row/policy rules are not enforced by JPA alone):
 * - Khóa chính: `PK(incident_id)`.
 * - UQ(detection_event_id, incident_type) khi detection_event_id không NULL.
 * - ACK/resolve actor và thời điểm phải nhất quán; tọa độ hoặc endpoint yêu cầu map_id.
 * - Nếu endpoint_id có giá trị: FK(map_id, endpoint_id) -> Endpoint(map_id, endpoint_id).
 * 
 * Nguồn: S0, S1, S3
 */
@Entity
@Table(name = "EmergencyIncident", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"detection_event_id", "incident_type"})
})
@Getter
@Setter
@NoArgsConstructor
@Check(constraints = "(x_m IS NULL AND y_m IS NULL AND endpoint_id IS NULL) OR map_id IS NOT NULL")
public class EmergencyIncident {

    // Conditional unique: UQ(detection_event_id, incident_type).
    // On the current MySQL stack, a unique tuple containing SQL NULL is not compared.
    // The source predicate excludes rows whose subject/detector/schedule key is NULL,
    // so this composite unique preserves that predicate without extra columns.
    // TODO: Clarify from physical database design
    // Confirm NULL-distinct semantics before changing the database dialect.

    /** ID sự cố. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Id
    @Column(name = "incident_id", nullable = false)
    private UUID incident_id;

    /** Nguồn detector; SOS trực tiếp có thể NULL. Tham chiếu DetectionEvent.detection_event_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "detection_event_id", referencedColumnName = "detection_event_id", nullable = true)
    private DetectionEvent detection_event_id;

    /** Robot phát hiện/tiếp nhận ban đầu. Tham chiếu Robot.robot_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "robot_id", referencedColumnName = "robot_id", nullable = true)
    private Robot robot_id;

    /** Map vị trí sự cố nếu biết. Tham chiếu Map.map_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "map_id", referencedColumnName = "map_id", nullable = true)
    private Map map_id;

    /** Điểm gần sự cố, không bắt buộc. Tham chiếu Endpoint.endpoint_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "endpoint_id", nullable = true)
    private UUID endpoint_id;

    // Read through the existing (map_id, endpoint_id) columns; write using map_id and endpoint_id.
    // Read-only joins avoid two writers for map_id. Refresh after changing either key.
    @Setter(lombok.AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumns(value = {
        @JoinColumn(name = "map_id", referencedColumnName = "map_id", nullable = true, insertable = false, updatable = false),
        @JoinColumn(name = "endpoint_id", referencedColumnName = "endpoint_id", nullable = true, insertable = false, updatable = false)
    }, foreignKey = @ForeignKey(name = "fk_incident_map_endpoint"))
    private Endpoint endpoint;

    /** Nhân viên tiếp nhận. Tham chiếu Account.account_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "acknowledged_by_account_id", referencedColumnName = "account_id", nullable = true)
    private Account acknowledged_by_account_id;

    /** Nhân viên xử lý xong. Tham chiếu Account.account_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "resolved_by_account_id", referencedColumnName = "account_id", nullable = true)
    private Account resolved_by_account_id;

    /** SOS, SUSPECTED_FALL, INTRUSION... theo policy. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Column(name = "incident_type", nullable = false, length = 64)
    private String incident_type;

    /** WARNING, CRITICAL. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false)
    private EmergencyIncidentSeverity severity;

    /** OPEN, ACKNOWLEDGED, IN_PROGRESS, RESOLVED, CLOSED. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private EmergencyIncidentStatus status;

    /** Mô tả. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "description", nullable = true, columnDefinition = "text")
    private String description;

    /** Vị trí sự cố nếu có. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "x_m", nullable = true, precision = 12, scale = 4)
    private BigDecimal x_m;

    /** Vị trí sự cố nếu có. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "y_m", nullable = true, precision = 12, scale = 4)
    private BigDecimal y_m;

    /** Thời điểm xảy ra. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "occurred_at", nullable = false)
    private Instant occurred_at;

    /** Đã tiếp nhận, chưa phải đã xử lý. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "acknowledged_at", nullable = true)
    private Instant acknowledged_at;

    /** Đã xử lý xong. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "resolved_at", nullable = true)
    private Instant resolved_at;

    /** Đóng hồ sơ. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "closed_at", nullable = true)
    private Instant closed_at;
}
