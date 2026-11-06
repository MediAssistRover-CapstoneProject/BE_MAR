package com.mediassistrover.backend.dto.facility;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class MapPresentationContracts {
    private MapPresentationContracts() { }
    public enum FeatureType { ROOM, CORRIDOR, WALL, DOOR, LABEL, LANDMARK }
    public record Point(@NotNull @DecimalMin("0") Double x, @NotNull @DecimalMin("0") Double y) { }
    public record Feature(
        @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,64}") String id,
        @NotNull FeatureType type,
        @NotNull @Size(max=120) String label,
        @NotBlank @Pattern(regexp="#[0-9a-fA-F]{6}") String color,
        @NotNull @Size(min=1,max=128) List<@NotNull @Valid Point> points,
        @NotNull @DecimalMin("0.01") @DecimalMax("2") Double lineWidthM,
        @NotNull @DecimalMin("0.05") @DecimalMax("5") Double fontSizeM,
        UUID endpointId) { }
    /** Coordinates in metres in the map's local grid, x right / y up, before origin/yaw. */
    public record Document(@Min(1) @Max(1) int schemaVersion,
        @NotBlank @Pattern(regexp="#[0-9a-fA-F]{6}") String backgroundColor,
        @NotNull @Size(max=500) List<@NotNull @Valid Feature> features) { }
    public record Save(@Min(0) long expectedRevision,
        @NotBlank @Pattern(regexp="[a-fA-F0-9]{64}") String sourceAssetHash,
        @NotNull @Valid Document document) { }
    public record Change(@Min(1) long expectedRevision) { }
    public record Source(UUID mapId, String assetHash, double widthM, double heightM,
        double resolutionMPerPixel, double originXM, double originYM, double originYawRad,
        String frameId, int widthPx, int heightPx) { }
    public record Editor(Source source, long revision, Document draft, Document published,
        Long publishedRevision, Instant publishedAt, boolean sourceChanged) { }
    public record Published(Source source, long revision, Document document, Instant publishedAt) { }
}
