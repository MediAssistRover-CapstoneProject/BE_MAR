package com.mediassistrover.backend.controller.pi;

import com.mediassistrover.backend.ai.RestrictedZoneEventService;
import com.mediassistrover.backend.security.DevicePrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/pi/restricted-zone-events")
@PreAuthorize("hasRole('DEVICE')") @RequiredArgsConstructor
public class PiRestrictedZoneController {
    private final RestrictedZoneEventService events;
    @GetMapping("/profile") public com.fasterxml.jackson.databind.JsonNode profile(@RequestParam java.util.UUID zoneId,
            @AuthenticationPrincipal DevicePrincipal device){return events.profile(zoneId,device);}
    @PostMapping public RestrictedZoneEventService.Result report(@Valid @RequestBody RestrictedZoneEventService.Request body,
            @AuthenticationPrincipal DevicePrincipal device) {return events.record(body,device);}
}
