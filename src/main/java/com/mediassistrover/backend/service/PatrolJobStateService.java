package com.mediassistrover.backend.service;

import com.mediassistrover.backend.dto.job.response.JobResponses.*;
import com.mediassistrover.backend.entity.Job;
import com.mediassistrover.backend.enums.JobStatus;
import com.mediassistrover.backend.repository.JobSuspensionHoldRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.UUID;

/** REST and SSE use the same durable patrol projection. */
@Service @RequiredArgsConstructor
public class PatrolJobStateService {
    private final JobSuspensionService suspension;
    private final JobSuspensionHoldRepository holds;

    public PatrolJobState snapshot(Job job) {
        if(!suspension.isPatrol(job))return null;
        var step=job.getSelectedJobStep();UUID endpoint=null;
        if(step!=null && step.getResolvedInputsJson()!=null) {
            String value=step.getResolvedInputsJson().path("endpoint_id").asText("");
            try{if(!value.isEmpty())endpoint=UUID.fromString(value);}catch(IllegalArgumentException ignored){ }
        }
        var active=holds.findByJobJobIdOrderByCreatedAt(job.getJobId()).stream()
                .filter(h->h.getReleasedAt()==null).map(h->new SuspensionHoldResponse(h.getSource(),h.getSourceId(),h.getCreatedAt())).toList();
        String blocked=job.getStatus()==JobStatus.SUSPENDED?suspension.resumeBlockReason(job):null;
        return new PatrolJobState(Math.max(1,job.getPatrolRound()),job.getPatrolRepeatCount(),job.getPatrolRepeatCount()==0,
                job.isResumeRequested(),job.getSuspendRequestedAt(),job.getSuspendedAt(),job.getSuspendedDurationMs(),
                step==null?null:step.getJobStepId(),endpoint,step==null?null:step.getStepTypeSnapshot(),blocked,active);
    }
}
