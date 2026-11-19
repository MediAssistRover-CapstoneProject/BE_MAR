package com.mediassistrover.backend.config;
import com.mediassistrover.backend.repository.JobRepository;
import com.mediassistrover.backend.service.JobCancellationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Component @RequiredArgsConstructor @Slf4j
@ConditionalOnProperty(name="app.operations.scheduler-enabled",havingValue="true",matchIfMissing=true)
public class JobCancellationScheduler {
    private final JobRepository jobs;
    private final JobCancellationService cancellations;
    @Scheduled(fixedDelayString="${app.workflow.cancellation-check-ms:1000}")
    public void reconcile() {
        // Snapshot IDs first: settled rows disappear while processing, so offset pagination would skip them.
        for(var id:jobs.findOutstandingCancellationIds(org.springframework.data.domain.Pageable.unpaged()))
            try {cancellations.advance(id);} catch(RuntimeException e) {log.warn("Cancellation retry job={} code={}",id,e.getClass().getSimpleName());}
    }
}
