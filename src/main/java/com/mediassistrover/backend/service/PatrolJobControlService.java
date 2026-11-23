package com.mediassistrover.backend.service;

import com.mediassistrover.backend.dto.job.response.JobResponses.JobDetailResponse;
import com.mediassistrover.backend.enums.JobStatus;
import com.mediassistrover.backend.exception.*;
import com.mediassistrover.backend.repository.*;
import com.mediassistrover.backend.security.AccountPrincipal;
import com.mediassistrover.backend.validation.StaffOperationalScopePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service @RequiredArgsConstructor
public class PatrolJobControlService {
    private final JobRepository jobs;
    private final JobService views;
    private final JobSuspensionService suspension;
    private final StaffOperationalScopePolicy scope;
    private final AccountRepository accounts;
    private final AuditService audit;

    @Transactional
    public JobDetailResponse suspend(UUID id,String reason,AccountPrincipal actor,RequestMetadata metadata) {
        var job=jobs.lockById(id).orElseThrow(()->new ResourceNotFoundException("Job not found"));
        scope.requireJobAccess(job,actor);
        if(!suspension.isPatrol(job))throw new BusinessRuleException("Suspension is supported only for backend schema 3 patrol jobs");
        if(job.getCancelRequestedAt()!=null || !Set.of(JobStatus.QUEUED,JobStatus.ACCEPTED,JobStatus.RUNNING,JobStatus.SUSPENDING,JobStatus.SUSPENDED).contains(job.getStatus()))
            throw new BusinessRuleException("This job cannot be suspended in its current state");
        suspension.request(job,"MANUAL",id);
        audit.record(accounts.findById(actor.accountId()).orElseThrow(),"PATROL_SUSPEND_REQUESTED","Job",id,null,Map.of("reason",reason),metadata);
        return views.get(id,false,actor);
    }

    @Transactional
    public JobDetailResponse resume(UUID id,String reason,AccountPrincipal actor,RequestMetadata metadata) {
        var job=jobs.lockById(id).orElseThrow(()->new ResourceNotFoundException("Job not found"));
        scope.requireJobAccess(job,actor);
        if(!suspension.isPatrol(job) || job.getCancelRequestedAt()!=null || !(JobOccupancyPolicy.unstartedPatrolIntent(job)
                || Set.of(JobStatus.SUSPENDING,JobStatus.SUSPENDED,JobStatus.RUNNING).contains(job.getStatus())))
            throw new BusinessRuleException("This patrol cannot be resumed in its current state");
        suspension.release(job,"MANUAL",id);
        job.setResumeRequested(true);
        // The arbiter rechecks event sources and foreground ownership before performing the handoff.
        audit.record(accounts.findById(actor.accountId()).orElseThrow(),"PATROL_RESUME_REQUESTED","Job",id,null,Map.of("reason",reason),metadata);
        return views.get(id,false,actor);
    }
}
