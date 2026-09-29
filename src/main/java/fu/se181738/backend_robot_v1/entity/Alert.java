package fu.se181738.backend_robot_v1.entity;

import fu.se181738.backend_robot_v1.enums.AlertAckPolicy;
import fu.se181738.backend_robot_v1.enums.AlertSeverity;
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
 * Alert mapped from docs/logicalDiagram.md.
 * Source constraints (cross-row/policy rules are not enforced by JPA alone):
 * - Khóa chính: `PK(alert_id)`.
 * - Alert không bắt buộc có incident; hỗ trợ lỗi điều hướng và cảnh báo độc lập.
 * - require_ack=true yêu cầu expires_at; mục tiêu ACK phải được resolve thành AlertRecipient.
 * - delivered/acked tổng hợp từ AlertRecipient theo ack_policy; khác EVENT_ACK của giao thức.
 * 
 * Nguồn: S0, S3
 */
@Entity
@Table(name = "Alert")
@Getter
@Setter
@NoArgsConstructor
@Check(constraints = "require_ack = false OR expires_at IS NOT NULL")
public class Alert {

    /** ID thông báo; trả dưới tên notification_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Id
    @Column(name = "alert_id", nullable = false)
    private UUID alert_id;

    /** Sự cố liên quan. Tham chiếu EmergencyIncident.incident_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "incident_id", referencedColumnName = "incident_id", nullable = true)
    private EmergencyIncident incident_id;

    /** Detector liên quan khi chưa tạo được incident. Tham chiếu DetectionEvent.detection_event_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "detection_event_id", referencedColumnName = "detection_event_id", nullable = true)
    private DetectionEvent detection_event_id;

    /** Bước NOTIFY tạo thông báo; NULL với cảnh báo độc lập. Tham chiếu JobStep.job_step_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "job_step_id", referencedColumnName = "job_step_id", nullable = true)
    private JobStep job_step_id;

    /** Yêu cầu giao nhận/sạc/phục hồi liên quan. Tham chiếu ServiceRequest.service_request_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "service_request_id", referencedColumnName = "service_request_id", nullable = true)
    private ServiceRequest service_request_id;

    /** Vùng giám sát liên quan. Tham chiếu Zone.zone_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "zone_id", referencedColumnName = "zone_id", nullable = true)
    private Zone zone_id;

    /** SOS, DELIVERY_COMPLETED, NAVIGATION_FAILED... theo registry. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "notification_type", nullable = false, length = 100)
    private String notification_type;

    /** INFO, WARNING, CRITICAL. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false)
    private AlertSeverity severity;

    /** Thông điệp đã resolve. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "message", nullable = false, columnDefinition = "text")
    private String message;

    /** Payload theo notification_type, có schema. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    // Structure not specified in ERD: retain raw JSON (object/array/scalar/null).
    // TODO: Define the JSON schema once the physical design is clarified.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "data_json", nullable = false)
    private String data_json;

    /** Nhóm/người nhận lúc yêu cầu, dùng audit. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    // Structure not specified in ERD: retain raw JSON (object/array/scalar/null).
    // TODO: Define the JSON schema once the physical design is clarified.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "requested_recipients_json", nullable = false)
    private String requested_recipients_json;

    /** Có yêu cầu nhân viên tiếp nhận. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "require_ack", nullable = false)
    private Boolean require_ack;

    /** ANY, ALL; mặc định ANY. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique. No inferred constraint/default.
    // ERD default: ANY.
    // TODO: Clarify from physical database design
    // The ERD does not assign default generation to database/application/compiler.
    @Enumerated(EnumType.STRING)
    @Column(name = "ack_policy", nullable = false)
    private AlertAckPolicy ack_policy;

    /** Khóa chống tạo trùng; bước NOTIFY dùng job_step_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Column(name = "operation_key", nullable = false, unique = true, length = 150)
    private String operation_key;

    /** Thời điểm tạo. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "created_at", nullable = false)
    private Instant created_at;

    /** Hết thời gian giao/ACK. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "expires_at", nullable = true)
    private Instant expires_at;
}
