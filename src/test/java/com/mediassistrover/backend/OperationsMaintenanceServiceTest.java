package com.mediassistrover.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediassistrover.backend.entity.Job;
import com.mediassistrover.backend.entity.JobStep;
import com.mediassistrover.backend.entity.JobTask;
import com.mediassistrover.backend.entity.Robot;
import com.mediassistrover.backend.entity.ServiceRequest;
import com.mediassistrover.backend.enums.JobStatus;
import com.mediassistrover.backend.enums.JobStepStatus;
import com.mediassistrover.backend.enums.RobotStatus;
import com.mediassistrover.backend.enums.ServiceRequestStatus;
import com.mediassistrover.backend.repository.JobEventRepository;
import com.mediassistrover.backend.repository.JobRepository;
import com.mediassistrover.backend.repository.JobStepRepository;
import com.mediassistrover.backend.repository.RefreshTokenRepository;
import com.mediassistrover.backend.repository.RevokedTokenRepository;
import com.mediassistrover.backend.service.impl.OperationsMaintenanceServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperationsMaintenanceServiceTest {
    @Mock JobRepository jobRepository;
    @Mock JobStepRepository stepRepository;
    @Mock JobEventRepository eventRepository;
    @Mock RevokedTokenRepository revokedTokenRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;

    @Test
    void overdueDispatchMovesJobAndRequestToRecoveryAndRobotToError() {
        Instant now = Instant.parse("2026-10-06T03:00:00Z");
        Robot robot = Robot.builder().status(RobotStatus.IDLE).build();
        ServiceRequest request = ServiceRequest.builder().status(ServiceRequestStatus.QUEUED).build();
        Job job = Job.builder().jobId(UUID.randomUUID()).status(JobStatus.QUEUED).robot(robot).serviceRequest(request)
                .dispatchExpiresAt(now).build();
        when(jobRepository.findOverdueLegacyIds(now)).thenReturn(List.of(job.getJobId()));
        when(jobRepository.lockById(job.getJobId())).thenReturn(Optional.of(job));

        int changed = service().markOverdueJobs(now);

        assertThat(changed).isEqualTo(1);
        assertThat(job.getStatus()).isEqualTo(JobStatus.RECOVERY_REQUIRED);
        assertThat(job.getErrorCode()).isEqualTo("DISPATCH_ACK_TIMEOUT");
        assertThat(request.getStatus()).isEqualTo(ServiceRequestStatus.RECOVERY_REQUIRED);
        assertThat(robot.getStatus()).isEqualTo(RobotStatus.ERROR);
        verify(eventRepository).save(org.mockito.ArgumentMatchers.argThat(event ->
                event.getOccurredAt().equals(now) && "DISPATCH_ACK_TIMEOUT".equals(event.getEventType())));
    }

    @Test
    void overdueScanRechecksModernJobsAcceptedStatusAndExtendedDeadlineAfterPrimaryLock() {
        Instant now=Instant.parse("2026-10-06T03:00:00Z");
        Job modern=Job.builder().jobId(UUID.randomUUID()).status(JobStatus.QUEUED).beOrchestrated(true).dispatchExpiresAt(now.minusSeconds(1)).build();
        Job accepted=Job.builder().jobId(UUID.randomUUID()).status(JobStatus.ACCEPTED).dispatchExpiresAt(now.minusSeconds(1)).build();
        Job extended=Job.builder().jobId(UUID.randomUUID()).status(JobStatus.QUEUED).dispatchExpiresAt(now.plusSeconds(1)).build();
        Job noDeadline=Job.builder().jobId(UUID.randomUUID()).status(JobStatus.QUEUED).build();
        var candidates=List.of(modern,accepted,extended,noDeadline);
        when(jobRepository.findOverdueLegacyIds(now)).thenReturn(candidates.stream().map(Job::getJobId).toList());
        for(Job job:candidates)when(jobRepository.lockById(job.getJobId())).thenReturn(Optional.of(job));
        assertThat(service().markOverdueJobs(now)).isZero();
        assertThat(modern.getStatus()).isEqualTo(JobStatus.QUEUED);
        assertThat(accepted.getStatus()).isEqualTo(JobStatus.ACCEPTED);
        org.mockito.Mockito.verifyNoInteractions(eventRepository);
        var order=org.mockito.Mockito.inOrder(jobRepository);
        order.verify(jobRepository).findOverdueLegacyIds(now);
        for(Job job:candidates)order.verify(jobRepository).lockById(job.getJobId());
    }

    @Test
    void stepAtItsExactDeadlineTimesOutOnlyOnce() {
        Instant now = Instant.parse("2026-10-06T03:00:00Z");
        Job job = Job.builder().jobId(UUID.randomUUID()).status(JobStatus.RUNNING).build();
        JobTask task = JobTask.builder().job(job).build();
        JobStep step = JobStep.builder().jobStepId(UUID.randomUUID()).jobTask(task).status(JobStepStatus.RUNNING)
                .startedAt(now.minusSeconds(30)).timeoutSecSnapshot(30).build();
        when(stepRepository.findPotentialTimeoutIds(now.minusSeconds(1))).thenReturn(List.of(step.getJobStepId()));
        when(stepRepository.findJobId(step.getJobStepId())).thenReturn(Optional.of(job.getJobId()));
        when(jobRepository.lockById(job.getJobId())).thenReturn(Optional.of(job));
        when(stepRepository.lockById(step.getJobStepId())).thenReturn(Optional.of(step));

        assertThat(service().markTimedOutSteps(now)).isEqualTo(1);
        assertThat(step.getStatus()).isEqualTo(JobStepStatus.TIMED_OUT);
        assertThat(step.getEndedAt()).isEqualTo(now);
        assertThat(step.getErrorCode()).isEqualTo("STEP_TIMEOUT");
        verify(eventRepository).save(org.mockito.ArgumentMatchers.argThat(event ->
                "STEP_TIMEOUT".equals(event.getEventType())));
        var order=org.mockito.Mockito.inOrder(jobRepository,stepRepository);
        order.verify(stepRepository).findPotentialTimeoutIds(now.minusSeconds(1));
        order.verify(stepRepository).findJobId(step.getJobStepId());
        order.verify(jobRepository).lockById(job.getJobId());
        order.verify(stepRepository).lockById(step.getJobStepId());
    }

    @Test
    void schema3MqttDurationWaitIsNeverCompletedOrTimedOutByLegacyScheduler() {
        Instant now = Instant.parse("2026-10-06T03:00:00Z");
        Job job = Job.builder().jobId(UUID.randomUUID()).status(JobStatus.RUNNING).beOrchestrated(true).schemaVersion(3).build();
        JobTask task = JobTask.builder().job(job).build();
        JobStep step = JobStep.builder().jobStepId(UUID.randomUUID()).jobTask(task).stepTypeSnapshot("WAIT")
                .resolvedInputsJson(new ObjectMapper().createObjectNode()
                        .put("wait_type", "DURATION").put("duration_sec", 5))
                .status(JobStepStatus.RUNNING).startedAt(now.minusSeconds(30))
                .timeoutSecSnapshot(10).build();
        when(stepRepository.findPotentialTimeoutIds(now.minusSeconds(1))).thenReturn(List.of(step.getJobStepId()));
        when(stepRepository.findJobId(step.getJobStepId())).thenReturn(Optional.of(job.getJobId()));
        when(jobRepository.lockById(job.getJobId())).thenReturn(Optional.of(job));

        assertThat(service().markTimedOutSteps(now)).isZero();
        assertThat(step.getStatus()).isEqualTo(JobStepStatus.RUNNING);
        verify(stepRepository,org.mockito.Mockito.never()).lockById(step.getJobStepId());
    }

    @Test
    void tokenCleanupCombinesBothStoresAtInclusiveBoundary() {
        Instant now = Instant.parse("2026-10-06T03:00:00Z");
        when(revokedTokenRepository.deleteByExpiresAtLessThanEqual(now)).thenReturn(2L);
        when(refreshTokenRepository.deleteByExpiresAtLessThanEqual(now)).thenReturn(3L);

        assertThat(service().cleanupExpiredTokens(now)).isEqualTo(5L);
    }

    private OperationsMaintenanceServiceImpl service() {
        return new OperationsMaintenanceServiceImpl(jobRepository, stepRepository, eventRepository,
                revokedTokenRepository, refreshTokenRepository, new ObjectMapper());
    }
}
