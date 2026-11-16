package com.mediassistrover.backend.mqtt;

import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.JobStatus;
import com.mediassistrover.backend.repository.*;
import com.mediassistrover.backend.service.RobotTaskArbitrationService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PatrolPublicationGateTest {
    final RobotCommandOutboxRepository commands=mock(RobotCommandOutboxRepository.class);
    final JobRepository jobs=mock(JobRepository.class);
    final RobotRepository robots=mock(RobotRepository.class);
    final JobStepRepository steps=mock(JobStepRepository.class);
    final RobotMqttSessionRepository sessions=mock(RobotMqttSessionRepository.class);
    final RobotTaskArbitrationService arbitration=mock(RobotTaskArbitrationService.class);
    final MqttPublicationPlans plans=mock(MqttPublicationPlans.class,CALLS_REAL_METHODS);
    final MqttProperties properties=mock(MqttProperties.class);
    final UUID id=UUID.randomUUID(),robotId=UUID.randomUUID(),stepId=UUID.randomUUID(),jobId=UUID.randomUUID();
    final Job job=Job.builder().jobId(jobId).status(JobStatus.RUNNING).build();
    final RobotCommandOutbox command=RobotCommandOutbox.builder().commandId(id).robotId(robotId).jobId(jobId).jobStepId(stepId).build();
    PatrolPublicationGateTest(){
        Map.of("commands",commands,"jobs",jobs,"robots",robots,"steps",steps,"sessions",sessions,"arbitration",arbitration,
            "entityManager",mock(jakarta.persistence.EntityManager.class),"properties",properties)
            .forEach((name,value)->ReflectionTestUtils.setField(plans,name,value));
        when(commands.findById(id)).thenReturn(Optional.of(command));when(commands.lock(id)).thenReturn(Optional.of(command));
        when(jobs.lockById(jobId)).thenReturn(Optional.of(job));
        when(robots.findByIdForUpdate(robotId)).thenReturn(Optional.of(new Robot()));
        when(steps.findById(stepId)).thenReturn(Optional.of(new JobStep()));
    }
    @Test void sourceFactBlocksExecutionBeforeAnyPublishAttempt(){
        when(arbitration.patrolPublicationBlocked(job)).thenReturn(true);
        assertNull(plans.command(id));assertEquals(0,command.getSendAttempts());
        verifyNoInteractions(properties);
    }
    @Test void pendingStopBypassesNewExecutionHold(){
        command.setCommandType("CANCEL_STEP");job.setStatus(JobStatus.SUSPENDING);
        when(properties.manages(robotId)).thenReturn(false);
        assertNull(plans.command(id));verifyNoInteractions(arbitration);
        verify(properties).manages(robotId);
    }
}
