package fu.se181738.backend_robot_v1.entity;

import fu.se181738.backend_robot_v1.enums.ShiftStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;

/**
 * Shift mapped from docs/logicalDiagram.md.
 * Source constraints (cross-row/policy rules are not enforced by JPA alone):
 * - Khóa chính: `PK(shift_id)`.
 * - CHECK starts_at < ends_at. Không chồng ca của cùng nhân viên theo policy.
 * - Shift ở đây là phân công cá nhân; ca chung nhiều nhân viên cần mô hình khác.
 * 
 * Nguồn: S5 + giả định cần chốt
 */
@Entity
@Table(name = "Shift")
@Getter
@Setter
@NoArgsConstructor
@Check(constraints = "starts_at < ends_at")
public class Shift {

    /** ID lần phân công. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Id
    @Column(name = "shift_id", nullable = false)
    private UUID shift_id;

    /** Nhân viên được phân công. Tham chiếu StaffProfile.staff_profile_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "staff_profile_id", referencedColumnName = "staff_profile_id", nullable = false)
    private StaffProfile staff_profile_id;

    /** Phòng phụ trách. Tham chiếu Room.room_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", referencedColumnName = "room_id", nullable = false)
    private Room room_id;

    /** Mã phân công. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Column(name = "shift_code", nullable = false, unique = true, length = 64)
    private String shift_code;

    /** Tên ca. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "shift_name", nullable = false, length = 100)
    private String shift_name;

    /** Bắt đầu phân công. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "starts_at", nullable = false)
    private Instant starts_at;

    /** Kết thúc phân công. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "ends_at", nullable = false)
    private Instant ends_at;

    /** PLANNED, ACTIVE, COMPLETED, CANCELLED. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ShiftStatus status;

    /** Ghi chú phân công. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "note", nullable = true, columnDefinition = "text")
    private String note;

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
