package com.mediassistrover.backend.controller.pi;

import com.mediassistrover.backend.ai.PatrolFallEventService;
import com.mediassistrover.backend.config.OpenApiConfig;
import com.mediassistrover.backend.security.DevicePrincipal;
import com.mediassistrover.backend.util.RequestMetadataFactory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/pi/patrol-fall-events")
@PreAuthorize("hasRole('DEVICE')")
@SecurityRequirement(name = OpenApiConfig.DEVICE_BEARER_SCHEME)
public class PiPatrolFallController {
    private final PatrolFallEventService service;

    @PostMapping
    @Operation(summary = "Report a suspected fall during an opted-in patrol checkpoint for staff review")
    public PatrolFallEventService.Result record(@Valid @RequestBody PatrolFallEventService.Request input,
                                                @AuthenticationPrincipal DevicePrincipal device,
                                                HttpServletRequest request) {
        return service.record(input, device, RequestMetadataFactory.from(request));
    }
}
