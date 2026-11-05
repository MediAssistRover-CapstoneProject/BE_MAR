package com.mediassistrover.backend.controller.schedule;
import com.mediassistrover.backend.dto.common.PageResponse;
import com.mediassistrover.backend.dto.common.DeletionContracts.*;
import com.mediassistrover.backend.dto.schedule.request.ScheduleRequests.ScheduleCreateRequest;
import com.mediassistrover.backend.dto.schedule.response.ScheduleResponse;
import com.mediassistrover.backend.enums.ScheduleStatus;
import com.mediassistrover.backend.security.AccountPrincipal;
import com.mediassistrover.backend.service.ScheduleService;
import com.mediassistrover.backend.util.RequestMetadataFactory;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@RestController @RequestMapping("/api/v1/schedules") @RequiredArgsConstructor @Validated
@PreAuthorize("hasAnyRole('ADMIN','STAFF')") @Tag(name="Schedules")
public class ScheduleController {
    private final ScheduleService service;
    private final com.mediassistrover.backend.service.DeletionPolicy deletionPolicy;
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public ScheduleResponse create(@Valid @RequestBody ScheduleCreateRequest b,@AuthenticationPrincipal AccountPrincipal a,HttpServletRequest r){return service.create(b,a,RequestMetadataFactory.from(r));}
    @GetMapping public PageResponse<ScheduleResponse> list(@RequestParam(required=false)ScheduleStatus status,@RequestParam(defaultValue="false")boolean includeDeleted,@RequestParam(defaultValue="0")@Min(0)int page,@RequestParam(defaultValue="20")@Min(1)@Max(100)int size,@AuthenticationPrincipal AccountPrincipal a){return PageResponse.from(service.list(status,includeDeleted,a,PageRequest.of(page,size,Sort.by("scheduleCode"))));}
    @GetMapping("/{id}") public ScheduleResponse get(@PathVariable UUID id,@RequestParam(defaultValue="false")boolean includeDeleted,@AuthenticationPrincipal AccountPrincipal a){return service.get(id,includeDeleted,a);}
    @PostMapping("/{id}/activate") public ScheduleResponse activate(@PathVariable UUID id,@AuthenticationPrincipal AccountPrincipal a,HttpServletRequest r){return service.activate(id,a,RequestMetadataFactory.from(r));}
    @PostMapping("/{id}/pause") public ScheduleResponse pause(@PathVariable UUID id,@AuthenticationPrincipal AccountPrincipal a,HttpServletRequest r){return service.pause(id,a,RequestMetadataFactory.from(r));}
    @PostMapping("/{id}/retire") public ScheduleResponse retire(@PathVariable UUID id,@Valid @RequestBody LifecycleActionRequest b,@AuthenticationPrincipal AccountPrincipal a,HttpServletRequest r){return service.retire(id,b.reason(),a,RequestMetadataFactory.from(r));}
    @DeleteMapping("/{id}") public ScheduleResponse delete(@PathVariable UUID id,@Valid @RequestBody LifecycleActionRequest b,@AuthenticationPrincipal AccountPrincipal a,HttpServletRequest r){return service.retire(id,b.reason(),a,RequestMetadataFactory.from(r));}
    @PostMapping("/{id}/restore") public ScheduleResponse restore(@PathVariable UUID id,@Valid @RequestBody LifecycleActionRequest b,@AuthenticationPrincipal AccountPrincipal a,HttpServletRequest r){return service.restore(id,b.reason(),a,RequestMetadataFactory.from(r));}
    @GetMapping("/{id}/deletion-impact") public DeletionImpactResponse impact(@PathVariable UUID id,@AuthenticationPrincipal AccountPrincipal a){return service.deletionImpact(id,a);}
    @DeleteMapping("/{id}/force") @PreAuthorize("hasRole('ADMIN')") public HardDeleteResponse force(@PathVariable UUID id,@RequestHeader("X-Confirm-Hard-Delete")Boolean confirmed,@Valid @RequestBody LifecycleActionRequest b,@AuthenticationPrincipal AccountPrincipal a,HttpServletRequest r){deletionPolicy.requireHardDeleteConfirmation(confirmed);return service.forceDelete(id,b.reason(),a,RequestMetadataFactory.from(r));}
}
