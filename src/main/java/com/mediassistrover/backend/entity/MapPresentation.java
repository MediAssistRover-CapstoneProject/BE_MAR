package com.mediassistrover.backend.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

/** Display geometry only: never used by navigation or the occupancy grid. */
@Entity @Table(name="MapPresentation") @Getter @Setter @NoArgsConstructor
public class MapPresentation {
    @Id @Column(name="map_id", columnDefinition="BINARY(16)") private UUID mapId;
    @Column(nullable=false) private long revision;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name="source_asset_hash", nullable=false, length=64, columnDefinition="CHAR(64)") private String sourceAssetHash;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="source_json", nullable=false, columnDefinition="json") private JsonNode sourceJson;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="draft_json", nullable=false, columnDefinition="json") private JsonNode draftJson;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="published_json", columnDefinition="json") private JsonNode publishedJson;
    @Column(name="published_revision") private Long publishedRevision;
    @Column(name="published_at") private Instant publishedAt;
    @Column(name="updated_at", nullable=false) private Instant updatedAt;
}
