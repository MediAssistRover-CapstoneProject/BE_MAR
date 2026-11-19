package com.mediassistrover.backend.controller.emergency;

import com.mediassistrover.backend.security.AccountPrincipal;
import com.mediassistrover.backend.service.SosIncidentRoutingService;
import com.mediassistrover.backend.util.RequestMetadataFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/emergency-incidents")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class SosRoutingController {
    private final SosIncidentRoutingService routing;

    public record Retry(@NotNull @PositiveOrZero Long rowVersion) { }

    @GetMapping("/{id}/routing")
    public SosIncidentRoutingService.Response inspect(@PathVariable UUID id, @AuthenticationPrincipal AccountPrincipal actor) {
        return routing.inspect(id, actor);
    }

    @PostMapping("/{id}/retry-routing")
    public SosIncidentRoutingService.Response retry(@PathVariable UUID id, @Valid @RequestBody Retry body,
            @AuthenticationPrincipal AccountPrincipal actor, HttpServletRequest request) {
        return routing.retry(id, body.rowVersion(), actor, RequestMetadataFactory.from(request));
    }
}
