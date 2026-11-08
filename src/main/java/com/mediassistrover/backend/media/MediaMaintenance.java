package com.mediassistrover.backend.media;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
public class MediaMaintenance {
    private final MediaSessionService sessions;
    @Scheduled(fixedDelay=5000,scheduler="mediaMaintenanceScheduler")
    public void run(){sessions.maintain();}
    @Configuration
    static class Scheduling {
        @Bean(name="mediaMaintenanceScheduler")
        ThreadPoolTaskScheduler mediaMaintenanceScheduler(){
            var scheduler=new ThreadPoolTaskScheduler();scheduler.setPoolSize(1);scheduler.setThreadNamePrefix("media-cleanup-");
            scheduler.setWaitForTasksToCompleteOnShutdown(false);return scheduler;
        }
    }
}
