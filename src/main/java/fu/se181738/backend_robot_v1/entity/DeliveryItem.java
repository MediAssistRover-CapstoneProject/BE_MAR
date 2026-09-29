package fu.se181738.backend_robot_v1.entity;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;

/**
 * DeliveryItem mapped from docs/logicalDiagram.md.
 * Source constraints (cross-row/policy rules are not enforced by JPA alone):
 * - Khóa chính: `PK(delivery_item_id)`.
 * - UQ(delivery_request_id, item_code); gộp số lượng cùng mã trong một yêu cầu.
 * - Snapshot danh sách vào Job.input_json; xác nhận thực tế lưu result_json của HANDLE_PAYLOAD.
 * 
 * Nguồn: S0, S1, S3
 */
@Entity
@Table(name = "DeliveryItem", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"delivery_request_id", "item_code"})
})
@Getter
@Setter
@NoArgsConstructor
@Check(constraints = "quantity >= 1")
public class DeliveryItem {

    /** ID dòng vật phẩm. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Id
    @Column(name = "delivery_item_id", nullable = false)
    private UUID delivery_item_id;

    /** Tham chiếu PK service_request_id của DeliveryRequest. Tham chiếu DeliveryRequest.service_request_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "delivery_request_id", referencedColumnName = "service_request_id", nullable = false)
    private DeliveryRequest delivery_request_id;

    /** Mã vật phẩm, bắt buộc theo HANDLE_PAYLOAD. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Column(name = "item_code", nullable = false, length = 80)
    private String item_code;

    /** Tên vật phẩm. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "item_name", nullable = false, length = 160)
    private String item_name;

    /** DOCUMENT, SAMPLE, MEDICATION, SUPPLY... theo danh mục. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "item_category", nullable = false, length = 64)
    private String item_category;

    /** Số nguyên >= 1. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    /** Đơn vị tính. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "unit", nullable = false, length = 32)
    private String unit;

    /** Yêu cầu xử lý nhạy cảm. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "is_sensitive", nullable = false)
    private Boolean is_sensitive;

    /** Hướng dẫn xử lý. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "handling_instruction", nullable = true, columnDefinition = "text")
    private String handling_instruction;

    /** Ghi chú. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "item_note", nullable = true, columnDefinition = "text")
    private String item_note;
}
