package com.mediassistrover.backend.controller.facility;

import com.mediassistrover.backend.dto.common.PageResponse;
import com.mediassistrover.backend.dto.common.DeletionContracts.*;
import com.mediassistrover.backend.dto.facility.request.FacilityRequests.HospitalInformationArticleRequest;
import com.mediassistrover.backend.dto.facility.response.FacilityResponses.HospitalInformationArticleResponse;
import com.mediassistrover.backend.enums.HospitalInformationStatus;
import com.mediassistrover.backend.security.AccountPrincipal;
import com.mediassistrover.backend.service.*;
import com.mediassistrover.backend.util.RequestMetadataFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/hospital-information/{hospitalId}/articles")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasAnyRole('ADMIN','STAFF')")
public class HospitalInformationArticleController {
    private final HospitalInformationArticleService service;
    private final DeletionPolicy deletionPolicy;

    @GetMapping public PageResponse<HospitalInformationArticleResponse> list(
            @PathVariable UUID hospitalId, @RequestParam(required = false) HospitalInformationStatus status,
            @RequestParam(defaultValue="false") boolean includeDeleted,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(service.list(hospitalId,status,includeDeleted,
                PageRequest.of(page, size, Sort.by("createdAt").descending())));
    }
    @GetMapping("/{articleId}") public HospitalInformationArticleResponse get(
            @PathVariable UUID hospitalId,@PathVariable UUID articleId,@RequestParam(defaultValue="false")boolean includeDeleted) { return service.get(hospitalId,articleId,includeDeleted); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('ADMIN')")
    public HospitalInformationArticleResponse create(@PathVariable UUID hospitalId,
            @Valid @RequestBody HospitalInformationArticleRequest body,
            @AuthenticationPrincipal AccountPrincipal actor, HttpServletRequest request) {
        return service.create(hospitalId, body, actor.accountId(), RequestMetadataFactory.from(request));
    }
    @PutMapping("/{articleId}") @PreAuthorize("hasRole('ADMIN')")
    public HospitalInformationArticleResponse update(@PathVariable UUID hospitalId, @PathVariable UUID articleId,
            @Valid @RequestBody HospitalInformationArticleRequest body,
            @AuthenticationPrincipal AccountPrincipal actor, HttpServletRequest request) {
        return service.update(hospitalId, articleId, body, actor.accountId(), RequestMetadataFactory.from(request));
    }
    @PostMapping("/{articleId}/publish") @PreAuthorize("hasRole('ADMIN')")
    public HospitalInformationArticleResponse publish(@PathVariable UUID hospitalId, @PathVariable UUID articleId,
            @AuthenticationPrincipal AccountPrincipal actor, HttpServletRequest request) {
        return service.publish(hospitalId, articleId, actor.accountId(), RequestMetadataFactory.from(request));
    }
    @DeleteMapping("/{articleId}") @PreAuthorize("hasRole('ADMIN')")
    public HospitalInformationArticleResponse retire(@PathVariable UUID hospitalId, @PathVariable UUID articleId,
            @Valid @RequestBody LifecycleActionRequest body,@AuthenticationPrincipal AccountPrincipal actor,HttpServletRequest request) {
        return service.retire(hospitalId,articleId,body.reason(),actor.accountId(),RequestMetadataFactory.from(request));
    }
    @PostMapping("/{articleId}/retire") @PreAuthorize("hasRole('ADMIN')") public HospitalInformationArticleResponse retireAlias(@PathVariable UUID hospitalId,@PathVariable UUID articleId,@Valid @RequestBody LifecycleActionRequest body,@AuthenticationPrincipal AccountPrincipal actor,HttpServletRequest request){return service.retire(hospitalId,articleId,body.reason(),actor.accountId(),RequestMetadataFactory.from(request));}
    @PostMapping("/{articleId}/restore") @PreAuthorize("hasRole('ADMIN')") public HospitalInformationArticleResponse restore(@PathVariable UUID hospitalId,@PathVariable UUID articleId,@Valid @RequestBody LifecycleActionRequest body,@AuthenticationPrincipal AccountPrincipal actor,HttpServletRequest request){return service.restore(hospitalId,articleId,body.reason(),actor.accountId(),RequestMetadataFactory.from(request));}
    @GetMapping("/{articleId}/deletion-impact") @PreAuthorize("hasRole('ADMIN')") public DeletionImpactResponse impact(@PathVariable UUID hospitalId,@PathVariable UUID articleId){return service.deletionImpact(hospitalId,articleId);}
    @DeleteMapping("/{articleId}/force") @PreAuthorize("hasRole('ADMIN')") public HardDeleteResponse forceDelete(@PathVariable UUID hospitalId,@PathVariable UUID articleId,@RequestHeader("X-Confirm-Hard-Delete")Boolean confirmed,@Valid @RequestBody LifecycleActionRequest body,@AuthenticationPrincipal AccountPrincipal actor,HttpServletRequest request){deletionPolicy.requireHardDeleteConfirmation(confirmed);return service.forceDelete(hospitalId,articleId,body.reason(),actor.accountId(),RequestMetadataFactory.from(request));}
}
