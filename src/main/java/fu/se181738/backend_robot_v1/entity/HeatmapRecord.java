package fu.se181738.backend_robot_v1.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * HeatmapRecord mapped from docs/logicalDiagram.md.
 * Source constraints (cross-row/policy rules are not enforced by JPA alone):
 * - Khóa chính: `PK(heatmap_record_id)`.
 * - CHECK period_start < period_end; UQ(map_id, heatmap_type, period_start, period_end, cell_size_m).
 * 
 * Nguồn: S0, S2
 */
@Entity
@Table(name = "HeatmapRecord", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"map_id", "heatmap_type", "period_start", "period_end", "cell_size_m"})
})
@Getter
@Setter
@NoArgsConstructor
@Check(constraints = "period_start < period_end")
@Check(constraints = "cell_size_m > 0")
@Check(constraints = "source_record_count >= 0")
public class HeatmapRecord {

    /** ID heatmap. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Id
    @Column(name = "heatmap_record_id", nullable = false)
    private UUID heatmap_record_id;

    /** Map nền. Tham chiếu Map.map_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "map_id", referencedColumnName = "map_id", nullable = false)
    private Map map_id;

    /** CROWD_COUNT, VISIT_FREQUENCY... theo định nghĩa chỉ số. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Column(name = "heatmap_type", nullable = false, length = 64)
    private String heatmap_type;

    /** Bắt đầu cửa sổ dữ liệu. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Column(name = "period_start", nullable = false)
    private Instant period_start;

    /** Kết thúc cửa sổ dữ liệu. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Column(name = "period_end", nullable = false)
    private Instant period_end;

    /** Kích thước ô lưới > 0. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Column(name = "cell_size_m", nullable = false, precision = 10, scale = 4)
    private BigDecimal cell_size_m;

    /** Ô lưới và giá trị; có schema/version. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    // Structure not specified in ERD: retain raw JSON (object/array/scalar/null).
    // TODO: Define the JSON schema once the physical design is clarified.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "grid_data_json", nullable = false)
    private String grid_data_json;

    /** Ảnh dựng sẵn nếu có. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "image_uri", nullable = true, columnDefinition = "text")
    private String image_uri;

    /** Giá trị cực đại trong grid. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "max_value", nullable = true, precision = 14, scale = 4)
    private BigDecimal max_value;

    /** Số bản ghi dùng tổng hợp >= 0. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "source_record_count", nullable = false)
    private Integer source_record_count;

    /** Thời điểm tạo. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "generated_at", nullable = false)
    private Instant generated_at;
}
