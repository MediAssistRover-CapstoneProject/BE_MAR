package com.mediassistrover.backend.controller.pi;

import com.mediassistrover.backend.ai.FallObservationService;
import com.mediassistrover.backend.config.OpenApiConfig;
import com.mediassistrover.backend.security.DevicePrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/v1/pi/fall-observations") @PreAuthorize("hasRole('DEVICE')")
@SecurityRequirement(name=OpenApiConfig.DEVICE_BEARER_SCHEME)
public class PiFallObservationController {
    private final FallObservationService service;
    @PostMapping @Operation(summary="Request a bounded observation pause for the current patrol MOVE")
    public FallObservationService.Result request(@Valid @RequestBody FallObservationService.Request input,@AuthenticationPrincipal DevicePrincipal device){return service.request(input,device);}
    @GetMapping("/{id}") @Operation(summary="Read physical-stop confirmation and the stationary observation window")
    public FallObservationService.Result get(@PathVariable UUID id,@AuthenticationPrincipal DevicePrincipal device){return service.get(id,device);}
    @PostMapping("/{id}/complete") @Operation(summary="Release only this observation hold or hand it to a recorded fall incident")
    public FallObservationService.Result complete(@PathVariable UUID id,@Valid @RequestBody FallObservationService.Complete input,@AuthenticationPrincipal DevicePrincipal device){return service.complete(id,input,device);}
}
