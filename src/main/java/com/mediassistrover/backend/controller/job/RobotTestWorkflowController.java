package com.mediassistrover.backend.controller.job;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediassistrover.backend.dto.workflow.request.WorkflowRequests.GraphCreateRequest;
import com.mediassistrover.backend.security.AccountPrincipal;
import com.mediassistrover.backend.service.RobotTestWorkflowService;
import com.mediassistrover.backend.util.RequestMetadataFactory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequiredArgsConstructor @PreAuthorize("hasRole('ADMIN')")
@Tag(name="Robot test workflows",description="Commissioning using existing MOVE, WAIT and CONTROL_HARDWARE steps")
public class RobotTestWorkflowController {
    private final RobotTestWorkflowService tests;
    @GetMapping("/api/v1/test-workflows/by-idempotency-key")
    @Operation(summary="Find a previous test job without issuing another command")
    public RobotTestWorkflowService.Result lookup(@RequestParam String key,@AuthenticationPrincipal AccountPrincipal actor){return tests.lookup(key,actor);}
    @PostMapping("/api/v1/test-workflows/preview")
    @Operation(summary="Preview a basic-step workflow; no robot command or database write")
    public GraphCreateRequest preview(@Valid @RequestBody RobotTestWorkflowService.Create body){return tests.preview(body);}
    @PostMapping("/api/v1/test-workflows/run")
    @Operation(summary="Create/reuse a test workflow and run one job",description="ADMIN only. Reuse Idempotency-Key for retries of the exact same request. MOVE requires the current READY map session. Servo opening requires commissioning unlock enabled on BE and Pi.")
    public RobotTestWorkflowService.Result run(@RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody RobotTestWorkflowService.Create body,@AuthenticationPrincipal AccountPrincipal actor,HttpServletRequest request){
        return tests.run(body,key,actor,RequestMetadataFactory.from(request));
    }
    @GetMapping("/api/v1/robots/{robotId}/test-configuration")
    public ObjectNode configuration(@PathVariable UUID robotId){return tests.configuration(robotId);}
    @PostMapping("/api/v1/robots/{robotId}/servo-test")
    @Operation(summary="Open/close servo through a single CONTROL_HARDWARE job",description="Returns a job ID; a published command does not prove mechanical door position. Does not reset navigation or localization.")
    public RobotTestWorkflowService.Result servo(@PathVariable UUID robotId,@RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody RobotTestWorkflowService.Servo body,@AuthenticationPrincipal AccountPrincipal actor,HttpServletRequest request){
        var kind="OPEN".equals(body.action())?RobotTestWorkflowService.Kind.OPEN_SERVO:RobotTestWorkflowService.Kind.CLOSE_SERVO;
        return tests.run(new RobotTestWorkflowService.Create(robotId,null,null,null,body.compartmentId(),
                List.of(new RobotTestWorkflowService.TestStep(kind,null,null))),key,actor,RequestMetadataFactory.from(request));
    }
}
