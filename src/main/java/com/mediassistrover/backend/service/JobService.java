package com.mediassistrover.backend.service;
import com.mediassistrover.backend.dto.job.request.JobRequests.*;
import com.mediassistrover.backend.dto.job.response.JobResponses.*;
import com.mediassistrover.backend.enums.JobStatus;
import com.mediassistrover.backend.security.AccountPrincipal;
import org.springframework.data.domain.*;
import java.util.UUID;
public interface JobService {
    Page<JobResponse> list(JobStatus status,AccountPrincipal actor,Pageable pageable);
    JobDetailResponse get(UUID id,AccountPrincipal actor);
    Page<JobEventResponse> events(UUID id,AccountPrincipal actor,Pageable pageable);
    JobResponse cancel(UUID id,CancelJobRequest request,AccountPrincipal actor,RequestMetadata metadata);
    JobResponse reconcile(UUID id,ReconcileJobRequest request,AccountPrincipal actor,RequestMetadata metadata);
    JobResponse assignRobot(UUID id,UUID robotId,AccountPrincipal actor,RequestMetadata metadata);
    JobResponse assignStaff(UUID id,UUID staffId,AccountPrincipal actor,RequestMetadata metadata);
}
