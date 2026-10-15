package com.mediassistrover.backend;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.mqtt.*;
import com.mediassistrover.backend.repository.*;
import com.mediassistrover.backend.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.annotation.DirtiesContext;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class MqttRuntimeIntegrationTest extends MySqlIntegrationTest {
    @Autowired ObjectMapper json; @Autowired MqttWire wire; @Autowired MqttProperties properties; @Autowired MqttConnectionState connection;
    @Autowired MqttInboundRouter router; @Autowired MqttInboxStore store; @Autowired MqttInboxProcessor processor;
    @Autowired MqttDurablePublisher publisher; @Autowired MqttPublicationPlans plans; @Autowired MqttSessionRegistry sessions;
    @Autowired WorkflowOrchestratorService orchestrator; @Autowired OrchestrationWorkService wakeups;
    @Autowired JobService jobService;
    @Autowired JobRepository jobs; @Autowired JobStepRepository steps; @Autowired RobotRepository robots;
    @Autowired RobotCommandOutboxRepository commands; @Autowired RobotMqttInboxRepository inbox; @Autowired RobotMqttSessionRepository sessionRows;
    @Autowired JobEventRepository events; @Autowired WorkflowOrchestrationWorkRepository work;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired com.mediassistrover.backend.validation.WorkflowExecutionReadiness readiness;
    @Autowired jakarta.persistence.EntityManager entityManager;
    @Autowired NavigationProfileService profiles;
    @MockitoBean PahoMqttTransport transport;
    private TransactionTemplate tx;
    private MqttTestFixture fixture;
    private final List<Publication> published=new java.util.concurrent.CopyOnWriteArrayList<>();
    record Publication(String topic,JsonNode json,int qos) { }

    @BeforeEach void setup() {
        tx=new TransactionTemplate(transactionManager);properties.setEnabled(true);properties.setDispatchEnabled(true);properties.setAllowSimulation(true);
        properties.setEnabledStepTypes(new HashSet<>(List.of("MOVE","WAIT","CONTROL_HARDWARE","EXECUTE_SENSING")));properties.getRobotIds().clear();connection.connectedNow();published.clear();
        when(transport.connected()).thenReturn(true);
        when(transport.publish(anyString(),any(byte[].class),anyInt())).thenAnswer(invocation->{published.add(new Publication(invocation.getArgument(0),wire.parse(invocation.getArgument(1)),invocation.getArgument(2)));return CompletableFuture.completedFuture(null);});
    }
    @AfterEach void cleanup() {
        if (fixture!=null) tx.executeWithoutResult(status->MqttTestFixture.cleanup(entityManager,fixture));
        properties.setEnabled(false);properties.setDispatchEnabled(false);properties.getRobotIds().clear();connection.disconnected();
    }
    private MqttTestFixture fixture(StepType type,String sensing) {
        fixture=tx.execute(status->MqttTestFixture.create(entityManager,json,type,sensing));properties.getRobotIds().add(fixture.robotId);
        tx.executeWithoutResult(status->wakeups.request(jobs.findById(fixture.jobId).orElseThrow()));orchestrator.advance(fixture.jobId);return fixture;
    }
    private void receive(ObjectNode raw) {router.route(MqttTopics.topic(fixture.robotId,"events"),wire.bytes(raw),1,false);}
    private void hello() {receive(fixture.hello(json));}
    private void heartbeat() {router.route(MqttTopics.topic(fixture.robotId,"heartbeat"),wire.bytes(fixture.heartbeat(json)),0,false);}
    private RobotCommandOutbox dispatch() {hello();heartbeat();confirmProfile();orchestrator.advance(fixture.jobId);RobotCommandOutbox c=commands.findByJobStepId(fixture.piStepId).orElseThrow();fixture.commandId=c.getCommandId();publisher.command(c.getCommandId());return commands.findById(c.getCommandId()).orElseThrow();}
    private void confirmProfile() {if(fixture.type!=StepType.MOVE)return;var robot=robots.findById(fixture.robotId).orElseThrow();var device=new com.mediassistrover.backend.security.DevicePrincipal(robot.getRobotId(),robot.getRobotCode(),robot.getDeviceTokenVersion(),"test",Instant.now().plusSeconds(300));var manifest=profiles.manifest(device);profiles.confirm(new NavigationProfileService.Confirm(fixture.transportId,fixture.producerId,fixture.mapId,manifest.profile().path("map_asset_sha256").asText(),manifest.profileHash()),device);}
    private ObjectNode ack(boolean accepted) {return fixture.event(json,"COMMAND_ACK",json.createObjectNode().put("commandId",fixture.commandId.toString()).put("commandType","EXECUTE_STEP").put("accepted",accepted).put("status","ACCEPTED"));}
    private ObjectNode start() {return fixture.event(json,"STEP_STARTED",json.createObjectNode().put("attemptNo",1).put("stepType",fixture.type.name()).set("resolvedInputs",commands.findById(fixture.commandId).orElseThrow().getPayloadJson().path("step").path("params")));}
    private ObjectNode result(boolean stopped) {var p=json.createObjectNode().put("status","SUCCEEDED").put("retryable",false).put("physicalStopConfirmed",stopped);p.putNull("errorCode");p.putNull("details");p.set("result",fixture.output(json));return fixture.event(json,"STEP_RESULT",p);}
    private ObjectNode error(String status,Boolean stopped) {var p=json.createObjectNode().put("status",status).put("errorCode","TEST_ERROR").put("retryable",false);p.putNull("details");if(stopped!=null)p.put("physicalStopConfirmed",stopped);return fixture.event(json,"STEP_ERROR",p);}
    private Job currentJob() {return jobs.findById(fixture.jobId).orElseThrow();}

    @Test void helloNeedsHeartbeatAndRetainedPresenceCannotAuthorizeDispatch() {
        fixture(StepType.WAIT,null);
        var presence=json.createObjectNode().put("robotId",fixture.robotId.toString()).put("transportSessionId",fixture.transportId.toString()).put("online",true);
        router.route(MqttTopics.topic(fixture.robotId,"presence"),wire.bytes(presence),1,true);orchestrator.advance(fixture.jobId);
        assertThat(commands.findByJobStepId(fixture.piStepId)).isEmpty();hello();
        assertThat(sessionRows.findById(fixture.robotId).orElseThrow().isHandshakeAcked()).isTrue();
        tx.executeWithoutResult(status->wakeups.request(jobs.findById(fixture.jobId).orElseThrow()));orchestrator.advance(fixture.jobId);
        assertThat(commands.findByJobStepId(fixture.piStepId)).isEmpty();heartbeat();orchestrator.advance(fixture.jobId);
        assertThat(commands.findByJobStepId(fixture.piStepId)).isPresent();
        assertThat(published).filteredOn(p->"HELLO_ACK".equals(p.json.path("messageType").asText())).hasSize(1);
        assertThat(inbox.count()).isEqualTo(1); // heartbeat/presence do not enter durable inbox
    }

    @Test void oneCommandContainsOnlyFrozenActionAndRequiresBothAckStages() {
        fixture(StepType.WAIT,null);RobotCommandOutbox c=dispatch();
        JsonNode command=c.getPayloadJson();assertThat(command.has("workflowDefinition")).isFalse();assertThat(command.has("context")).isFalse();assertThat(command.has("jobSteps")).isFalse();
        assertThat(command.path("step").path("params").path("duration_sec").asInt()).isEqualTo(2);assertThat(command.path("step").has("attemptNo")).isFalse();
        tx.executeWithoutResult(status->{steps.findById(fixture.piStepId).orElseThrow().getStep().setParamsJson(json.createObjectNode().put("wait_type","EVENT").put("event_type","CONFIG_CHANGED").put("correlation_key","changed"));
            jobs.findById(fixture.jobId).orElseThrow().setContextJson(json.createObjectNode().put("changed",true));});
        heartbeat();orchestrator.advance(fixture.jobId);assertThat(commands.findById(c.getCommandId()).orElseThrow().getPayloadJson()).isEqualTo(command);
        assertThat(steps.findById(fixture.piStepId).orElseThrow().getStatus()).isEqualTo(JobStepStatus.PENDING);
        receive(ack(true));assertThat(steps.findById(fixture.piStepId).orElseThrow().getStatus()).isEqualTo(JobStepStatus.PENDING);
        receive(start());assertThat(steps.findById(fixture.piStepId).orElseThrow().getStatus()).isEqualTo(JobStepStatus.RUNNING);
        receive(result(true));orchestrator.advance(fixture.jobId);orchestrator.advance(fixture.jobId);orchestrator.advance(fixture.jobId);
        assertThat(currentJob().getStatus()).isEqualTo(JobStatus.SUCCEEDED);assertThat(currentJob().getContextJson().path("done").asBoolean()).isTrue();
        assertThat(published).filteredOn(p->p.topic.endsWith("/commands")).hasSize(1);
    }

    @Test void duplicateResultAcksAgainWithoutCompletingOrAdvancingTwice() {
        fixture(StepType.WAIT,null);dispatch();receive(ack(true));receive(start());ObjectNode result=result(true);receive(result);
        long generation=work.findById(fixture.jobId).orElseThrow().getRequestedGeneration();long eventCount=events.count();receive(result);
        assertThat(events.count()).isEqualTo(eventCount);assertThat(work.findById(fixture.jobId).orElseThrow().getRequestedGeneration()).isEqualTo(generation);
        assertThat(published).filteredOn(p->result.path("messageId").asText().equals(p.json.path("messageId").asText())).hasSize(2);
    }

    @Test void conflictingIdentityIsRejectedAndOriginalReceiptRemainsAccepted() {
        fixture(StepType.WAIT,null);dispatch();ObjectNode ack=ack(true);receive(ack);ObjectNode changed=ack.deepCopy();((ObjectNode)changed.path("payload")).put("accepted",false);
        assertThatThrownBy(()->receive(changed)).hasMessageContaining("conflicting business content");
        assertThat(inbox.findById(UUID.fromString(ack.path("messageId").asText())).orElseThrow().getAccepted()).isTrue();
        assertThat(currentJob().getStatus()).isEqualTo(JobStatus.RECOVERY_REQUIRED);
        assertThat(published.getLast().json.path("accepted").asBoolean()).isFalse();
    }

    @Test void startedWithDifferentParamsRollsBackAndRejectsBeforePiCanAct() {
        fixture(StepType.WAIT,null);dispatch();receive(ack(true));ObjectNode start=start();((ObjectNode)start.path("payload").path("resolvedInputs")).put("duration_sec",3);receive(start);
        assertThat(steps.findById(fixture.piStepId).orElseThrow().getStatus()).isEqualTo(JobStepStatus.PENDING);
        assertThat(currentJob().getStatus()).isEqualTo(JobStatus.RECOVERY_REQUIRED);assertThat(published.getLast().json.path("accepted").asBoolean()).isFalse();
    }

    @Test void errorBeforeStartedPreservesTimeoutAndUsesConfiguredTimeoutRoute() {
        fixture(StepType.WAIT,null);dispatch();receive(error("TIMED_OUT",null));
        assertThat(steps.findById(fixture.piStepId).orElseThrow().getStatus()).isEqualTo(JobStepStatus.TIMED_OUT);
        orchestrator.advance(fixture.jobId);assertThat(currentJob().getStatus()).isEqualTo(JobStatus.FAILED);
    }

    @Test void restartErrorWithoutPhysicalStopKeepsReportedStatusAndHoldsRobot() {
        fixture(StepType.CONTROL_HARDWARE,null);dispatch();receive(error("FAILED",null));
        assertThat(steps.findById(fixture.piStepId).orElseThrow().getStatus()).isEqualTo(JobStepStatus.FAILED);
        assertThat(currentJob().getStatus()).isEqualTo(JobStatus.RECOVERY_REQUIRED);assertThat(robots.findById(fixture.robotId).orElseThrow().getStatus()).isEqualTo(RobotStatus.ERROR);
        orchestrator.advance(fixture.jobId);assertThat(steps.findById(fixture.endStepId).orElseThrow().getStartedAt()).isNull();
    }

    @Test void moveSuccessWithoutStopEvidenceCannotRunNextStep() {
        fixture(StepType.MOVE,null);dispatch();receive(ack(true));receive(start());receive(result(false));
        assertThat(steps.findById(fixture.piStepId).orElseThrow().getStatus()).isEqualTo(JobStepStatus.SUCCEEDED);
        assertThat(currentJob().getStatus()).isEqualTo(JobStatus.RECOVERY_REQUIRED);orchestrator.advance(fixture.jobId);
        assertThat(steps.findById(fixture.endStepId).orElseThrow().getStatus()).isEqualTo(JobStepStatus.PENDING);
    }

    @Test void moveLockHealthAndClearanceAcceptTheirOwnResolvedOutputs() {
        for (String variant:List.of("MOVE","LOCK","ROBOT_HEALTH","SIDE_CLEARANCE")) {
            StepType type=variant.equals("MOVE")?StepType.MOVE:variant.equals("LOCK")?StepType.CONTROL_HARDWARE:StepType.EXECUTE_SENSING;
            fixture(type,type==StepType.EXECUTE_SENSING?variant:null);dispatch();receive(ack(true));receive(start());receive(result(true));
            orchestrator.advance(fixture.jobId);orchestrator.advance(fixture.jobId);orchestrator.advance(fixture.jobId);
            assertThat(currentJob().getStatus()).as(variant).isEqualTo(JobStatus.SUCCEEDED);
            tx.executeWithoutResult(status->MqttTestFixture.cleanup(entityManager,fixture));fixture=null;published.clear();
        }
    }

    @Test void heldJobKeepsRobotBusyWhenPiHeartbeatReportsIdle() {
        fixture(StepType.WAIT,null);dispatch();heartbeat();
        assertThat(robots.findById(fixture.robotId).orElseThrow().getStatus()).isEqualTo(RobotStatus.BUSY);
    }

    @Test void sameSessionRetryKeepsCommandIdentityBodyAndExpiry() {
        fixture(StepType.WAIT,null);RobotCommandOutbox c=dispatch();JsonNode snapshot=c.getPayloadJson().deepCopy();
        tx.executeWithoutResult(status->commands.findById(c.getCommandId()).orElseThrow().setLastPublishAt(Instant.now().minusSeconds(10)));
        publisher.command(c.getCommandId());RobotCommandOutbox retried=commands.findById(c.getCommandId()).orElseThrow();
        assertThat(retried.getSendAttempts()).isEqualTo(2);assertThat(retried.getPayloadJson()).isEqualTo(snapshot);assertThat(retried.getExpiresAt()).isEqualTo(c.getExpiresAt());
        properties.setDispatchEnabled(false);tx.executeWithoutResult(status->commands.findById(c.getCommandId()).orElseThrow().setLastPublishAt(Instant.now().minusSeconds(10)));
        publisher.command(c.getCommandId());assertThat(commands.findById(c.getCommandId()).orElseThrow().getSendAttempts()).isEqualTo(2);
    }

    @Test void replayedAcceptedAckIsNotACompletion() {
        fixture(StepType.WAIT,null);dispatch();ObjectNode ack=ack(true);((ObjectNode)ack.path("payload")).put("replayed",true).set("result",json.createObjectNode().put("status","ACCEPTED"));receive(ack);
        assertThat(steps.findById(fixture.piStepId).orElseThrow().getStatus()).isEqualTo(JobStepStatus.PENDING);
        assertThat(commands.findById(fixture.commandId).orElseThrow().getState()).isEqualTo("ACKNOWLEDGED");
    }

    @Test void oldSessionAndCrossRobotPayloadCannotChangeJob() {
        fixture(StepType.WAIT,null);dispatch();ObjectNode ack=ack(true);ack.put("transportSessionId",UUID.randomUUID().toString());
        assertThatThrownBy(()->receive(ack)).hasMessageContaining("Stale MQTT session");
        ObjectNode foreign=ack(true);foreign.put("robotId",UUID.randomUUID().toString());
        assertThatThrownBy(()->receive(foreign)).hasMessageContaining("topic identity");assertThat(steps.findById(fixture.piStepId).orElseThrow().getStatus()).isEqualTo(JobStepStatus.PENDING);
    }

    @Test void crashBeforeProcessingRetainsInboxAndRollbackDoesNotSendEventAck() {
        fixture(StepType.WAIT,null);dispatch();ObjectNode ack=ack(true);MqttWire.Envelope e=wire.envelope(ack);store.receive(ack,e);
        int before=published.size();
        tx.executeWithoutResult(status->{processor.process(e.messageId());publisher.acknowledgement(e.messageId(),true);assertThat(published).hasSize(before);status.setRollbackOnly();});
        assertThat(inbox.findById(e.messageId()).orElseThrow().getState()).isEqualTo("PENDING");assertThat(published).hasSize(before);
        processor.process(e.messageId());publisher.acknowledgement(e.messageId(),true);
        assertThat(inbox.findById(e.messageId()).orElseThrow().getState()).isEqualTo("PROCESSED");assertThat(published).hasSize(before+1);
    }

    @Test void reconnectWithoutJournalEvidenceDoesNotRepublishPhysicalCommand() {
        fixture(StepType.CONTROL_HARDWARE,null);RobotCommandOutbox c=dispatch();fixture.transportId=UUID.randomUUID();hello();heartbeat();
        assertThat(currentJob().getStatus()).isEqualTo(JobStatus.RECOVERY_REQUIRED);
        tx.executeWithoutResult(status->commands.findById(c.getCommandId()).orElseThrow().setLastPublishAt(Instant.now().minusSeconds(10)));publisher.command(c.getCommandId());
        assertThat(published).filteredOn(p->p.topic.endsWith("/commands")).hasSize(1);
    }

    @Test void expiredUnpublishedCommandDoesNotExecuteOrWaitForFakePiSuccess() {
        fixture(StepType.WAIT,null);hello();heartbeat();orchestrator.advance(fixture.jobId);RobotCommandOutbox c=commands.findByJobStepId(fixture.piStepId).orElseThrow();
        tx.executeWithoutResult(status->commands.findById(c.getCommandId()).orElseThrow().setExpiresAt(Instant.now().minusSeconds(1)));publisher.command(c.getCommandId());
        assertThat(commands.findById(c.getCommandId()).orElseThrow().getState()).isEqualTo("EXPIRED");
        assertThat(steps.findById(fixture.piStepId).orElseThrow().getStatus()).isEqualTo(JobStepStatus.TIMED_OUT);assertThat(published).noneMatch(p->p.topic.endsWith("/commands"));
    }

    @Test void reconnectWithJournalRebindsOnlyTransportAndReplaysSameOperation() {
        fixture(StepType.WAIT,null);RobotCommandOutbox c=dispatch();ObjectNode original=(ObjectNode)c.getPayloadJson().deepCopy();
        fixture.transportId=UUID.randomUUID();ObjectNode hello=fixture.hello(json);
        ((com.fasterxml.jackson.databind.node.ArrayNode)hello.path("payload").path("states")).addObject()
                .put("jobId",fixture.jobId.toString()).put("jobStepId",fixture.piStepId.toString()).put("operationKey",fixture.operationKey).put("status","ACCEPTED");
        receive(hello);heartbeat();
        tx.executeWithoutResult(status->commands.findById(c.getCommandId()).orElseThrow().setLastPublishAt(Instant.now().minusSeconds(10)));
        publisher.command(c.getCommandId());ObjectNode resent=(ObjectNode)commands.findById(c.getCommandId()).orElseThrow().getPayloadJson();
        assertThat(resent.path("transportSessionId").asText()).isEqualTo(fixture.transportId.toString());
        original.remove("transportSessionId");ObjectNode business=resent.deepCopy();business.remove("transportSessionId");assertThat(business).isEqualTo(original);
        assertThat(currentJob().getStatus()).isEqualTo(JobStatus.RUNNING);receive(ack(true));receive(start());receive(result(true));
        assertThat(steps.findById(fixture.piStepId).orElseThrow().getStatus()).isEqualTo(JobStepStatus.SUCCEEDED);
    }

    @Test void replayedSuccessRequiresOutputAndPreviouslyCommittedStart() {
        fixture(StepType.WAIT,null);dispatch();receive(ack(true));receive(start());
        ObjectNode replay=ack(true);ObjectNode payload=(ObjectNode)replay.path("payload");payload.put("replayed",true);
        payload.set("result",result(true).path("payload"));receive(replay);
        assertThat(steps.findById(fixture.piStepId).orElseThrow().getStatus()).isEqualTo(JobStepStatus.SUCCEEDED);
        tx.executeWithoutResult(status->MqttTestFixture.cleanup(entityManager,fixture));fixture=null;published.clear();
        fixture(StepType.WAIT,null);dispatch();replay=ack(true);payload=(ObjectNode)replay.path("payload");payload.put("replayed",true);payload.set("result",result(true).path("payload"));receive(replay);
        assertThat(currentJob().getStatus()).isEqualTo(JobStatus.RECOVERY_REQUIRED);
        assertThat(steps.findById(fixture.piStepId).orElseThrow().getStatus()).isEqualTo(JobStepStatus.PENDING);
    }

    @Test void concurrentDuplicateResultsCommitOneEventAndOneWakeup() throws Exception {
        fixture(StepType.WAIT,null);dispatch();receive(ack(true));receive(start());ObjectNode completion=result(true);
        long before=events.count(),generation=work.findById(fixture.jobId).orElseThrow().getRequestedGeneration();
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);var gate=new java.util.concurrent.CountDownLatch(1);
        try {
            var first=pool.submit(()->{gate.await();receive(completion.deepCopy());return true;});
            var second=pool.submit(()->{gate.await();receive(completion.deepCopy());return true;});gate.countDown();
            assertThat(first.get(15,java.util.concurrent.TimeUnit.SECONDS)).isTrue();assertThat(second.get(15,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        } finally {pool.shutdownNow();}
        assertThat(events.count()).isEqualTo(before+1);
        assertThat(work.findById(fixture.jobId).orElseThrow().getRequestedGeneration()).isEqualTo(generation+1);
        assertThat(inbox.findById(UUID.fromString(completion.path("messageId").asText())).orElseThrow().getAccepted()).isTrue();
    }

    @Test void publishedExpiryRequiresRecoveryAndDoesNotReleaseRobot() {
        fixture(StepType.CONTROL_HARDWARE,null);RobotCommandOutbox c=dispatch();
        tx.executeWithoutResult(status->commands.findById(c.getCommandId()).orElseThrow().setExpiresAt(Instant.now().minusSeconds(1)));publisher.command(c.getCommandId());
        assertThat(currentJob().getStatus()).isEqualTo(JobStatus.RECOVERY_REQUIRED);
        assertThat(commands.findById(c.getCommandId()).orElseThrow().getState()).isEqualTo("RECOVERY_REQUIRED");
        assertThat(robots.findById(fixture.robotId).orElseThrow().getStatus()).isEqualTo(RobotStatus.ERROR);
    }

    @Test void recoveryAndCancelledStatusesArePreservedRatherThanRetried() {
        fixture(StepType.WAIT,null);dispatch();receive(error("CANCELLED",true));
        assertThat(currentJob().getStatus()).isEqualTo(JobStatus.CANCELLED);assertThat(steps.findById(fixture.piStepId).orElseThrow().getStatus()).isEqualTo(JobStepStatus.CANCELLED);
        tx.executeWithoutResult(status->MqttTestFixture.cleanup(entityManager,fixture));fixture=null;
        fixture(StepType.CONTROL_HARDWARE,null);dispatch();receive(error("RECOVERY_REQUIRED",true));
        assertThat(currentJob().getStatus()).isEqualTo(JobStatus.RECOVERY_REQUIRED);assertThat(steps.findById(fixture.piStepId).orElseThrow().getStatus()).isEqualTo(JobStepStatus.RECOVERY_REQUIRED);
    }

    @Test void allocationSeparatesMqttPhysicalWorkflowFromLegacyAndKeepsServerOnlyAvailable() {
        fixture(StepType.WAIT,null);
        tx.executeWithoutResult(status->{var job=jobs.findById(fixture.jobId).orElseThrow();var robot=job.getRobot();var workflow=job.getWorkflow();
            assertThat(readiness.acceptsRobot(workflow,robot)).isTrue();properties.getRobotIds().clear();assertThat(readiness.acceptsRobot(workflow,robot)).isFalse();
            steps.findById(fixture.piStepId).orElseThrow().getStep().setEnabled(false);assertThat(readiness.acceptsRobot(workflow,robot)).isTrue();
            properties.getRobotIds().add(fixture.robotId);workflow.setSchemaVersion(2);assertThat(readiness.acceptsRobot(workflow,robot)).isFalse();
        });
    }

    @Test void committedHelloReplayReestablishesSessionAfterBackendConnectionRestart() {
        fixture(StepType.WAIT,null);ObjectNode hello=fixture.hello(json);receive(hello);heartbeat();
        var prior=sessionRows.findById(fixture.robotId).orElseThrow();assertThat(sessions.fresh(prior)).isTrue();
        connection.disconnected();connection.connectedNow();assertThat(sessions.fresh(prior)).isFalse();
        receive(hello);assertThat(sessionRows.findById(fixture.robotId).orElseThrow().getConnectionId()).isEqualTo(connection.connectionId());
        assertThat(sessionRows.findById(fixture.robotId).orElseThrow().getLastHeartbeatAt()).isNull();
        heartbeat();assertThat(sessions.fresh(sessionRows.findById(fixture.robotId).orElseThrow())).isTrue();
        assertThat(inbox.findById(UUID.fromString(hello.path("messageId").asText())).orElseThrow().getAccepted()).isTrue();
    }

    @Test void restartedPiCannotResumeAnAttemptedPhysicalOperationAutomatically() {
        fixture(StepType.CONTROL_HARDWARE,null);dispatch();fixture.producerId=UUID.randomUUID();fixture.transportId=UUID.randomUUID();ObjectNode hello=fixture.hello(json);
        ((com.fasterxml.jackson.databind.node.ArrayNode)hello.path("payload").path("states")).addObject()
                .put("jobId",fixture.jobId.toString()).put("jobStepId",fixture.piStepId.toString()).put("operationKey",fixture.operationKey).put("status","ACCEPTED");receive(hello);
        assertThat(currentJob().getStatus()).isEqualTo(JobStatus.RECOVERY_REQUIRED);
        assertThat(sessionRows.findById(fixture.robotId).orElseThrow().isDispatchReady()).isFalse();
        assertThat(published).filteredOn(p->p.topic.endsWith("/commands")).hasSize(1);
    }

    @Test void adminAssignmentAndGenericReconcileCannotBypassMqttTransportOrStopChecks() {
        fixture(StepType.MOVE,null);
        var principal=tx.execute(status->com.mediassistrover.backend.security.AccountPrincipal.fromAccount(entityManager.find(Account.class,fixture.accountId)));
        tx.executeWithoutResult(status->{var job=jobs.findById(fixture.jobId).orElseThrow();job.setStatus(JobStatus.QUEUED);job.setAcceptedAt(null);job.getRobot().setStatus(RobotStatus.IDLE);});
        properties.getRobotIds().clear();
        assertThatThrownBy(()->jobService.assignRobot(fixture.jobId,fixture.robotId,principal,null)).hasMessageContaining("workflow transport");
        properties.getRobotIds().add(fixture.robotId);tx.executeWithoutResult(status->{var job=jobs.findById(fixture.jobId).orElseThrow();job.setStatus(JobStatus.RUNNING);job.getRobot().setStatus(RobotStatus.BUSY);});
        dispatch();receive(ack(true));receive(start());receive(result(false));
        var response=new com.mediassistrover.backend.dto.job.request.JobRequests.ReconcileJobRequest("Staff reviewed",com.mediassistrover.backend.dto.job.request.JobRequests.ReconcileOutcome.CONFIRM_CANCELLED);
        assertThatThrownBy(()->jobService.reconcile(fixture.jobId,response,principal,null)).hasMessageContaining("operation/stop reconciliation");
        assertThat(currentJob().getStatus()).isEqualTo(JobStatus.RECOVERY_REQUIRED);assertThat(robots.findById(fixture.robotId).orElseThrow().getStatus()).isEqualTo(RobotStatus.ERROR);
    }
}
