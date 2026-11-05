package com.mediassistrover.backend.controller.setting;

import com.mediassistrover.backend.dto.common.PageResponse;
import com.mediassistrover.backend.dto.common.DeletionContracts.*;
import com.mediassistrover.backend.dto.setting.request.SystemSettingRequests.Create;
import com.mediassistrover.backend.dto.setting.request.SystemSettingRequests.Update;
import com.mediassistrover.backend.dto.setting.response.SystemSettingResponse;
import com.mediassistrover.backend.security.AccountPrincipal;
import com.mediassistrover.backend.service.SystemSettingService;
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
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/system-settings")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin System Settings", description = "Runtime business configuration; changes are audited")
public class SystemSettingController {
    private final SystemSettingService service;
    private final com.mediassistrover.backend.service.DeletionPolicy deletionPolicy;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SystemSettingResponse create(@Valid @RequestBody Create body,
                                        @AuthenticationPrincipal AccountPrincipal actor,
                                        HttpServletRequest request) {
        return service.create(body, actor.accountId(), RequestMetadataFactory.from(request));
    }

    @GetMapping
    public PageResponse<SystemSettingResponse> list(@RequestParam(required = false) String category,@RequestParam(defaultValue="false")boolean includeDeleted,
                                                     @RequestParam(defaultValue = "0") @Min(0) int page,
                                                     @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(service.list(category,includeDeleted,
                PageRequest.of(page, size, Sort.by("category").and(Sort.by("settingKey")))));
    }

    @GetMapping("/{id}")
    public SystemSettingResponse get(@PathVariable UUID id,@RequestParam(defaultValue="false")boolean includeDeleted) { return service.get(id,includeDeleted); }

    @PutMapping("/{id}")
    public SystemSettingResponse update(@PathVariable UUID id, @Valid @RequestBody Update body,
                                        @AuthenticationPrincipal AccountPrincipal actor,
                                        HttpServletRequest request) {
        return service.update(id, body, actor.accountId(), RequestMetadataFactory.from(request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Remove an override; application property becomes the fallback")
    public SystemSettingResponse delete(@PathVariable UUID id,@Valid @RequestBody LifecycleActionRequest body,@AuthenticationPrincipal AccountPrincipal actor,HttpServletRequest request) {
        return service.delete(id,body.reason(),actor.accountId(),RequestMetadataFactory.from(request));
    }
    @PostMapping("/{id}/restore") public SystemSettingResponse restore(@PathVariable UUID id,@Valid @RequestBody LifecycleActionRequest body,@AuthenticationPrincipal AccountPrincipal actor,HttpServletRequest request){return service.restore(id,body.reason(),actor.accountId(),RequestMetadataFactory.from(request));}
    @GetMapping("/{id}/deletion-impact") public DeletionImpactResponse impact(@PathVariable UUID id){return service.deletionImpact(id);}
    @DeleteMapping("/{id}/force") public HardDeleteResponse force(@PathVariable UUID id,@RequestHeader("X-Confirm-Hard-Delete")Boolean confirmed,@Valid @RequestBody LifecycleActionRequest body,@AuthenticationPrincipal AccountPrincipal actor,HttpServletRequest request){deletionPolicy.requireHardDeleteConfirmation(confirmed);return service.forceDelete(id,body.reason(),actor.accountId(),RequestMetadataFactory.from(request));}
}
