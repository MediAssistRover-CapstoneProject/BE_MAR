package fu.se181738.backend_robot_v1.entity;

import fu.se181738.backend_robot_v1.enums.JobStatus;
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
 * Job mapped from docs/logicalDiagram.md.
 * Source constraints (cross-row/policy rules are not enforced by JPA alone):
 * - Khóa chính: `PK(job_id)`.
 * - UQ(schedule_id, schedule_fire_at, schedule_item_key) khi có lịch.
 * - Tối đa một Job chiếm một robot ở ACCEPTED/RUNNING; RECOVERY_REQUIRED giữ khóa vận hành đến khi đối soát.
 * - ACCEPTED/RUNNING yêu cầu robot_id; check hash/catalog/Map trước dispatch.
 * - recovery_of_job_id != job_id, không tạo vòng; robot chỉ nhận việc mới sau khi Job RECOVERY_REQUIRED đã reconciled_at.
 * - reconciled_by_account_id và reconciled_at cùng có hoặc cùng NULL; ghi audit kết quả đối soát.
 * 
 * Nguồn: S0, S1, S3
 */
@Entity
@Table(name = "Job", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"schedule_id", "schedule_fire_at", "schedule_item_key"})
})
@Getter
@Setter
@NoArgsConstructor
@Check(constraints = "recovery_of_job_id IS NULL OR recovery_of_job_id <> job_id")
@Check(constraints = "status NOT IN ('ACCEPTED', 'RUNNING') OR robot_id IS NOT NULL")
@Check(constraints = "(reconciled_by_account_id IS NULL AND reconciled_at IS NULL) OR (reconciled_by_account_id IS NOT NULL AND reconciled_at IS NOT NULL)")
@Check(constraints = "priority BETWEEN 0 AND 100")
@Check(constraints = "row_version >= 1")
public class Job {

    // Conditional unique: UQ(schedule_id, schedule_fire_at, schedule_item_key).
    // On the current MySQL stack, a unique tuple containing SQL NULL is not compared.
    // The source predicate excludes rows whose subject/detector/schedule key is NULL,
    // so this composite unique preserves that predicate without extra columns.
    // TODO: Clarify from physical database design
    // Confirm NULL-distinct semantics before changing the database dialect.

