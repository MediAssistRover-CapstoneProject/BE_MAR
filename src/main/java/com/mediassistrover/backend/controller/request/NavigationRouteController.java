package com.mediassistrover.backend.controller.request;

import com.mediassistrover.backend.security.AccountPrincipal;
import com.mediassistrover.backend.service.NavigationRouteService;
import com.mediassistrover.backend.util.RequestMetadataFactory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/navigation-routes") @RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')") @Tag(name="Navigation routes")
public class NavigationRouteController {
    private final NavigationRouteService routes;
    @PostMapping @Operation(summary="Create an ordered endpoint route and generate its workflow automatically",
        description="Uses one robot/map/session. Default submit=true schedules the request; submit=false only creates a draft. Requires READY localization. Reuse Idempotency-Key only for the same route.")
    public NavigationRouteService.Result create(@RequestHeader("Idempotency-Key") String key,
        @Valid @RequestBody NavigationRouteService.Create body,@AuthenticationPrincipal AccountPrincipal actor,HttpServletRequest request) {
        return routes.create(body,key,actor,RequestMetadataFactory.from(request));
    }
}
