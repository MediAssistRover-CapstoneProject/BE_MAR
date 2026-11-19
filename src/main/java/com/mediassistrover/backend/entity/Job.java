package com.mediassistrover.backend.entity;
import com.mediassistrover.backend.enums.JobStopStatus;

import com.fasterxml.jackson.databind.JsonNode;
import com.mediassistrover.backend.enums.JobStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="Job") @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Job {
    @Id @GeneratedValue @UuidGenerator @Column(name="job_id",columnDefinition="BINARY(16)") private UUID jobId;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="workflow_id",nullable=false) private Workflow workflow;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="service_request_id") private ServiceRequest serviceRequest;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="schedule_id") private Schedule schedule;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="robot_id") private Robot robot;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="map_id") private FacilityMap map;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="recovery_of_job_id") private Job recoveryOfJob;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="cancel_requested_by_account_id") private Account cancelRequestedByAccount;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="reconciled_by_account_id") private Account reconciledByAccount;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="assigned_staff_account_id") private Account assignedStaffAccount;
    @Column(name="job_code",nullable=false,length=64,unique=true) private String jobCode;
    @Enumerated(EnumType.STRING) @Column(name="status",nullable=false,length=32) private JobStatus status;
    @Column(name="priority",nullable=false) private int priority;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="input_json",nullable=false,columnDefinition="JSON") private JsonNode inputJson;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="context_json",nullable=false,columnDefinition="JSON") private JsonNode contextJson;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="result_json",columnDefinition="JSON") private JsonNode resultJson;
    @Column(name="workflow_hash",nullable=false,length=64,columnDefinition="CHAR(64)") private String workflowHash;
    @Column(name="protocol_version",nullable=false) private int protocolVersion;
    @Column(name="schema_version",nullable=false) private int schemaVersion;
    @Column(name="graph_version",nullable=false) private int graphVersion;
    @Column(name="be_orchestrated",nullable=false) private boolean beOrchestrated;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="selected_job_step_id") private JobStep selectedJobStep;
    @Column(name="schedule_fire_at") private Instant scheduleFireAt;
    @Column(name="schedule_item_key",length=150) private String scheduleItemKey;
    @Column(name="dispatch_command_id",columnDefinition="BINARY(16)") private UUID dispatchCommandId;
    @Column(name="dispatch_expires_at") private Instant dispatchExpiresAt;
    @Column(name="queued_at",nullable=false) private Instant queuedAt;
    @Column(name="scheduled_at") private Instant scheduledAt;
    @Column(name="accepted_at") private Instant acceptedAt;
    @Column(name="started_at") private Instant startedAt;
    @Column(name="cancel_command_id",columnDefinition="BINARY(16)") private UUID cancelCommandId;
    @Column(name="cancel_requested_at") private Instant cancelRequestedAt;
    @Enumerated(EnumType.STRING) @Column(name="stop_status",length=32) private JobStopStatus stopStatus;
    @Column(name="stop_requested_at") private Instant stopRequestedAt;
    @Column(name="stop_confirmed_at") private Instant stopConfirmedAt;
    public boolean stopOutstanding() { return stopStatus==JobStopStatus.PENDING || stopStatus==JobStopStatus.RECOVERY_REQUIRED; }
    @Column(name="suspend_requested_at") private Instant suspendRequestedAt;
    @Column(name="suspended_at") private Instant suspendedAt;
    @Column(name="suspended_duration_ms",nullable=false) private long suspendedDurationMs;
    @Column(name="resume_requested",nullable=false) private boolean resumeRequested;
    @Column(name="suspended_map_asset_hash",length=64) private String suspendedMapAssetHash;
    @Builder.Default @Column(name="patrol_repeat_count",nullable=false) private int patrolRepeatCount=1;
    @Builder.Default @Column(name="patrol_round",nullable=false) private int patrolRound=1;
    @Column(name="patrol_round_started_at") private Instant patrolRoundStartedAt;
    @Column(name="patrol_round_suspended_base_ms",nullable=false) private long patrolRoundSuspendedBaseMs;
    @Column(name="ended_at") private Instant endedAt;
    @Column(name="error_code",length=100) private String errorCode;
    @Column(name="failure_reason",columnDefinition="TEXT") private String failureReason;
    @Column(name="reconciled_at") private Instant reconciledAt;
    @Column(name="reconciliation_note",columnDefinition="TEXT") private String reconciliationNote;
    @Column(name="archived_at") private Instant archivedAt;
    @Version @Column(name="row_version",nullable=false) private long rowVersion;
}
