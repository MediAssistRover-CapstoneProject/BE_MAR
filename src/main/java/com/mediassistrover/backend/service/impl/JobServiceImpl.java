package com.mediassistrover.backend.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediassistrover.backend.dto.job.request.JobRequests.*;
import com.mediassistrover.backend.dto.job.response.JobResponses.*;
import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.exception.*;
import com.mediassistrover.backend.mapper.RequestJobMapper;
import com.mediassistrover.backend.repository.*;
import com.mediassistrover.backend.security.AccountPrincipal;
import com.mediassistrover.backend.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class JobServiceImpl implements JobService {
    private final JobRepository jobRepository; private final JobTaskRepository taskRepository;
    private final JobStepRepository stepRepository; private final JobEventRepository eventRepository;
    private final RobotRepository robotRepository; private final AccountRepository accountRepository;
    private final RequestJobMapper mapper; private final AuditService auditService;
    private final ObjectMapper objectMapper; private final Clock clock;
    @Override public Page<JobResponse> list(JobStatus status,AccountPrincipal a,Pageable p){Page<Job> jobs;if(a.role()==Role.ADMIN)jobs=status==null?jobRepository.findAll(p):jobRepository.findByStatus(status,p);else jobs=jobRepository.findByAssignedStaffAccountAccountIdOrServiceRequestRequestedByAccountAccountId(a.accountId(),a.accountId(),p);return jobs.map(mapper::toResponse);}
    @Override public JobDetailResponse get(UUID id,AccountPrincipal a){Job j=job(id);scope(j,a);return new JobDetailResponse(mapper.toResponse(j),taskRepository.findByJobJobIdOrderBySequenceNoAsc(id).stream().map(mapper::toResponse).toList(),stepRepository.findByJobId(id).stream().map(mapper::toResponse).toList());}
    @Override public Page<JobEventResponse> events(UUID id,AccountPrincipal a,Pageable p){scope(job(id),a);return eventRepository.findByJobJobId(id,p).map(mapper::toResponse);}
    @Override @Transactional public JobResponse cancel(UUID id,CancelJobRequest r,AccountPrincipal a,RequestMetadata md){Job j=job(id);scope(j,a);if(terminal(j.getStatus()))throw new BusinessRuleException("Job is already terminal");if(j.getStatus()==JobStatus.RECOVERY_REQUIRED)throw new BusinessRuleException("Reconcile RECOVERY_REQUIRED job instead");if(j.getCancelCommandId()==null){j.setCancelCommandId(UUID.randomUUID());j.setCancelRequestedAt(Instant.now(clock));j.setCancelRequestedByAccount(account(a.accountId()));event(j,a,"CANCEL_REQUESTED",j.getStatus().name(),j.getStatus().name(),objectMapper.createObjectNode().put("reason",r.reason()));}audit(a,"JOB_CANCEL_REQUESTED",j,md);return mapper.toResponse(j);}
    @Override @Transactional public JobResponse reconcile(UUID id,ReconcileJobRequest r,AccountPrincipal a,RequestMetadata md){Job j=job(id);scope(j,a);if(j.getStatus()!=JobStatus.RECOVERY_REQUIRED)throw new BusinessRuleException("Only RECOVERY_REQUIRED job can be reconciled");JobStatus next=r.outcome()==ReconcileOutcome.CONFIRM_CANCELLED?JobStatus.CANCELLED:JobStatus.FAILED;j.setStatus(next);j.setReconciledByAccount(account(a.accountId()));j.setReconciledAt(Instant.now(clock));j.setReconciliationNote(r.note());j.setEndedAt(Instant.now(clock));if(j.getServiceRequest()!=null){j.getServiceRequest().setStatus(next==JobStatus.CANCELLED?ServiceRequestStatus.CANCELLED:ServiceRequestStatus.FAILED);j.getServiceRequest().setCompletedAt(Instant.now(clock));}if(j.getRobot()!=null&&Set.of(RobotStatus.BUSY,RobotStatus.ERROR).contains(j.getRobot().getStatus()))j.getRobot().setStatus(RobotStatus.STARTING);event(j,a,"JOB_RECONCILED",JobStatus.RECOVERY_REQUIRED.name(),next.name(),objectMapper.createObjectNode().put("note",r.note()));audit(a,"JOB_RECONCILED",j,md);return mapper.toResponse(j);}
    @Override @Transactional public JobResponse assignRobot(UUID id,UUID robotId,AccountPrincipal a,RequestMetadata md){Job j=job(id);if(j.getStatus()!=JobStatus.QUEUED||j.getAcceptedAt()!=null)throw new BusinessRuleException("Robot can only be assigned before acceptance");Robot robot=robotRepository.findById(robotId).orElseThrow(()->new ResourceNotFoundException("Robot not found: "+robotId));if(robot.getStatus()!=RobotStatus.IDLE||!jobRepository.findByRobotRobotIdAndStatusIn(robotId,List.of(JobStatus.QUEUED,JobStatus.ACCEPTED,JobStatus.RUNNING,JobStatus.RECOVERY_REQUIRED)).stream().filter(x->!x.getJobId().equals(id)).toList().isEmpty())throw new ConflictException("Robot is not available");j.setRobot(robot);j.setDispatchCommandId(UUID.randomUUID());j.setDispatchExpiresAt(Instant.now(clock).plusSeconds(60));audit(a,"JOB_ROBOT_ASSIGNED",j,md);return mapper.toResponse(j);}
    @Override @Transactional public JobResponse assignStaff(UUID id,UUID staffId,AccountPrincipal a,RequestMetadata md){Job j=job(id);Account staff=account(staffId);if(staff.getRole()!=Role.STAFF||staff.getStatus()!=AccountStatus.ACTIVE)throw new BusinessRuleException("Assigned account must be active STAFF");j.setAssignedStaffAccount(staff);audit(a,"JOB_STAFF_ASSIGNED",j,md);return mapper.toResponse(j);}
    private void scope(Job j,AccountPrincipal a){if(a.role()==Role.ADMIN)return;boolean owner=j.getServiceRequest()!=null&&j.getServiceRequest().getRequestedByAccount()!=null&&j.getServiceRequest().getRequestedByAccount().getAccountId().equals(a.accountId());boolean assigned=j.getAssignedStaffAccount()!=null&&j.getAssignedStaffAccount().getAccountId().equals(a.accountId());if(!owner&&!assigned)throw new AccessDeniedException("Job is outside your scope");}
    private boolean terminal(JobStatus s){return Set.of(JobStatus.SUCCEEDED,JobStatus.FAILED,JobStatus.CANCELLED).contains(s);}
    private Job job(UUID id){return jobRepository.findById(id).orElseThrow(()->new ResourceNotFoundException("Job not found: "+id));}
    private Account account(UUID id){return accountRepository.findById(id).orElseThrow(()->new ResourceNotFoundException("Account not found: "+id));}
    private void event(Job j,AccountPrincipal a,String type,String previous,String next,com.fasterxml.jackson.databind.JsonNode data){Instant now=Instant.now(clock);eventRepository.save(JobEvent.builder().jobEventId(UUID.randomUUID()).job(j).robot(j.getRobot()).actorAccount(account(a.accountId())).producerType(JobEventProducerType.valueOf(a.role().name())).producerSessionId(UUID.randomUUID()).sequenceNo(1).eventType(type).previousStatus(previous).newStatus(next).statusScope("JOB").eventDataJson(data).occurredAt(now).receivedAt(now).persistedAt(now).build());}
    private void audit(AccountPrincipal a,String action,Job j,RequestMetadata md){auditService.record(account(a.accountId()),action,"Job",j.getJobId(),null,Map.of("status",j.getStatus().name()),md);}
}
