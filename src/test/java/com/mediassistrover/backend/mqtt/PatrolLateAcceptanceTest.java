package com.mediassistrover.backend.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.exception.BusinessRuleException;
import com.mediassistrover.backend.repository.*;
import com.mediassistrover.backend.service.OrchestrationWorkService;
import com.mediassistrover.backend.validation.StepExecutionPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PatrolLateAcceptanceTest {
    final ObjectMapper json=new ObjectMapper();
    final Instant now=Instant.parse("2026-11-16T05:00:00Z");
    final JobStepRepository steps=mock(JobStepRepository.class);
    final RobotCommandOutboxRepository commands=mock(RobotCommandOutboxRepository.class);
    final MqttJobSafety safety=mock(MqttJobSafety.class);
    final MqttOperationControlService control=mock(MqttOperationControlService.class);
    final OrchestrationWorkService wakeups=mock(OrchestrationWorkService.class);
    final ApplicationEventPublisher updates=mock(ApplicationEventPublisher.class);
    final StepExecutionPolicy policy=mock(StepExecutionPolicy.class);
    final MqttRuntimeEvents runtime=mock(MqttRuntimeEvents.class,CALLS_REAL_METHODS);
    final Robot robot=Robot.builder().robotId(UUID.randomUUID()).status(RobotStatus.BUSY).build();
    final Job job=Job.builder().jobId(UUID.randomUUID()).robot(robot).status(JobStatus.RUNNING).beOrchestrated(true).schemaVersion(3).build();
    final JobStep old=JobStep.builder().jobStepId(UUID.randomUUID()).stepTypeSnapshot("MOVE").status(JobStepStatus.CANCELLED)
        .operationKey("old:move:1").interruptionReason("JOB_SUSPEND").resolvedInputsJson(json.createObjectNode()).endedAt(now.minusSeconds(10)).build();
    final RobotCommandOutbox command=RobotCommandOutbox.builder().commandId(UUID.randomUUID()).jobId(job.getJobId()).jobStepId(old.getJobStepId())
        .robotId(robot.getRobotId()).operationKey(old.getOperationKey()).commandType("EXECUTE_STEP").sendAttempts(1)
        .state("COMPLETED").physicalStopConfirmed(true).payloadJson(json.createObjectNode().put("startMode","ACCEPT_AND_START"))
        .resultEnvelopeJson(json.createObjectNode().put("evidence","confirmed-cancellation")).build();

    PatrolLateAcceptanceTest(){
        job.setSelectedJobStep(JobStep.builder().jobStepId(UUID.randomUUID()).status(JobStepStatus.RUNNING).build());
        Map.ofEntries(Map.entry("steps",steps),Map.entry("commands",commands),Map.entry("safety",safety),Map.entry("control",control),
            Map.entry("wakeups",wakeups),Map.entry("updates",updates),Map.entry("executionPolicy",policy),
            Map.entry("clock",Clock.fixed(now,ZoneOffset.UTC))).forEach((key,value)->ReflectionTestUtils.setField(runtime,key,value));
        when(commands.lock(command.getCommandId())).thenReturn(Optional.of(command));when(steps.lockById(old.getJobStepId())).thenReturn(Optional.of(old));
        when(policy.executor(eq(3),eq(StepType.MOVE),any())).thenReturn(StepExecutorKind.PI);
    }
    MqttWire.Envelope ack(boolean accepted,UUID stepId,String operation){
        var p=json.createObjectNode().put("commandId",command.getCommandId().toString()).put("commandType","EXECUTE_STEP")
            .put("accepted",accepted).put("status","START_REQUESTED");p.putObject("start").put("attemptNo",1);
        return new MqttWire.Envelope("1.0",UUID.randomUUID(),operation,command.getCommandId(),robot.getRobotId(),job.getJobId(),stepId,
            "COMMAND_ACK",now.toString(),UUID.randomUUID(),4,UUID.randomUUID(),p);
    }
    void unchanged(){
        assertEquals(JobStepStatus.CANCELLED,old.getStatus());assertEquals("COMPLETED",command.getState());
        assertTrue(command.getPhysicalStopConfirmed());assertEquals("confirmed-cancellation",command.getResultEnvelopeJson().path("evidence").asText());
        assertNull(command.getAcknowledgedAt());assertEquals(RobotStatus.BUSY,robot.getStatus());
        verifyNoInteractions(safety,control,wakeups,updates);
    }
    @Test void delayedExecuteAcceptanceCannotRestartOldVisitOrRecoverNewForeground(){
        var selected=job.getSelectedJobStep();
        assertDoesNotThrow(()->runtime.apply(job,ack(true,old.getJobStepId(),old.getOperationKey())));
        assertSame(selected,job.getSelectedJobStep());assertEquals(JobStatus.RUNNING,job.getStatus());unchanged();
    }
    @Test void delayedAcceptanceIsHarmlessWhileStoppedVisitIsStillSelectedDuringSuspension(){
        job.setSelectedJobStep(old);job.setStatus(JobStatus.SUSPENDED);
        assertDoesNotThrow(()->runtime.apply(job,ack(true,old.getJobStepId(),old.getOperationKey())));
        assertEquals(JobStatus.SUSPENDED,job.getStatus());unchanged();
    }
    @Test void terminalCancelledJobMayAcknowledgeOnlyTheScopedAlreadyStoppedAcceptance(){
        job.setStatus(JobStatus.CANCELLED);
        assertDoesNotThrow(()->runtime.apply(job,ack(true,old.getJobStepId(),old.getOperationKey())));
        assertEquals(JobStatus.CANCELLED,job.getStatus());unchanged();
    }
    @Test void wrongOperationOrStepCannotUseHarmlessReceiptException(){
        assertThrows(BusinessRuleException.class,()->runtime.apply(job,ack(true,old.getJobStepId(),"foreign:operation")));
        assertThrows(BusinessRuleException.class,()->runtime.apply(job,ack(true,UUID.randomUUID(),old.getOperationKey())));unchanged();
    }
    @Test void rejectedAcceptanceAndMissingStopProofAreNotSilentlyAcknowledged(){
        assertThrows(BusinessRuleException.class,()->runtime.apply(job,ack(false,old.getJobStepId(),old.getOperationKey())));
        command.setPhysicalStopConfirmed(false);
        assertThrows(BusinessRuleException.class,()->runtime.apply(job,ack(true,old.getJobStepId(),old.getOperationKey())));
        verifyNoInteractions(safety,control,wakeups,updates);
    }

    @Test void logicallyCancelledJobStillAcceptsScopedStopResultWithoutRestarting() {
        job.setStatus(JobStatus.CANCELLED);job.setStopStatus(JobStopStatus.PENDING);job.setSelectedJobStep(old);
        old.setInterruptionReason("JOB_CANCEL");old.setStatus(JobStepStatus.RUNNING);
        command.setPhysicalStopConfirmed(false);command.setState("STARTED");
        ReflectionTestUtils.setField(runtime,"wire",new MqttWire());
        var payload=json.createObjectNode().put("status","CANCELLED").put("physicalStopConfirmed",true);
        var e=new MqttWire.Envelope("1.0",UUID.randomUUID(),old.getOperationKey(),command.getCommandId(),robot.getRobotId(),job.getJobId(),old.getJobStepId(),
            "STEP_ERROR",now.toString(),UUID.randomUUID(),5,UUID.randomUUID(),payload);
        runtime.apply(job,e);
        assertEquals(JobStatus.CANCELLED,job.getStatus());assertTrue(command.getPhysicalStopConfirmed());
        verify(control).completeStopped(job,old,command,"JOB_CANCEL");
    }

    @Test void cancelledOperationCannotClaimStoppedWhileStillRunning() {
        job.setStatus(JobStatus.CANCELLED);job.setStopStatus(JobStopStatus.PENDING);
        command.setPhysicalStopConfirmed(false);command.setState("STARTED");
        var payload=json.createObjectNode().put("status","RUNNING").put("physicalStopConfirmed",true);
        var e=new MqttWire.Envelope("1.0",UUID.randomUUID(),old.getOperationKey(),command.getCommandId(),robot.getRobotId(),job.getJobId(),old.getJobStepId(),
            "STEP_ERROR",now.toString(),UUID.randomUUID(),5,UUID.randomUUID(),payload);
        assertThrows(BusinessRuleException.class,()->runtime.apply(job,e));
        assertFalse(command.getPhysicalStopConfirmed());verifyNoInteractions(control);
    }

    @Test void duplicateUnconfirmedResultCannotUndoCommittedStop() {
        job.setStatus(JobStatus.CANCELLED);job.setStopStatus(JobStopStatus.CONFIRMED);
        var payload=json.createObjectNode().put("status","CANCELLED").put("physicalStopConfirmed",false);
        var e=new MqttWire.Envelope("1.0",UUID.randomUUID(),old.getOperationKey(),command.getCommandId(),robot.getRobotId(),job.getJobId(),old.getJobStepId(),
            "STEP_ERROR",now.toString(),UUID.randomUUID(),5,UUID.randomUUID(),payload);
        runtime.apply(job,e);unchanged();
        assertEquals(JobStopStatus.CONFIRMED,job.getStopStatus());
    }
}
