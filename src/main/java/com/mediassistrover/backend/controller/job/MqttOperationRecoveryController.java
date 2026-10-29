package com.mediassistrover.backend.controller.job;
import com.fasterxml.jackson.databind.JsonNode;
import com.mediassistrover.backend.dto.job.request.MqttOperationRecoveryRequest;
import com.mediassistrover.backend.mqtt.MqttOperationRecoveryService;
import com.mediassistrover.backend.security.AccountPrincipal;
import com.mediassistrover.backend.util.RequestMetadataFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequestMapping("/api/v1/jobs") @RequiredArgsConstructor @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
public class MqttOperationRecoveryController {
    private final MqttOperationRecoveryService service;
    @PostMapping("/{jobId}/operations/{jobStepId}/close-rejected")
    @PreAuthorize("hasRole('ADMIN')")
    @io.swagger.v3.oas.annotations.Operation(summary="Close an expired MOVE rejected before execution",
        description="Checks committed Pi LOCALIZATION_NOT_READY rejection ACK. No live Pi, stop confirmation or terminal journal required for this unaccepted command. Does not mark robot IDLE or enable motion.")
    public JsonNode closeRejected(@PathVariable UUID jobId,@PathVariable UUID jobStepId,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody com.mediassistrover.backend.dto.job.request.CloseRejectedJobRequest request,
            @AuthenticationPrincipal AccountPrincipal actor,HttpServletRequest http) {
        return service.reconcile(jobId,jobStepId,key,new MqttOperationRecoveryRequest(
            request.commandId(),request.operationKey(),null,MqttOperationRecoveryRequest.Evidence.COMMAND_NOT_ACCEPTED,
            false,com.mediassistrover.backend.dto.job.request.JobRequests.ReconcileOutcome.CONFIRM_CANCELLED,request.note()),
            actor,RequestMetadataFactory.from(http));
    }
    private final com.mediassistrover.backend.mqtt.BulkRecoveryService bulk;
    @PostMapping("/recovery/bulk-close")
    @PreAuthorize("hasRole('ADMIN')")
    @io.swagger.v3.oas.annotations.Operation(summary="Close a batch of recovery jobs with verified stop evidence",
            description="ADMIN only, at most 50 explicit jobs. dryRun returns evidence without writes, not an eligibility guarantee. Each item has its own idempotency key and transaction. BLOCKED items remain unchanged. Closing one job invalidates that robot's transport session; another job on the same robot may require a fresh HELLO and updated evidence.")
    public JsonNode bulkClose(@Valid @RequestBody com.mediassistrover.backend.dto.job.request.BulkRecoveryRequest request,
            @AuthenticationPrincipal AccountPrincipal actor,HttpServletRequest http) {
        return bulk.close(request,actor,RequestMetadataFactory.from(http));
    }
    @GetMapping("/{jobId}/operations/recovery")
    public JsonNode evidence(@PathVariable UUID jobId,@AuthenticationPrincipal AccountPrincipal actor){return service.evidence(jobId,actor);}
    @PostMapping("/{jobId}/operations/{jobStepId}/reconcile")
    public JsonNode reconcile(@PathVariable UUID jobId,@PathVariable UUID jobStepId,@RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody MqttOperationRecoveryRequest request,@AuthenticationPrincipal AccountPrincipal actor,HttpServletRequest http){
        return service.reconcile(jobId,jobStepId,key,request,actor,RequestMetadataFactory.from(http));
    }
}
