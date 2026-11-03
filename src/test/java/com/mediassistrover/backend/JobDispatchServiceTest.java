package com.mediassistrover.backend;

import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.repository.*;
import com.mediassistrover.backend.service.OrchestrationWorkService;
import com.mediassistrover.backend.service.impl.JobDispatchServiceImpl;
import com.mediassistrover.backend.validation.WorkflowExecutionReadiness;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class JobDispatchServiceTest {
    final JobRepository jobs=mock(JobRepository.class);
    final RobotRepository robots=mock(RobotRepository.class);
    final OrchestrationWorkService wakeups=mock(OrchestrationWorkService.class);
    final WorkflowExecutionReadiness readiness=mock(WorkflowExecutionReadiness.class);
    final JobDispatchServiceImpl service=new JobDispatchServiceImpl(jobs,robots,Clock.systemUTC(),wakeups,readiness);

    @Test void assignmentRechecksSelectedIdsAndDoesNotTouchAlreadyAssignedOrStartedJobs() {
        Job assigned=Job.builder().jobId(UUID.randomUUID()).status(JobStatus.QUEUED).robot(Robot.builder().build()).build();
        Job started=Job.builder().jobId(UUID.randomUUID()).status(JobStatus.RUNNING).build();
        UUID deleted=UUID.randomUUID();
        when(jobs.findUnassignedQueuedJobIds(org.springframework.data.domain.PageRequest.of(0,3)))
            .thenReturn(List.of(assigned.getJobId(),started.getJobId(),deleted));
        when(jobs.lockById(assigned.getJobId())).thenReturn(Optional.of(assigned));
        when(jobs.lockById(started.getJobId())).thenReturn(Optional.of(started));
        when(jobs.lockById(deleted)).thenReturn(Optional.empty());
        assertThat(service.dispatchQueuedJobs(3)).isZero();
        verifyNoInteractions(robots,wakeups,readiness);
    }

    @Test void readyModernJobStillGetsRobotWithoutLegacyDispatchDeadline() {
        Workflow workflow=Workflow.builder().build();
        Job job=Job.builder().jobId(UUID.randomUUID()).status(JobStatus.QUEUED).beOrchestrated(true).workflow(workflow).build();
        Robot robot=Robot.builder().robotId(UUID.randomUUID()).status(RobotStatus.IDLE).build();
        when(jobs.findUnassignedQueuedJobIds(org.springframework.data.domain.PageRequest.of(0,1))).thenReturn(List.of(job.getJobId()));
        when(jobs.lockById(job.getJobId())).thenReturn(Optional.of(job));
        when(robots.findByStatusOrderByRobotCodeAsc(RobotStatus.IDLE)).thenReturn(List.of(robot));
        when(robots.findByIdForUpdate(robot.getRobotId())).thenReturn(Optional.of(robot));
        when(readiness.acceptsRobot(workflow,robot)).thenReturn(true);
        assertThat(service.dispatchQueuedJobs(1)).isEqualTo(1);
        assertThat(job.getRobot()).isSameAs(robot);
        assertThat(job.getDispatchCommandId()).isNull();
        assertThat(job.getDispatchExpiresAt()).isNull();
        verify(wakeups).request(job);
    }
}
