package com.mediassistrover.backend.service;
import com.mediassistrover.backend.dto.account.request.StaffRequests;
import com.mediassistrover.backend.dto.account.response.StaffResponse;
import org.springframework.data.domain.*;
import java.util.UUID;
public interface StaffService {
    StaffResponse create(StaffRequests.Create request, UUID actorId, RequestMetadata metadata);
    StaffResponse update(UUID id, StaffRequests.Update request, UUID actorId, RequestMetadata metadata);
    StaffResponse get(UUID id,boolean includeDeleted);
    Page<StaffResponse> list(boolean includeDeleted,Pageable pageable);
    StaffResponse deactivate(UUID id,String reason,UUID actorId,RequestMetadata metadata);
    StaffResponse restore(UUID id,String reason,UUID actorId,RequestMetadata metadata);
    com.mediassistrover.backend.dto.common.DeletionContracts.DeletionImpactResponse deletionImpact(UUID id);
    com.mediassistrover.backend.dto.common.DeletionContracts.HardDeleteResponse forceDelete(UUID id,String reason,UUID actorId,RequestMetadata metadata);
}
