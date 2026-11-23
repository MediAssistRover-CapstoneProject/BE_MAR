package com.mediassistrover.backend.mqtt;

import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.repository.*;
import com.mediassistrover.backend.service.RobotTaskArbitrationService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import java.time.*;
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

    @Test void cancelledJobPublishesStopWhileExecutionRemainsBlocked() {
        var now=java.time.Instant.now();var transport=UUID.randomUUID();
        var session=new RobotMqttSession();session.setTransportSessionId(transport);session.setHandshakeAcked(true);session.setLastHeartbeatAt(now);
        var registry=mock(MqttSessionRegistry.class);
        when(sessions.lock(robotId)).thenReturn(Optional.of(session));when(registry.owned(session)).thenReturn(true);
        ReflectionTestUtils.setField(plans,"registry",registry);ReflectionTestUtils.setField(plans,"clock",java.time.Clock.fixed(now,java.time.ZoneOffset.UTC));
        job.setStatus(JobStatus.CANCELLED);job.setStopStatus(com.mediassistrover.backend.enums.JobStopStatus.PENDING);
        command.setCommandType("CANCEL_STEP");command.setState("PENDING");command.setExpiresAt(now.plusSeconds(30));
        command.setPayloadJson(new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("transportSessionId",transport.toString()));
        when(properties.manages(robotId)).thenReturn(true);
        assertNotNull(plans.command(id));assertEquals(1,command.getSendAttempts());
        assertEquals("PUBLISHING",command.getState());verifyNoInteractions(arbitration);
    }

    @Test void futurePatrolDoesNotOwnTheCurrentHeartbeatMapOrBecomeBusy(){
        var f=new IntentContextFixture();
        f.registry.heartbeat(f.heartbeat());
        assertEquals(JobStatus.QUEUED,f.patrol.getStatus());assertEquals(RobotStatus.IDLE,f.robot.getStatus());
        assertTrue(f.link.isDispatchReady());assertSame(f.currentMap,f.robot.getCurrentMap());
        verifyNoInteractions(f.safety);
        var moving=Job.builder().jobId(UUID.randomUUID()).status(JobStatus.RUNNING).beOrchestrated(true)
                .schemaVersion(3).map(f.futureMap).robot(f.robot).build();
        when(f.jobs.lockRobotJobs(eq(robotId),any())).thenReturn(List.of(f.patrol,moving));
        f.registry.heartbeat(f.heartbeat());
        assertFalse(f.link.isDispatchReady());
        verify(f.safety).recovery(moving,"Heartbeat map differs from held Job");
        verify(f.safety,never()).recovery(eq(f.patrol),anyString());
    }

    @Test void operatorCanPrepareNavigationForUntouchedPatrolButCannotAlterStartedOrPinnedWork(){
        var f=new IntentContextFixture();
        var rows=mock(RobotNavigationSessionRepository.class);var maps=mock(FacilityMapRepository.class);
        var profiles=mock(RobotNavigationProfileRepository.class);var bundles=mock(com.mediassistrover.backend.mapping.MapBundleService.class);
        var navigation=mock(com.mediassistrover.backend.service.NavigationSessionService.class,CALLS_REAL_METHODS);
        Map.of("rows",rows,"robots",f.robots,"maps",maps,"links",mock(RobotMqttSessionRepository.class),
                "profiles",profiles,"jobs",f.jobs,"bundles",bundles,"wire",f.wire,"clock",f.clock)
                .forEach((name,value)->ReflectionTestUtils.setField(navigation,name,value));
        when(maps.findById(f.futureMap.getMapId())).thenReturn(Optional.of(f.futureMap));
        when(bundles.navigationSafe(f.futureMap.getMapId())).thenReturn(true);
        var request=new com.mediassistrover.backend.service.NavigationSessionService.Create(f.futureMap.getMapId(),300);
        assertEquals("WAITING_FOR_PI",navigation.create(robotId,request).path("state").asText());
        verify(rows).saveAndFlush(any());clearInvocations(rows);
        f.patrol.setStartedAt(f.clock.instant());
        assertThrows(com.mediassistrover.backend.exception.BusinessRuleException.class,()->navigation.create(robotId,request));
        f.patrol.setStartedAt(null);f.patrol.setSelectedJobStep(new JobStep());
        assertThrows(com.mediassistrover.backend.exception.BusinessRuleException.class,()->navigation.create(robotId,request));
        f.patrol.setSelectedJobStep(null);f.patrol.setStopStatus(JobStopStatus.PENDING);
        assertThrows(com.mediassistrover.backend.exception.BusinessRuleException.class,()->navigation.create(robotId,request));
        f.patrol.setStopStatus(null);f.patrol.setWorkflow(Workflow.builder().serviceType("NAVIGATION").build());
        assertThrows(com.mediassistrover.backend.exception.BusinessRuleException.class,()->navigation.create(robotId,request));
        verify(rows,never()).saveAndFlush(any());
    }

    @Test void futurePatrolDoesNotPreventReviewingTheActualMappingRecoveryJob(){
        var f=new IntentContextFixture();
        var mapping=Job.builder().jobId(UUID.randomUUID()).robot(f.robot).status(JobStatus.RECOVERY_REQUIRED).beOrchestrated(true).build();
        var recovery=mock(com.mediassistrover.backend.mapping.MappingRecoveryService.class,CALLS_REAL_METHODS);
        Map.of("jobs",f.jobs,"steps",mock(JobStepRepository.class),"links",mock(RobotMqttSessionRepository.class),
                "maps",mock(MappingSessionRepository.class),"commands",mock(RobotCommandOutboxRepository.class),
                "scope",mock(com.mediassistrover.backend.validation.StaffOperationalScopePolicy.class),"wire",f.wire,"clock",f.clock)
                .forEach((name,value)->ReflectionTestUtils.setField(recovery,name,value));
        when(f.jobs.findById(mapping.getJobId())).thenReturn(Optional.of(mapping));
        when(f.jobs.findByRobotRobotIdAndStatusIn(eq(robotId),any())).thenReturn(List.of(mapping,f.patrol));
        assertFalse(recovery.evidence(mapping.getJobId(),null).path("blockers").toString().contains("OTHER_HELD_ROBOT_JOB"));
        f.patrol.setAcceptedAt(f.clock.instant());
        assertTrue(recovery.evidence(mapping.getJobId(),null).path("blockers").toString().contains("OTHER_HELD_ROBOT_JOB"));
    }

    private class IntentContextFixture {
        final MqttWire wire=new MqttWire();final Clock clock=Clock.fixed(Instant.parse("2026-11-23T09:00:00Z"),ZoneOffset.UTC);
        final JobRepository jobs=mock(JobRepository.class);final RobotRepository robots=mock(RobotRepository.class);
        final MqttJobSafety safety=mock(MqttJobSafety.class);
        final FacilityMap currentMap=FacilityMap.builder().mapId(UUID.randomUUID()).status(MapStatus.ACTIVE).assetHash("a".repeat(64)).frameId("map").build();
        final FacilityMap futureMap=FacilityMap.builder().mapId(UUID.randomUUID()).status(MapStatus.ACTIVE).assetHash("b".repeat(64)).frameId("map").build();
        final Robot robot=Robot.builder().robotId(robotId).status(RobotStatus.IDLE).currentMap(currentMap).build();
        final Job patrol=Job.builder().jobId(UUID.randomUUID()).robot(robot).map(futureMap).status(JobStatus.QUEUED)
                .beOrchestrated(true).schemaVersion(3).workflow(Workflow.builder().serviceType("PATROL").build()).build();
        final RobotMqttSession link=new RobotMqttSession();final UUID transport=UUID.randomUUID(),producer=UUID.randomUUID();
        final MqttSessionRegistry registry=mock(MqttSessionRegistry.class,CALLS_REAL_METHODS);
        IntentContextFixture(){
            link.setProducerSessionId(producer);link.setHelloJson(wire.object());link.setDispatchReady(true);
            var maps=mock(FacilityMapRepository.class);var mapping=mock(com.mediassistrover.backend.mapping.MappingLifecycleService.class);
            when(jobs.lockRobotJobs(eq(robotId),any())).thenReturn(List.of(patrol));when(robots.findByIdForUpdate(robotId)).thenReturn(Optional.of(robot));
            when(maps.findById(currentMap.getMapId())).thenReturn(Optional.of(currentMap));when(mapping.telemetryMatches(eq(robotId),any())).thenReturn(true);
            doReturn(link).when(registry).requireCurrent(robotId,transport);
            Map<String,Object> fields=new HashMap<>();fields.put("jobs",jobs);fields.put("robots",robots);fields.put("maps",maps);fields.put("safety",safety);
            fields.put("mapping",mapping);fields.put("clock",clock);fields.put("commands",mock(RobotCommandOutboxRepository.class));
            fields.put("navigationSessions",mock(com.mediassistrover.backend.service.NavigationSessionService.class));fields.put("navigationProfiles",mock(RobotNavigationProfileRepository.class));
            fields.put("histories",mock(RobotStatusHistoryRepository.class));fields.put("wakeups",mock(com.mediassistrover.backend.service.OrchestrationWorkService.class));
            fields.put("applicationEvents",mock(org.springframework.context.ApplicationEventPublisher.class));
            fields.forEach((name,value)->ReflectionTestUtils.setField(registry,name,value));
        }
        MqttWire.Envelope heartbeat(){
            var payload=wire.object().put("currentMapId",currentMap.getMapId().toString()).put("mapAssetHash",currentMap.getAssetHash())
                    .put("status","IDLE").putNull("activeJobStepId");payload.set("capabilities",wire.object().arrayNode());
            return new MqttWire.Envelope("1",UUID.randomUUID(),null,null,robotId,null,null,"HEARTBEAT",clock.instant().toString(),producer,1,transport,payload);
        }
    }
}
