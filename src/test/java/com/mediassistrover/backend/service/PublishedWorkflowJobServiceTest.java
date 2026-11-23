package com.mediassistrover.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.exception.ConflictException;
import com.mediassistrover.backend.mqtt.MqttRolloutService;
import com.mediassistrover.backend.mqtt.MqttWire;
import com.mediassistrover.backend.repository.*;
import com.mediassistrover.backend.security.AccountPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.security.access.AccessDeniedException;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PublishedWorkflowJobServiceTest {
    private final JobExecutionService execution = mock(JobExecutionService.class);
    private final RobotTestSubmissionRepository submissions = mock(RobotTestSubmissionRepository.class);
    private final JobRepository jobs = mock(JobRepository.class);
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final MqttRolloutService rollout = mock(MqttRolloutService.class);
    private final AuditService audit = mock(AuditService.class);
    private final MqttWire wire = new MqttWire();
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-22T03:00:00Z"), ZoneOffset.UTC);
    private final PublishedWorkflowJobService service = new PublishedWorkflowJobService(
            execution, submissions, jobs, accounts, rollout, wire, clock, audit);
    private final Account account = Account.builder().accountId(UUID.randomUUID()).role(Role.ADMIN)
            .status(AccountStatus.ACTIVE).username("admin").build();
    private final AccountPrincipal admin = AccountPrincipal.fromAccount(account);
    private final UUID workflowId = UUID.randomUUID(), robotId = UUID.randomUUID();
    private final RequestMetadata metadata = new RequestMetadata("127.0.0.1", "test", "workflow-run");
    private final Map<String, RobotTestSubmission> receipts = new HashMap<>();
    private final Map<UUID, Job> savedJobs = new HashMap<>();

    @BeforeEach void repositoryFixture() {
        // Preserve the first reservation, as the unique request key does in the repository.
        when(submissions.reserve(anyString(), any(), anyString(), any())).thenAnswer(call -> {
            String key = call.getArgument(0);
            receipts.computeIfAbsent(key, ignored -> {
                var receipt = new RobotTestSubmission();
                receipt.setRequestKey(key);
                receipt.setActorAccountId(call.getArgument(1));
                receipt.setRequestFingerprint(call.getArgument(2));
                receipt.setCreatedAt(call.getArgument(3));
                return receipt;
            });
            return 1;
        });
        when(submissions.lockByKey(anyString())).thenAnswer(call -> Optional.ofNullable(receipts.get(call.getArgument(0))));
        when(submissions.findById(anyString())).thenAnswer(call -> Optional.ofNullable(receipts.get(call.getArgument(0))));
        when(jobs.findById(any())).thenAnswer(call -> Optional.ofNullable(savedJobs.get(call.getArgument(0))));
        when(accounts.findById(account.getAccountId())).thenReturn(Optional.of(account));
    }

    @Test void replayReturnsTheExistingJobWithoutExecutingOrAuditingAgain() {
        Job job = executeNext("NAVIGATION");
        var request = request("{\"endpoint_id\":\"target\"}");
        var initial = service.run(workflowId, request, "retry-key", admin, metadata);
        assertFalse(initial.replayed());
        job.setStatus(JobStatus.RUNNING);
        clearInvocations(execution, audit);

        var replay = service.run(workflowId, request, "retry-key", admin, metadata);

        assertEquals(initial.jobId(), replay.jobId());
        assertEquals("RUNNING", replay.status());
        assertTrue(replay.replayed());
        verifyNoInteractions(execution, audit);
        assertEquals(1, receipts.size());
    }

    @Test void changingThePayloadForAnExistingKeyConflictsBeforeExecution() {
        Job job = executeNext("SPEECH");
        service.run(workflowId, request("{\"speech_text\":\"First\"}"), "one-run", admin, metadata);
        clearInvocations(execution, audit);

        assertThrows(ConflictException.class, () -> service.run(workflowId,
                request("{\"speech_text\":\"Different\"}"), "one-run", admin, metadata));

        assertEquals(job.getJobId(), receipts.values().iterator().next().getJobId());
        verifyNoInteractions(execution, audit);
    }

    @Test void objectKeyOrderDoesNotChangeTheRequestFingerprint() {
        executeNext("NAVIGATION");
        var first = request("{\"map_id\":\"map\",\"options\":{\"speed\":1,\"label\":\"A\"}}");
        var reordered = request("{\"options\":{\"label\":\"A\",\"speed\":1},\"map_id\":\"map\"}");
        var initial = service.run(workflowId, first, "canonical", admin, metadata);

        var replay = service.run(workflowId, reordered, "canonical", admin, metadata);

        assertEquals(initial.jobId(), replay.jobId());
        assertTrue(replay.replayed());
        verify(execution, times(1)).createForPublished(any(), any(), any(), any(), any(), any());
    }

    @Test void nonAdministratorsAreRejectedBeforeAnyMutation() {
        AccountPrincipal staff = AccountPrincipal.fromAccount(Account.builder().accountId(UUID.randomUUID())
                .role(Role.STAFF).status(AccountStatus.ACTIVE).build());

        assertThrows(AccessDeniedException.class, () -> service.run(workflowId,
                request("{}"), "forbidden", staff, metadata));
        assertThrows(AccessDeniedException.class, () -> service.lookup(workflowId, "forbidden", staff));

        verifyNoInteractions(execution, submissions, jobs, accounts, rollout, audit);
    }

    @Test void serverActorIsRecordedAndOnlyRobotTestsReceiveCommissioningReceiptProof() {
        for (String type : List.of("NAVIGATION", "ROBOT_TEST")) {
            Job job = executeNext(type);
            ((com.fasterxml.jackson.databind.node.ObjectNode) job.getInputJson()).put("requested_by_account_id", "spoofed");

            var result = service.run(workflowId, request("{}"), "run-" + type, admin, metadata);

            assertEquals(job.getJobId(), result.jobId());
            assertEquals(admin.accountId().toString(), job.getInputJson().path("requested_by_account_id").asText());
            var receipt = receipts.values().stream().filter(row -> job.getJobId().equals(row.getJobId())).findFirst().orElseThrow();
            if ("ROBOT_TEST".equals(type)) {
                assertEquals(receipt.getRequestKey(), job.getInputJson().path("test_request_key").asText());
                assertEquals(receipt.getRequestFingerprint(), job.getInputJson().path("test_request_fingerprint").asText());
            } else {
                assertFalse(job.getInputJson().has("test_request_key"));
                assertFalse(job.getInputJson().has("test_request_fingerprint"));
            }
            verify(audit).record(eq(account), eq("PUBLISHED_WORKFLOW_JOB_CREATED"), eq("Job"),
                    eq(job.getJobId()), isNull(), eq(Map.of("workflowId", workflowId, "robotId", robotId)), eq(metadata));
        }
    }

    @Test void lookupRejectsAKeyThatBelongsToAnotherWorkflow() {
        var initial = executeNext("NAVIGATION");
        service.run(workflowId, request("{}"), "lookup-key", admin, metadata);
        clearInvocations(execution, audit);

        assertThrows(ConflictException.class, () -> service.lookup(UUID.randomUUID(), "lookup-key", admin));
        assertEquals(initial.getJobId(), service.lookup(workflowId, "lookup-key", admin).jobId());
        assertNull(service.lookup(workflowId, "unknown-key", admin));
        verifyNoInteractions(execution, audit);
    }

    private PublishedWorkflowJobService.Create request(String inputs) {
        JsonNode input = wire.parse(inputs.getBytes(StandardCharsets.UTF_8));
        return new PublishedWorkflowJobService.Create(robotId, null, null, input, 50);
    }
    private Job executeNext(String type) {
        Job job = Job.builder().jobId(UUID.randomUUID()).status(JobStatus.QUEUED).inputJson(wire.object())
                .workflow(Workflow.builder().workflowId(workflowId).serviceType(type).build()).build();
        savedJobs.put(job.getJobId(), job);
        when(execution.createForPublished(any(), any(), any(), any(), any(), any())).thenReturn(job);
        return job;
    }

    @Test void publishedRunQueuesBehindABusyRobotAndStripsClientReceiptAuthority() {
        var fixture=new ExecutionFixture("ROBOT_TEST","WF_EXISTING_TEST");
        var inputs=wire.object().put("test_request_key","forged").put("requested_by_account_id","forged");
        var job=fixture.executor.createForPublished(workflowId,robotId,null,null,inputs,42);
        assertEquals(JobStatus.QUEUED,job.getStatus());
        assertEquals(42,job.getPriority());
        assertEquals(RobotStatus.BUSY,fixture.robot.getStatus());
        assertSame(fixture.robot,job.getRobot());
        assertFalse(job.getInputJson().has("test_request_key"));
        assertFalse(job.getInputJson().has("requested_by_account_id"));
        verify(fixture.wakeups).request(job);
        verify(fixture.steps).save(any(JobStep.class));
    }

    @Test void directDeliveryRunCannotBypassPersistedStaffAndAuthenticationContext() {
        var fixture=new ExecutionFixture("DELIVERY","WF_DELIVERY");
        assertThrows(com.mediassistrover.backend.exception.BusinessRuleException.class,()->
                fixture.executor.createForPublished(workflowId,robotId,null,null,wire.object(),50));
        verify(fixture.jobRows,never()).saveAndFlush(any());
        verifyNoInteractions(fixture.wakeups);
    }

    @Test void patrolCannotSkipCheckpointAndAdmissionChecks() {
        for(String code:List.of("OTHER_PREFIX","WF_PATROL_EMPTY")) {
            var fixture=new ExecutionFixture("PATROL",code);
            assertThrows(com.mediassistrover.backend.exception.BusinessRuleException.class,()->
                    fixture.executor.createForPublished(workflowId,robotId,null,null,wire.object(),5));
            verify(fixture.jobRows,never()).saveAndFlush(any());
        }
    }

    @Test void scheduledPatrolQueuesBehindBusyOrOfflineRobotAndCoalescesExistingIntent() {
        var fixture=new ExecutionFixture("PATROL","WF_PATROL_SCHEDULE");
        var map=FacilityMap.builder().mapId(UUID.randomUUID()).assetHash("a".repeat(64)).frameId("map").build();
        var schedule=Schedule.builder().scheduleId(UUID.randomUUID()).workflow(fixture.definition)
                .robot(fixture.robot).priority(5).inputTemplateJson(wire.object()).build();
        when(rollout.lockForRuntimeCreation()).thenReturn(new MqttRolloutService.State(
                MqttRolloutService.Mode.MQTT_ONLY,false,1,clock.instant(),"test"));
        when(fixture.patrol.queuedMap(fixture.definition,fixture.robot,5,null,schedule.getInputTemplateJson())).thenReturn(map);
        for(var state:List.of(RobotStatus.BUSY,RobotStatus.OFFLINE)) {
            fixture.robot.setStatus(state);
            var job=fixture.executor.createForSchedule(schedule,clock.instant(),schedule.getInputTemplateJson());
            assertEquals(JobStatus.QUEUED,job.getStatus());assertSame(fixture.robot,job.getRobot());
            assertSame(map,job.getMap());assertEquals(state,fixture.robot.getStatus());
            assertEquals(clock.instant(),job.getScheduleFireAt());
            assertEquals(map.getAssetHash(),job.getContextJson().path("patrol_map_asset_hash").asText());
            assertFalse(job.getInputJson().has("navigation_session_id"));
        }
        clearInvocations(fixture.jobRows,fixture.wakeups);
        when(fixture.jobRows.existsNonterminalPatrol(robotId)).thenReturn(true);
        assertNull(fixture.executor.createForSchedule(schedule,clock.instant().plusSeconds(60),schedule.getInputTemplateJson()));
        verify(fixture.jobRows,never()).saveAndFlush(any());verifyNoInteractions(fixture.wakeups);
        verify(fixture.patrol,never()).readyMap(any(),any(),anyInt());
    }

    @Test void scheduledOccurrenceReplayUsesExactIndexedLookupWithoutCreatingAnotherJob() {
        var fixture=new ExecutionFixture("PATROL","WF_PATROL_REPLAY");
        var schedule=Schedule.builder().scheduleId(UUID.randomUUID()).workflow(fixture.definition).build();
        var existing=Job.builder().jobId(UUID.randomUUID()).status(JobStatus.QUEUED).build();
        when(fixture.jobRows.findByScheduleScheduleIdAndScheduleFireAtAndScheduleItemKey(
                schedule.getScheduleId(),clock.instant(),"DEFAULT")).thenReturn(Optional.of(existing));
        assertSame(existing,fixture.executor.createForSchedule(schedule,clock.instant(),wire.object()));
        verify(fixture.jobRows,never()).saveAndFlush(any());verify(fixture.jobRows,never()).findAll();
        verifyNoInteractions(fixture.patrol,fixture.wakeups);
    }

    private class ExecutionFixture {
        final com.mediassistrover.backend.service.impl.JobExecutionServiceImpl executor=
                mock(com.mediassistrover.backend.service.impl.JobExecutionServiceImpl.class,CALLS_REAL_METHODS);
        final JobRepository jobRows=mock(JobRepository.class);
        final JobStepRepository steps=mock(JobStepRepository.class);
        final OrchestrationWorkService wakeups=mock(OrchestrationWorkService.class);
        final PatrolWorkflowService patrol=mock(PatrolWorkflowService.class);
        final Workflow definition;
        final Robot robot=Robot.builder().robotId(robotId).status(RobotStatus.BUSY).build();
        ExecutionFixture(String type,String code) {
            var definitions=mock(WorkflowRepository.class);var actions=mock(StepRepository.class);
            var robots=mock(RobotRepository.class);var tasks=mock(TaskRepository.class);
            var runtimeTasks=mock(JobTaskRepository.class);var mapping=mock(MappingSessionRepository.class);
            var inputFactory=new com.mediassistrover.backend.service.impl.RequestJobInputSnapshotFactory(
                    new com.fasterxml.jackson.databind.ObjectMapper(),mock(DeliveryRequestRepository.class),
                    mock(DeliveryItemRepository.class),mock(NavigationRequestRepository.class),mock(EscortRequestRepository.class));
            var readiness=mock(com.mediassistrover.backend.validation.WorkflowExecutionReadiness.class);
            definition=Workflow.builder().workflowId(workflowId).workflowCode(code).serviceType(type)
                    .schemaVersion(3).graphVersion(3).status(WorkflowStatus.PUBLISHED).acceptsNewJobs(true)
                    .definitionHash("published").defaultInputsJson(wire.object()).inputSchemaJson(wire.parse(
                            "{\"type\":\"object\",\"additionalProperties\":false}".getBytes(StandardCharsets.UTF_8))).build();
            var task=Task.builder().taskId(UUID.randomUUID()).workflow(definition).taskName("Task").sequenceNo(1).configJson(wire.object()).build();
            var step=Step.builder().stepId(UUID.randomUUID()).workflow(definition).task(task).stepCode("SPEAK")
                    .stepType(StepType.INTERACT).enabled(true).paramsJson(wire.object().put("action","SPEAK"))
                    .inputMappingJson(wire.object()).timeoutSec(30).build();
            when(definitions.findById(workflowId)).thenReturn(Optional.of(definition));
            when(actions.findByWorkflowWorkflowIdOrderBySequenceNoAsc(workflowId)).thenReturn(List.of(step));
            when(tasks.findByWorkflowWorkflowIdOrderBySequenceNoAsc(workflowId)).thenReturn(List.of(task));
            when(runtimeTasks.save(any())).thenAnswer(call->call.getArgument(0));
            when(robots.findByIdForUpdate(robotId)).thenReturn(Optional.of(robot));
            when(mapping.findByRobotIdAndPotentiallyOpenTrue(robotId)).thenReturn(List.of());
            when(readiness.acceptsRobot(definition,robot)).thenReturn(true);
            when(jobRows.saveAndFlush(any())).thenAnswer(call->{Job job=call.getArgument(0);job.setJobId(UUID.randomUUID());return job;});
            Map<String,Object> fields=new HashMap<>();
            fields.put("rollout",rollout);fields.put("workflowRepository",definitions);fields.put("stepRepository",actions);
            fields.put("inputSnapshots",inputFactory);fields.put("workflowInputs",new com.mediassistrover.backend.validation.WorkflowInputValidator());
            fields.put("robotRepository",robots);fields.put("mappingSessions",mapping);fields.put("readiness",readiness);
            fields.put("jobRepository",jobRows);fields.put("taskRepository",tasks);fields.put("jobTaskRepository",runtimeTasks);
            fields.put("jobStepRepository",steps);fields.put("clock",clock);fields.put("objectMapper",new com.fasterxml.jackson.databind.ObjectMapper());
            fields.put("eventRepository",mock(JobEventRepository.class));fields.put("wakeups",wakeups);
            fields.put("patrol",patrol);
            fields.put("deliveryEvents",mock(org.springframework.context.ApplicationEventPublisher.class));
            fields.forEach((name,value)->ReflectionTestUtils.setField(executor,name,value));
        }
    }
}
