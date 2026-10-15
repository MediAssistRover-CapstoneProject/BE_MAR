package com.mediassistrover.backend.dto.request.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.mediassistrover.backend.enums.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

public final class ServiceRequestRequests {
    private ServiceRequestRequests() {}
    public record InformationCreateRequest(@Min(0) @Max(100) int priority,@Size(max=4000) String note,JsonNode data) {}
    public record NavigationCreateRequest(@NotNull UUID mapId,UUID roomId,UUID destinationEndpointId,
            UUID destinationGroupId,@NotNull TargetMode targetMode,@NotNull NavigationMode navigationMode,
            @Size(max=4000) String accessibilityNeed,@Min(1) @Max(20) int passengerCount,
            @Size(max=4000) String arrivalInstruction,@Min(0) @Max(100) int priority,@Size(max=4000) String note,JsonNode data) {}
    public record EscortCreateRequest(@NotNull UUID mapId,UUID originRoomId,UUID destinationRoomId,
            UUID originEndpointId,UUID destinationEndpointId,UUID destinationGroupId,@NotNull TargetMode targetMode,
            @Size(max=100) String patientReference,@NotNull EscortMode escortMode,
            @NotNull @DecimalMin("0.100") @DecimalMax("3.000") BigDecimal walkingSpeedMps,
            @Min(1) @Max(3600) int confirmationTimeoutSec,@Size(max=4000) String escortNote,
            @Min(0) @Max(100) int priority,@Size(max=4000) String note,JsonNode data) {}
    public record OperationalCreateRequest(@NotNull ServiceRequestType requestType,@NotNull UUID robotId,
            @Min(0) @Max(100) int priority,@Size(max=4000) String note,@NotNull JsonNode data) {}
    public record CancelRequest(@NotBlank @Size(max=1000) String reason) {}
}
