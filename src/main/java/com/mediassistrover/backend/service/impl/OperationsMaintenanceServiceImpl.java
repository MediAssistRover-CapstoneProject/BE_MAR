package com.mediassistrover.backend.service.impl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.repository.*;
import com.mediassistrover.backend.service.OperationsMaintenanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
@Service @RequiredArgsConstructor
public class OperationsMaintenanceServiceImpl implements OperationsMaintenanceService {
    private final JobRepository jobRepository; private final JobStepRepository stepRepository;
    private final JobEventRepository eventRepository; private final RevokedTokenRepository revokedTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository; private final ObjectMapper objectMapper;
    @Override @Transactional public int markOverdueJobs(Instant now){List<Job> jobs=jobRepository.lockOverdue(List.of(JobStatus.QUEUED),now);jobs.forEach(j->recovery(j,"DISPATCH_ACK_TIMEOUT",now));return jobs.size();}
    @Override @Transactional public int markTimedOutSteps(Instant now){List<JobStep> timedOut=stepRepository.lockPotentialTimeouts(now.minusSeconds(1)).stream().filter(s->!s.getStartedAt().plusSeconds(s.getTimeoutSecSnapshot()).isAfter(now)).toList();for(JobStep s:timedOut){s.setStatus(JobStepStatus.TIMED_OUT);s.setEndedAt(now);s.setErrorCode("STEP_TIMEOUT");Job j=s.getJobTask().getJob();eventRepository.save(JobEvent.builder().jobEventId(UUID.randomUUID()).job(j).robot(j.getRobot()).jobTask(s.getJobTask()).jobStep(s).producerType(JobEventProducerType.SCHEDULER).producerSessionId(UUID.randomUUID()).sequenceNo(1).eventType("STEP_TIMEOUT").previousStatus(JobStepStatus.RUNNING.name()).newStatus(JobStepStatus.TIMED_OUT.name()).statusScope("JOB_STEP").eventDataJson(objectMapper.createObjectNode().put("reason","STEP_TIMEOUT")).occurredAt(now).receivedAt(now).persistedAt(now).build());}return timedOut.size();}
    @Override @Transactional public long cleanupExpiredTokens(Instant now){return revokedTokenRepository.deleteByExpiresAtLessThanEqual(now)+refreshTokenRepository.deleteByExpiresAtLessThanEqual(now);}
    private void recovery(Job j,String code,Instant now){if(Set.of(JobStatus.SUCCEEDED,JobStatus.FAILED,JobStatus.CANCELLED,JobStatus.RECOVERY_REQUIRED).contains(j.getStatus()))return;String old=j.getStatus().name();j.setStatus(JobStatus.RECOVERY_REQUIRED);j.setErrorCode(code);j.setFailureReason("Manual reconciliation is required");if(j.getServiceRequest()!=null)j.getServiceRequest().setStatus(ServiceRequestStatus.RECOVERY_REQUIRED);if(j.getRobot()!=null)j.getRobot().setStatus(RobotStatus.ERROR);eventRepository.save(JobEvent.builder().jobEventId(UUID.randomUUID()).job(j).robot(j.getRobot()).producerType(JobEventProducerType.SCHEDULER).producerSessionId(UUID.randomUUID()).sequenceNo(1).eventType(code).previousStatus(old).newStatus(JobStatus.RECOVERY_REQUIRED.name()).statusScope("JOB").eventDataJson(objectMapper.createObjectNode().put("reason",code)).occurredAt(now).receivedAt(now).persistedAt(now).build());}
}
