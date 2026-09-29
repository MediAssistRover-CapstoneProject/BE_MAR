package fu.se181738.backend_robot_v1.entity;

import fu.se181738.backend_robot_v1.enums.HospitalInformationStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * HospitalInformation mapped from docs/logicalDiagram.md.
 * Source constraints (cross-row/policy rules are not enforced by JPA alone):
 * - Khóa chính: `PK(hospital_information_id)`.
 * - Không làm bảng cha của Map/Room. Đây là nội dung, không phải thực thể Hospital.
 * - Chỉ QUERY nội dung PUBLISHED, đúng ngôn ngữ và còn hiệu lực.
 * 
 * Nguồn: S0, S2, S3
 */
@Entity
@Table(name = "HospitalInformation")
@Getter
@Setter
@NoArgsConstructor
public class HospitalInformation {

    /** ID mục thông tin. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Id
    @Column(name = "hospital_information_id", nullable = false)
    private UUID hospital_information_id;

    /** Bản đồ liên quan; NULL với thông tin chung. Tham chiếu Map.map_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "map_id", referencedColumnName = "map_id", nullable = true)
    private Map map_id;

    /** Phòng liên quan; NULL với thông tin chung. Tham chiếu Room.room_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "room_id", referencedColumnName = "room_id", nullable = true)
    private Room room_id;

    /** Người cập nhật nội dung. Tham chiếu Account.account_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "updated_by_account_id", referencedColumnName = "account_id", nullable = false)
    private Account updated_by_account_id;

    /** PROCEDURE, ROOM_INFO, OPENING_HOURS, FAQ... theo danh mục. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "info_category", nullable = false, length = 64)
    private String info_category;

    /** Tiêu đề. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "title", nullable = false, length = 255)
    private String title;

    /** Nội dung để hiển thị hoặc trả lời bằng TTS. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    /** Ví dụ vi-VN. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "language_code", nullable = false, length = 16)
    private String language_code;

    /** Mảng chuỗi từ khóa tìm kiếm. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    // Structure not specified in ERD: retain raw JSON (object/array/scalar/null).
    // TODO: Define the JSON schema once the physical design is clarified.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "keywords_json", nullable = false)
    private String keywords_json;

    /** DRAFT, PUBLISHED, RETIRED. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private HospitalInformationStatus status;

    /** Bắt đầu hiệu lực. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "effective_from", nullable = true)
    private Instant effective_from;

    /** Hết hiệu lực. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "effective_until", nullable = true)
    private Instant effective_until;

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
