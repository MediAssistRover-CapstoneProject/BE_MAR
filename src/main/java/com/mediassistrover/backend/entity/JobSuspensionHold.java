package com.mediassistrover.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

/** A released source remains recorded so a duplicate start cannot resurrect it. */
@Entity @Table(name="JobSuspensionHold",uniqueConstraints=@UniqueConstraint(name="uq_job_suspension_source",columnNames={"job_id","source","source_id"}))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class JobSuspensionHold {
    @Id @GeneratedValue @org.hibernate.annotations.UuidGenerator @Column(name="hold_id",columnDefinition="BINARY(16)") private UUID holdId;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="job_id",nullable=false) private Job job;
    @Column(name="source",nullable=false,length=32) private String source;
    @Column(name="source_id",nullable=false,columnDefinition="BINARY(16)") private UUID sourceId;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @Column(name="released_at") private Instant releasedAt;
}
