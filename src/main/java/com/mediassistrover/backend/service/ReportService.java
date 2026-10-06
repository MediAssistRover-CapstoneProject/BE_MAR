package com.mediassistrover.backend.service;

import com.mediassistrover.backend.dto.report.response.OperationsOverviewResponse;

public interface ReportService {
    OperationsOverviewResponse getOperationsOverview();
}
