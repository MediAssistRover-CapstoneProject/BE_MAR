package com.mediassistrover.backend.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.mapping.MappingLifecycleService;
import com.mediassistrover.backend.repository.*;
import com.mediassistrover.backend.service.*;
import com.mediassistrover.backend.validation.StepExecutionPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PatrolSuspensionControlTest {
    final Instant now=Instant.parse("2026-11-16T03:00:00Z");
    final RobotCommandOutboxRepository commands=mock(RobotCommandOutboxRepository.class);
    final RobotRepository robots=mock(RobotRepository.class);
    final RobotMqttSessionRepository sessions=mock(RobotMqttSessionRepository.class);
    final JobRepository jobs=mock(JobRepository.class);
    final JobStepRepository steps=mock(JobStepRepository.class);
    final JobTaskRepository tasks=mock(JobTaskRepository.class);
    final JobSuspensionService suspension=mock(JobSuspensionService.class);
    final MqttOperationControlService control=mock(MqttOperationControlService.class,CALLS_REAL_METHODS);
    final Robot robot=Robot.builder().robotId(UUID.randomUUID()).status(RobotStatus.BUSY).build();
    final Job job=Job.builder().jobId(UUID.randomUUID()).robot(robot).status(JobStatus.SUSPENDING).resumeRequested(true).build();
    final JobStep step=JobStep.builder().jobStepId(UUID.randomUUID()).jobTask(JobTask.builder().job(job).build()).status(JobStepStatus.CANCELLED)
        .interruptionReason("JOB_SUSPEND").stepTypeSnapshot("MOVE").resolvedInputsJson(new ObjectMapper().createObjectNode()).build();
    final RobotCommandOutbox execute=RobotCommandOutbox.builder().commandId(UUID.randomUUID()).sendAttempts(1).physicalStopConfirmed(true).state("COMPLETED").build();

    PatrolSuspensionControlTest(){
        job.setSelectedJobStep(step);var wire=mock(MqttWire.class);when(wire.object()).thenAnswer(i->new ObjectMapper().createObjectNode());
        Map.ofEntries(Map.entry("commands",commands),Map.entry("robots",robots),Map.entry("sessions",sessions),Map.entry("jobs",jobs),
            Map.entry("steps",steps),Map.entry("tasks",tasks),Map.entry("suspension",suspension),Map.entry("executionPolicy",mock(StepExecutionPolicy.class)),
            Map.entry("wire",wire),Map.entry("events",mock(JobEventRepository.class)),Map.entry("updates",mock(ApplicationEventPublisher.class)),
            Map.entry("wakeups",mock(OrchestrationWorkService.class)),Map.entry("mapping",mock(MappingLifecycleService.class)),
            Map.entry("clock",Clock.fixed(now,ZoneOffset.UTC))).forEach((key,value)->ReflectionTestUtils.setField(control,key,value));
        when(robots.findByIdForUpdate(robot.getRobotId())).thenReturn(Optional.of(robot));
        when(commands.findByJobStepId(step.getJobStepId())).thenReturn(Optional.of(execute));
        when(suspension.deadline(job)).thenReturn(now.plusSeconds(60));
    }

    @Test void resumedJobPassesSettledSuspendOperationToContinuationInsteadOfLivelock(){
        job.setStatus(JobStatus.RUNNING);
        assertFalse(control.handle(job,step));verify(suspension,never()).settled(any());
        assertEquals(JobStatus.RUNNING,job.getStatus());
    }
    @Test void successfulStepRacingWithStopAlsoPassesToNormalGraphSuccessorAfterResume(){
        job.setStatus(JobStatus.RUNNING);step.setStatus(JobStepStatus.SUCCEEDED);
        assertFalse(control.handle(job,step));assertEquals(JobStepStatus.SUCCEEDED,step.getStatus());
    }
    @Test void confirmedStopSettlesBusinessJobWithoutCancellingIt(){
        control.completeStopped(job,step,execute,"JOB_SUSPEND");
        verify(suspension).settled(job);assertEquals(JobStatus.SUSPENDING,job.getStatus(),"Suspension service owns the transition");
        assertNull(job.getEndedAt());assertTrue(job.isResumeRequested());
    }
    @Test void oldStopReceiptCannotSuspendNewSelectedOperation(){
        job.setStatus(JobStatus.RUNNING);job.setSelectedJobStep(JobStep.builder().jobStepId(UUID.randomUUID()).build());
        control.completeStopped(job,step,execute,"JOB_SUSPEND");verify(suspension,never()).settled(any());
        assertEquals(JobStatus.RUNNING,job.getStatus());
    }
    @Test void permanentCancellationWinsWhileSuspendingAndDoesNotReleaseAnotherOwner(){
        job.setCancelRequestedAt(now);
        when(jobs.findByRobotRobotIdAndStatusIn(eq(robot.getRobotId()),any())).thenReturn(List.of(Job.builder().jobId(UUID.randomUUID()).status(JobStatus.RUNNING).build()));
        control.completeStopped(job,step,execute,"JOB_SUSPEND");
        assertEquals(JobStatus.CANCELLED,job.getStatus());assertFalse(job.isResumeRequested());
        assertEquals(RobotStatus.BUSY,robot.getStatus());verify(suspension,never()).settled(any());
    }
}
