package com.mediassistrover.backend.scheduler;
import com.mediassistrover.backend.service.OperationsMaintenanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.*;
@Component @RequiredArgsConstructor @ConditionalOnProperty(prefix="app.operations",name="scheduler-enabled",havingValue="true",matchIfMissing=true)
public class ExpiredTokenCleanupScheduler {
    private final OperationsMaintenanceService service; private final Clock clock;
    @Scheduled(fixedDelayString="${app.operations.token-cleanup-ms:3600000}") public void run(){service.cleanupExpiredTokens(Instant.now(clock));}
}
