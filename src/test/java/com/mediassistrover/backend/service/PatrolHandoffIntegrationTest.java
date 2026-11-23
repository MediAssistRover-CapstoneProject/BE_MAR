package com.mediassistrover.backend.service;

import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.mqtt.MqttOperationControlService;
import com.mediassistrover.backend.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Cross-service lifecycle with durable repository behavior, without a broker or hardware. */
class PatrolHandoffIntegrationTest {
    @Test void threeDeliveriesRunInQueueOrderAndPatrolWaitsUntilAllHaveSettled(){
        var f=new JobSuspensionServiceTest();
        f.job.setStatus(JobStatus.SUSPENDED);f.job.setPriority(5);f.job.setResumeRequested(true);
        f.job.setSuspendedMapAssetHash(f.map.getAssetHash());f.robot.setStatus(RobotStatus.IDLE);
        var deliveries=new ArrayList<Job>();
        for(int i=0;i<3;i++)deliveries.add(Job.builder().jobId(UUID.randomUUID()).robot(f.robot).map(f.map)
                .workflow(Workflow.builder().serviceType("DELIVERY").build()).status(JobStatus.QUEUED)
                .priority(80).queuedAt(f.now.plusSeconds(i)).schemaVersion(3).beOrchestrated(true).build());
        var all=new ArrayList<Job>(deliveries);all.add(f.job);
        when(f.jobs.findByRobotRobotIdAndStatusIn(eq(f.robot.getRobotId()),any())).thenAnswer(i->{
            Collection<JobStatus> statuses=i.getArgument(1);return all.stream().filter(j->statuses.contains(j.getStatus()) || j.stopOutstanding()).toList();
        });
        when(f.jobs.lockRobotJobs(eq(f.robot.getRobotId()),any())).thenAnswer(i->{
            Collection<JobStatus> statuses=i.getArgument(1);return all.stream().filter(j->statuses.contains(j.getStatus()) || j.stopOutstanding()).toList();
        });
        when(f.jobs.findById(any())).thenAnswer(i->all.stream().filter(j->j.getJobId().equals(i.getArgument(0))).findFirst());
        when(f.holds.findByJobJobIdOrderByCreatedAt(f.job.getJobId())).thenAnswer(i->new ArrayList<>(f.store.values()));
        var arbiter=new RobotTaskArbitrationService(f.jobs,f.holds,f.service,f.voices,f.incidents,f.media,f.wakeups,
                f.robots,f.localization,f.alerts,mock(PatrolWorkflowService.class));
        for(int i=0;i<3;i++) {
            arbiter.reconcileRobot(f.robot.getRobotId());
            assertEquals(JobStatus.SUSPENDED,f.job.getStatus());
            assertTrue(arbiter.canStart(deliveries.get(i)));
            for(int later=i+1;later<3;later++)assertFalse(arbiter.canStart(deliveries.get(later)));
            deliveries.get(i).setStatus(JobStatus.RUNNING);f.robot.setStatus(RobotStatus.BUSY);
            assertFalse(f.service.tryResume(f.job));
            // A terminal delivery with outstanding physical stop must still hold the queue.
            deliveries.get(i).setStatus(i==0?JobStatus.FAILED:JobStatus.SUCCEEDED);
            if(i==0) {
                deliveries.get(i).setStopStatus(JobStopStatus.RECOVERY_REQUIRED);f.robot.setStatus(RobotStatus.IDLE);
                assertFalse(arbiter.canStart(deliveries.get(1)));assertFalse(f.service.tryResume(f.job));
                deliveries.get(i).setStopStatus(JobStopStatus.CONFIRMED);
            }
            f.robot.setStatus(RobotStatus.IDLE);
        }
        arbiter.reconcileRobot(f.robot.getRobotId());
        assertEquals(JobStatus.RUNNING,f.job.getStatus());assertEquals(RobotStatus.BUSY,f.robot.getStatus());
        assertFalse(f.service.hasHolds(f.job));
    }

