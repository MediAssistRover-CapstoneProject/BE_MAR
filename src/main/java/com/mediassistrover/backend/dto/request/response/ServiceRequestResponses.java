package com.mediassistrover.backend.dto.request.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.mediassistrover.backend.enums.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class ServiceRequestResponses {
    private ServiceRequestResponses() {}
    public record ServiceRequestResponse(UUID serviceRequestId,UUID requestedByAccountId,UUID requestedRobotId,
            String requestCode,ServiceRequestType requestType,RequestSourceType sourceType,int priority,
            ServiceRequestStatus status,String requestNote,JsonNode requestDataJson,String idempotencyKey,
            Instant requestedAt,Instant scheduledAt,Instant cancelledAt,Instant completedAt,long rowVersion,Instant updatedAt) {}
    public record NavigationResponse(ServiceRequestResponse request,UUID mapId,UUID roomId,UUID destinationEndpointId,
            UUID destinationGroupId,UUID resolvedEndpointId,TargetMode targetMode,NavigationMode navigationMode,
            String accessibilityNeed,int passengerCount,String arrivalInstruction) {}
    public record EscortResponse(ServiceRequestResponse request,UUID mapId,UUID originRoomId,UUID destinationRoomId,
            UUID originEndpointId,UUID destinationEndpointId,UUID destinationGroupId,UUID resolvedEndpointId,
            TargetMode targetMode,String patientReference,EscortMode escortMode,BigDecimal walkingSpeedMps,
            int confirmationTimeoutSec,String escortNote) {}
}
