package fu.se181738.backend_robot_v1.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * AuditLog mapped from docs/logicalDiagram.md.
 * Source constraints (cross-row/policy rules are not enforced by JPA alone):
 * - Khóa chính: `PK(audit_log_id)`.
 * - Không ghi password_hash, token, QR secret vào snapshot.
 * - entity_type + entity_id do ứng dụng kiểm; không vẽ FK giả cho tham chiếu đa hình.
 * 
 * Nguồn: S0, S2
 */
@Entity
@Table(name = "AuditLog")
@Getter
@Setter
@NoArgsConstructor
public class AuditLog {

    /** ID nhật ký. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Id
    @Column(name = "audit_log_id", nullable = false)
    private UUID audit_log_id;

    /** Người thao tác; NULL khi do hệ thống. Tham chiếu Account.account_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "account_id", referencedColumnName = "account_id", nullable = true)
    private Account account_id;

    /** Thao tác được ghi nhận. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "action_code", nullable = false, length = 80)
    private String action_code;

    /** Loại thực thể bị tác động. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "entity_type", nullable = false, length = 80)
    private String entity_type;

    /** Tham chiếu đa hình; không phải FK tới một bảng cố định. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "entity_id", nullable = true)
    private UUID entity_id;

    /** Snapshot trước thao tác; loại bỏ thông tin bí mật. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    // Structure not specified in ERD: retain raw JSON (object/array/scalar/null).
    // TODO: Define the JSON schema once the physical design is clarified.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_data_json", nullable = true)
    private String before_data_json;

    /** Snapshot sau thao tác; loại bỏ thông tin bí mật. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    // Structure not specified in ERD: retain raw JSON (object/array/scalar/null).
    // TODO: Define the JSON schema once the physical design is clarified.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_data_json", nullable = true)
    private String after_data_json;

    /** IPv4/IPv6. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "ip_address", nullable = true, length = 45)
    private String ip_address;

    /** Thông tin client. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "user_agent", nullable = true, columnDefinition = "text")
    private String user_agent;

    /** Mã truy vết request. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "correlation_id", nullable = true, length = 100)
    private String correlation_id;

    /** Thời điểm thao tác. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "occurred_at", nullable = false)
    private Instant occurred_at;
}
