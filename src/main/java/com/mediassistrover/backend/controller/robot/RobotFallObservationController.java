package com.mediassistrover.backend.controller.robot;

import com.mediassistrover.backend.ai.FallObservationService;
import com.mediassistrover.backend.config.OpenApiConfig;
import com.mediassistrover.backend.security.AccountPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/robots")
@PreAuthorize("hasAnyRole('ADMIN','STAFF')") @SecurityRequirement(name=OpenApiConfig.USER_BEARER_SCHEME)
public class RobotFallObservationController {
    private final FallObservationService service;
    @GetMapping("/{id}/fall-observation") @Operation(summary="Read the robot's latest fall observation, separate from incidents")
    public ResponseEntity<FallObservationService.Result> latest(@PathVariable UUID id,@AuthenticationPrincipal AccountPrincipal actor){return ResponseEntity.of(service.latest(id,actor));}
}
