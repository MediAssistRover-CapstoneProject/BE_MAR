package com.mediassistrover.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "StaffProfile")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StaffProfile {
    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "staff_profile_id", columnDefinition = "BINARY(16)")
    private UUID staffProfileId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, unique = true)
    private Account account;

    @Column(name = "staff_code", nullable = false, length = 64, unique = true)
    private String staffCode;

    @Column(name = "department_code", length = 64)
    private String departmentCode;

    @Column(name = "job_title", length = 100)
    private String jobTitle;

    @Column(name = "contact_extension", length = 20)
    private String contactExtension;

    @Column(name = "url", columnDefinition = "TEXT")
    private String url;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name="face_avatar_version",columnDefinition="BINARY(16)") private UUID faceAvatarVersion;
    @Column(name="face_status",length=24) private String faceStatus;
    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(name="face_template",columnDefinition="TEXT") private String faceTemplate;
    @Column(name="face_model_version",length=64) private String faceModelVersion;
    @Column(name="face_reviewed_by",columnDefinition="BINARY(16)") private UUID faceReviewedBy;
    @Column(name="face_reviewed_at") private Instant faceReviewedAt;
    @jakarta.persistence.Version @Column(name="face_row_version",nullable=false) private long faceRowVersion;

    public void setUrl(String value) {
        if (!java.util.Objects.equals(url,value)) {
            faceAvatarVersion=UUID.randomUUID();faceTemplate=null;faceModelVersion=null;
            faceStatus=value==null||value.isBlank()?"MISSING":"PENDING";
            faceReviewedBy=null;faceReviewedAt=null;
        }
        url=value;
    }

    @jakarta.persistence.PrePersist
    public void initializeFace() { if(faceAvatarVersion==null) faceAvatarVersion=UUID.randomUUID(); if(faceStatus==null) faceStatus=url==null||url.isBlank()?"MISSING":"PENDING"; }

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
