package com.mediassistrover.backend.controller.workflow;

import com.mediassistrover.backend.security.AccountPrincipal;
import com.mediassistrover.backend.service.PublishedWorkflowJobService;
import com.mediassistrover.backend.util.RequestMetadataFactory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequestMapping("/api/v1/workflows/{workflowId}/jobs")
@PreAuthorize("hasRole('ADMIN')") @RequiredArgsConstructor
@Tag(name="Published workflow runs")
public class WorkflowJobController {
    private final PublishedWorkflowJobService runs;
    @PostMapping
    @Operation(summary="Queue one run of a published workflow with its configured inputs",
            description="Reuse Idempotency-Key for retries. MOVE requires the current localized map/session. Delivery uses the service request API to bind staff, items and QR/face verification.")
    public PublishedWorkflowJobService.Result create(@PathVariable UUID workflowId,
            @RequestHeader("Idempotency-Key") String key,@Valid @RequestBody PublishedWorkflowJobService.Create body,
            @AuthenticationPrincipal AccountPrincipal actor,HttpServletRequest request) {
        return runs.run(workflowId,body,key,actor,RequestMetadataFactory.from(request));
    }
    @GetMapping("/by-idempotency-key")
    @Operation(summary="Find the existing run after a timeout without creating another job")
    public ResponseEntity<PublishedWorkflowJobService.Result> lookup(@PathVariable UUID workflowId,
            @RequestParam String key,@AuthenticationPrincipal AccountPrincipal actor) {
        var result=runs.lookup(workflowId,key,actor);
        return result==null?ResponseEntity.noContent().build():ResponseEntity.ok(result);
    }
}
