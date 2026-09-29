package fu.se181738.backend_robot_v1.entity;

import fu.se181738.backend_robot_v1.enums.SystemSettingDataType;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * SystemSetting mapped from docs/logicalDiagram.md.
 * Source constraints (cross-row/policy rules are not enforced by JPA alone):
 * - Khóa chính: `PK(system_setting_id)`.
 * - Validate setting_value_json theo setting_key và data_type.
 * - Secret và thông tin đăng nhập dịch vụ nằm trong secret/config store của triển khai.
 * 
 * Nguồn: S0, S2
 */
@Entity
@Table(name = "SystemSetting")
@Getter
@Setter
@NoArgsConstructor
public class SystemSetting {

    /** ID cấu hình. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Id
    @Column(name = "system_setting_id", nullable = false)
    private UUID system_setting_id;

    /** Người cập nhật; NULL với seed hệ thống. Tham chiếu Account.account_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "updated_by_account_id", referencedColumnName = "account_id", nullable = true)
    private Account updated_by_account_id;

    /** Tên khóa cấu hình. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Column(name = "setting_key", nullable = false, unique = true, length = 100)
    private String setting_key;

    /** Giá trị JSON có kiểu. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    // Structure not specified in ERD: retain raw JSON (object/array/scalar/null).
    // TODO: Define the JSON schema once the physical design is clarified.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "setting_value_json", nullable = false)
    private String setting_value_json;

    /** STRING, NUMBER, BOOLEAN, OBJECT, ARRAY. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Enumerated(EnumType.STRING)
    @Column(name = "data_type", nullable = false)
    private SystemSettingDataType data_type;

    /** Nhóm cấu hình. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "category", nullable = false, length = 64)
    private String category;

    /** Ý nghĩa của cấu hình. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "description", nullable = true, columnDefinition = "text")
    private String description;

    /** Mặc định false; không dùng cho secret. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique. No inferred constraint/default.
    // ERD default: false.
    // TODO: Clarify from physical database design
    // The ERD does not assign default generation to database/application/compiler.
    @Column(name = "is_public", nullable = false)
    private Boolean is_public;

    /** Thời điểm cập nhật. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "updated_at", nullable = false)
    private Instant updated_at;
}
