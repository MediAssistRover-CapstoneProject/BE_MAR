package fu.se181738.backend_robot_v1.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;

/**
 * EndpointGroupMember mapped from docs/logicalDiagram.md.
 * Source constraints (cross-row/policy rules are not enforced by JPA alone):
 * - Khóa chính: `PK(endpoint_group_id, endpoint_id)`.
 * - PK(endpoint_group_id, endpoint_id); UQ(endpoint_group_id, sequence_no).
 * - FK(map_id, endpoint_group_id) và FK(map_id, endpoint_id) buộc thành viên cùng Map.
 * 
 * Nguồn: S0, S4
 */
@Entity
@Table(name = "EndpointGroupMember", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"endpoint_group_id", "sequence_no"})
})
@Getter
@Setter
@NoArgsConstructor
@IdClass(EndpointGroupMemberId.class)
@Check(constraints = "sequence_no >= 1")
public class EndpointGroupMember {

    // TODO: Clarify from physical database design
    // Composite FK tuples are incomplete in Composite FK notes; keep explicit single FKs only.

    /** Thành phần PK ghép. Tham chiếu EndpointGroup.endpoint_group_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "endpoint_group_id", referencedColumnName = "endpoint_group_id", nullable = false)
    private EndpointGroup endpoint_group_id;

    /** Thành phần PK ghép. Tham chiếu Endpoint.endpoint_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "endpoint_id", referencedColumnName = "endpoint_id", nullable = false)
    private Endpoint endpoint_id;

    /** Map chung để ràng buộc FK ghép. Tham chiếu Map.map_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "map_id", referencedColumnName = "map_id", nullable = false)
    private Map map_id;

    /** Thứ tự ưu tiên >= 1. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Column(name = "sequence_no", nullable = false)
    private Integer sequence_no;

    /** Bật/tắt thành viên mà không mất lịch sử cấu hình. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "is_active", nullable = false)
    private Boolean is_active;

    /** Thời điểm thêm. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "created_at", nullable = false)
    private Instant created_at;
}
