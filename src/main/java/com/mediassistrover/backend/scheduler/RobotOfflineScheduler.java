package com.mediassistrover.backend.scheduler;

import com.mediassistrover.backend.service.RobotService;
import lombok.RequiredArgsConstructor;
import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
@ConditionalOnProperty(prefix="app.operations",name="scheduler-enabled",havingValue="true",matchIfMissing=true)
public class RobotOfflineScheduler {
    private static final Logger log=LoggerFactory.getLogger(RobotOfflineScheduler.class);
    private final RobotService robotService;
    @Scheduled(fixedDelayString="${app.operations.robot-offline-scan-ms:10000}")
    public void markOfflineRobots(){int count=robotService.markHeartbeatTimeoutsOffline();if(count>0)log.info("Marked {} robot(s) OFFLINE because heartbeat timed out",count);}
}
