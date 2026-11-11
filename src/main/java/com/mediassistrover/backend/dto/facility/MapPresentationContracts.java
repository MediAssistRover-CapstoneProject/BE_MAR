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
        UUID endpointId,
        String endpointName,
        UUID roomId,
        @Size(max=64) String roomCode,
        @Size(max=160) String roomName,
        @Size(max=64) String roomType,
        @Size(max=64) String departmentCode,
        String roomDescription,
        Boolean roomPublicVisible) {
        public Feature(String id,FeatureType type,String label,String color,List<Point> points,
                       Double lineWidthM,Double fontSizeM,UUID endpointId) {
            this(id,type,label,color,points,lineWidthM,fontSizeM,endpointId,null,null,null,null,null,null,null,null);
        }
        public Feature(String id,FeatureType type,String label,String color,List<Point> points,
                       Double lineWidthM,Double fontSizeM,UUID endpointId,UUID roomId,String roomCode,
                       String roomName,String roomType,String departmentCode,String roomDescription,
                       Boolean roomPublicVisible) {
            this(id,type,label,color,points,lineWidthM,fontSizeM,endpointId,null,roomId,roomCode,roomName,
                    roomType,departmentCode,roomDescription,roomPublicVisible);
        }
        public Feature withRoom(UUID value,String code,String name,String type,String department,String description,
                                boolean visible,String synchronizedEndpointName) {
            return new Feature(id,this.type,label,color,points,lineWidthM,fontSizeM,endpointId,synchronizedEndpointName,
                    value,code,name,type,department,description,visible);
        }
    }
    /** Coordinates stay in the map's local grid; rotation affects presentation only. */
    public record Document(@Min(1) @Max(1) int schemaVersion,
        @NotBlank @Pattern(regexp="#[0-9a-fA-F]{6}") String backgroundColor,
        @NotNull @Size(max=500) List<@NotNull @Valid Feature> features,
        @DecimalMin("-180") @DecimalMax("180") Double rotationDegrees) {
        public Document {
            if(rotationDegrees==null) rotationDegrees=0.;
        }
        public Document(int schemaVersion,String backgroundColor,List<Feature> features) {
            this(schemaVersion,backgroundColor,features,0.);
        }
    }
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
