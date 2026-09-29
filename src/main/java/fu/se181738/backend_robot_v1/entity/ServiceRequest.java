package fu.se181738.backend_robot_v1.entity;

import fu.se181738.backend_robot_v1.enums.ServiceRequestRequestType;
import fu.se181738.backend_robot_v1.enums.ServiceRequestSourceType;
import fu.se181738.backend_robot_v1.enums.ServiceRequestStatus;
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
 * ServiceRequest mapped from docs/logicalDiagram.md.
 * Source constraints (cross-row/policy rules are not enforced by JPA alone):
 * - Khóa chính: `PK(service_request_id)`.
 * - NAVIGATION/ESCORT/DELIVERY có đúng một bảng con tương ứng khi SUBMITTED; loại trừ hai bảng còn lại.
 * - Các loại khác có thể không có ba bảng con; không tạo FK 1-1 bắt buộc tất cả.
 * - Một request có nhiều Job theo thời gian; trạng thái request được tổng hợp theo nghiệp vụ.
 * 
 * Nguồn: S0, S1, S3
 */
@Entity
@Table(name = "ServiceRequest")
@Getter
@Setter
@NoArgsConstructor
@Check(constraints = "priority BETWEEN 0 AND 100")
public class ServiceRequest {

    /** ID yêu cầu. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Id
    @Column(name = "service_request_id", nullable = false)
    private UUID service_request_id;

    /** Người tạo; NULL với guest/system. Tham chiếu Account.account_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "requested_by_account_id", referencedColumnName = "account_id", nullable = true)
    private Account requested_by_account_id;

    /** Robot được yêu cầu, ví dụ sạc; chưa phải phân công job. Tham chiếu Robot.robot_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "requested_robot_id", referencedColumnName = "robot_id", nullable = true)
    private Robot requested_robot_id;

    /** SOS/sự cố liên quan. Tham chiếu EmergencyIncident.incident_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "related_incident_id", referencedColumnName = "incident_id", nullable = true)
    private EmergencyIncident related_incident_id;

    /** Mã tra cứu yêu cầu. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Column(name = "request_code", nullable = false, unique = true, length = 64)
    private String request_code;

    /** NAVIGATION, ESCORT, DELIVERY, INFORMATION, CROWD_MONITORING, SOS, FALL_RESPONSE, RESTRICTED_MONITORING, MANUAL_CHARGE, RECOVERY, STARTUP. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Enumerated(EnumType.STRING)
    @Column(name = "request_type", nullable = false)
    private ServiceRequestRequestType request_type;

    /** STAFF_UI, ROBOT_UI, VOICE, SYSTEM, DETECTOR. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    private ServiceRequestSourceType source_type;

    /** [0,100]; số lớn ưu tiên hơn. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "priority", nullable = false)
    private Integer priority;

    /** DRAFT, SUBMITTED, QUEUED, IN_PROGRESS, COMPLETED, FAILED, CANCELLED, RECOVERY_REQUIRED. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ServiceRequestStatus status;

    /** Ghi chú chung. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "request_note", nullable = true, columnDefinition = "text")
    private String request_note;

    /** Dữ liệu riêng các loại không có bảng con; validate theo request_type. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    // Structure not specified in ERD: retain raw JSON (object/array/scalar/null).
    // TODO: Define the JSON schema once the physical design is clarified.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_data_json", nullable = false)
    private String request_data_json;

    /** Khóa chống gửi trùng của lần tạo request. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 150)
    private String idempotency_key;

    /** Thời điểm tạo yêu cầu. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "requested_at", nullable = false)
    private Instant requested_at;

    /** Thời điểm mong muốn phục vụ. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "scheduled_at", nullable = true)
    private Instant scheduled_at;

    /** Thời điểm hủy. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "cancelled_at", nullable = true)
    private Instant cancelled_at;

    /** Thời điểm hoàn thành thành công. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "completed_at", nullable = true)
    private Instant completed_at;

    /** Thời điểm cập nhật. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "updated_at", nullable = false)
    private Instant updated_at;
}
