package fu.se181738.backend_robot_v1.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Room mapped from docs/logicalDiagram.md.
 * Source constraints (cross-row/policy rules are not enforced by JPA alone):
 * - Khóa chính: `PK(room_id)`.
 * - Map của phòng được xác định qua Endpoint ở từng phiên bản Map.
 * - default_endpoint_group_id là lựa chọn mặc định, không biểu diễn tất cả nhóm chứa phòng.
 * 
 * Nguồn: S5 + thiết kế đề xuất
 */
@Entity
@Table(name = "Room")
@Getter
@Setter
@NoArgsConstructor
public class Room {

    /** ID phòng nghiệp vụ. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Id
    @Column(name = "room_id", nullable = false)
    private UUID room_id;

    /** Nhóm đích mặc định khi người dùng chọn phòng. Tham chiếu EndpointGroup.endpoint_group_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "default_endpoint_group_id", referencedColumnName = "endpoint_group_id", nullable = true)
    private EndpointGroup default_endpoint_group_id;

    /** Mã phòng duy nhất trong phạm vi hệ thống một bệnh viện. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Column(name = "room_code", nullable = false, unique = true, length = 64)
    private String room_code;

    /** Tên phòng. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "room_name", nullable = false, length = 160)
    private String room_name;

    /** EXAM, LAB, PHARMACY, PAYMENT, OTHER. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "room_type", nullable = false, length = 64)
    private String room_type;

    /** Tầng/khu vực vật lý. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "floor_label", nullable = true, length = 32)
    private String floor_label;

    /** Khoa quản lý; không phải FK khi chưa có bảng khoa. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "department_code", nullable = true, length = 64)
    private String department_code;

    /** Mô tả. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "description", nullable = true, columnDefinition = "text")
    private String description;

    /** Cho phép chọn trên UI. */
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
