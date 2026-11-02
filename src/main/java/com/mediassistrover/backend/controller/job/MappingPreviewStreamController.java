package com.mediassistrover.backend.controller.job;

import com.mediassistrover.backend.mapping.MappingPreviewStream;
import com.mediassistrover.backend.security.AccountPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.UUID;

@RestController @RequiredArgsConstructor @PreAuthorize("hasRole('ADMIN')")
public class MappingPreviewStreamController {
    private final MappingPreviewStream stream;
    @GetMapping(value="/api/v1/jobs/{jobId}/mapping-stream",produces="text/event-stream")
    public SseEmitter subscribe(@PathVariable UUID jobId,@AuthenticationPrincipal AccountPrincipal actor,jakarta.servlet.http.HttpServletResponse response){
        response.setHeader("X-Accel-Buffering","no");response.setHeader("Cache-Control","no-store");
        return stream.subscribe(jobId,actor);
    }
}
