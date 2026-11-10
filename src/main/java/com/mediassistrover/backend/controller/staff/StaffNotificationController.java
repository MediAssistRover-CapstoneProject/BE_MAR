package com.mediassistrover.backend.controller.staff;

import com.mediassistrover.backend.security.AccountPrincipal;
import com.mediassistrover.backend.service.StaffNoticeStream;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/staff/notifications")
@PreAuthorize("hasRole('STAFF')")
public class StaffNotificationController {
    private final StaffNoticeStream stream;

    @GetMapping(value = "/events", produces = "text/event-stream")
    public SseEmitter events(@AuthenticationPrincipal AccountPrincipal actor, HttpServletResponse response) {
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-store");
        return stream.subscribe(actor);
    }
}