    /** ID lần chạy. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Id
    @Column(name = "job_id", nullable = false)
    private UUID job_id;

    /** Đúng phiên bản Workflow đã chọn. Tham chiếu Workflow.workflow_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workflow_id", referencedColumnName = "workflow_id", nullable = false)
    private Workflow workflow_id;

    /** Yêu cầu gốc; NULL với lịch/system độc lập. Tham chiếu ServiceRequest.service_request_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "service_request_id", referencedColumnName = "service_request_id", nullable = true)
    private ServiceRequest service_request_id;

    /** Lịch tạo job; NULL với job thủ công. Tham chiếu Schedule.schedule_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "schedule_id", referencedColumnName = "schedule_id", nullable = true)
    private Schedule schedule_id;

    /** Robot thực thi; NULL khi chưa phân công. Tham chiếu Robot.robot_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "robot_id", referencedColumnName = "robot_id", nullable = true)
    private Robot robot_id;

    /** Map của job; bắt buộc khi có MOVE. Tham chiếu Map.map_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "map_id", referencedColumnName = "map_id", nullable = true)
    private Map map_id;

    /** Job cũ được đối soát/phục hồi; không phải gọi workflow con. Tham chiếu Job.job_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "recovery_of_job_id", referencedColumnName = "job_id", nullable = true)
    private Job recovery_of_job_id;

    /** Người yêu cầu hủy; NULL với hệ thống. Tham chiếu Account.account_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "cancel_requested_by_account_id", referencedColumnName = "account_id", nullable = true)
    private Account cancel_requested_by_account_id;

    /** Người đối soát robot/cửa/hàng sau RECOVERY_REQUIRED. Tham chiếu Account.account_id. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "reconciled_by_account_id", referencedColumnName = "account_id", nullable = true)
    private Account reconciled_by_account_id;

    /** Mã theo dõi. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Column(name = "job_code", nullable = false, unique = true, length = 64)
    private String job_code;

    /** QUEUED, ACCEPTED, RUNNING, SUCCEEDED, FAILED, CANCELLED, RECOVERY_REQUIRED. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private JobStatus status;

    /** [0,100]; số lớn ưu tiên hơn. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "priority", nullable = false)
    private Integer priority;

    /** JobParam đã resolve/validate; snapshot bất biến khi dispatch. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    // Structure not specified in ERD: retain raw JSON (object/array/scalar/null).
    // TODO: Define the JSON schema once the physical design is clarified.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_json", nullable = false)
    private String input_json;

    /** Biến /vars của lần chạy. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    // Structure not specified in ERD: retain raw JSON (object/array/scalar/null).
    // TODO: Define the JSON schema once the physical design is clarified.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "context_json", nullable = false)
    private String context_json;

    /** Kết quả cuối hợp lệ; không dựng kết quả thành công khi thiếu dữ liệu. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    // Structure not specified in ERD: retain raw JSON (object/array/scalar/null).
    // TODO: Define the JSON schema once the physical design is clarified.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_json", nullable = true)
    private String result_json;

    /** Hash Workflow đã chốt. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "workflow_hash", nullable = false, length = 64)
    private String workflow_hash;

    /** = 1 cho giao thức hiện tại. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "protocol_version", nullable = false)
    private Integer protocol_version;

    /** = 2 cho I/O step hiện tại. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "schema_version", nullable = false)
    private Integer schema_version;

    /** = 3 cho artifact graph hiện tại. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "graph_version", nullable = false)
    private Integer graph_version;

    /** Lần kích hoạt lịch tạo job. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Column(name = "schedule_fire_at", nullable = true)
    private Instant schedule_fire_at;

    /** Khóa checkpoint/đơn vị sinh job trong cùng lần kích hoạt. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Column(name = "schedule_item_key", nullable = true, length = 100)
    private String schedule_item_key;

    /** ID DISPATCH_JOB dùng chống phát lệnh trùng. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Column(name = "dispatch_command_id", nullable = true, unique = true)
    private UUID dispatch_command_id;

    /** Hạn nhận lệnh dispatch. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "dispatch_expires_at", nullable = true)
    private Instant dispatch_expires_at;

    /** Vào hàng đợi. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "queued_at", nullable = false)
    private Instant queued_at;

    /** Thời điểm dự kiến chạy. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "scheduled_at", nullable = true)
    private Instant scheduled_at;

    /** Pi xác nhận chấp nhận; chưa có nghĩa thành công. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "accepted_at", nullable = true)
    private Instant accepted_at;

    /** Bắt đầu chạy. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "started_at", nullable = true)
    private Instant started_at;

    /** ID CANCEL_JOB nếu có. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: default. No inferred constraint/default.
    @Column(name = "cancel_command_id", nullable = true, unique = true)
    private UUID cancel_command_id;

    /** Đã yêu cầu hủy, chưa chắc robot dừng xong. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "cancel_requested_at", nullable = true)
    private Instant cancel_requested_at;

    /** Kết thúc lần chạy kể cả FAILED/CANCELLED/RECOVERY_REQUIRED. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "ended_at", nullable = true)
    private Instant ended_at;

    /** Mã lỗi cuối. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "error_code", nullable = true, length = 100)
    private String error_code;

    /** Mô tả lỗi. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "failure_reason", nullable = true, columnDefinition = "text")
    private String failure_reason;

    /** Đã kiểm và giải phóng khóa vận hành sau sự cố. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "reconciled_at", nullable = true)
    private Instant reconciled_at;

    /** Kết quả kiểm robot, cửa và hàng; không tự suy trạng thái vật lý. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    @Column(name = "reconciliation_note", nullable = true, columnDefinition = "text")
    private String reconciliation_note;

    /** Số phiên bản optimistic concurrency >= 1. */
    // TODO: Clarify from physical database design
    // Unclear from ERD: unique, default. No inferred constraint/default.
    // TODO: Clarify from physical database design
    // Optimistic concurrency is required, but its initial seed/owner is unspecified.
    // Do not use @Version with Hibernate's default zero seed: ERD requires >= 1.
    @Column(name = "row_version", nullable = false)
    private Long row_version;
}
