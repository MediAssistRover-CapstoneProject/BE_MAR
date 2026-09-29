package fu.se181738.backend_robot_v1.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * StaffProfile mapped from docs/logicalDiagram.md.
 * Source constraints (cross-row/policy rules are not enforced by JPA alone):
 * - Khóa chính: `PK(staff_profile_id)`.
 * - Không lặp full_name/email/phone đã có tại Account.
 * 
 * Nguồn: S5 + thiết kế đề xuất
 */
@Entity
@Table(name = "StaffProfile")
@Getter
@Setter
@NoArgsConstructor
public class StaffProfile {

    /** ID hồ sơ nhân viên. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Id
    @Column(name = "staff_profile_id", nullable = false)
    private UUID staff_profile_id;

    /** Mỗi tài khoản có tối đa một hồ sơ nhân viên. Tham chiếu Account.account_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", referencedColumnName = "account_id", nullable = false, unique = true)
    private Account account_id;

    /** Mã nhân viên. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Column(name = "staff_code", nullable = false, unique = true, length = 64)
    private String staff_code;

    /** Mã khoa/bộ phận theo danh mục bên ngoài; chưa là FK. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "department_code", nullable = true, length = 64)
    private String department_code;

    /** Chức danh. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "job_title", nullable = true, length = 100)
    private String job_title;

    /** Máy nhánh nội bộ. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "contact_extension", nullable = true, length = 20)
    private String contact_extension;

    /** Còn được phân công hay không. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "is_active", nullable = false)
    private Boolean is_active;

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
