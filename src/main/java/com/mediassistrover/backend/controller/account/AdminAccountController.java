package com.mediassistrover.backend.controller.account;

import com.mediassistrover.backend.dto.account.request.ResetPasswordRequest;
import com.mediassistrover.backend.dto.account.response.AccountResponse;
import com.mediassistrover.backend.dto.common.PageResponse;
import com.mediassistrover.backend.dto.common.DeletionContracts.*;
import com.mediassistrover.backend.security.AccountPrincipal;
import com.mediassistrover.backend.service.AdminAccountService;
import com.mediassistrover.backend.util.RequestMetadataFactory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/accounts")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Accounts", description = "Chỉ ADMIN")
public class AdminAccountController {

    private final AdminAccountService service;
    private final com.mediassistrover.backend.service.DeletionPolicy deletionPolicy;

    @PostMapping("/{accountId}/lock")
    @Operation(summary = "Khóa Account và thu hồi toàn bộ phiên")
    AccountResponse lock(@PathVariable UUID accountId,
                         @AuthenticationPrincipal AccountPrincipal actor,
                         HttpServletRequest request) {
        return service.lock(accountId, actor, RequestMetadataFactory.from(request));
    }

    @PostMapping("/{accountId}/unlock")
    @Operation(summary = "Mở khóa Account")
    AccountResponse unlock(@PathVariable UUID accountId,
                           @AuthenticationPrincipal AccountPrincipal actor,
                           HttpServletRequest request) {
        return service.unlock(accountId, actor, RequestMetadataFactory.from(request));
    }

    @PostMapping("/{accountId}/reset-password")
    @Operation(summary = "Admin reset mật khẩu và thu hồi toàn bộ phiên")
    ResponseEntity<Void> resetPassword(@PathVariable UUID accountId,
                                       @AuthenticationPrincipal AccountPrincipal actor,
                                       @Valid @RequestBody ResetPasswordRequest body,
                                       HttpServletRequest request) {
        service.resetPassword(accountId, body.newPassword(), actor, RequestMetadataFactory.from(request));
        return ResponseEntity.noContent().build();
    }
    @DeleteMapping("/{accountId}") AccountResponse deactivate(@PathVariable UUID accountId,@Valid @RequestBody LifecycleActionRequest body,@AuthenticationPrincipal AccountPrincipal actor,HttpServletRequest request){return service.deactivate(accountId,body.reason(),actor,RequestMetadataFactory.from(request));}
    @PostMapping("/{accountId}/restore") AccountResponse restore(@PathVariable UUID accountId,@Valid @RequestBody LifecycleActionRequest body,@AuthenticationPrincipal AccountPrincipal actor,HttpServletRequest request){return service.restore(accountId,body.reason(),actor,RequestMetadataFactory.from(request));}
    @GetMapping("/{accountId}/deletion-impact") DeletionImpactResponse impact(@PathVariable UUID accountId,@AuthenticationPrincipal AccountPrincipal actor){return service.deletionImpact(accountId,actor);}
    @DeleteMapping("/{accountId}/force") HardDeleteResponse force(@PathVariable UUID accountId,@org.springframework.web.bind.annotation.RequestHeader("X-Confirm-Hard-Delete")Boolean confirmed,@Valid @RequestBody LifecycleActionRequest body,@AuthenticationPrincipal AccountPrincipal actor,HttpServletRequest request){deletionPolicy.requireHardDeleteConfirmation(confirmed);return service.forceDelete(accountId,body.reason(),actor,RequestMetadataFactory.from(request));}
}
