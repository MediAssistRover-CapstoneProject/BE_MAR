package com.mediassistrover.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Durable battery episode and return intent. Test inputs never replace measured telemetry. */
@Entity @Table(name="RobotBatteryPolicy") @Getter @Setter @NoArgsConstructor
public class RobotBatteryPolicy {
    @Id @Column(name="robot_id",columnDefinition="BINARY(16)") private UUID robotId;
    @Column(name="episode_id",columnDefinition="BINARY(16)") private UUID episodeId;
    @Column(name="policy_state",nullable=false,length=32) private String state="NORMAL";
    @Column(name="low_notified",nullable=false) private boolean lowNotified;
    @Column(name="critical_notified",nullable=false) private boolean criticalNotified;
    @Column(name="critical_latched",nullable=false) private boolean criticalLatched;
    @Column(name="return_requested",nullable=false) private boolean returnRequested;
    @Column(name="last_sample_at") private Instant lastSampleAt;
    @Column(name="critical_since") private Instant criticalSince;
    @Column(name="rearm_since") private Instant rearmSince;
    @Column(name="return_job_id",columnDefinition="BINARY(16)") private UUID returnJobId;
    @Column(name="warning_job_id",columnDefinition="BINARY(16)") private UUID warningJobId;
    @Column(name="return_source",nullable=false,length=16) private String returnSource="AUTOMATIC";
    @Column(name="charger_endpoint_id",columnDefinition="BINARY(16)") private UUID chargerEndpointId;
    @Column(name="blocked_reason",length=1000) private String blockedReason;
    @Column(name="last_evaluated_at") private Instant lastEvaluatedAt;
    @Column(name="test_percent",precision=5,scale=2) private BigDecimal testPercent;
    @Column(name="test_expires_at") private Instant testExpiresAt;
    @Column(name="test_actor_id",columnDefinition="BINARY(16)") private UUID testActorId;
    @Version @Column(name="row_version",nullable=false) private long rowVersion;
}
