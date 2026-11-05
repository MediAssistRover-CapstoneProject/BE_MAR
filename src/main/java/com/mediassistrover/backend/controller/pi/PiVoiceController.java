package com.mediassistrover.backend.controller.pi;

import com.mediassistrover.backend.config.OpenApiConfig;
import com.mediassistrover.backend.dto.pi.request.PiEnvelope;
import com.mediassistrover.backend.dto.pi.response.PiResponses.AckResponse;
import com.mediassistrover.backend.dto.voice.request.VoiceRequests.*;
import com.mediassistrover.backend.security.DevicePrincipal;
import com.mediassistrover.backend.service.VoiceService;
import com.mediassistrover.backend.service.VoiceNavigationService;
import com.mediassistrover.backend.util.RequestMetadataFactory;
import jakarta.servlet.http.HttpServletRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import com.mediassistrover.backend.dto.media.response.MediaObjectResponse;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/pi/voice")
@RequiredArgsConstructor
@PreAuthorize("hasRole('DEVICE')")
@SecurityRequirement(name = OpenApiConfig.DEVICE_BEARER_SCHEME)
@Tag(name = "Server-Pi Voice", description = "Voice text history and confirmed endpoint navigation; audio STT/TTS uses /api/v1/pi/speech")
public class PiVoiceController {
    private final VoiceService service;
    private final VoiceNavigationService navigation;

    @GetMapping("/navigation-destinations")
    @Operation(summary="List active accessible endpoint names in this device's current map session; no public visibility or room link required")
    public com.fasterxml.jackson.databind.node.ObjectNode destinations(@AuthenticationPrincipal DevicePrincipal device){
        return navigation.destinations(device);
    }

    @PostMapping("/navigation-requests")
    @Operation(summary="Submit a confirmed voice destination as a real NAVIGATION request/job; MOVE succeeds before arrival SPEAK")
    public com.mediassistrover.backend.dto.request.response.ServiceRequestResponses.DeviceRequestDetailResponse navigate(
            @RequestHeader("Idempotency-Key") String key,@Valid @RequestBody VoiceNavigationService.Create body,
            @AuthenticationPrincipal DevicePrincipal device,HttpServletRequest request){
        return navigation.create(body,key,device,RequestMetadataFactory.from(request));
    }

    @PostMapping("/interactions")
    @Operation(summary = "Start an idempotent voice interaction")
    public AckResponse start(@Valid @RequestBody PiEnvelope<InteractionStartPayload> envelope,
                             @AuthenticationPrincipal DevicePrincipal principal) {
        return service.startInteraction(envelope, principal);
    }

    @PostMapping("/interactions/{interactionId}/completion")
    @Operation(summary = "Complete, cancel or fail an active voice interaction")
    public AckResponse complete(@PathVariable UUID interactionId,
                                @Valid @RequestBody PiEnvelope<InteractionCompletionPayload> envelope,
                                @AuthenticationPrincipal DevicePrincipal principal) {
        return service.completeInteraction(interactionId, envelope, principal);
    }

    @PostMapping(value="/interactions/{interactionId}/audio",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary="Upload scoped WAV audio for this robot interaction")
    public MediaObjectResponse audio(@PathVariable UUID interactionId,
            @Parameter(required=true,schema=@Schema(type="string",format="binary")) @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal DevicePrincipal principal){return service.uploadInteractionAudio(interactionId,file,principal);}

    @PostMapping("/commands")
    @Operation(summary = "Append an idempotent recognized voice command")
    public AckResponse command(@Valid @RequestBody PiEnvelope<CommandPayload> envelope,
                               @AuthenticationPrincipal DevicePrincipal principal) {
        return service.recordCommand(envelope, principal);
    }

    @PostMapping("/commands/{commandId}/result")
    @Operation(summary = "Record the controlled processing result of a received voice command")
    public AckResponse commandResult(@PathVariable UUID commandId,
                                     @Valid @RequestBody PiEnvelope<CommandResultPayload> envelope,
                                     @AuthenticationPrincipal DevicePrincipal principal) {
        return service.recordCommandResult(commandId, envelope, principal);
    }
}
