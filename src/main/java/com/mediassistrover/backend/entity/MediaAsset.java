package com.mediassistrover.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="MediaAsset") @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class MediaAsset {
    @Id @GeneratedValue @UuidGenerator @Column(name="media_asset_id",columnDefinition="BINARY(16)") private UUID mediaAssetId;
    @Column(name="object_key",nullable=false,length=255,unique=true) private String objectKey;
    @Column(name="url",nullable=false,columnDefinition="TEXT") private String url;
    @Column(name="media_type",nullable=false,length=16) private String mediaType;
    @Column(name="content_type",nullable=false,length=100) private String contentType;
    @Column(name="size_bytes",nullable=false) private long sizeBytes;
    @Column(name="sha256",length=64,columnDefinition="CHAR(64)") private String sha256;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
}
