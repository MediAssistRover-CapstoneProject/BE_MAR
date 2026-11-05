package com.mediassistrover.backend.service.impl;

import com.mediassistrover.backend.dto.account.response.AccountResponse;
import com.mediassistrover.backend.dto.common.PageResponse;
import com.mediassistrover.backend.entity.Account;
import com.mediassistrover.backend.enums.AccountStatus;
import com.mediassistrover.backend.enums.RevocationReason;
import com.mediassistrover.backend.enums.Role;
import com.mediassistrover.backend.exception.BusinessRuleException;
import com.mediassistrover.backend.exception.ConflictException;
import com.mediassistrover.backend.exception.ResourceNotFoundException;
import com.mediassistrover.backend.mapper.AccountMapper;
import com.mediassistrover.backend.repository.AccountRepository;
import com.mediassistrover.backend.security.AccountPrincipal;
import com.mediassistrover.backend.service.AdminAccountService;
import com.mediassistrover.backend.service.AuditService;
import com.mediassistrover.backend.service.RefreshTokenService;
import com.mediassistrover.backend.service.RequestMetadata;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminAccountServiceImpl implements AdminAccountService {

    private final AccountRepository accountRepository;
    private final AccountMapper accountMapper;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final AuditService auditService;
    private final Clock clock;
    private final AccountHardDeleteService hardDelete;

    @Override
    @Transactional
    public AccountResponse lock(UUID accountId, AccountPrincipal actorPrincipal, RequestMetadata metadata) {
        Account actor = require(actorPrincipal.accountId());
        Account account = require(accountId);
        if (actor.getAccountId().equals(accountId)) {
            throw new BusinessRuleException("Admin không thể tự khóa tài khoản đang sử dụng");
        }
        if (account.getStatus() == AccountStatus.LOCKED) {
            return accountMapper.toResponse(account);
        }
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new ConflictException("Chỉ có thể khóa Account đang ACTIVE");
        }
        AccountStatus before = account.getStatus();
        account.setStatus(AccountStatus.LOCKED);
        account.setTokenVersion(account.getTokenVersion() + 1);
        account.setLockedUntil(null);
        refreshTokenService.revokeAll(account, RevocationReason.ACCOUNT_LOCKED);
        auditStatus(actor, account, "ACCOUNT_LOCKED", before, metadata);
        return accountMapper.toResponse(account);
    }

    @Override
    @Transactional
    public AccountResponse unlock(UUID accountId, AccountPrincipal actorPrincipal, RequestMetadata metadata) {
        Account actor = require(actorPrincipal.accountId());
        Account account = require(accountId);
        if (account.getStatus() != AccountStatus.LOCKED) {
            throw new ConflictException("Chỉ có thể mở khóa Account đang LOCKED");
        }
        AccountStatus before = account.getStatus();
        account.setStatus(AccountStatus.ACTIVE);
        account.setFailedLoginAttempts(0);
        account.setLockedUntil(null);
        auditStatus(actor, account, "ACCOUNT_UNLOCKED", before, metadata);
        return accountMapper.toResponse(account);
    }

    @Override
    @Transactional
    public void resetPassword(UUID accountId, String newPassword, AccountPrincipal actorPrincipal,
                              RequestMetadata metadata) {
        Account actor = require(actorPrincipal.accountId());
        Account account = require(accountId);
        account.setPasswordHash(passwordEncoder.encode(newPassword));
        account.setPasswordChangedAt(Instant.now(clock));
        account.setMustChangePassword(true);
        account.setTokenVersion(account.getTokenVersion() + 1);
        account.setFailedLoginAttempts(0);
        account.setLockedUntil(null);
        refreshTokenService.revokeAll(account, RevocationReason.ADMIN_RESET);
        auditService.record(actor, "PASSWORD_RESET_BY_ADMIN", "Account", accountId, null, null, metadata);
    }
    @Override @Transactional public AccountResponse deactivate(UUID id,String reason,AccountPrincipal actorPrincipal,RequestMetadata md){Account actor=require(actorPrincipal.accountId());Account target=require(id);nonStaffTarget(actor,target);AccountStatus before=target.getStatus();target.setStatus(AccountStatus.INACTIVE);target.setTokenVersion(target.getTokenVersion()+1);refreshTokenService.revokeAll(target,RevocationReason.ACCOUNT_LOCKED);auditService.record(actor,"ACCOUNT_DEACTIVATED","Account",id,Map.of("status",before.name()),Map.of("status","INACTIVE","reason",reason),md);return accountMapper.toResponse(target);}
    @Override @Transactional public AccountResponse restore(UUID id,String reason,AccountPrincipal actorPrincipal,RequestMetadata md){Account actor=require(actorPrincipal.accountId());Account target=require(id);nonStaffTarget(actor,target);if(target.getStatus()!=AccountStatus.INACTIVE)throw new ConflictException("Only INACTIVE Account can be restored");target.setStatus(AccountStatus.ACTIVE);auditService.record(actor,"ACCOUNT_RESTORED","Account",id,Map.of("status","INACTIVE"),Map.of("status","ACTIVE","reason",reason),md);return accountMapper.toResponse(target);}
    @Override public com.mediassistrover.backend.dto.common.DeletionContracts.DeletionImpactResponse deletionImpact(UUID id,AccountPrincipal actorPrincipal){Account actor=require(actorPrincipal.accountId());Account target=require(id);nonStaffTarget(actor,target);return hardDelete.impact(id);}
    @Override @Transactional public com.mediassistrover.backend.dto.common.DeletionContracts.HardDeleteResponse forceDelete(UUID id,String reason,AccountPrincipal actorPrincipal,RequestMetadata md){Account actor=require(actorPrincipal.accountId());Account target=require(id);nonStaffTarget(actor,target);var result=hardDelete.delete(id);auditService.record(actor,"ACCOUNT_FORCE_DELETED","Account",id,null,Map.of("reason",reason,"deleted",result.deleted()),md);return result;}

    private void auditStatus(Account actor, Account account, String action, AccountStatus before,
                             RequestMetadata metadata) {
        auditService.record(actor, action, "Account", account.getAccountId(),
                Map.of("status", before.name()), Map.of("status", account.getStatus().name()), metadata);
    }

    private Account require(UUID accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy Account"));
    }
    private void nonStaffTarget(Account actor,Account target){if(actor.getAccountId().equals(target.getAccountId()))throw new BusinessRuleException("Admin cannot delete own account");if(target.getRole()==Role.STAFF)throw new BusinessRuleException("Use Admin Staff lifecycle for STAFF account");}
}
