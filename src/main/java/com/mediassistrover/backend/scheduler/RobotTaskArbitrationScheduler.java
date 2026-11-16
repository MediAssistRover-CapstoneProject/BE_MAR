package com.mediassistrover.backend.scheduler;

import com.mediassistrover.backend.repository.JobRepository;
import com.mediassistrover.backend.service.RobotTaskArbitrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor @Slf4j
@ConditionalOnProperty(name="app.operations.scheduler-enabled",havingValue="true",matchIfMissing=true)
public class RobotTaskArbitrationScheduler {
    private final JobRepository jobs;
    private final RobotTaskArbitrationService arbitration;
    @Scheduled(fixedDelayString="${app.workflow.arbitration-delay-ms:500}")
    public void reconcile() {
        for (var robotId : jobs.findPatrolArbitrationRobotIds(PageRequest.of(0, 1000))) {
            try { arbitration.reconcileRobot(robotId); }
            catch (RuntimeException error) { log.warn("Robot {} arbitration will retry: {}", robotId, error.getMessage()); }
        }
    }
}
