package com.mediassistrover.backend.service;
import com.mediassistrover.backend.dto.job.request.JobRequests.*;
import com.mediassistrover.backend.dto.job.response.JobResponses.*;
import com.mediassistrover.backend.enums.JobStatus;
import com.mediassistrover.backend.security.AccountPrincipal;
import org.springframework.data.domain.*;
import java.util.UUID;
import java.time.Instant;
public interface JobService {
    Page<JobResponse> list(JobStatus status,UUID workflowId,UUID requestId,UUID robotId,
                           Instant from,Instant to,boolean includeDeleted,AccountPrincipal actor,Pageable pageable);
    JobDetailResponse get(UUID id,boolean includeDeleted,AccountPrincipal actor);
    Page<JobEventResponse> events(UUID id,AccountPrincipal actor,Pageable pageable);
    JobResponse cancel(UUID id,CancelJobRequest request,AccountPrincipal actor,RequestMetadata metadata);
    JobResponse reconcile(UUID id,ReconcileJobRequest request,AccountPrincipal actor,RequestMetadata metadata);
    JobResponse assignRobot(UUID id,UUID robotId,AccountPrincipal actor,RequestMetadata metadata);
    JobResponse assignStaff(UUID id,UUID staffId,AccountPrincipal actor,RequestMetadata metadata);
    JobResponse archive(UUID id,String reason,AccountPrincipal actor,RequestMetadata metadata);
    com.mediassistrover.backend.dto.common.DeletionContracts.DeletionImpactResponse deletionImpact(UUID id,AccountPrincipal actor);
    com.mediassistrover.backend.dto.common.DeletionContracts.HardDeleteResponse forceDelete(UUID id,String reason,AccountPrincipal actor,RequestMetadata metadata);
}
