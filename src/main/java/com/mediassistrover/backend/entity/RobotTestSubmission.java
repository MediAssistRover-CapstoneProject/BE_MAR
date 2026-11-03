package com.mediassistrover.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="RobotTestSubmission") @Getter @Setter @NoArgsConstructor
public class RobotTestSubmission {
    @Id @Column(name="request_key",length=64) private String requestKey;
    @Column(name="actor_account_id",nullable=false,columnDefinition="BINARY(16)") private UUID actorAccountId;
    @Column(name="request_fingerprint",nullable=false,length=64) private String requestFingerprint;
    @Column(name="job_id",columnDefinition="BINARY(16)") private UUID jobId;
    @Column(name="created_at",nullable=false) private Instant createdAt;
}
