package com.mediassistrover.backend.config;

import com.mediassistrover.backend.mqtt.MqttMaintenanceScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;

class SchedulerIsolationTest {
    @Test void linkHeartbeatStillRunsWhileAllGeneralSchedulerThreadsAreBusy() throws Exception {
        var config = new SchedulerConfig();
        var general = config.taskScheduler();
        var heartbeat = config.mqttHeartbeatScheduler();
        var occupied = new CountDownLatch(4);
        var release = new CountDownLatch(1);
        var sent = new CountDownLatch(1);
        general.initialize();heartbeat.initialize();
        try {
            for (int i=0;i<4;i++) general.execute(() -> {
                occupied.countDown();
                try {release.await(5,TimeUnit.SECONDS);} catch (InterruptedException ex) {Thread.currentThread().interrupt();}
            });
            assertThat(occupied.await(2,TimeUnit.SECONDS)).isTrue();
            assertThat(MqttMaintenanceScheduler.class.getMethod("heartbeats").getAnnotation(Scheduled.class).scheduler())
                    .isEqualTo("mqttHeartbeatScheduler");
            heartbeat.schedule(sent::countDown,Instant.now());
            assertThat(sent.await(2,TimeUnit.SECONDS)).isTrue();
        } finally {release.countDown();general.shutdown();heartbeat.shutdown();}
    }
}
