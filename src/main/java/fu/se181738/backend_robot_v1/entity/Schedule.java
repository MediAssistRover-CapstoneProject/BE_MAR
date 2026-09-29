package fu.se181738.backend_robot_v1.entity;

import fu.se181738.backend_robot_v1.enums.ScheduleMisfirePolicy;
import fu.se181738.backend_robot_v1.enums.ScheduleScheduleType;
import fu.se181738.backend_robot_v1.enums.ScheduleStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Schedule mapped from docs/logicalDiagram.md.
 * Source constraints (cross-row/policy rules are not enforced by JPA alone):
 * - Khóa chính: `PK(schedule_id)`.
 * - CHECK cấu hình thời gian theo schedule_type; workflow PUBLISHED và accepts_new_jobs.
 * - Job có schedule_fire_at + schedule_item_key để chống scheduler tạo trùng.
 * 
 * Nguồn: S1, S5 + thiết kế đề xuất
 */
@Entity
@Table(name = "Schedule")
@Getter
@Setter
@NoArgsConstructor
@Check(constraints = "priority BETWEEN 0 AND 100")
public class Schedule {

    /** ID lịch. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Id
    @Column(name = "schedule_id", nullable = false)
    private UUID schedule_id;

    /** Phiên bản Workflow sẽ chạy. Tham chiếu Workflow.workflow_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workflow_id", referencedColumnName = "workflow_id", nullable = false)
    private Workflow workflow_id;

    /** Robot cố định; NULL nếu scheduler lựa chọn. Tham chiếu Robot.robot_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "robot_id", referencedColumnName = "robot_id", nullable = true)
    private Robot robot_id;

    /** Người cấu hình; NULL với system. Tham chiếu Account.account_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "created_by_account_id", referencedColumnName = "account_id", nullable = true)
    private Account created_by_account_id;

    /** Mã lịch. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Column(name = "schedule_code", nullable = false, unique = true, length = 64)
    private String schedule_code;

    /** Tên lịch. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "schedule_name", nullable = false, length = 160)
    private String schedule_name;

    /** ONCE, INTERVAL, CRON. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Enumerated(EnumType.STRING)
    @Column(name = "schedule_type", nullable = false)
    private ScheduleScheduleType schedule_type;

    /** IANA timezone, ví dụ Asia/Ho_Chi_Minh. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "timezone", nullable = false, length = 64)
    private String timezone;

    /** Thời điểm bắt đầu hiệu lực. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "starts_at", nullable = false)
    private Instant starts_at;

    /** Hết hiệu lực. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "ends_at", nullable = true)
    private Instant ends_at;

    /** Khoảng lặp > 0 cho INTERVAL. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "interval_sec", nullable = true)
    private Integer interval_sec;

    /** Biểu thức cron đã kiểm cho CRON. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "cron_expression", nullable = true, length = 120)
    private String cron_expression;

    /** Input mẫu; resolve điểm/nhóm trước khi tạo job. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    // Structure not specified in ERD: retain raw JSON (object/array/scalar/null).
    // TODO: Define the JSON schema once the physical design is clarified.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_template_json", nullable = false)
    private String input_template_json;

    /** [0,100]; số lớn ưu tiên hơn. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "priority", nullable = false)
    private Integer priority;

    /** SKIP, RUN_ONCE; tránh tự phát lại tác vụ vật lý hàng loạt. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Enumerated(EnumType.STRING)
    @Column(name = "misfire_policy", nullable = false)
    private ScheduleMisfirePolicy misfire_policy;

    /** ACTIVE, PAUSED, RETIRED; đây là lịch, không phải trạng thái Pause của robot. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ScheduleStatus status;

    /** Lần kích hoạt tiếp theo. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "next_run_at", nullable = true)
    private Instant next_run_at;

    /** Lần kích hoạt gần nhất. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "last_run_at", nullable = true)
    private Instant last_run_at;

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
