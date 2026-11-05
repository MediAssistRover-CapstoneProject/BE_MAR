package com.mediassistrover.backend.controller.shift;

import com.mediassistrover.backend.dto.common.PageResponse;
import com.mediassistrover.backend.dto.common.DeletionContracts.*;
import com.mediassistrover.backend.dto.shift.request.ShiftRequests.*;
import com.mediassistrover.backend.dto.shift.response.ShiftResponses.*;
import com.mediassistrover.backend.enums.ShiftStatus;
import com.mediassistrover.backend.security.AccountPrincipal;
import com.mediassistrover.backend.service.ShiftService;
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
@RequestMapping("/api/v1/admin/shifts")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Staff Shifts", description = "Staff assignment and child time-slot management")
public class AdminShiftController {
    private final ShiftService service;
    private final com.mediassistrover.backend.service.DeletionPolicy deletionPolicy;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a planned Shift and its Slots atomically")
    public ShiftResponse create(@Valid @RequestBody Create body,
                                @AuthenticationPrincipal AccountPrincipal actor, HttpServletRequest request) {
        return service.create(body, actor.accountId(), RequestMetadataFactory.from(request));
    }

    @GetMapping
    public PageResponse<ShiftResponse> list(@RequestParam(required = false) UUID staffProfileId,
                                            @RequestParam(required = false) UUID roomId,
                                            @RequestParam(required = false) ShiftStatus status,
                                            @RequestParam(defaultValue="false") boolean includeDeleted,
                                            @RequestParam(defaultValue = "0") @Min(0) int page,
                                            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(service.list(staffProfileId, roomId, status, includeDeleted,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startsAt"))));
    }

    @GetMapping("/{shiftId}")
    public ShiftResponse get(@PathVariable UUID shiftId,@RequestParam(defaultValue="false")boolean includeDeleted) { return service.get(shiftId,includeDeleted); }

    @PutMapping("/{shiftId}")
    public ShiftResponse update(@PathVariable UUID shiftId, @Valid @RequestBody Update body,
                                @AuthenticationPrincipal AccountPrincipal actor, HttpServletRequest request) {
        return service.update(shiftId, body, actor.accountId(), RequestMetadataFactory.from(request));
    }

    @PostMapping("/{shiftId}/transition")
    public ShiftResponse transition(@PathVariable UUID shiftId, @Valid @RequestBody Transition body,
                                    @AuthenticationPrincipal AccountPrincipal actor, HttpServletRequest request) {
        return service.transition(shiftId, body, actor.accountId(), RequestMetadataFactory.from(request));
    }

    @DeleteMapping("/{shiftId}")
    @Operation(summary = "Soft-delete an unstarted PLANNED Shift")
    public ShiftResponse delete(@PathVariable UUID shiftId,@Valid @RequestBody LifecycleActionRequest body,@AuthenticationPrincipal AccountPrincipal actor,HttpServletRequest request) {
        return service.delete(shiftId,body.reason(), actor.accountId(), RequestMetadataFactory.from(request));
    }
    @PostMapping("/{shiftId}/restore") public ShiftResponse restore(@PathVariable UUID shiftId,@Valid @RequestBody LifecycleActionRequest body,@AuthenticationPrincipal AccountPrincipal actor,HttpServletRequest request){return service.restore(shiftId,body.reason(),actor.accountId(),RequestMetadataFactory.from(request));}
    @GetMapping("/{shiftId}/deletion-impact") public DeletionImpactResponse impact(@PathVariable UUID shiftId){return service.deletionImpact(shiftId);}
    @DeleteMapping("/{shiftId}/force") public HardDeleteResponse force(@PathVariable UUID shiftId,@RequestHeader("X-Confirm-Hard-Delete")Boolean confirmed,@Valid @RequestBody LifecycleActionRequest body,@AuthenticationPrincipal AccountPrincipal actor,HttpServletRequest request){deletionPolicy.requireHardDeleteConfirmation(confirmed);return service.forceDelete(shiftId,body.reason(),actor.accountId(),RequestMetadataFactory.from(request));}

    @PostMapping("/{shiftId}/slots")
    @ResponseStatus(HttpStatus.CREATED)
    public SlotResponse addSlot(@PathVariable UUID shiftId, @Valid @RequestBody SlotCreate body,
                                @AuthenticationPrincipal AccountPrincipal actor, HttpServletRequest request) {
        return service.addSlot(shiftId, body, actor.accountId(), RequestMetadataFactory.from(request));
    }

    @PutMapping("/{shiftId}/slots/{slotId}")
    public SlotResponse updateSlot(@PathVariable UUID shiftId, @PathVariable UUID slotId,
                                   @Valid @RequestBody SlotUpdate body,
                                   @AuthenticationPrincipal AccountPrincipal actor, HttpServletRequest request) {
        return service.updateSlot(shiftId, slotId, body, actor.accountId(), RequestMetadataFactory.from(request));
    }

    @PostMapping("/{shiftId}/slots/{slotId}/transition")
    public SlotResponse transitionSlot(@PathVariable UUID shiftId, @PathVariable UUID slotId,
                                       @Valid @RequestBody SlotTransition body,
                                       @AuthenticationPrincipal AccountPrincipal actor, HttpServletRequest request) {
        return service.transitionSlot(shiftId, slotId, body, actor.accountId(), RequestMetadataFactory.from(request));
    }

    @DeleteMapping("/{shiftId}/slots/{slotId}")
    public SlotResponse deleteSlot(@PathVariable UUID shiftId,@PathVariable UUID slotId,@Valid @RequestBody LifecycleActionRequest body,@AuthenticationPrincipal AccountPrincipal actor,HttpServletRequest request) {
        return service.deleteSlot(shiftId,slotId,body.reason(),actor.accountId(),RequestMetadataFactory.from(request));
    }
    @PostMapping("/{shiftId}/slots/{slotId}/restore") public SlotResponse restoreSlot(@PathVariable UUID shiftId,@PathVariable UUID slotId,@Valid @RequestBody LifecycleActionRequest body,@AuthenticationPrincipal AccountPrincipal actor,HttpServletRequest request){return service.restoreSlot(shiftId,slotId,body.reason(),actor.accountId(),RequestMetadataFactory.from(request));}
    @GetMapping("/{shiftId}/slots/{slotId}/deletion-impact") public DeletionImpactResponse slotImpact(@PathVariable UUID shiftId,@PathVariable UUID slotId){return service.slotDeletionImpact(shiftId,slotId);}
    @DeleteMapping("/{shiftId}/slots/{slotId}/force") public HardDeleteResponse forceSlot(@PathVariable UUID shiftId,@PathVariable UUID slotId,@RequestHeader("X-Confirm-Hard-Delete")Boolean confirmed,@Valid @RequestBody LifecycleActionRequest body,@AuthenticationPrincipal AccountPrincipal actor,HttpServletRequest request){deletionPolicy.requireHardDeleteConfirmation(confirmed);return service.forceDeleteSlot(shiftId,slotId,body.reason(),actor.accountId(),RequestMetadataFactory.from(request));}
}
