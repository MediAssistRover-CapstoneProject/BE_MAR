package fu.se181738.backend_robot_v1.entity;

import fu.se181738.backend_robot_v1.enums.JobEventProducerType;
import fu.se181738.backend_robot_v1.enums.JobEventStatusScope;
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
 * JobEvent mapped from docs/logicalDiagram.md.
 * Source constraints (cross-row/policy rules are not enforced by JPA alone):
 * - Khóa chính: `PK(job_event_id)`.
 * - UQ(producer_session_id, sequence_no); job_event_id chống trùng toàn cục.
 * - FK JobTask/JobStep phải cùng job_id; scope khớp đối tượng nguồn.
 * - Lưu event và cập nhật Job/JobStep trong cùng transaction rồi gửi EVENT_ACK.
 * - Các attempt/result của bước lưu append-only; không lấy EVENT_ACK thay ACK cảnh báo.
 * 
 * Nguồn: S0, S3
 */
@Entity
@Table(name = "JobEvent", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"producer_session_id", "sequence_no"})
})
@Getter
@Setter
@NoArgsConstructor
@Check(constraints = "sequence_no >= 1")
public class JobEvent {

    /** Dùng chính event_id của giao thức khi đến từ Pi. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Id
    @Column(name = "job_event_id", nullable = false)
    private UUID job_event_id;

    /** Job liên quan. Tham chiếu Job.job_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", referencedColumnName = "job_id", nullable = false)
    private Job job_id;

    /** Robot phát event nếu có. Tham chiếu Robot.robot_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "robot_id", referencedColumnName = "robot_id", nullable = true)
    private Robot robot_id;

    /** Task liên quan. Tham chiếu JobTask.job_task_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "job_task_id", referencedColumnName = "job_task_id", nullable = true)
    private JobTask job_task_id;

    /** Lượt bước liên quan. Tham chiếu JobStep.job_step_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "job_step_id", referencedColumnName = "job_step_id", nullable = true)
    private JobStep job_step_id;

    /** Người xác nhận sự kiện nghiệp vụ. Tham chiếu Account.account_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "actor_account_id", referencedColumnName = "account_id", nullable = true)
    private Account actor_account_id;

    /** PI, SERVER, USER_ACTION. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Enumerated(EnumType.STRING)
    @Column(name = "producer_type", nullable = false)
    private JobEventProducerType producer_type;

    /** ID phiên producer. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Column(name = "producer_session_id", nullable = false)
    private UUID producer_session_id;

    /** Thứ tự event trong producer session >= 1. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Column(name = "sequence_no", nullable = false)
    private Long sequence_no;

    /** STEP_RESULT, JOB_RESULT, STAFF_ARRIVED... theo registry. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "event_type", nullable = false, length = 100)
    private String event_type;

    /** Mã incident/yêu cầu để WAIT đối chiếu; không phải FK đa hình. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "correlation_key", nullable = true, length = 150)
    private String correlation_key;

    /** Lần thử của bước nếu có. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "attempt_no", nullable = true)
    private Integer attempt_no;

    /** Trạng thái nguồn trước event, diễn giải theo scope. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "previous_status", nullable = true, length = 40)
    private String previous_status;

    /** Trạng thái nguồn sau event. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "new_status", nullable = true, length = 40)
    private String new_status;

    /** JOB, JOB_TASK, JOB_STEP; NULL nếu không chuyển trạng thái. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Enumerated(EnumType.STRING)
    @Column(name = "status_scope", nullable = true)
    private JobEventStatusScope status_scope;

    /** Payload event theo schema, gồm output thật. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    // Structure not specified in ERD: retain raw JSON (object/array/scalar/null).
    // TODO: Define the JSON schema once the physical design is clarified.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "event_data_json", nullable = false)
    private String event_data_json;

    /** Thời điểm phát sinh. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "occurred_at", nullable = false)
    private Instant occurred_at;

    /** Thời điểm server nhận. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "received_at", nullable = false)
    private Instant received_at;

    /** Thời điểm transaction đã ghi bền. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "persisted_at", nullable = false)
    private Instant persisted_at;
}
