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
    final RobotCommandOutboxRepository commands=mock(RobotCommandOutboxRepository.class);
    final AuditService audit=mock(AuditService.class);
    final com.mediassistrover.backend.mqtt.MqttOperationControlService control=mock(com.mediassistrover.backend.mqtt.MqttOperationControlService.class);
    final JobCancellationService cancellation=new JobCancellationService(jobs,steps,tasks,robots,commands,
        mock(RobotOperationAuthorizationRepository.class),control,mock(com.mediassistrover.backend.mapping.MappingLifecycleService.class),
        audit,mock(org.springframework.context.ApplicationEventPublisher.class),Clock.systemUTC());
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
        ReflectionTestUtils.setField(service,"cancellation",cancellation);
        when(accounts.findById(account.getAccountId())).thenReturn(Optional.of(account));
    }

    Job job(JobStatus status) {
        var robot = Robot.builder().robotId(UUID.randomUUID()).status(RobotStatus.BUSY).build();
        var job = new Job();
        job.setJobId(UUID.randomUUID()); job.setStatus(status); job.setRobot(robot);
        job.setBeOrchestrated(true);job.setSchemaVersion(3);
        when(jobs.lockById(job.getJobId())).thenReturn(Optional.of(job));
        when(robots.findByIdForUpdate(robot.getRobotId())).thenReturn(Optional.of(robot));
        return job;
    }

    @Test void legacyAcceptedWithoutStartTimestampStillRequiresStopEvidence() {
        var job=job(JobStatus.ACCEPTED);job.setBeOrchestrated(false);
        service.cancel(job.getJobId(),new CancelJobRequest("stop"),actor,null);
        assertEquals(JobStatus.CANCELLED,job.getStatus());
        assertEquals(JobStopStatus.RECOVERY_REQUIRED,job.getStopStatus());
        assertEquals(RobotStatus.ERROR,job.getRobot().getStatus());
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
        verify(audit,times(1)).record(any(),eq("JOB_CANCELLED"),eq("Job"),eq(job.getJobId()),any(),any(),isNull());
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

    @Test void schemaThreeCancelReturnsImmediatelyButHoldsRobotUntilStop() {
        var job=job(JobStatus.RUNNING);job.setBeOrchestrated(true);job.setSchemaVersion(3);
        var step=new JobStep();step.setJobStepId(UUID.randomUUID());step.setStatus(JobStepStatus.RUNNING);
        job.setSelectedJobStep(step);
        var command=RobotCommandOutbox.builder().commandId(UUID.randomUUID()).jobId(job.getJobId()).jobStepId(step.getJobStepId())
            .commandType("EXECUTE_STEP").state("STARTED").sendAttempts(1).build();
        when(commands.findByJobIdOrderByCreatedAt(job.getJobId())).thenReturn(List.of(command));
        when(commands.findByJobStepId(step.getJobStepId())).thenReturn(Optional.of(command));
        when(steps.findById(step.getJobStepId())).thenReturn(Optional.of(step));
        when(steps.findByJobId(job.getJobId())).thenReturn(List.of(step));
        service.cancel(job.getJobId(),new CancelJobRequest("stop"),actor,null);
        service.cancel(job.getJobId(),new CancelJobRequest("again"),actor,null);
        assertEquals(JobStatus.CANCELLED,job.getStatus());assertEquals(JobStopStatus.PENDING,job.getStopStatus());
        assertEquals(RobotStatus.BUSY,job.getRobot().getStatus());assertNotNull(job.getEndedAt());
        verify(control,times(1)).handle(job,step);
        command.setPhysicalStopConfirmed(true);
        cancellation.advance(job.getJobId());
        assertEquals(JobStopStatus.CONFIRMED,job.getStopStatus());assertEquals(RobotStatus.IDLE,job.getRobot().getStatus());
    }

    @Test void completedSpeechDoesNotKeepCancelledMoveInRecovery() {
        var job=job(JobStatus.RUNNING);var json=new ObjectMapper();
        var speech=JobStep.builder().jobStepId(UUID.randomUUID()).stepTypeSnapshot("INTERACT").status(JobStepStatus.SUCCEEDED).build();
        var move=JobStep.builder().jobStepId(UUID.randomUUID()).stepTypeSnapshot("MOVE").status(JobStepStatus.RUNNING).build();
        job.setSelectedJobStep(move);
        var finished=RobotCommandOutbox.builder().commandId(UUID.randomUUID()).jobId(job.getJobId()).jobStepId(speech.getJobStepId())
                .robotId(job.getRobot().getRobotId()).operationKey("job:speech:1").commandType("EXECUTE_STEP").state("COMPLETED").sendAttempts(1)
                .payloadJson(json.createObjectNode().set("step",json.createObjectNode().put("stepType","INTERACT"))).build();
        var moving=RobotCommandOutbox.builder().commandId(UUID.randomUUID()).jobId(job.getJobId()).jobStepId(move.getJobStepId())
                .commandType("EXECUTE_STEP").state("STARTED").sendAttempts(1).build();
        when(commands.findByJobIdOrderByCreatedAt(job.getJobId())).thenReturn(List.of(finished,moving));
        when(steps.findById(speech.getJobStepId())).thenReturn(Optional.of(speech));
        when(steps.findById(move.getJobStepId())).thenReturn(Optional.of(move));
        when(steps.findByJobId(job.getJobId())).thenReturn(List.of(speech,move));
        service.cancel(job.getJobId(),new CancelJobRequest("stop current move"),actor,null);
        assertEquals(JobStopStatus.PENDING,job.getStopStatus());
        verify(control,never()).handle(job,speech);verify(control).handle(job,move);
        assertNull(finished.getPhysicalStopConfirmed());assertEquals(JobStepStatus.SUCCEEDED,speech.getStatus());
        moving.setPhysicalStopConfirmed(true);cancellation.advance(job.getJobId());
        assertEquals(JobStopStatus.CONFIRMED,job.getStopStatus());assertEquals(RobotStatus.IDLE,job.getRobot().getStatus());
        // The shared exception is restricted to completed, uninterrupted speech/wait.
        for(String type:List.of("MOVE","CONTROL_HARDWARE","EXECUTE_SENSING")) {
            finished.setPayloadJson(json.createObjectNode().set("step",json.createObjectNode().put("stepType",type)));
            assertFalse(com.mediassistrover.backend.mqtt.MqttJobSafety.completedWithoutPhysicalStop(finished,job.getCancelRequestedAt()));
        }
        finished.setPayloadJson(json.createObjectNode().set("step",json.createObjectNode().put("stepType","INTERACT")));
        finished.setInterruptionReason("JOB_CANCEL");assertFalse(com.mediassistrover.backend.mqtt.MqttJobSafety.completedWithoutPhysicalStop(finished,job.getCancelRequestedAt()));
        var success=json.createObjectNode().put("messageType","STEP_RESULT").put("jobId",job.getJobId().toString())
                .put("jobStepId",speech.getJobStepId().toString()).put("robotId",job.getRobot().getRobotId().toString())
                .put("correlationId",finished.getCommandId().toString()).put("operationKey",finished.getOperationKey())
                .put("occurredAt",job.getCancelRequestedAt().minusSeconds(10).toString());
        success.putObject("payload").put("status","SUCCEEDED").put("stepType","INTERACT");finished.setResultEnvelopeJson(success);
        job.setStopStatus(JobStopStatus.RECOVERY_REQUIRED);cancellation.advance(job.getJobId());
        assertEquals(JobStopStatus.CONFIRMED,job.getStopStatus());assertNull(finished.getPhysicalStopConfirmed());
        success.put("occurredAt",job.getCancelRequestedAt().plusSeconds(1).toString());
        assertFalse(com.mediassistrover.backend.mqtt.MqttJobSafety.completedWithoutPhysicalStop(finished,job.getCancelRequestedAt()));
        success.put("occurredAt",job.getCancelRequestedAt().minusSeconds(10).toString()).put("correlationId",UUID.randomUUID().toString());
        assertFalse(com.mediassistrover.backend.mqtt.MqttJobSafety.completedWithoutPhysicalStop(finished,job.getCancelRequestedAt()));
    }

    @Test void cancellingSuspendedPatrolDoesNotReleaseItsForegroundRobot() {
        var job=job(JobStatus.SUSPENDED);job.setBeOrchestrated(true);job.setSchemaVersion(3);job.setResumeRequested(true);
        var foreground=job(JobStatus.RUNNING);
        when(jobs.findByRobotRobotIdAndStatusIn(eq(job.getRobot().getRobotId()),any())).thenReturn(List.of(foreground));
        service.cancel(job.getJobId(),new CancelJobRequest("end patrol permanently"),actor,null);
        assertEquals(JobStatus.CANCELLED,job.getStatus());assertFalse(job.isResumeRequested());
        assertEquals(RobotStatus.BUSY,job.getRobot().getStatus());
    }

    @Test void recoveryCancelWithEarlierRequestKeepsRobotUntilStopIsConfirmed() {
        var job=job(JobStatus.RECOVERY_REQUIRED);
        job.setCancelRequestedAt(Instant.now().minusSeconds(30));job.setCancelCommandId(UUID.randomUUID());
        var step=new JobStep();step.setJobStepId(UUID.randomUUID());step.setStatus(JobStepStatus.RECOVERY_REQUIRED);
        job.setSelectedJobStep(step);
        var command=RobotCommandOutbox.builder().commandId(UUID.randomUUID()).jobId(job.getJobId()).jobStepId(step.getJobStepId())
            .commandType("EXECUTE_STEP").state("RECOVERY_REQUIRED").sendAttempts(1).build();
        when(commands.findByJobIdOrderByCreatedAt(job.getJobId())).thenReturn(List.of(command));
        when(commands.findByJobStepId(step.getJobStepId())).thenReturn(Optional.of(command));
        when(steps.findById(step.getJobStepId())).thenReturn(Optional.of(step));
        when(steps.findByJobId(job.getJobId())).thenReturn(List.of(step));
        service.cancel(job.getJobId(),new CancelJobRequest("Cancel recovery job"),actor,null);
        service.cancel(job.getJobId(),new CancelJobRequest("Retry"),actor,null);
        assertEquals(JobStatus.CANCELLED,job.getStatus());assertEquals(JobStopStatus.PENDING,job.getStopStatus());
        assertEquals(RobotStatus.BUSY,job.getRobot().getStatus());assertNull(job.getStopConfirmedAt());
        assertEquals("JOB_CANCEL",step.getInterruptionReason());
        verify(control,times(1)).handle(job,step);
        verify(audit,times(1)).record(any(),eq("JOB_CANCELLED"),eq("Job"),eq(job.getJobId()),any(),any(),isNull());
        command.setPhysicalStopConfirmed(true);
        cancellation.advance(job.getJobId());
        assertEquals(JobStopStatus.CONFIRMED,job.getStopStatus());assertNotNull(job.getStopConfirmedAt());
        assertEquals(RobotStatus.IDLE,job.getRobot().getStatus());
    }

    @Test void cancelledUnpublishedJobNeedsNoPhysicalStop() {
        var job=job(JobStatus.QUEUED);job.setBeOrchestrated(true);
        service.cancel(job.getJobId(),new CancelJobRequest("stop"),actor,null);
        assertEquals(JobStatus.CANCELLED,job.getStatus());assertEquals(JobStopStatus.NOT_REQUIRED,job.getStopStatus());
        verifyNoInteractions(control);
    }

    private record Settlement(com.mediassistrover.backend.mqtt.MqttOperationRecoveryService service,
                              Job job,JobStep step,RobotCommandOutbox command,
                              com.mediassistrover.backend.security.DevicePrincipal device,
                              com.mediassistrover.backend.mqtt.MqttOperationRecoveryService.CancelledMoveSettlement request,
                              RobotMqttInbox stopReceipt) { }

    private Settlement settlement(JobStatus outcome,boolean reconciled) {
        var operation=mock(com.mediassistrover.backend.mqtt.MqttOperationRecoveryService.class,CALLS_REAL_METHODS);
        var commandRepository=mock(RobotCommandOutboxRepository.class);
        var inboxRepository=mock(RobotMqttInboxRepository.class);
        Map<String,Object> fields=Map.of("jobs",jobs,"robots",robots,"steps",steps,"commands",commandRepository,
                "inbox",inboxRepository,"events",events,"wire",new com.mediassistrover.backend.mqtt.MqttWire(),
                "clock",Clock.systemUTC(),"mapping",mock(com.mediassistrover.backend.mapping.MappingLifecycleService.class),
                "updates",mock(org.springframework.context.ApplicationEventPublisher.class));
        fields.forEach((name,value)->ReflectionTestUtils.setField(operation,name,value));
        var job=job(outcome);job.setBeOrchestrated(true);job.setEndedAt(Instant.now());
        job.getRobot().setRobotCode("mar-v1");
        if(reconciled){job.setReconciledAt(Instant.now());job.setReconciledByAccount(account);job.setReconciliationNote("Inspected and closed");}
        var task=new JobTask();task.setJob(job);
        var step=new JobStep();step.setJobStepId(UUID.randomUUID());step.setJobTask(task);
        step.setStatus(outcome==JobStatus.FAILED?JobStepStatus.FAILED:JobStepStatus.CANCELLED);
        step.setStepTypeSnapshot("MOVE");step.setOperationKey(job.getJobId()+":MOVE:1");job.setSelectedJobStep(step);
        var command=RobotCommandOutbox.builder().commandId(UUID.randomUUID()).jobId(job.getJobId())
                .jobStepId(step.getJobStepId()).robotId(job.getRobot().getRobotId()).operationKey(step.getOperationKey())
                .sendAttempts(1).physicalStopConfirmed(true).state("COMPLETED").build();
        var stopId=UUID.randomUUID();var wire=new com.mediassistrover.backend.mqtt.MqttWire();
        var envelope=wire.object().put("messageType","STEP_ERROR").put("messageId",stopId.toString())
                .put("robotId",command.getRobotId().toString()).put("jobId",job.getJobId().toString())
                .put("jobStepId",step.getJobStepId().toString()).put("correlationId",command.getCommandId().toString())
                .put("operationKey",step.getOperationKey());
        envelope.putObject("payload").put("physicalStopConfirmed",true);command.setResultEnvelopeJson(envelope);
        var stopReceipt=new RobotMqttInbox();stopReceipt.setRobotId(command.getRobotId());
        stopReceipt.setMessageType("STEP_ERROR");stopReceipt.setAccepted(true);
        when(inboxRepository.findById(stopId)).thenReturn(Optional.of(stopReceipt));
        when(steps.findById(step.getJobStepId())).thenReturn(Optional.of(step));
        when(steps.lockById(step.getJobStepId())).thenReturn(Optional.of(step));
        when(commandRepository.findByJobStepId(step.getJobStepId())).thenReturn(Optional.of(command));
        when(commandRepository.lock(command.getCommandId())).thenReturn(Optional.of(command));
        var device=new com.mediassistrover.backend.security.DevicePrincipal(command.getRobotId(),"mar-v1",
                job.getRobot().getDeviceTokenVersion(),"device",Instant.now().plusSeconds(600));
        var request=new com.mediassistrover.backend.mqtt.MqttOperationRecoveryService.CancelledMoveSettlement(command.getCommandId(),step.getOperationKey());
        return new Settlement(operation,job,step,command,device,request,stopReceipt);
    }

    @Test void terminalMoveSettlementRetiresCancelledAndExplicitlyReconciledFailedOperations() {
        for(var outcome:List.of(JobStatus.CANCELLED,JobStatus.FAILED)) {
            var f=settlement(outcome,outcome==JobStatus.FAILED);
            if(outcome==JobStatus.FAILED) {
                f.step.setStatus(JobStepStatus.RECOVERY_REQUIRED);
                f.command.setState("RECOVERY_REQUIRED");
            }
            var receipt=f.service.settleCancelledMove(f.job.getJobId(),f.step.getJobStepId(),f.request,f.device);
            assertEquals(outcome.name(),receipt.path("status").asText());
            assertTrue(receipt.path("physicalStopConfirmed").asBoolean());
            assertEquals(outcome,f.job.getStatus());
            assertEquals(outcome.name(),f.step.getStatus().name());
            assertEquals("COMPLETED",f.command.getState());
            assertEquals("STEP_ERROR",f.command.getResultEnvelopeJson().path("messageType").asText());
            if(outcome==JobStatus.FAILED)assertEquals("CONFIRM_FAILED",receipt.path("reconciliationOutcome").asText());
        }
    }

    @Test void failedSettlementRejectsUnreconciledOrUnconfirmedPhysicalStop() {
        var unreconciled=settlement(JobStatus.FAILED,false);
        assertThrows(com.mediassistrover.backend.exception.BusinessRuleException.class,()->unreconciled.service
                .settleCancelledMove(unreconciled.job.getJobId(),unreconciled.step.getJobStepId(),unreconciled.request,unreconciled.device));
        var f=settlement(JobStatus.FAILED,true);
        f.command.setPhysicalStopConfirmed(false);
        assertThrows(com.mediassistrover.backend.exception.BusinessRuleException.class,()->f.service
                .settleCancelledMove(f.job.getJobId(),f.step.getJobStepId(),f.request,f.device));
        f.command.setPhysicalStopConfirmed(true);f.stopReceipt.setAccepted(false);
        assertThrows(com.mediassistrover.backend.exception.BusinessRuleException.class,()->f.service
                .settleCancelledMove(f.job.getJobId(),f.step.getJobStepId(),f.request,f.device));
        verify(events,never()).save(any());
    }
}
