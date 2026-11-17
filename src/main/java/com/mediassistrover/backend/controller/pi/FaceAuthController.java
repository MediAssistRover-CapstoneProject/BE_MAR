package com.mediassistrover.backend.controller.pi;
import com.mediassistrover.backend.service.FaceAuthService;
import com.mediassistrover.backend.security.DevicePrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.ResponseEntity;
import java.util.UUID;

@RestController @RequestMapping("/api/v1/pi/jobs/{jobId}/face-auth")
@PreAuthorize("hasRole('DEVICE')") @RequiredArgsConstructor
public class FaceAuthController {
    private final FaceAuthService face;
    @GetMapping public ResponseEntity<FaceAuthService.Window> active(@PathVariable UUID jobId,@AuthenticationPrincipal DevicePrincipal p){
        return face.active(jobId,p).map(ResponseEntity::ok).orElseGet(()->ResponseEntity.noContent().build());
    }
    @PostMapping("/verify") public Object verify(@PathVariable UUID jobId,@RequestBody FaceAuthService.Scan scan,@AuthenticationPrincipal DevicePrincipal p){return face.verify(jobId,scan,p);}
}
