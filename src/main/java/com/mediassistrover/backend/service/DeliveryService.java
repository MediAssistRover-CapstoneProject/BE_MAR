package com.mediassistrover.backend.service;
import com.mediassistrover.backend.dto.delivery.request.DeliveryRequests.*;
import com.mediassistrover.backend.dto.delivery.response.DeliveryResponses.DeliveryResponse;
import com.mediassistrover.backend.security.AccountPrincipal;
import java.util.UUID;
public interface DeliveryService {
    DeliveryResponse create(Create request,String idempotencyKey,AccountPrincipal actor,RequestMetadata metadata);
    DeliveryResponse get(UUID id,AccountPrincipal actor);
    DeliveryResponse confirmPickup(UUID id,Confirm request,AccountPrincipal actor,RequestMetadata metadata);
    DeliveryResponse confirmDelivery(UUID id,Confirm request,AccountPrincipal actor,RequestMetadata metadata);
}
