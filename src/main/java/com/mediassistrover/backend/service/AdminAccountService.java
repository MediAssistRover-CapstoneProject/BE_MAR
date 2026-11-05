package com.mediassistrover.backend.service;

import com.mediassistrover.backend.dto.account.response.AccountResponse;
import com.mediassistrover.backend.dto.common.PageResponse;
import com.mediassistrover.backend.security.AccountPrincipal;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface AdminAccountService {
    AccountResponse lock(UUID accountId, AccountPrincipal actor, RequestMetadata metadata);
    AccountResponse unlock(UUID accountId, AccountPrincipal actor, RequestMetadata metadata);
    void resetPassword(UUID accountId, String newPassword, AccountPrincipal actor, RequestMetadata metadata);
    AccountResponse deactivate(UUID accountId,String reason,AccountPrincipal actor,RequestMetadata metadata);
    AccountResponse restore(UUID accountId,String reason,AccountPrincipal actor,RequestMetadata metadata);
    com.mediassistrover.backend.dto.common.DeletionContracts.DeletionImpactResponse deletionImpact(UUID accountId,AccountPrincipal actor);
    com.mediassistrover.backend.dto.common.DeletionContracts.HardDeleteResponse forceDelete(UUID accountId,String reason,AccountPrincipal actor,RequestMetadata metadata);
}
