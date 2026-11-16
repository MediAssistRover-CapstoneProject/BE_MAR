package com.mediassistrover.backend.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediassistrover.backend.dto.job.request.JobRequests.CancelJobRequest;
import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.mapper.RequestJobMapper;
import com.mediassistrover.backend.repository.*;
import com.mediassistrover.backend.security.AccountPrincipal;
import com.mediassistrover.backend.service.*;
import com.mediassistrover.backend.validation.StaffOperationalScopePolicy;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ImmediateJobCancelTest {
    final JobRepository jobs = mock(JobRepository.class);
    final RobotRepository robots = mock(RobotRepository.class);
    final JobStepRepository steps = mock(JobStepRepository.class);
    final JobTaskRepository tasks = mock(JobTaskRepository.class);
    final AccountRepository accounts = mock(AccountRepository.class);
    final JobEventRepository events = mock(JobEventRepository.class);
    final StaffOperationalScopePolicy scope = mock(StaffOperationalScopePolicy.class);
    final OrchestrationWorkService wakeups = mock(OrchestrationWorkService.class);
    final JobServiceImpl service = mock(JobServiceImpl.class, CALLS_REAL_METHODS);
    final Account account = Account.builder().accountId(UUID.randomUUID()).role(Role.ADMIN).build();
    final AccountPrincipal actor = AccountPrincipal.fromAccount(account);

    ImmediateJobCancelTest() {
        Map<String,Object> fields = Map.ofEntries(
            Map.entry("jobRepository",jobs), Map.entry("robotRepository",robots),
            Map.entry("stepRepository",steps), Map.entry("taskRepository",tasks),
            Map.entry("accountRepository",accounts), Map.entry("eventRepository",events),
            Map.entry("staffScope",scope), Map.entry("wakeups",wakeups),
            Map.entry("mapper",mock(RequestJobMapper.class)), Map.entry("auditService",mock(AuditService.class)),
            Map.entry("objectMapper",new ObjectMapper()), Map.entry("clock",Clock.systemUTC()));
        fields.forEach((name,value) -> ReflectionTestUtils.setField(service,name,value));
        when(accounts.findById(account.getAccountId())).thenReturn(Optional.of(account));
    }

    Job job(JobStatus status) {
        var robot = Robot.builder().robotId(UUID.randomUUID()).status(RobotStatus.BUSY).build();
        var job = new Job();
        job.setJobId(UUID.randomUUID()); job.setStatus(status); job.setRobot(robot);
        when(jobs.lockById(job.getJobId())).thenReturn(Optional.of(job));
        when(robots.findByIdForUpdate(robot.getRobotId())).thenReturn(Optional.of(robot));
        return job;
    }

    @Test void cancelsRunningAndRecoveryImmediatelyWithoutPi() {
        for (JobStatus status : List.of(JobStatus.RUNNING,JobStatus.RECOVERY_REQUIRED)) {
            var job = job(status);
            var step = new JobStep(); step.setStatus(JobStepStatus.RUNNING);
            var task = new JobTask(); task.setStatus(JobTaskStatus.RUNNING);
            when(steps.findByJobId(job.getJobId())).thenReturn(List.of(step));
            when(tasks.findByJobJobIdOrderBySequenceNoAsc(job.getJobId())).thenReturn(List.of(task));
            service.cancel(job.getJobId(),new CancelJobRequest("stop"),actor,null);
            assertEquals(JobStatus.CANCELLED,job.getStatus());
            assertEquals(RobotStatus.IDLE,job.getRobot().getStatus());
            assertEquals(JobStepStatus.CANCELLED,step.getStatus());
            assertEquals(JobTaskStatus.CANCELLED,task.getStatus());
            assertNotNull(job.getEndedAt());
        }
    }

    @Test void repeatedCancelDoesNotReleaseRobotRunningAnotherJob() {
        var job = job(JobStatus.RUNNING);
        var other = job(JobStatus.RUNNING);
        when(jobs.findByRobotRobotIdAndStatusIn(eq(job.getRobot().getRobotId()),any()))
                .thenReturn(List.of(other));
        service.cancel(job.getJobId(),new CancelJobRequest("stop"),actor,null);
        service.cancel(job.getJobId(),new CancelJobRequest("again"),actor,null);
        assertEquals(RobotStatus.BUSY,job.getRobot().getStatus());
        verify(events,times(1)).save(any());
        verify(wakeups,times(1)).request(job);
    }

    @Test void cancelledJobRejectsLateMqttAndDrainsOrchestrationWork() {
        var job = job(JobStatus.CANCELLED);
        job.setBeOrchestrated(true);
        job.setSchemaVersion(3);
        var envelope = mock(com.mediassistrover.backend.mqtt.MqttWire.Envelope.class);
        when(envelope.robotId()).thenReturn(job.getRobot().getRobotId());
        var runtime = mock(com.mediassistrover.backend.mqtt.MqttRuntimeEvents.class, CALLS_REAL_METHODS);
        assertThrows(com.mediassistrover.backend.exception.BusinessRuleException.class,
                () -> runtime.apply(job,envelope));
        var work = mock(WorkflowOrchestrationWorkRepository.class);
        var row = new WorkflowOrchestrationWork();
        row.setRequestedGeneration(2);
        when(work.findForUpdate(job.getJobId())).thenReturn(Optional.of(row));
        var orchestrator = mock(WorkflowOrchestratorService.class,CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(orchestrator,"jobs",jobs);
        ReflectionTestUtils.setField(orchestrator,"work",work);
        ReflectionTestUtils.setField(orchestrator,"clock",Clock.systemUTC());
        orchestrator.advance(job.getJobId());
        assertEquals(2,row.getProcessedGeneration());
        assertEquals(JobStatus.CANCELLED,job.getStatus());
    }

    @Test void unauthorizedCallerCannotCancel() {
        var job = job(JobStatus.RUNNING);
        doThrow(new AccessDeniedException("denied")).when(scope).requireJobAccess(job,actor);
        assertThrows(AccessDeniedException.class,
                () -> service.cancel(job.getJobId(),new CancelJobRequest("stop"),actor,null));
        assertEquals(JobStatus.RUNNING,job.getStatus());
        verifyNoInteractions(events,robots);
    }

    @Test void schemaThreeCancelWaitsForPhysicalStopAndIsIdempotent() {
        var job=job(JobStatus.RUNNING);job.setBeOrchestrated(true);job.setSchemaVersion(3);
        var step=new JobStep();step.setStatus(JobStepStatus.RUNNING);
        job.setSelectedJobStep(step);
        service.cancel(job.getJobId(),new CancelJobRequest("stop"),actor,null);
        service.cancel(job.getJobId(),new CancelJobRequest("stop again"),actor,null);
        assertEquals(JobStatus.RUNNING,job.getStatus());
        assertEquals(JobStepStatus.RUNNING,step.getStatus());
        assertEquals(RobotStatus.BUSY,job.getRobot().getStatus());
        assertNotNull(job.getCancelRequestedAt());assertNull(job.getEndedAt());
        assertFalse(job.isResumeRequested());
        verify(wakeups,times(1)).request(job);
    }

    @Test void cancellingSuspendedPatrolDoesNotReleaseItsForegroundRobot() {
        var job=job(JobStatus.SUSPENDED);job.setBeOrchestrated(true);job.setSchemaVersion(3);job.setResumeRequested(true);
        service.cancel(job.getJobId(),new CancelJobRequest("end patrol permanently"),actor,null);
        assertEquals(JobStatus.SUSPENDED,job.getStatus());assertFalse(job.isResumeRequested());
        assertNotNull(job.getCancelRequestedAt());assertEquals(RobotStatus.BUSY,job.getRobot().getStatus());
    }

    @Test void uncertainSchemaThreeOperationRequiresRecoveryInsteadOfFakeCancellation() {
        var job=job(JobStatus.RECOVERY_REQUIRED);job.setBeOrchestrated(true);job.setSchemaVersion(3);
        assertThrows(com.mediassistrover.backend.exception.BusinessRuleException.class,
                ()->service.cancel(job.getJobId(),new CancelJobRequest("stop"),actor,null));
        assertEquals(JobStatus.RECOVERY_REQUIRED,job.getStatus());assertNull(job.getCancelRequestedAt());
    }
}
