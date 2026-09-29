package fu.se181738.backend_robot_v1.entity;

import fu.se181738.backend_robot_v1.enums.EscortRequestEscortMode;
import fu.se181738.backend_robot_v1.enums.EscortRequestTargetMode;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;

/**
 * EscortRequest mapped from docs/logicalDiagram.md.
 * Source constraints (cross-row/policy rules are not enforced by JPA alone):
 * - Khóa chính: `PK(service_request_id)`.
 * - Quy tắc XOR và cùng Map như NavigationRequest; nguồn/đích Room phải phù hợp Endpoint.
 * - Không bật PATIENT_FOLLOW, auto-return hoặc tự docking chỉ bằng thêm field.
 * 
 * Nguồn: S0, S1, S3, S4
 */
@Entity
@Table(name = "EscortRequest")
@Getter
@Setter
@NoArgsConstructor
@Check(constraints = "(target_mode = 'ENDPOINT' AND destination_endpoint_id IS NOT NULL AND destination_group_id IS NULL) OR (target_mode = 'GROUP' AND destination_endpoint_id IS NULL AND destination_group_id IS NOT NULL)")
@Check(constraints = "walking_speed_mps > 0 AND walking_speed_mps <= 0.3")
@Check(constraints = "confirmation_timeout_sec > 0")
public class EscortRequest {

    /** PK/FK dùng chung. Tham chiếu ServiceRequest.service_request_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Id
    @Column(name = "service_request_id", nullable = false)
    private UUID service_request_id;

    // Same service_request_id column; this association does not add a database field.
    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_request_id", referencedColumnName = "service_request_id", nullable = false)
    private ServiceRequest serviceRequest;

    /** Map của yêu cầu. Tham chiếu Map.map_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "map_id", referencedColumnName = "map_id", nullable = false)
    private Map map_id;

    /** Phòng xuất phát nếu có. Tham chiếu Room.room_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "origin_room_id", referencedColumnName = "room_id", nullable = true)
    private Room origin_room_id;

    /** Phòng đích nếu có. Tham chiếu Room.room_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "destination_room_id", referencedColumnName = "room_id", nullable = true)
    private Room destination_room_id;

    /** Điểm đón nếu nghiệp vụ xác định. Tham chiếu Endpoint.endpoint_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "origin_endpoint_id", referencedColumnName = "endpoint_id", nullable = true)
    private Endpoint origin_endpoint_id;

    /** Đích cố định. Tham chiếu Endpoint.endpoint_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "destination_endpoint_id", referencedColumnName = "endpoint_id", nullable = true)
    private Endpoint destination_endpoint_id;

    /** Nhóm đích. Tham chiếu EndpointGroup.endpoint_group_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "destination_group_id", referencedColumnName = "endpoint_group_id", nullable = true)
    private EndpointGroup destination_group_id;

    /** Đích đã chọn từ ý định. Tham chiếu Endpoint.endpoint_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "resolved_endpoint_id", referencedColumnName = "endpoint_id", nullable = true)
    private Endpoint resolved_endpoint_id;

    /** ENDPOINT, GROUP. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Enumerated(EnumType.STRING)
    @Column(name = "target_mode", nullable = false)
    private EscortRequestTargetMode target_mode;

    /** Tham chiếu ngoài hệ thống; không FK tới bảng Patient không tồn tại. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "patient_reference", nullable = true, length = 100)
    private String patient_reference;

    /** GUIDE_TO_TARGET cho phạm vi phần cứng hiện tại. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Enumerated(EnumType.STRING)
    @Column(name = "escort_mode", nullable = false)
    private EscortRequestEscortMode escort_mode;

    /** > 0, <= 0.3 và không vượt trần cục bộ. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "walking_speed_mps", nullable = false, precision = 5, scale = 3)
    private BigDecimal walking_speed_mps;

    /** Giới hạn chờ xác nhận điểm đến > 0. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "confirmation_timeout_sec", nullable = false)
    private Integer confirmation_timeout_sec;

    /** Ghi chú hộ tống. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "escort_note", nullable = true, columnDefinition = "text")
    private String escort_note;
}
