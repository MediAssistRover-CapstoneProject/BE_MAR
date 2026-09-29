package fu.se181738.backend_robot_v1.entity;

import fu.se181738.backend_robot_v1.enums.AlertRecipientChannel;
import fu.se181738.backend_robot_v1.enums.AlertRecipientDeliveryStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * AlertRecipient mapped from docs/logicalDiagram.md.
 * Source constraints (cross-row/policy rules are not enforced by JPA alone):
 * - Khóa chính: `PK(alert_recipient_id)`.
 * - UQ(alert_id, account_id, channel).
 * - ACK chỉ chấp nhận từ đúng người nhận hoặc policy ủy quyền được audit.
 * 
 * Nguồn: S3 + chuẩn hóa đề xuất
 */
@Entity
@Table(name = "AlertRecipient", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"alert_id", "account_id", "channel"})
})
@Getter
@Setter
@NoArgsConstructor
public class AlertRecipient {

    /** ID đích nhận. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Id
    @Column(name = "alert_recipient_id", nullable = false)
    private UUID alert_recipient_id;

    /** Thông báo. Tham chiếu Alert.alert_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "alert_id", referencedColumnName = "alert_id", nullable = false)
    private Alert alert_id;

    /** Người nhận đã resolve và kiểm quyền. Tham chiếu Account.account_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", referencedColumnName = "account_id", nullable = false)
    private Account account_id;

    /** WEB, MOBILE_PUSH, EMAIL; chỉ kênh đã triển khai. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false)
    private AlertRecipientChannel channel;

    /** Địa chỉ đích khi gửi; không chứa secret. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "recipient_address_snapshot", nullable = true, length = 255)
    private String recipient_address_snapshot;

    /** Vai trò làm căn cứ resolve. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "resolved_role_snapshot", nullable = true, length = 64)
    private String resolved_role_snapshot;

    /** PENDING, DELIVERED, FAILED. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false)
    private AlertRecipientDeliveryStatus delivery_status;

    /** Thời điểm gửi. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "sent_at", nullable = true)
    private Instant sent_at;

    /** Thời điểm xác nhận giao. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "delivered_at", nullable = true)
    private Instant delivered_at;

    /** Nhân viên này tiếp nhận. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "acknowledged_at", nullable = true)
    private Instant acknowledged_at;

    /** Lỗi gửi. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "error_code", nullable = true, length = 100)
    private String error_code;

    /** Thời điểm tạo. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "created_at", nullable = false)
    private Instant created_at;
}
