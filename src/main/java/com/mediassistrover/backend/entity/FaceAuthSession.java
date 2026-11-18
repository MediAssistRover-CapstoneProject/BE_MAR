package com.mediassistrover.backend.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="face_auth_session") @Getter @Setter @NoArgsConstructor
public class FaceAuthSession {
    @Id @Column(name="session_id",columnDefinition="BINARY(16)") private UUID sessionId;
    @Column(name="job_id",nullable=false,columnDefinition="BINARY(16)") private UUID jobId;
    @Column(name="auth_job_step_id",nullable=false,unique=true,columnDefinition="BINARY(16)") private UUID authJobStepId;
    @Column(name="robot_id",nullable=false,columnDefinition="BINARY(16)") private UUID robotId;
    @Column(name="transport_session_id",nullable=false,columnDefinition="BINARY(16)") private UUID transportSessionId;
    @Column(name="account_id",nullable=false,columnDefinition="BINARY(16)") private UUID accountId;
    @Column(name="avatar_version",nullable=false,columnDefinition="BINARY(16)") private UUID avatarVersion;
    @Column(name="expires_at",nullable=false) private Instant expiresAt;
    @Column(name="used_at") private Instant usedAt;
}
