package com.mediassistrover.backend.service;

import com.mediassistrover.backend.dto.robot.request.RobotRequests.*;
import com.mediassistrover.backend.dto.robot.response.RobotResponses.*;
import com.mediassistrover.backend.enums.RobotStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;
import java.time.Instant;
import com.mediassistrover.backend.security.AccountPrincipal;

public interface RobotService {
    RobotResponse create(RobotCreateRequest request,UUID actorId,RequestMetadata metadata);
    RobotResponse update(UUID id,RobotUpdateRequest request,UUID actorId,RequestMetadata metadata);
    RobotResponse get(UUID id, boolean includeDeleted);
    Page<RobotResponse> list(RobotStatus status, boolean includeDeleted, Pageable pageable);
    RobotResponse retire(UUID id, String reason, UUID actorId, RequestMetadata metadata);
    RobotResponse restore(UUID id, String reason, UUID actorId, RequestMetadata metadata);
    com.mediassistrover.backend.dto.common.DeletionContracts.DeletionImpactResponse deletionImpact(UUID id);
    com.mediassistrover.backend.dto.common.DeletionContracts.HardDeleteResponse forceDelete(UUID id, String reason, UUID actorId, RequestMetadata metadata);
    DeviceSecretResponse provisionSecret(UUID id,UUID actorId,RequestMetadata metadata);
    DeviceSecretResponse rotateSecret(UUID id,UUID actorId,RequestMetadata metadata);
    RobotResponse maintenance(UUID id,String reason,UUID actorId,RequestMetadata metadata);
    RobotResponse reconcile(UUID id,String reason,AccountPrincipal actor,RequestMetadata metadata);
    Page<RobotStatusHistoryResponse> statusHistory(UUID id,Pageable pageable);
    Page<RobotLocationResponse> locationHistory(UUID id,Instant from,Instant to,Pageable pageable);
    int markHeartbeatTimeoutsOffline();
}
