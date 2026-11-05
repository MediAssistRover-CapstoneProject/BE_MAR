package com.mediassistrover.backend.controller.monitoring;
import com.fasterxml.jackson.databind.JsonNode;
import com.mediassistrover.backend.ai.AiSensingService;
import com.mediassistrover.backend.security.AccountPrincipal;
import com.mediassistrover.backend.dto.common.DeletionContracts.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController @RequestMapping("/api/v1/admin/monitoring/robots") @PreAuthorize("hasRole('ADMIN')") @RequiredArgsConstructor
public class AiSensingProfileController {
    private final AiSensingService service;
    private final com.mediassistrover.backend.service.DeletionPolicy deletionPolicy;
    @PutMapping("/{robotId}/ai-profile") public JsonNode configure(@PathVariable UUID robotId,@Valid @RequestBody AiSensingService.Configure input,@AuthenticationPrincipal AccountPrincipal actor){return service.configure(robotId,input,actor);}
    @DeleteMapping("/ai-profiles/{profileId}") public JsonNode delete(@PathVariable UUID profileId,@Valid @RequestBody LifecycleActionRequest body,@AuthenticationPrincipal AccountPrincipal actor){return service.retire(profileId,body.reason(),actor);}
    @PostMapping("/ai-profiles/{profileId}/restore") public JsonNode restore(@PathVariable UUID profileId,@Valid @RequestBody LifecycleActionRequest body,@AuthenticationPrincipal AccountPrincipal actor){return service.restore(profileId,body.reason(),actor);}
    @GetMapping("/ai-profiles/{profileId}/deletion-impact") public DeletionImpactResponse impact(@PathVariable UUID profileId,@AuthenticationPrincipal AccountPrincipal actor){return service.deletionImpact(profileId,actor);}
    @DeleteMapping("/ai-profiles/{profileId}/force") public HardDeleteResponse force(@PathVariable UUID profileId,@RequestHeader("X-Confirm-Hard-Delete")Boolean confirmed,@Valid @RequestBody LifecycleActionRequest body,@AuthenticationPrincipal AccountPrincipal actor){deletionPolicy.requireHardDeleteConfirmation(confirmed);return service.forceDelete(profileId,body.reason(),actor);}
}
