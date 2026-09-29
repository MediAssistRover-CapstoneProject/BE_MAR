package fu.se181738.backend_robot_v1.entity;

import fu.se181738.backend_robot_v1.enums.EndpointGroupSelectionPolicy;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * EndpointGroup mapped from docs/logicalDiagram.md.
 * Source constraints (cross-row/policy rules are not enforced by JPA alone):
 * - Khóa chính: `PK(endpoint_group_id)`.
 * - UQ(map_id, group_code); UQ(map_id, endpoint_group_id).
 * - Chọn nhóm và chọn một endpoint là hai loại ý định khác nhau.
 * 
 * Nguồn: S0, S4
 */
@Entity
@Table(name = "EndpointGroup", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"map_id", "group_code"}),
    @UniqueConstraint(columnNames = {"map_id", "endpoint_group_id"})
})
@Getter
@Setter
@NoArgsConstructor
public class EndpointGroup {

    /** ID nhóm điểm. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Id
    @Column(name = "endpoint_group_id", nullable = false)
    private UUID endpoint_group_id;

    /** Giới hạn nhóm trong một phiên bản Map. Tham chiếu Map.map_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "map_id", referencedColumnName = "map_id", nullable = false)
    private Map map_id;

    /** Mã nhóm trong Map. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Column(name = "group_code", nullable = false, length = 64)
    private String group_code;

    /** Tên nhóm. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "group_name", nullable = false, length = 160)
    private String group_name;

    /** PRIORITY, NEAREST_REACHABLE; policy do server kiểm. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Enumerated(EnumType.STRING)
    @Column(name = "selection_policy", nullable = false)
    private EndpointGroupSelectionPolicy selection_policy;

    /** Có được thử điểm khác trong nhóm khi đích bị chặn. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "allow_fallback", nullable = false)
    private Boolean allow_fallback;

    /** Mục đích nhóm. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "description", nullable = true, columnDefinition = "text")
    private String description;

    /** Cho phép sử dụng. */
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
