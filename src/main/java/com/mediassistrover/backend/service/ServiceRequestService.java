package com.mediassistrover.backend.service;
import com.mediassistrover.backend.dto.request.request.ServiceRequestRequests.*;
import com.mediassistrover.backend.dto.request.response.ServiceRequestResponses.*;
import com.mediassistrover.backend.enums.ServiceRequestStatus;
import com.mediassistrover.backend.enums.ServiceRequestType;
import com.mediassistrover.backend.security.AccountPrincipal;
import org.springframework.data.domain.*;
import java.util.UUID;
import java.time.Instant;
public interface ServiceRequestService {
    ServiceRequestResponse createDeviceInformation(InformationCreateRequest request,String key,com.mediassistrover.backend.security.DevicePrincipal actor,RequestMetadata metadata);
    NavigationResponse createDeviceNavigation(NavigationCreateRequest request,String key,com.mediassistrover.backend.security.DevicePrincipal actor,RequestMetadata metadata);
    EscortResponse createDeviceEscort(EscortCreateRequest request,String key,com.mediassistrover.backend.security.DevicePrincipal actor,RequestMetadata metadata);
    DeviceRequestDetailResponse getDeviceRequest(UUID id,com.mediassistrover.backend.security.DevicePrincipal actor);
    DeviceRequestDetailResponse cancelDeviceRequest(UUID id,String reason,String idempotencyKey,com.mediassistrover.backend.security.DevicePrincipal actor,RequestMetadata metadata);
    ServiceRequestResponse createInformation(InformationCreateRequest request,String idempotencyKey,AccountPrincipal actor,RequestMetadata metadata);
    NavigationResponse createNavigation(NavigationCreateRequest request,String idempotencyKey,AccountPrincipal actor,RequestMetadata metadata);
    EscortResponse createEscort(EscortCreateRequest request,String idempotencyKey,AccountPrincipal actor,RequestMetadata metadata);
    ServiceRequestResponse createOperational(OperationalCreateRequest request,String idempotencyKey,AccountPrincipal actor,RequestMetadata metadata);
    ServiceRequestResponse get(UUID id,boolean includeDeleted,AccountPrincipal actor);
    java.util.Optional<ServiceRequestResponse> findByIdempotencyKey(String key,AccountPrincipal actor);
    Page<ServiceRequestResponse> list(ServiceRequestStatus status,ServiceRequestType requestType,UUID robotId,
                                      Instant from,Instant to,boolean includeDeleted,AccountPrincipal actor,Pageable pageable);
    ServiceRequestResponse submit(UUID id,AccountPrincipal actor,RequestMetadata metadata);
    ServiceRequestResponse cancel(UUID id,String reason,AccountPrincipal actor,RequestMetadata metadata);
    ServiceRequestResponse archive(UUID id,String reason,AccountPrincipal actor,RequestMetadata metadata);
    com.mediassistrover.backend.dto.common.DeletionContracts.DeletionImpactResponse deletionImpact(UUID id,AccountPrincipal actor);
    com.mediassistrover.backend.dto.common.DeletionContracts.HardDeleteResponse forceDelete(UUID id,String reason,AccountPrincipal actor,RequestMetadata metadata);
}
