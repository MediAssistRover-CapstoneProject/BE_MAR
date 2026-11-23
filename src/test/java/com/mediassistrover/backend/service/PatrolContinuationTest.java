package com.mediassistrover.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.mapping.MappingLifecycleService;
import com.mediassistrover.backend.mqtt.*;
import com.mediassistrover.backend.repository.*;
import com.mediassistrover.backend.service.impl.JobStepRuntimeGuardImpl;
import com.mediassistrover.backend.validation.StepExecutionPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PatrolContinuationTest {
    final Instant now=Instant.parse("2026-11-16T02:00:00Z");
    final ObjectMapper json=new ObjectMapper();
    final JobRepository jobs=mock(JobRepository.class);
    final JobStepRepository steps=mock(JobStepRepository.class);
    final JobTaskRepository tasks=mock(JobTaskRepository.class);
    final RobotRepository robots=mock(RobotRepository.class);
    final WorkflowOrchestrationWorkRepository work=mock(WorkflowOrchestrationWorkRepository.class);
    final WorkflowRuntimeTransitionService transitions=mock(WorkflowRuntimeTransitionService.class);
    final JobStepInputResolver resolver=mock(JobStepInputResolver.class);
    final MqttStepDispatchService dispatch=mock(MqttStepDispatchService.class);
    final MqttOperationControlService control=mock(MqttOperationControlService.class);
    final JobSuspensionService suspension=mock(JobSuspensionService.class);
    final WorkflowOrchestratorService orchestrator=mock(WorkflowOrchestratorService.class,CALLS_REAL_METHODS);
    final List<JobStep> visits=new ArrayList<>();
    final Workflow wf=Workflow.builder().workflowId(UUID.randomUUID()).serviceType("PATROL").maxDurationSec(60).maxTotalVisits(2).outputMappingJson(json.createObjectNode()).build();
    final Robot robot=Robot.builder().robotId(UUID.randomUUID()).status(RobotStatus.BUSY).build();
    final Job job=Job.builder().jobId(UUID.randomUUID()).workflow(wf).robot(robot).status(JobStatus.RUNNING).beOrchestrated(true).schemaVersion(3)
        .patrolRound(1).patrolRepeatCount(2).startedAt(now.minusSeconds(10)).patrolRoundStartedAt(now.minusSeconds(10)).build();
    final Task task=Task.builder().taskId(UUID.randomUUID()).workflow(wf).build();
    final JobTask runtimeTask=JobTask.builder().jobTaskId(UUID.randomUUID()).job(job).task(task).status(JobTaskStatus.RUNNING).startedAt(now.minusSeconds(10)).build();
    final Step move=Step.builder().stepId(UUID.randomUUID()).workflow(wf).task(task).enabled(true).stepCode("MOVE_A").stepType(StepType.MOVE)
        .maxVisits(1).timeoutSec(30).paramsJson(json.createObjectNode()).build();
    final WorkflowOrchestrationWork pending=new WorkflowOrchestrationWork();

    PatrolContinuationTest(){
        wf.setStartStep(move);pending.setRequestedGeneration(1);
        var guard=new JobStepRuntimeGuardImpl(steps,transitions);
        Map.ofEntries(Map.entry("jobs",jobs),Map.entry("steps",steps),Map.entry("tasks",tasks),Map.entry("robots",robots),
            Map.entry("work",work),Map.entry("transitions",transitions),Map.entry("resolver",resolver),Map.entry("guard",guard),
            Map.entry("mqttDispatch",dispatch),Map.entry("mqttControl",control),Map.entry("suspension",suspension),
            Map.entry("mapping",mock(MappingLifecycleService.class)),Map.entry("executionPolicy",mock(StepExecutionPolicy.class)),
            Map.entry("arbitration",mock(RobotTaskArbitrationService.class)),Map.entry("events",mock(JobEventRepository.class)),
            Map.entry("wakeups",mock(OrchestrationWorkService.class)),Map.entry("updates",mock(ApplicationEventPublisher.class)),
            Map.entry("json",json),Map.entry("deliveryRelease",mock(DeliveryReleasePolicy.class)),
            Map.entry("clock",Clock.fixed(now,ZoneOffset.UTC))).forEach((key,value)->ReflectionTestUtils.setField(orchestrator,key,value));
        when(jobs.lockById(job.getJobId())).thenReturn(Optional.of(job));when(work.findForUpdate(job.getJobId())).thenReturn(Optional.of(pending));
        when(steps.findByJobId(job.getJobId())).thenReturn(visits);when(tasks.findByJobJobIdOrderBySequenceNoAsc(job.getJobId())).thenReturn(List.of(runtimeTask));
        when(resolver.resolve(any())).thenReturn(json.createObjectNode().put("endpoint_id",UUID.randomUUID().toString()));
        when(resolver.runtimeContext(any())).thenReturn(json.createObjectNode());
        when(suspension.deadline(job)).thenAnswer(i->JobSuspensionService.effectiveDeadline(job));when(suspension.isPatrol(job)).thenReturn(true);
        when(steps.saveAndFlush(any())).thenAnswer(i->{JobStep value=i.getArgument(0);value.setJobStepId(UUID.randomUUID());visits.add(value);return value;});
    }
    JobStep completed(Step definition,JobStepStatus status){
        var value=JobStep.builder().jobStepId(UUID.randomUUID()).jobTask(runtimeTask).step(definition).stepCodeSnapshot(definition.getStepCode())
            .stepTypeSnapshot(definition.getStepType().name()).status(status).visitNo(1).patrolRound(1).executionSequenceNo(visits.size()+1)
            .selectedAt(now.minusSeconds(10)).startedAt(now.minusSeconds(10)).endedAt(now.minusSeconds(2)).operationKey(job.getJobId()+":"+definition.getStepCode()+":1")
            .resolvedInputsJson(json.createObjectNode()).build();
        visits.add(value);job.setSelectedJobStep(value);return value;
    }
    @Test void midMoveContinuationRetainsJobAndTargetWithFreshOperationIdentity(){
        UUID id=job.getJobId();var old=completed(move,JobStepStatus.CANCELLED);old.setInterruptionReason("JOB_SUSPEND");
        orchestrator.advance(id);
        var next=job.getSelectedJobStep();assertNotSame(old,next);assertEquals(id,next.getJobTask().getJob().getJobId());
        assertSame(move,next.getStep());assertEquals(old.getJobStepId(),next.getResumedFromJobStepId());
        assertNotEquals(old.getOperationKey(),next.getOperationKey());assertEquals(1,next.getPatrolRound());
        assertEquals(JobStatus.RUNNING,job.getStatus());assertNull(job.getErrorCode(),"Continuation must not consume maxVisits=1 again");
        verify(dispatch).dispatch(next);
    }
    @Test void successBeforeStopContinuesToNextStepInsteadOfRepeatingReachedEndpoint(){
        var old=completed(move,JobStepStatus.SUCCEEDED);old.setInterruptionReason("JOB_SUSPEND");
        var sense=Step.builder().stepId(UUID.randomUUID()).workflow(wf).task(task).enabled(true).stepCode("SENSE_A").stepType(StepType.EXECUTE_SENSING)
            .maxVisits(1).timeoutSec(30).paramsJson(json.createObjectNode()).build();
        when(transitions.resolve(old)).thenReturn(StepTransition.builder().toStep(sense).build());
        orchestrator.advance(job.getJobId());assertSame(sense,job.getSelectedJobStep().getStep());
        assertNull(job.getSelectedJobStep().getResumedFromJobStepId());
    }
    @Test void finiteRepeatStartsNextRoundUnderSameJobThenFinishesAtConfiguredCount(){
        var old=completed(move,JobStepStatus.SUCCEEDED);
        var success=StepTransition.builder().terminalStatus(TerminalStatus.SUCCEEDED).build();when(transitions.resolve(old)).thenReturn(success);
        orchestrator.advance(job.getJobId());var roundTwo=job.getSelectedJobStep();
        assertEquals(2,job.getPatrolRound());assertEquals(2,roundTwo.getPatrolRound());assertEquals(JobStatus.RUNNING,job.getStatus());
        assertEquals(now,job.getPatrolRoundStartedAt());assertSame(move,roundTwo.getStep());
        roundTwo.setStatus(JobStepStatus.SUCCEEDED);roundTwo.setStartedAt(now);roundTwo.setEndedAt(now);
        when(transitions.resolve(roundTwo)).thenReturn(success);pending.setRequestedGeneration(2);
        orchestrator.advance(job.getJobId());assertEquals(JobStatus.SUCCEEDED,job.getStatus());assertEquals(2,job.getPatrolRound());
    }
    @Test void continuousRepeatStartsAnotherRoundWithoutUnboundedGraphVisits(){
        job.setPatrolRepeatCount(0);job.setPatrolRound(15);
        var old=completed(move,JobStepStatus.SUCCEEDED);old.setPatrolRound(15);
        when(transitions.resolve(old)).thenReturn(StepTransition.builder().terminalStatus(TerminalStatus.SUCCEEDED).build());
        orchestrator.advance(job.getJobId());assertEquals(16,job.getPatrolRound());assertEquals(16,job.getSelectedJobStep().getPatrolRound());
        assertEquals(JobStatus.RUNNING,job.getStatus());
    }
}
