package fu.se181738.backend_robot_v1.entity;

import fu.se181738.backend_robot_v1.enums.DeliveryRequestTargetMode;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;

/**
 * DeliveryRequest mapped from docs/logicalDiagram.md.
 * Source constraints (cross-row/policy rules are not enforced by JPA alone):
 * - Khóa chính: `PK(service_request_id)`.
 * - XOR delivery_endpoint_id / delivery_group_id; resolved theo chế độ chọn.
 * - Cùng Map, room tương ứng endpoint; cần >= 1 DeliveryItem trước SUBMITTED/dispatch.
 * - Không coi servo published=true là đã khóa vật lý; xác nhận nhân viên lưu trong JobStep/JobEvent.
 * 
 * Nguồn: S0, S1, S3, S4
 */
@Entity
@Table(name = "DeliveryRequest")
@Getter
@Setter
@NoArgsConstructor
@Check(constraints = "(target_mode = 'ENDPOINT' AND delivery_endpoint_id IS NOT NULL AND delivery_group_id IS NULL) OR (target_mode = 'GROUP' AND delivery_endpoint_id IS NULL AND delivery_group_id IS NOT NULL)")
public class DeliveryRequest {

    /** PK/FK dùng chung; contract delivery_id tham chiếu khóa này. Tham chiếu ServiceRequest.service_request_id. */
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

    /** Map của lượt giao. Tham chiếu Map.map_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "map_id", referencedColumnName = "map_id", nullable = false)
    private Map map_id;

    /** Phòng lấy nếu có. Tham chiếu Room.room_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "pickup_room_id", referencedColumnName = "room_id", nullable = true)
    private Room pickup_room_id;

    /** Phòng giao nếu có. Tham chiếu Room.room_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "delivery_room_id", referencedColumnName = "room_id", nullable = true)
    private Room delivery_room_id;

    /** Điểm lấy cụ thể. Tham chiếu Endpoint.endpoint_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pickup_endpoint_id", referencedColumnName = "endpoint_id", nullable = false)
    private Endpoint pickup_endpoint_id;

    /** Điểm giao cố định. Tham chiếu Endpoint.endpoint_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "delivery_endpoint_id", referencedColumnName = "endpoint_id", nullable = true)
    private Endpoint delivery_endpoint_id;

    /** Nhóm điểm giao nếu dùng chế độ nhóm. Tham chiếu EndpointGroup.endpoint_group_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "delivery_group_id", referencedColumnName = "endpoint_group_id", nullable = true)
    private EndpointGroup delivery_group_id;

    /** Điểm giao đã chốt. Tham chiếu Endpoint.endpoint_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "resolved_delivery_endpoint_id", referencedColumnName = "endpoint_id", nullable = true)
    private Endpoint resolved_delivery_endpoint_id;

    /** Người gửi thực tế đã xác thực. Tham chiếu Account.account_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "sender_account_id", referencedColumnName = "account_id", nullable = true)
    private Account sender_account_id;

    /** Người nhận thực tế đã xác thực. Tham chiếu Account.account_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "receiver_account_id", referencedColumnName = "account_id", nullable = true)
    private Account receiver_account_id;

    /** ENDPOINT, GROUP. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Enumerated(EnumType.STRING)
    @Column(name = "target_mode", nullable = false)
    private DeliveryRequestTargetMode target_mode;

    /** Tên người nhận dự kiến. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "recipient_name", nullable = true, length = 160)
    private String recipient_name;

    /** Liên hệ người nhận dự kiến. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "recipient_contact", nullable = true, length = 100)
    private String recipient_contact;

    /** Luồng secure delivery hiện tại phải true. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "requires_confirmation", nullable = false)
    private Boolean requires_confirmation;

    /** Yêu cầu ghi chú; không phải số đo hay khả năng làm lạnh. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "temperature_requirement", nullable = true, columnDefinition = "text")
    private String temperature_requirement;

    /** Hạn giao. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "delivery_deadline", nullable = true)
    private Instant delivery_deadline;

    /** Nhân viên xác nhận đã nhận hàng. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "picked_up_at", nullable = true)
    private Instant picked_up_at;

    /** Nhân viên xác nhận bàn giao. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "delivered_at", nullable = true)
    private Instant delivered_at;

    /** Ghi chú. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "delivery_note", nullable = true, columnDefinition = "text")
    private String delivery_note;
}
