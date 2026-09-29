package fu.se181738.backend_robot_v1.entity;

import fu.se181738.backend_robot_v1.enums.AccountStatus;
import fu.se181738.backend_robot_v1.enums.AccountType;
import fu.se181738.backend_robot_v1.enums.AuthProvider;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;

/**
 * Account mapped from docs/logicalDiagram.md.
 * Source constraints (cross-row/policy rules are not enforced by JPA alone):
 * - Khóa chính: `PK(account_id)`.
 * - UQ(auth_provider, external_subject) khi subject có giá trị.
 * - LOCAL yêu cầu password_hash; OIDC yêu cầu external_subject.
 * - Account không bắt buộc có StaffProfile; vai trò STAFF/ADMIN được kiểm theo policy.
 * 
 * Nguồn: S0, S2
 */
@Entity
@Table(name = "Account", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"auth_provider", "external_subject"})
})
@Getter
@Setter
@NoArgsConstructor
@Check(constraints = "(auth_provider <> 'LOCAL' OR password_hash IS NOT NULL) AND (auth_provider <> 'OIDC' OR external_subject IS NOT NULL)")
public class Account {

    // Conditional unique: UQ(auth_provider, external_subject).
    // On the current MySQL stack, a unique tuple containing SQL NULL is not compared.
    // The source predicate excludes rows whose subject/detector/schedule key is NULL,
    // so this composite unique preserves that predicate without extra columns.
    // TODO: Clarify from physical database design
    // Confirm NULL-distinct semantics before changing the database dialect.

    /** ID nội bộ; chuỗi UUID được dùng khi truyền giao thức. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Id
    @Column(name = "account_id", nullable = false)
    private UUID account_id;

    /** Mã quản lý tài khoản. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Column(name = "account_code", nullable = false, unique = true, length = 64)
    private String account_code;

    /** Tên đăng nhập; duy nhất sau chuẩn hóa chữ hoa/thường. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Column(name = "username", nullable = false, unique = true, length = 100)
    private String username;

    /** Tên người dùng. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "full_name", nullable = false, length = 160)
    private String full_name;

    /** Email liên hệ; không mặc định là định danh đăng nhập. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "email", nullable = true, length = 254)
    private String email;

    /** Số điện thoại. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "phone", nullable = true, length = 32)
    private String phone;

    /** PATIENT, STAFF, ADMIN; quyền theo ma trận ứng dụng. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false)
    private AccountType account_type;

    /** LOCAL hoặc OIDC; lựa chọn triển khai. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", nullable = false)
    private AuthProvider auth_provider;

    /** Chỉ lưu hash mật khẩu khi LOCAL; không lưu mật khẩu rõ. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "password_hash", nullable = true, columnDefinition = "text")
    private String password_hash;

    /** Định danh từ nhà cung cấp OIDC. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Column(name = "external_subject", nullable = true, length = 255)
    private String external_subject;

    /** ACTIVE, INACTIVE, LOCKED. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AccountStatus status;

    /** Lần đăng nhập gần nhất. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "last_login_at", nullable = true)
    private Instant last_login_at;

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
