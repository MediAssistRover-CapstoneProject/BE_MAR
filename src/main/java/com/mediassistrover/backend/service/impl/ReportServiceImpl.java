package com.mediassistrover.backend.service.impl;

import com.mediassistrover.backend.dto.report.response.OperationsOverviewResponse;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.repository.*;
import com.mediassistrover.backend.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportServiceImpl implements ReportService {
    private final JobRepository jobRepository;
    private final RobotRepository robotRepository;
    private final ServiceRequestRepository requestRepository;
    private final AlertRepository alertRepository;
    private final EmergencyIncidentRepository incidentRepository;
    private final Clock clock;

    @Override
    public OperationsOverviewResponse getOperationsOverview() {
        Map<JobStatus, Long> jobs = zeroed(JobStatus.class);
        jobRepository.countGroupedByStatus().forEach(row -> jobs.put(row.getStatus(), row.getTotal()));
        Map<RobotStatus, Long> robots = zeroed(RobotStatus.class);
        robotRepository.countGroupedByStatus().forEach(row -> robots.put(row.getStatus(), row.getTotal()));
        Map<ServiceRequestType, Long> requests = zeroed(ServiceRequestType.class);
        requestRepository.countGroupedByType().forEach(row -> requests.put(row.getRequestType(), row.getTotal()));

        long succeeded = jobs.get(JobStatus.SUCCEEDED);
        long terminal = succeeded + jobs.get(JobStatus.FAILED) + jobs.get(JobStatus.CANCELLED);
        double rate = terminal == 0 ? 0.0 : Math.round((succeeded * 10000.0) / terminal) / 100.0;
        long openAlerts = alertRepository.countByStatusIn(List.of(AlertStatus.OPEN, AlertStatus.ACKNOWLEDGED));
        long activeIncidents = incidentRepository.countByStatusIn(List.of(
                EmergencyIncidentStatus.OPEN, EmergencyIncidentStatus.ACKNOWLEDGED,
                EmergencyIncidentStatus.IN_PROGRESS));
        return new OperationsOverviewResponse(Instant.now(clock), jobs, robots, requests,
                openAlerts, activeIncidents, terminal, succeeded, rate);
    }

    private static <E extends Enum<E>> Map<E, Long> zeroed(Class<E> enumType) {
        Map<E, Long> result = new EnumMap<>(enumType);
        for (E value : enumType.getEnumConstants()) result.put(value, 0L);
        return result;
    }
}
