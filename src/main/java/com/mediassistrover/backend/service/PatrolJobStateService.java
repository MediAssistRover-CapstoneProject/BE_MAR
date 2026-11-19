package com.mediassistrover.backend.service;

import com.mediassistrover.backend.dto.job.response.JobResponses.*;
import com.mediassistrover.backend.entity.Job;
import com.mediassistrover.backend.entity.JobStep;
import com.mediassistrover.backend.enums.JobStatus;
import com.mediassistrover.backend.repository.JobSuspensionHoldRepository;
import com.mediassistrover.backend.repository.JobStepRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import java.util.UUID;

/** REST and SSE use the same durable patrol projection. */
@Service @RequiredArgsConstructor
public class PatrolJobStateService {
    private final JobSuspensionService suspension;
    private final JobSuspensionHoldRepository holds;
    private final JobStepRepository steps;

    public PatrolJobState snapshot(Job job) {
        if(!suspension.isPatrol(job))return null;
        var step=job.getSelectedJobStep();UUID endpoint=endpoint(step);
        if(endpoint==null && step!=null && "EXECUTE_SENSING".equals(step.getStepTypeSnapshot())) {
            // Observation stays at the reached checkpoint; do not expose another round's endpoint or scan all repeat history.
            endpoint=steps.findReachedPatrolCheckpoints(job.getJobId(),job.getPatrolRound(),step.getExecutionSequenceNo(),PageRequest.of(0,1))
                    .stream().findFirst().map(PatrolJobStateService::endpoint).orElse(null);
        }
        var active=holds.findByJobJobIdOrderByCreatedAt(job.getJobId()).stream()
                .filter(h->h.getReleasedAt()==null).map(h->new SuspensionHoldResponse(h.getSource(),h.getSourceId(),h.getCreatedAt())).toList();
        String blocked=job.getStatus()==JobStatus.SUSPENDED?suspension.resumeBlockReason(job):null;
        return new PatrolJobState(Math.max(1,job.getPatrolRound()),job.getPatrolRepeatCount(),job.getPatrolRepeatCount()==0,
                job.isResumeRequested(),job.getSuspendRequestedAt(),job.getSuspendedAt(),job.getSuspendedDurationMs(),
                step==null?null:step.getJobStepId(),endpoint,step==null?null:step.getStepTypeSnapshot(),blocked,active);
    }
    private static UUID endpoint(JobStep step) {
        if(step==null)return null;
        String value=step.getResolvedInputsJson()==null?"":step.getResolvedInputsJson().path("endpoint_id").asText("");
        if(value.isEmpty() && step.getStep()!=null && step.getStep().getParamsJson()!=null)
            value=step.getStep().getParamsJson().path("endpoint_id").asText("");
        try{return value.isEmpty()?null:UUID.fromString(value);}catch(IllegalArgumentException ignored){return null;}
    }
}
