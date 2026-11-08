package com.mediassistrover.backend.media;

import com.mediassistrover.backend.entity.Account;
import com.mediassistrover.backend.entity.EmergencyIncident;
import com.mediassistrover.backend.entity.Robot;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "RobotMediaSession")
@Getter @Setter @NoArgsConstructor
public class MediaSession {
    public enum Mode { MONITOR, ASSIST }
    public enum State { RINGING, CONNECTING, ACTIVE, RECONNECTING, ENDED, FAILED, EXPIRED }
    @Id @GeneratedValue @UuidGenerator @Column(name="session_id", columnDefinition="BINARY(16)") private UUID sessionId;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="robot_id", nullable=false) private Robot robot;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="incident_id") private EmergencyIncident incident;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="staff_account_id") private Account staff;
    @Column(name="staff_token_version") private Long staffTokenVersion;
    @Column(name="active_robot_id",columnDefinition="BINARY(16)",unique=true) private UUID activeRobotId;
    @Enumerated(EnumType.STRING) @Column(name="mode",nullable=false,length=16) private Mode mode;
    @Enumerated(EnumType.STRING) @Column(name="state",nullable=false,length=24) private State state;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @Column(name="accepted_at") private Instant acceptedAt;
    @Column(name="expires_at",nullable=false) private Instant expiresAt;
    @Column(name="device_seen_at") private Instant deviceSeenAt;
    @Column(name="reconnect_started_at") private Instant reconnectStartedAt;
    @Column(name="ended_at") private Instant endedAt;
    @Column(name="end_reason",length=96) private String endReason;
    @Column(name="cleanup_pending",nullable=false) private boolean cleanupPending;
    @Version @Column(name="row_version",nullable=false) private long rowVersion;
    public boolean open() { return activeRobotId != null; }
    public String room() { return "mar-" + sessionId; }
}
