package com.mediassistrover.backend.controller.pi;
import com.fasterxml.jackson.databind.JsonNode;
import com.mediassistrover.backend.service.NavigationProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequestMapping("/api/v1/robots") @RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class NavigationZoneStatusController {
    private final NavigationProfileService profiles;
    @GetMapping("/{robotId}/navigation-zones")
    public JsonNode status(@PathVariable UUID robotId){return profiles.zoneStatus(robotId);}
}
