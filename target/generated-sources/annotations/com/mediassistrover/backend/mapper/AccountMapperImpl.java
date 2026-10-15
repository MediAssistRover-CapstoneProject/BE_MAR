package com.mediassistrover.backend.mapper;

import com.mediassistrover.backend.dto.account.response.AccountResponse;
import com.mediassistrover.backend.entity.Account;
import com.mediassistrover.backend.enums.AccountStatus;
import com.mediassistrover.backend.enums.AuthProvider;
import com.mediassistrover.backend.enums.Role;
import java.time.Instant;
import java.util.UUID;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-10-13T11:40:03+0700",
    comments = "version: 1.6.3, compiler: javac, environment: Java 21.0.12.1 (Eclipse Adoptium)"
)
@Component
public class AccountMapperImpl implements AccountMapper {

    @Override
    public AccountResponse toResponse(Account account) {
        if ( account == null ) {
            return null;
        }

        Role role = null;
        UUID accountId = null;
        String accountCode = null;
        String username = null;
        String fullName = null;
        String email = null;
        String phone = null;
        AuthProvider authProvider = null;
        AccountStatus status = null;
        Instant lastLoginAt = null;
        boolean mustChangePassword = false;
        Instant createdAt = null;
        Instant updatedAt = null;

        role = account.getRole();
        accountId = account.getAccountId();
        accountCode = account.getAccountCode();
        username = account.getUsername();
        fullName = account.getFullName();
        email = account.getEmail();
        phone = account.getPhone();
        authProvider = account.getAuthProvider();
        status = account.getStatus();
        lastLoginAt = account.getLastLoginAt();
        mustChangePassword = account.isMustChangePassword();
        createdAt = account.getCreatedAt();
        updatedAt = account.getUpdatedAt();

        AccountResponse accountResponse = new AccountResponse( accountId, accountCode, username, fullName, email, phone, role, authProvider, status, lastLoginAt, mustChangePassword, createdAt, updatedAt );

        return accountResponse;
    }
}
