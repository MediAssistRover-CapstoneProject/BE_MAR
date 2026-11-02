package com.mediassistrover.backend.config;

import com.mediassistrover.backend.service.*;
import com.mediassistrover.backend.repository.*;
import com.mediassistrover.backend.enums.JobStatus;
import com.mediassistrover.backend.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor @Slf4j
@ConditionalOnProperty(name="app.operations.scheduler-enabled",havingValue="true",matchIfMissing=true)
public class WorkflowOrchestrationScheduler {
    private final WorkflowOrchestrationWorkRepository work;
    private final WorkflowOrchestratorService orchestrator;
    private final JobRepository jobs;
    private final OrchestrationWorkService wakeups;
    private final JobUiEventStream uiEvents;
    private final RobotRealtimeEventStream robotEvents;
    private final java.time.Clock clock;
    @Scheduled(fixedDelayString="${app.workflow.orchestration-delay-ms:500}")
    public void runPending() {
        for (var id:work.findPending(PageRequest.of(0,32))) {
            try { orchestrator.advance(id); }
            catch (BusinessRuleException ex) { orchestrator.reject(id,ex.getMessage()); }
            catch (RuntimeException ex) { log.error("Orchestration transaction failed for Job {}; durable work will retry",id,ex); }
        }
    }
    @Scheduled(fixedDelayString="${app.workflow.deadline-check-ms:1000}")
    public void checkDeadlines() {
        for (var id:jobs.findReadyBeJobIds(PageRequest.of(0,100))) wakeups.request(jobs.findById(id).orElseThrow());
        // A restart restores waiting server steps from DB; their deadlines do not depend on an in-memory timer.
        int page=0;
        java.util.List<JobRepository.DeadlineCandidate> batch;
        do {
            batch=jobs.findBeDeadlineCandidates(PageRequest.of(page++,100));
            var now=java.time.Instant.now(clock);
            for (var job:batch) if(needsDeadlineWork(job,now))wakeups.request(jobs.findById(job.getJobId()).orElseThrow());
        } while (batch.size()==100);
    }
    static boolean needsDeadlineWork(JobRepository.DeadlineCandidate job,java.time.Instant now) {
        if(job.getCancelRequestedAt()!=null||job.getInterruptionRequestedAt()!=null||job.getStartedAt()==null||job.getMaxDurationSec()==null
            ||!job.getStartedAt().plusSeconds(job.getMaxDurationSec()).isAfter(now))return true;
        if(job.getStepType()==null||!java.util.Set.of("MOVE","CONTROL_HARDWARE","EXECUTE_SENSING","INTERACT").contains(job.getStepType()))return true;
        if(job.getStepStatus()==com.mediassistrover.backend.enums.JobStepStatus.PENDING
            &&job.getCommandState()!=null&&java.util.Set.of("PENDING","PUBLISHING","PUBLISHED","ACKNOWLEDGED").contains(job.getCommandState())
            &&job.getCommandExpiresAt()!=null&&job.getCommandExpiresAt().isAfter(now))return false;
        if(job.getStepStatus()!=com.mediassistrover.backend.enums.JobStepStatus.RUNNING)return true;
        // Completion/error/cancel events wake the graph immediately. An unchanged
        // physical step only needs the deadline scan when its real timeout is due.
        return job.getStepStartedAt()==null||job.getStepTimeoutSec()==null
            ||!job.getStepStartedAt().plusSeconds(job.getStepTimeoutSec()).isAfter(now);
    }
    @Scheduled(fixedDelay=20_000)
    public void heartbeatUi() { uiEvents.heartbeat(); robotEvents.heartbeat(); }
}
