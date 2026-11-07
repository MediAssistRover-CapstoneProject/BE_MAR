package com.mediassistrover.backend.controller.robot;

import com.mediassistrover.backend.security.AccountPrincipal;
import com.mediassistrover.backend.speech.SpeechSettingsService;
import com.mediassistrover.backend.util.RequestMetadataFactory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/admin/speech-settings") @RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')") @Tag(name="Robot Speech")
public class RobotSpeechSettingsController {
    private final SpeechSettingsService settings;
    public record Update(@DecimalMin("0.25") @DecimalMax("4.0") Double speedMultiplier,
                         @Size(max=32) String voice,@Size(max=1000) String instructions) { }

    @GetMapping
    @Operation(summary="Read the shared robot speech speed and voice; no API keys are returned")
    public SpeechSettingsService.Profile get(){return settings.current();}

    @PutMapping
    @Operation(summary="Persist speech settings; subsequent phrases use a new audio cache key without restarting Pi")
    public SpeechSettingsService.Profile update(@Valid @RequestBody Update body,
            @AuthenticationPrincipal AccountPrincipal actor,HttpServletRequest request) {
        return settings.update(body.speedMultiplier(),body.voice(),body.instructions(),actor.accountId(),RequestMetadataFactory.from(request));
    }
}
