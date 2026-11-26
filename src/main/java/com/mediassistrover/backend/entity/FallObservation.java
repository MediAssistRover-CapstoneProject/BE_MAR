package com.mediassistrover.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** A bounded request to observe one person. It is not a fall incident. */
@Entity @Table(name="FallObservation")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class FallObservation {
    public enum State { SUSPENDING, STOP_CONFIRMED, OBSERVING, CLEAR, INCIDENT, INCONCLUSIVE, EXPIRED }
    public static final Set<State> ACTIVE=Set.of(State.SUSPENDING,State.STOP_CONFIRMED,State.OBSERVING);
    @Id @Column(name="observation_id",columnDefinition="BINARY(16)") private UUID observationId;
    @Column(name="robot_id",nullable=false,columnDefinition="BINARY(16)") private UUID robotId;
    @Column(name="job_id",nullable=false,columnDefinition="BINARY(16)") private UUID jobId;
    @Column(name="job_step_id",nullable=false,columnDefinition="BINARY(16)") private UUID jobStepId;
    @Column(name="operation_key",nullable=false,length=150) private String operationKey;
    @Column(name="monitoring_session_id",nullable=false,columnDefinition="BINARY(16)") private UUID monitoringSessionId;
    @Column(name="tracking_revision",nullable=false,columnDefinition="BINARY(16)") private UUID trackingRevision;
    @Column(name="transport_session_id",nullable=false,columnDefinition="BINARY(16)") private UUID transportSessionId;
    @Column(name="map_id",nullable=false,columnDefinition="BINARY(16)") private UUID mapId;
    @Column(name="map_asset_hash",nullable=false,length=64) private String mapAssetHash;
    @Column(name="navigation_profile_hash",length=64) private String navigationProfileHash;
    @Column(name="track_id",nullable=false) private int trackId;
    @Column(name="candidate_reason",nullable=false,length=64) private String candidateReason;
    @Column(name="continuous_duration_sec",nullable=false) private double continuousDurationSec;
    @Column(name="capture_id",nullable=false,length=100) private String captureId;
    @Column(name="detected_at",nullable=false) private Instant detectedAt;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @Column(name="expires_at",nullable=false) private Instant expiresAt;
    @Column(name="stop_confirmed_at") private Instant stopConfirmedAt;
    @Column(name="observe_after") private Instant observeAfter;
    @Column(name="observe_until") private Instant observeUntil;
    @Column(name="completed_at") private Instant completedAt;
    @Column(name="incident_id",columnDefinition="BINARY(16)") private UUID incidentId;
    @Enumerated(EnumType.STRING) @Column(name="state",nullable=false,length=24) private State state;
    @Column(name="completion_reason",length=80) private String completionReason;
    @Version @Column(name="row_version",nullable=false) private long rowVersion;
    public boolean active(Instant now){return ACTIVE.contains(state) && expiresAt.isAfter(now);}
}
