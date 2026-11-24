package com.mediassistrover.backend.controller.schedule;

import com.mediassistrover.backend.security.AccountPrincipal;
import com.mediassistrover.backend.service.PatrolWorkflowService;
import com.mediassistrover.backend.util.RequestMetadataFactory;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/patrol-workflows")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class PatrolWorkflowController {
    private final PatrolWorkflowService patrol;

    @GetMapping("/templates")
    @Operation(summary = "List reusable patrol templates for three through eleven checkpoints")
    public java.util.List<PatrolWorkflowService.TemplateResult> templates(@AuthenticationPrincipal AccountPrincipal actor) {
        return patrol.templates(actor);
    }

    @PostMapping("/templates")
    @Operation(summary = "Prepare or reuse an immutable patrol template without fixed map or endpoints")
    public PatrolWorkflowService.TemplateResult prepareTemplate(@Valid @RequestBody PatrolWorkflowService.PrepareTemplate request,
                                                                @AuthenticationPrincipal AccountPrincipal actor,
                                                                HttpServletRequest http) {
        return patrol.prepareTemplate(request, actor, RequestMetadataFactory.from(http));
    }

    @PostMapping("/preview")
    @Operation(summary = "Validate the map and checkpoints and preview the patrol workflow")
    public com.mediassistrover.backend.dto.workflow.request.WorkflowRequests.GraphCreateRequest preview(
            @Valid @RequestBody PatrolWorkflowService.Create request, @AuthenticationPrincipal AccountPrincipal actor) {
        return patrol.preview(request, actor);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Publish a finite patrol workflow for use with the Schedules API")
    public PatrolWorkflowService.Result create(@Valid @RequestBody PatrolWorkflowService.Create request,
                                               @AuthenticationPrincipal AccountPrincipal actor,
                                               HttpServletRequest http) {
        return patrol.create(request, actor, RequestMetadataFactory.from(http));
    }
}
