package com.mediassistrover.backend.service;

import com.mediassistrover.backend.dto.shift.request.ShiftRequests.*;
import com.mediassistrover.backend.dto.shift.response.ShiftResponses.*;
import com.mediassistrover.backend.enums.ShiftStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ShiftService {
    ShiftResponse create(Create request, UUID actorId, RequestMetadata metadata);
    ShiftResponse update(UUID shiftId, Update request, UUID actorId, RequestMetadata metadata);
    ShiftResponse transition(UUID shiftId, Transition request, UUID actorId, RequestMetadata metadata);
    ShiftResponse get(UUID shiftId, boolean includeDeleted);
    Page<ShiftResponse> list(UUID staffProfileId, UUID roomId, ShiftStatus status, boolean includeDeleted, Pageable pageable);
    Page<ShiftResponse> listOwn(UUID accountId, ShiftStatus status, Pageable pageable);
    ShiftResponse getOwn(UUID shiftId, UUID accountId);
    ShiftResponse delete(UUID shiftId, String reason, UUID actorId, RequestMetadata metadata);
    ShiftResponse restore(UUID shiftId, String reason, UUID actorId, RequestMetadata metadata);
    com.mediassistrover.backend.dto.common.DeletionContracts.DeletionImpactResponse deletionImpact(UUID shiftId);
    com.mediassistrover.backend.dto.common.DeletionContracts.HardDeleteResponse forceDelete(UUID shiftId, String reason, UUID actorId, RequestMetadata metadata);
    SlotResponse addSlot(UUID shiftId, SlotCreate request, UUID actorId, RequestMetadata metadata);
    SlotResponse updateSlot(UUID shiftId, UUID slotId, SlotUpdate request, UUID actorId, RequestMetadata metadata);
    SlotResponse transitionSlot(UUID shiftId, UUID slotId, SlotTransition request, UUID actorId, RequestMetadata metadata);
    SlotResponse deleteSlot(UUID shiftId, UUID slotId, String reason, UUID actorId, RequestMetadata metadata);
    SlotResponse restoreSlot(UUID shiftId, UUID slotId, String reason, UUID actorId, RequestMetadata metadata);
    com.mediassistrover.backend.dto.common.DeletionContracts.DeletionImpactResponse slotDeletionImpact(UUID shiftId, UUID slotId);
    com.mediassistrover.backend.dto.common.DeletionContracts.HardDeleteResponse forceDeleteSlot(UUID shiftId, UUID slotId, String reason, UUID actorId, RequestMetadata metadata);
}
