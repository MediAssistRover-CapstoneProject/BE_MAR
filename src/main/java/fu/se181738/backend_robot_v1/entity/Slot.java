package fu.se181738.backend_robot_v1.entity;

import fu.se181738.backend_robot_v1.enums.SlotStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;

/**
 * Slot mapped from docs/logicalDiagram.md.
 * Source constraints (cross-row/policy rules are not enforced by JPA alone):
 * - Khóa chính: `PK(slot_id)`.
 * - UQ(shift_id, sequence_no).
 * - Slot nằm trong thời gian Shift; các Slot trong cùng Shift không chồng lấn.
 * 
 * Nguồn: S5 + giả định cần chốt
 */
@Entity
@Table(name = "Slot", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"shift_id", "sequence_no"})
})
@Getter
@Setter
@NoArgsConstructor
@Check(constraints = "sequence_no >= 1")
public class Slot {

    /** ID khung thời gian. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Id
    @Column(name = "slot_id", nullable = false)
    private UUID slot_id;

    /** Phân công chứa khung này. Tham chiếu Shift.shift_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shift_id", referencedColumnName = "shift_id", nullable = false)
    private Shift shift_id;

    /** Thứ tự trong ca; >= 1. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Column(name = "sequence_no", nullable = false)
    private Integer sequence_no;

    /** Bắt đầu khung. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "starts_at", nullable = false)
    private Instant starts_at;

    /** Kết thúc khung. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "ends_at", nullable = false)
    private Instant ends_at;

    /** AVAILABLE, BLOCKED, CLOSED. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SlotStatus status;

    /** Lý do đóng hoặc hướng dẫn. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "note", nullable = true, columnDefinition = "text")
    private String note;

    /** Thời điểm tạo. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "created_at", nullable = false)
    private Instant created_at;
}
