package fu.se181738.backend_robot_v1.entity;

import fu.se181738.backend_robot_v1.enums.NavigationRequestNavigationMode;
import fu.se181738.backend_robot_v1.enums.NavigationRequestTargetMode;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;

/**
 * NavigationRequest mapped from docs/logicalDiagram.md.
 * Source constraints (cross-row/policy rules are not enforced by JPA alone):
 * - Khóa chính: `PK(service_request_id)`.
 * - XOR destination_endpoint_id / destination_group_id theo target_mode.
 * - Mọi endpoint/group cùng map_id; room_id phải khớp endpoint nếu được chọn theo phòng.
 * - ENDPOINT: resolved bằng đích đã chọn. GROUP: resolved phải là thành viên đang active tại lúc resolve.
 * 
 * Nguồn: S0, S1, S4
 */
@Entity
@Table(name = "NavigationRequest")
@Getter
@Setter
@NoArgsConstructor
@Check(constraints = "(target_mode = 'ENDPOINT' AND destination_endpoint_id IS NOT NULL AND destination_group_id IS NULL) OR (target_mode = 'GROUP' AND destination_endpoint_id IS NULL AND destination_group_id IS NOT NULL)")
@Check(constraints = "passenger_count >= 1")
public class NavigationRequest {

    /** PK dùng chung với request gốc. Tham chiếu ServiceRequest.service_request_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Id
    @Column(name = "service_request_id", nullable = false)
    private UUID service_request_id;

    // Same service_request_id column; this association does not add a database field.
    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_request_id", referencedColumnName = "service_request_id", nullable = false)
    private ServiceRequest serviceRequest;

    /** Map của yêu cầu. Tham chiếu Map.map_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "map_id", referencedColumnName = "map_id", nullable = false)
    private Map map_id;

    /** Phòng đích trên UI nếu có. Tham chiếu Room.room_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "room_id", referencedColumnName = "room_id", nullable = true)
    private Room room_id;

    /** Đích cố định người dùng chọn. Tham chiếu Endpoint.endpoint_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "destination_endpoint_id", referencedColumnName = "endpoint_id", nullable = true)
    private Endpoint destination_endpoint_id;

    /** Nhóm đích; hệ thống chọn điểm phù hợp. Tham chiếu EndpointGroup.endpoint_group_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "destination_group_id", referencedColumnName = "endpoint_group_id", nullable = true)
    private EndpointGroup destination_group_id;

    /** Điểm đã chốt trước dispatch; không thay ý định gốc. Tham chiếu Endpoint.endpoint_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "resolved_endpoint_id", referencedColumnName = "endpoint_id", nullable = true)
    private Endpoint resolved_endpoint_id;

    /** ENDPOINT hoặc GROUP. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Enumerated(EnumType.STRING)
    @Column(name = "target_mode", nullable = false)
    private NavigationRequestTargetMode target_mode;

    /** NORMAL hoặc ESCORT theo MOVE. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Enumerated(EnumType.STRING)
    @Column(name = "navigation_mode", nullable = false)
    private NavigationRequestNavigationMode navigation_mode;

    /** Nhu cầu tiếp cận. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "accessibility_need", nullable = true, columnDefinition = "text")
    private String accessibility_need;

    /** Số người được hướng dẫn >= 1; không chở người. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "passenger_count", nullable = false)
    private Integer passenger_count;

    /** Hướng dẫn khi tới nơi. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "arrival_instruction", nullable = true, columnDefinition = "text")
    private String arrival_instruction;
}
