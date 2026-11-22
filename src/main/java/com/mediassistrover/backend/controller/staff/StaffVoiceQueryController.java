package com.mediassistrover.backend.controller.staff;

import com.mediassistrover.backend.service.StaffVoiceQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/v1/staff/voice/staff-questions")
@PreAuthorize("hasAnyRole('ADMIN','STAFF')")
public class StaffVoiceQueryController {
    private final StaffVoiceQueryService queries;

    @PostMapping
    public StaffVoiceQueryService.Answer answer(@Valid @RequestBody StaffVoiceQueryService.Query body) {
        return queries.answer(body.question(), body.mapId());
    }
}
