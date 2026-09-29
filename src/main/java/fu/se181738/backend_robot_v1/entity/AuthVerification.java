package fu.se181738.backend_robot_v1.entity;

import fu.se181738.backend_robot_v1.enums.AuthVerificationMethod;
import fu.se181738.backend_robot_v1.enums.AuthVerificationPurpose;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * AuthVerification mapped from docs/logicalDiagram.md.
 * Source constraints (cross-row/policy rules are not enforced by JPA alone):
 * - Khóa chính: `PK(verification_id)`.
 * - Các JobStep phải cùng job_id; auth step phải đúng handler AUTHENTICATE.
 * - UNLOCK kiểm job, actor, purpose, hạn dùng; không lưu QR secret trong grant.
 * - auth_job_step_id là khóa idempotency; không tạo actor giả khi xác thực thất bại.
 * 
 * Nguồn: S3 + chuẩn hóa đề xuất
 */
@Entity
@Table(name = "AuthVerification")
@Getter
@Setter
@NoArgsConstructor
public class AuthVerification {

    /** ID grant trả trong AUTHENTICATE. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Id
    @Column(name = "verification_id", nullable = false)
    private UUID verification_id;

    /** Job được cấp grant. Tham chiếu Job.job_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", referencedColumnName = "job_id", nullable = false)
    private Job job_id;

    /** Bước AUTHENTICATE cấp grant; chống tạo grant trùng. Tham chiếu JobStep.job_step_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "auth_job_step_id", referencedColumnName = "job_step_id", nullable = false, unique = true)
    private JobStep auth_job_step_id;

    /** Nhân viên đã xác thực. Tham chiếu Account.account_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_account_id", referencedColumnName = "account_id", nullable = false)
    private Account actor_account_id;

    /** Bước UNLOCK sử dụng grant nếu dùng policy một lần. Tham chiếu JobStep.job_step_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @OneToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "used_by_job_step_id", referencedColumnName = "job_step_id", nullable = true, unique = true)
    private JobStep used_by_job_step_id;

    /** QR_CODE, STAFF_CONFIRMATION. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false)
    private AuthVerificationMethod method;

    /** DELIVERY_SENDER, DELIVERY_RECEIVER. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false)
    private AuthVerificationPurpose purpose;

    /** DELIVERY_SEND hoặc DELIVERY_RECEIVE theo policy. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "required_permission", nullable = false, length = 80)
    private String required_permission;

    /** Thời điểm cấp. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "issued_at", nullable = false)
    private Instant issued_at;

    /** Hạn dùng grant. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "valid_until", nullable = false)
    private Instant valid_until;

    /** Thời điểm thu hồi. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "revoked_at", nullable = true)
    private Instant revoked_at;
}
