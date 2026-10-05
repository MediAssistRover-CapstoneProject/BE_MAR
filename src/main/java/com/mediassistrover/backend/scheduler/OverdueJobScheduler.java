package com.mediassistrover.backend.scheduler;
import com.mediassistrover.backend.service.OperationsMaintenanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.*;
@Component @RequiredArgsConstructor @ConditionalOnProperty(prefix="app.operations",name="scheduler-enabled",havingValue="true",matchIfMissing=true)
public class OverdueJobScheduler {
    private final OperationsMaintenanceService service; private final Clock clock;
    @Scheduled(fixedDelayString="${app.operations.job-overdue-scan-ms:10000}") public void run(){service.markOverdueJobs(Instant.now(clock));}
}
