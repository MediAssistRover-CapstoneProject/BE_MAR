package com.mediassistrover.backend.media;

import com.mediassistrover.backend.security.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.*;

@RestController @RequiredArgsConstructor @Tag(name="Media / SOS calls")
public class RobotMediaSessionController {
    private final MediaSessionService media;
    public record Create(@NotNull UUID robotId,UUID incidentId,@NotNull MediaSession.Mode mode) { }
    public record End(@NotBlank @Pattern(regexp="[A-Z0-9_]{1,96}") String reason) { }
    public record DeviceState(@NotBlank @Pattern(regexp="CONNECTED|RECONNECTING|FAILED") String state,
                              @Pattern(regexp="[A-Z0-9_]{1,96}") String reason) { }
    @GetMapping("/api/v1/media/configuration") @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public Map<String,Object> configuration(@AuthenticationPrincipal AccountPrincipal actor){return media.configuration(actor);}
    @GetMapping("/api/v1/media-sessions") @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public List<MediaSessionService.View> list(@AuthenticationPrincipal AccountPrincipal actor){return media.list(actor);}
    @PostMapping("/api/v1/media-sessions") @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public MediaSessionService.View create(@Valid @RequestBody Create body,@AuthenticationPrincipal AccountPrincipal actor){return media.create(body.robotId(),body.incidentId(),body.mode(),actor);}
    @GetMapping("/api/v1/media-sessions/{id}") @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public MediaSessionService.View get(@PathVariable UUID id,@AuthenticationPrincipal AccountPrincipal actor){return media.get(id,actor);}
    @PostMapping("/api/v1/media-sessions/{id}/accept") @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public MediaSessionService.View accept(@PathVariable UUID id,@AuthenticationPrincipal AccountPrincipal actor){return media.accept(id,actor);}
    @PostMapping("/api/v1/media-sessions/{id}/join-token") @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public MediaSessionService.Credentials join(@PathVariable UUID id,@AuthenticationPrincipal AccountPrincipal actor){return media.staffToken(id,actor);}
    @PostMapping("/api/v1/media-sessions/{id}/end") @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public MediaSessionService.View end(@PathVariable UUID id,@Valid @RequestBody End body,@AuthenticationPrincipal AccountPrincipal actor){return media.end(id,body.reason(),actor);}
    @GetMapping(value="/api/v1/pi/media-session/events",produces="text/event-stream") @PreAuthorize("hasRole('DEVICE')")
    public SseEmitter events(@AuthenticationPrincipal DevicePrincipal actor){return media.deviceEvents(actor);}
    @GetMapping("/api/v1/pi/media-session") @PreAuthorize("hasRole('DEVICE')")
    public MediaSessionService.Event current(@AuthenticationPrincipal DevicePrincipal actor){return media.deviceCurrent(actor);}
    @PostMapping("/api/v1/pi/media-sessions/{id}/join-token") @PreAuthorize("hasRole('DEVICE')")
    public MediaSessionService.Credentials deviceJoin(@PathVariable UUID id,@AuthenticationPrincipal DevicePrincipal actor){return media.deviceToken(id,actor);}
    @PostMapping("/api/v1/pi/media-sessions/{id}/state") @PreAuthorize("hasRole('DEVICE')")
    public MediaSessionService.View deviceState(@PathVariable UUID id,@Valid @RequestBody DeviceState body,@AuthenticationPrincipal DevicePrincipal actor){return media.deviceState(id,body.state(),body.reason(),actor);}
}
