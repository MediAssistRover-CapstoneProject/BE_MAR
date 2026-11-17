package com.mediassistrover.backend.controller.pi;

import com.mediassistrover.backend.ai.RobotFallEventService;
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
@lombok.extern.slf4j.Slf4j
@RequestMapping("/api/v1/pi/fall-events")
@PreAuthorize("hasRole('DEVICE')")
@SecurityRequirement(name = OpenApiConfig.DEVICE_BEARER_SCHEME)
public class PiFallController {
    private final RobotFallEventService service;

    @PostMapping
    @Operation(summary = "Report a continuous camera fall suspicion without changing the current robot Job")
    public RobotFallEventService.Result record(@Valid @RequestBody RobotFallEventService.Request input,
                                               @AuthenticationPrincipal DevicePrincipal device,
                                               HttpServletRequest request) {
        try {
            return service.record(input, device, RequestMetadataFactory.from(request));
        } catch (com.mediassistrover.backend.exception.BusinessRuleException error) {
            // Service reasons are protocol messages; never log credentials or frames.
            log.warn("FALL_EVENT_REJECTED event={} robot={} reason={}", input.eventId(),
                    device == null ? null : device.robotId(), error.getMessage());
            throw error;
        }
    }
}
