package com.mediassistrover.backend.controller.report;

import com.mediassistrover.backend.dto.report.response.OperationsOverviewResponse;
import com.mediassistrover.backend.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/reports")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Reports", description = "Basic MVP operations statistics")
public class ReportController {
    private final ReportService service;

    @GetMapping("/overview")
    @Operation(summary = "Get job, robot, request, alert and emergency overview")
    public OperationsOverviewResponse overview() { return service.getOperationsOverview(); }
}