    @Test void confirmedStopHandsRobotToStaffThenResumesSamePatrolOnlyAfterEveryHoldClears(){
        var f=new JobSuspensionServiceTest();
        var events=mock(JobEventRepository.class);var updates=mock(ApplicationEventPublisher.class);
        var suspension=new JobSuspensionService(f.jobs,f.robots,f.holds,events,f.commands,f.sessions,f.registry,f.navigation,
            f.localization,f.wakeups,updates,f.json,Clock.fixed(f.now,ZoneOffset.UTC),f.voices,f.incidents,f.media,mock(com.mediassistrover.backend.repository.AlertRepository.class));
        var arbiter=new RobotTaskArbitrationService(f.jobs,f.holds,suspension,f.voices,f.incidents,f.media,f.wakeups,f.robots,f.localization,mock(com.mediassistrover.backend.repository.AlertRepository.class),mock(PatrolWorkflowService.class));
        var foreground=Job.builder().jobId(UUID.randomUUID()).workflow(Workflow.builder().serviceType("NAVIGATION").build())
            .robot(f.robot).map(f.map).status(JobStatus.QUEUED).priority(50).queuedAt(f.now).build();
        f.job.setPriority(1);f.job.setQueuedAt(f.now.minusSeconds(10));
        Map<UUID,Job> jobs=new LinkedHashMap<>();jobs.put(f.job.getJobId(),f.job);jobs.put(foreground.getJobId(),foreground);
        when(f.jobs.findByRobotRobotIdAndStatusIn(eq(f.robot.getRobotId()),any())).thenAnswer(i->{
            Collection<JobStatus> statuses=i.getArgument(1);return jobs.values().stream().filter(j->statuses.contains(j.getStatus())).toList();
        });
        when(f.jobs.lockRobotJobs(eq(f.robot.getRobotId()),any())).thenAnswer(i->{
            Collection<JobStatus> statuses=i.getArgument(1);return jobs.values().stream().filter(j->statuses.contains(j.getStatus())).toList();
        });
        when(f.jobs.findById(any())).thenAnswer(i->Optional.ofNullable(jobs.get(i.getArgument(0))));
        when(f.holds.findByJobJobIdOrderByCreatedAt(f.job.getJobId())).thenAnswer(i->new ArrayList<>(f.store.values()));
        UUID originalJob=f.job.getJobId();var target=f.move.getResolvedInputsJson().path("endpoint_id").asText();
        var execute=RobotCommandOutbox.builder().commandId(UUID.randomUUID()).sendAttempts(1).physicalStopConfirmed(false).state("STARTED").build();
        when(f.commands.findByJobStepId(f.move.getJobStepId())).thenReturn(Optional.of(execute));

        arbiter.reconcileRobot(f.robot.getRobotId());
        assertEquals(JobStatus.SUSPENDING,f.job.getStatus());assertEquals(RobotStatus.BUSY,f.robot.getStatus());
        assertFalse(arbiter.canStart(foreground));

        // A DB intent cannot free the robot; only the correlated physical stop does.
        assertThrows(com.mediassistrover.backend.exception.BusinessRuleException.class,()->suspension.settled(f.job));
        f.move.setStatus(JobStepStatus.CANCELLED);f.move.setInterruptionReason("JOB_SUSPEND");execute.setPhysicalStopConfirmed(true);
        var control=mock(MqttOperationControlService.class,CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(control,"commands",f.commands);ReflectionTestUtils.setField(control,"suspension",suspension);
        control.completeStopped(f.job,f.move,execute,"JOB_SUSPEND");
        assertEquals(JobStatus.SUSPENDED,f.job.getStatus());assertEquals(RobotStatus.IDLE,f.robot.getStatus());
        assertTrue(arbiter.canStart(foreground));

        foreground.setStatus(JobStatus.RUNNING);f.robot.setStatus(RobotStatus.BUSY);
        assertFalse(suspension.tryResume(f.job));
        UUID incident=UUID.randomUUID();suspension.request(f.job,"INCIDENT",incident);
        foreground.setStatus(JobStatus.SUCCEEDED);f.robot.setStatus(RobotStatus.IDLE);
        arbiter.reconcileRobot(f.robot.getRobotId());
        assertEquals(JobStatus.SUSPENDED,f.job.getStatus(),"Completed staff task does not clear a separate SOS hold");
        assertNotNull(f.store.get("JOB:"+foreground.getJobId()).getReleasedAt());
        suspension.release(f.job,"INCIDENT",incident);
        arbiter.reconcileRobot(f.robot.getRobotId());
        assertEquals(JobStatus.RUNNING,f.job.getStatus());assertEquals(RobotStatus.BUSY,f.robot.getStatus());
        assertEquals(originalJob,f.job.getJobId());assertSame(f.move,f.job.getSelectedJobStep());
        assertEquals(target,f.job.getSelectedJobStep().getResolvedInputsJson().path("endpoint_id").asText());
        assertEquals(2,f.job.getPatrolRound());assertNull(f.job.getEndedAt());
    }
}
