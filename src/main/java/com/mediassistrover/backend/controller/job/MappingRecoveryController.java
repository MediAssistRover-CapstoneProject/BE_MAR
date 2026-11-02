package com.mediassistrover.backend.controller.job;

import com.fasterxml.jackson.databind.JsonNode;
import com.mediassistrover.backend.dto.job.request.MappingRecoveryRequest;
import com.mediassistrover.backend.mapping.MappingRecoveryService;
import com.mediassistrover.backend.security.AccountPrincipal;
import com.mediassistrover.backend.util.RequestMetadataFactory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/jobs/{jobId}/mapping-recovery")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','STAFF')")
@Tag(name = "Mapping session")
public class MappingRecoveryController {
  private final MappingRecoveryService service;

  @GetMapping
  @Operation(
      summary =
          "Inspect recovery for an unstarted cleanup STOP or SAVE/STOP rejected before acceptance")
  public JsonNode evidence(
      @PathVariable UUID jobId, @AuthenticationPrincipal AccountPrincipal actor) {
    return service.evidence(jobId, actor);
  }

  @PostMapping
  @Operation(
      summary =
          "Close unstarted mapping cleanup or rejected SAVE/STOP after verified previous Pi stop receipts"
              + " and a fresh closed-session stationary heartbeat; never replays an operation")
  public JsonNode reconcile(
      @PathVariable UUID jobId,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody MappingRecoveryRequest request,
      @AuthenticationPrincipal AccountPrincipal actor,
      HttpServletRequest http) {
    return service.reconcile(jobId, key, request, actor, RequestMetadataFactory.from(http));
  }
}
