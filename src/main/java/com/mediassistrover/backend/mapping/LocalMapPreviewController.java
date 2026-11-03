package com.mediassistrover.backend.mapping;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediassistrover.backend.entity.FacilityMap;
import com.mediassistrover.backend.exception.BusinessRuleException;
import com.mediassistrover.backend.repository.FacilityMapRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Base64;
import java.util.UUID;

/** Local diagnostic only: parses supplied map files without persisting them. */
@RestController
@RequestMapping("/api/v1/maps/local-preview")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','STAFF')")
@ConditionalOnProperty(prefix = "app.map-preview", name = "enabled", havingValue = "true")
@Tag(name = "Local Map Preview")
@SecurityRequirement(name = "bearerAuth")
public class LocalMapPreviewController {
    private static final int YAML_LIMIT = 64 * 1024;
    private static final int PGM_LIMIT = 4_100_000;

    private final MappingArchiveReader reader;
    private final FacilityMapRepository maps;

    public record PreviewResponse(
            UUID mapId,
            boolean catalogFound,
            Boolean assetHashMatches,
            Boolean geometryMatches,
            String assetHash,
            ObjectNode metadata,
            String imageDataUrl) { }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Preview YAML/PGM map files without saving",
            description = "Local diagnostic endpoint. Parses map.yaml and map.pgm, returns exact metadata and a PNG data URL. "
                    + "When mapId is supplied, hash and geometry are compared with the existing catalog row. Nothing is written to MySQL or S3.",
            requestBody = @RequestBody(content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE)))
    public PreviewResponse preview(
            @Parameter(description = "Optional existing map UUID used only for comparison")
            @RequestPart(value = "mapId", required = false) String mapId,
            @Parameter(description = "ROS map YAML", content = @Content(schema = @Schema(type = "string", format = "binary")))
            @RequestPart("yaml") MultipartFile yaml,
            @Parameter(description = "ROS occupancy grid PGM", content = @Content(schema = @Schema(type = "string", format = "binary")))
            @RequestPart("pgm") MultipartFile pgm) {
        byte[] yamlBytes = bytes(yaml, YAML_LIMIT, "YAML");
        byte[] pgmBytes = bytes(pgm, PGM_LIMIT, "PGM");
        ObjectNode metadata = reader.inspectMap(yamlBytes, pgmBytes);
        String hash = MapBundleService.fingerprint(yamlBytes, pgmBytes);
        byte[] png = PgmPngRenderer.render(pgmBytes);

        UUID id = parseMapId(mapId);
        FacilityMap catalog = id == null ? null : maps.findById(id).orElse(null);
        Boolean hashMatches = catalog == null ? null : hash.equalsIgnoreCase(catalog.getAssetHash());
        Boolean geometryMatches = catalog == null ? null : geometryMatches(metadata, catalog);
        return new PreviewResponse(id, catalog != null, hashMatches, geometryMatches, hash, metadata,
                "data:image/png;base64," + Base64.getEncoder().encodeToString(png));
    }

    private byte[] bytes(MultipartFile file, int limit, String label) {
        if (file == null || file.isEmpty()) throw new BusinessRuleException(label + " file is required");
        if (file.getSize() > limit) throw new BusinessRuleException(label + " file exceeds " + limit + " bytes");
        try {
            byte[] value = file.getBytes();
            if (value.length == 0 || value.length > limit) throw new BusinessRuleException(label + " file size is invalid");
            return value;
        } catch (IOException ex) {
            throw new BusinessRuleException("Cannot read " + label + " file");
        }
    }

    private UUID parseMapId(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("mapId must be a UUID");
        }
    }

    private boolean geometryMatches(ObjectNode metadata, FacilityMap map) {
        return same(metadata, "width_m", map.getWidthM(), 4)
                && same(metadata, "height_m", map.getHeightM(), 4)
                && same(metadata, "resolution_m_per_pixel", map.getResolutionMPerPixel(), 6)
                && same(metadata, "origin_x_m", map.getOriginXM(), 4)
                && same(metadata, "origin_y_m", map.getOriginYM(), 4)
                && same(metadata, "origin_yaw_rad", map.getOriginYawRad(), 6);
    }

    private boolean same(ObjectNode metadata, String field, BigDecimal stored, int scale) {
        return stored != null && metadata.path(field).decimalValue().setScale(scale, RoundingMode.HALF_UP).compareTo(stored) == 0;
    }
}
