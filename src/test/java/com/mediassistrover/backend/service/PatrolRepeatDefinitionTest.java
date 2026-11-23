package com.mediassistrover.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.repository.*;
import com.mediassistrover.backend.exception.BusinessRuleException;
import com.mediassistrover.backend.mqtt.MqttProperties;
import com.mediassistrover.backend.mqtt.MqttStepDispatchService;
import com.mediassistrover.backend.validation.StepExecutionPolicy;
import com.mediassistrover.backend.validation.StepInputContractValidator;
import com.mediassistrover.backend.validation.WorkflowJsonValidator;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PatrolRepeatDefinitionTest {
    final PatrolWorkflowService service=mock(PatrolWorkflowService.class,CALLS_REAL_METHODS);
    final UUID mapId=UUID.randomUUID();
    PatrolRepeatDefinitionTest(){ReflectionTestUtils.setField(service,"json",new ObjectMapper());}
    PatrolWorkflowService.Create request(Integer repeat){
        return new PatrolWorkflowService.Create("Route",mapId,List.of(new PatrolWorkflowService.Checkpoint(UUID.randomUUID(),UUID.randomUUID(),"CROWD_DENSITY")),.25,60,12,false,repeat);
    }
    @Test void defaultAndFiniteRepeatAreExplicitWorkflowDefaults(){
        assertEquals(1,service.graph(request(null),"WF_TEST").workflow().defaultInputsJson().path("patrol_repeat_count").asInt());
        var graph=service.graph(request(3),"WF_TEST");
        assertEquals(3,graph.workflow().defaultInputsJson().path("patrol_repeat_count").asInt());
        assertEquals(mapId.toString(),graph.workflow().defaultInputsJson().path("map_id").asText());
    }
    @Test void continuousRepeatsRetainABoundedSingleRoundGraph(){
        var graph=service.graph(request(0),"WF_TEST");
        assertEquals(0,graph.workflow().defaultInputsJson().path("patrol_repeat_count").asInt());
        assertTrue(graph.graph().steps().stream().allMatch(step->step.maxVisits()==1));
        assertTrue(graph.workflow().maxDurationSec()>0);
    }
    @Test void unboundedConfigurationValuesAreRejected(){
        assertThrows(BusinessRuleException.class,()->service.graph(request(-1),"WF_TEST"));
        assertThrows(BusinessRuleException.class,()->service.graph(request(1001),"WF_TEST"));
    }
    @Test void fallOnlyCheckpointPassesTheIntegratedPiDispatchContract(){
        var request=new PatrolWorkflowService.Create("Fall route",mapId,
                List.of(new PatrolWorkflowService.Checkpoint(UUID.randomUUID(),null,"PATROL_FALL")),.25,60,3,true,1);
        var sensing=service.graph(request,"WF_FALL").graph().steps().stream()
                .filter(step->step.stepType()==StepType.EXECUTE_SENSING).findFirst().orElseThrow();
        var step=Step.builder().workflow(Workflow.builder().schemaVersion(3).build())
                .stepCode(sensing.stepCode()).stepType(sensing.stepType())
                .timeoutSec(sensing.timeoutSec())
                .paramsJson(sensing.paramsJson()).inputMappingJson(sensing.inputMappingJson()).build();
        var inputs=new StepInputContractValidator(new WorkflowJsonValidator());
        assertDoesNotThrow(()->inputs.validatePiParams(step,step.getParamsJson()));
        var config=new MqttProperties();config.setEnabled(true);config.setDispatchEnabled(true);
        config.setRobotIds(Set.of(UUID.randomUUID()));config.setEnabledStepTypes(Set.of("EXECUTE_SENSING"));
        var dispatch=mock(MqttStepDispatchService.class,CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(dispatch,"properties",config);
        ReflectionTestUtils.setField(dispatch,"executionPolicy",new StepExecutionPolicy(config));
        assertDoesNotThrow(()->dispatch.requireActionSupported(step));
        var withoutOptIn=step.getParamsJson().deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode)withoutOptIn).put("fall_detection",false);
        assertThrows(BusinessRuleException.class,()->inputs.validatePiParams(step,withoutOptIn));
    }

    @Test void onceScheduleRetainsItsDueIntentUntilAJobExists(){
        var f=new ScheduleFixture(ScheduleType.ONCE);var fire=f.schedule.getNextRunAt();
        assertEquals(0,f.runner.runDue(f.now,50));
        assertEquals(ScheduleStatus.ACTIVE,f.schedule.getStatus());assertEquals(fire,f.schedule.getNextRunAt());
        assertNull(f.schedule.getLastRunAt());
        when(f.execution.createForSchedule(f.schedule,fire,f.schedule.getInputTemplateJson()))
                .thenReturn(Job.builder().jobId(UUID.randomUUID()).status(JobStatus.QUEUED).build());
        assertEquals(1,f.runner.runDue(f.now.plusSeconds(10),50));
        assertEquals(ScheduleStatus.RETIRED,f.schedule.getStatus());assertNull(f.schedule.getNextRunAt());
        assertEquals(fire,f.schedule.getLastRunAt());
    }

    @Test void recurringPatrolCoalescesMissedOccurrencesWithoutCreatingBacklog(){
        var f=new ScheduleFixture(ScheduleType.INTERVAL);f.schedule.setIntervalSec(60);
        assertEquals(0,f.runner.runDue(f.now,50));
        assertEquals(f.now.plusSeconds(60),f.schedule.getNextRunAt());
        assertEquals(ScheduleStatus.ACTIVE,f.schedule.getStatus());assertNull(f.schedule.getLastRunAt());
        verify(f.execution,times(1)).createForSchedule(any(),any(),any());
    }

    @Test void blockedOnceScheduleCannotMonopolizeTheFirstDueBatch(){
        var f=new ScheduleFixture(ScheduleType.ONCE);var originalFire=f.schedule.getNextRunAt();
        var later=Schedule.builder().scheduleId(UUID.randomUUID()).workflow(f.schedule.getWorkflow())
                .scheduleType(ScheduleType.ONCE).status(ScheduleStatus.ACTIVE).nextRunAt(originalFire.plusSeconds(1))
                .inputTemplateJson(f.schedule.getInputTemplateJson()).build();
        when(f.repository.lockDueAfter(any(),eq(originalFire),eq(f.schedule.getScheduleId()),any())).thenReturn(List.of(later));
        when(f.execution.createForSchedule(eq(later),any(),any())).thenReturn(Job.builder().jobId(UUID.randomUUID()).build());
        assertEquals(0,f.runner.runDue(f.now,1));
        assertEquals(1,f.runner.runDue(f.now.plusSeconds(10),1));
        assertEquals(ScheduleStatus.RETIRED,later.getStatus());
        assertEquals(ScheduleStatus.ACTIVE,f.schedule.getStatus());assertEquals(originalFire,f.schedule.getNextRunAt());
        assertEquals(0,f.runner.runDue(f.now.plusSeconds(20),1),"The cursor wraps back to retry the retained occurrence");
        verify(f.execution,times(2)).createForSchedule(eq(f.schedule),eq(originalFire),any());
    }

    @Test void expiredPatrolWindowDoesNotCreateAnotherQueuedJob(){
        var f=new ScheduleFixture(ScheduleType.ONCE);f.schedule.setEndsAt(f.now.minusSeconds(1));
        assertEquals(0,f.runner.runDue(f.now,50));
        assertEquals(ScheduleStatus.RETIRED,f.schedule.getStatus());assertNull(f.schedule.getNextRunAt());
        verifyNoInteractions(f.execution);
    }

    @Test void firstCronFireUsesTimezoneAndNeverFiresAtAnUnmatchedStartsAt(){
        var f=new ScheduleFixture(ScheduleType.CRON);f.schedule.setCronExpression("0 0 8 * * *");
        f.schedule.setStatus(ScheduleStatus.PAUSED);
        f.schedule.setStartsAt(Instant.parse("2026-11-23T10:00:00Z"));
        f.runner.activate(f.schedule.getScheduleId(),f.actor,null);
        assertEquals(Instant.parse("2026-11-24T01:00:00Z"),f.schedule.getNextRunAt());
        f.schedule.setStatus(ScheduleStatus.PAUSED);f.schedule.setStartsAt(Instant.parse("2026-11-24T01:00:00Z"));
        f.runner.activate(f.schedule.getScheduleId(),f.actor,null);
        assertEquals(f.schedule.getStartsAt(),f.schedule.getNextRunAt(),"A matching inclusive start is eligible");
    }

    @Test void skippingAnExpiredOnceScheduleCannotLeaveAnActiveScheduleWithNoNextRun(){
        var f=new ScheduleFixture(ScheduleType.ONCE);f.schedule.setMisfirePolicy(MisfirePolicy.SKIP);
        f.schedule.setStatus(ScheduleStatus.PAUSED);
        f.runner.activate(f.schedule.getScheduleId(),f.actor,null);
        assertEquals(ScheduleStatus.RETIRED,f.schedule.getStatus());assertNull(f.schedule.getNextRunAt());
        verifyNoInteractions(f.execution);
    }

    @Test void queuedPatrolKeepsFixedCheckpointsWhileRuntimeReadinessExcludesOnlyItself(){
        var json=new ObjectMapper();var robotId=UUID.randomUUID();var endpointId=UUID.randomUUID();var candidateId=UUID.randomUUID();
        var map=FacilityMap.builder().mapId(mapId).status(MapStatus.ACTIVE).assetHash("a".repeat(64)).frameId("map").build();
        var robot=Robot.builder().robotId(robotId).status(RobotStatus.BUSY).currentMap(map).mapAssetHash(map.getAssetHash()).build();
        var workflow=Workflow.builder().workflowId(UUID.randomUUID()).workflowCode("WF_PATROL_QUEUE").serviceType("PATROL")
                .schemaVersion(3).defaultInputsJson(json.createObjectNode().put("map_id",mapId.toString())).build();
        var move=Step.builder().stepType(StepType.MOVE).enabled(true).paramsJson(json.createObjectNode()
                .put("mode","PATROL").put("map_id",mapId.toString()).put("endpoint_id",endpointId.toString())).build();
        var sense=Step.builder().stepType(StepType.EXECUTE_SENSING).enabled(true).paramsJson(json.createObjectNode().put("sensing_type","PATROL_FALL")).build();
        var endpoint=Endpoint.builder().endpointId(endpointId).map(map).active(true).accessible(true).yawRad(java.math.BigDecimal.ZERO).build();
        var maps=mock(FacilityMapRepository.class);var endpoints=mock(EndpointRepository.class);var steps=mock(StepRepository.class);
        var jobs=mock(JobRepository.class);var links=mock(RobotMqttSessionRepository.class);
        var registry=mock(com.mediassistrover.backend.mqtt.MqttSessionRegistry.class);var navigation=mock(NavigationProfileService.class);
        var mqtt=new MqttProperties();mqtt.setEnabled(true);mqtt.setDispatchEnabled(true);mqtt.setRobotIds(Set.of(robotId));
        mqtt.setEnabledStepTypes(Set.of("MOVE","EXECUTE_SENSING"));
        var link=new RobotMqttSession();link.setHeartbeatJson(json.createObjectNode().put("status","IDLE").putNull("activeJobStepId"));
        link.setHelloJson(json.createObjectNode().set("capabilities",json.createArrayNode().add("PATROL_FALL")));
        Map<String,Object> fields=new HashMap<>();fields.put("maps",maps);fields.put("endpoints",endpoints);fields.put("steps",steps);
        fields.put("jobs",jobs);fields.put("links",links);fields.put("registry",registry);fields.put("navigation",navigation);fields.put("mqtt",mqtt);
        fields.put("mediaSessions",mock(com.mediassistrover.backend.media.MediaSessionRepository.class));fields.put("incidents",mock(EmergencyIncidentRepository.class));
        fields.put("mappingSessions",mock(MappingSessionRepository.class));fields.put("commands",mock(RobotCommandOutboxRepository.class));
        fields.forEach((name,value)->ReflectionTestUtils.setField(service,name,value));
        when(maps.findById(mapId)).thenReturn(Optional.of(map));when(endpoints.findById(endpointId)).thenReturn(Optional.of(endpoint));
        when(steps.findByWorkflowWorkflowIdOrderBySequenceNoAsc(workflow.getWorkflowId())).thenReturn(List.of(move,sense));
        assertSame(map,service.queuedMap(workflow,robot,5,null,json.createObjectNode()));
        verifyNoInteractions(links,registry,navigation);
        assertNull(service.readyMap(workflow,robot,5,candidateId));
        robot.setStatus(RobotStatus.IDLE);
        var self=Job.builder().jobId(candidateId).status(JobStatus.QUEUED).build();
        when(jobs.findByRobotRobotIdAndStatusIn(robotId,JobOccupancyPolicy.NON_TERMINAL)).thenReturn(List.of(self));
        when(links.findById(robotId)).thenReturn(Optional.of(link));when(registry.fresh(link)).thenReturn(true);
        when(registry.advertises(eq(link),anyString())).thenReturn(true);when(navigation.ready(robot,link,move.getParamsJson())).thenReturn("profile");
        assertSame(map,service.readyMap(workflow,robot,5,candidateId));
        when(jobs.findByRobotRobotIdAndStatusIn(robotId,JobOccupancyPolicy.NON_TERMINAL))
                .thenReturn(List.of(self,Job.builder().jobId(UUID.randomUUID()).status(JobStatus.QUEUED).priority(1).build()));
        assertSame(map,service.readyMap(workflow,robot,5,candidateId),"Lower-priority queued work must not deadlock the selected candidate");
        when(registry.fresh(link)).thenReturn(false);assertNull(service.readyMap(workflow,robot,5,candidateId));
        when(registry.fresh(link)).thenReturn(true);
        when(jobs.findByRobotRobotIdAndStatusIn(robotId,JobOccupancyPolicy.NON_TERMINAL))
                .thenReturn(List.of(self,Job.builder().jobId(UUID.randomUUID()).status(JobStatus.RUNNING).build()));
        assertNull(service.readyMap(workflow,robot,5,candidateId));
        move.setParamsJson(json.createObjectNode().put("mode","PATROL").put("map_id",UUID.randomUUID().toString()).put("endpoint_id",endpointId.toString()));
        assertThrows(BusinessRuleException.class,()->service.queuedMap(workflow,robot,5,null,json.createObjectNode()));
    }

    private static class ScheduleFixture {
        final Instant now=Instant.parse("2026-11-23T09:00:00Z");
        final ScheduleRepository repository=mock(ScheduleRepository.class);
        final JobExecutionService execution=mock(JobExecutionService.class);
        final com.mediassistrover.backend.service.impl.ScheduleServiceImpl runner=
                mock(com.mediassistrover.backend.service.impl.ScheduleServiceImpl.class,CALLS_REAL_METHODS);
        final com.mediassistrover.backend.security.AccountPrincipal actor=com.mediassistrover.backend.security.AccountPrincipal
                .fromAccount(Account.builder().accountId(UUID.randomUUID()).role(Role.ADMIN).status(AccountStatus.ACTIVE).build());
        final Schedule schedule;
        ScheduleFixture(ScheduleType type) {
            var json=new ObjectMapper();var workflow=Workflow.builder().serviceType("PATROL").workflowCode("WF_PATROL_SCHEDULE")
                    .status(WorkflowStatus.PUBLISHED).acceptsNewJobs(true).build();
            schedule=Schedule.builder().scheduleId(UUID.randomUUID()).workflow(workflow).scheduleType(type).status(ScheduleStatus.PAUSED)
                    .timezone("Asia/Ho_Chi_Minh").startsAt(now.minusSeconds(3600)).nextRunAt(now.minusSeconds(600))
                    .misfirePolicy(MisfirePolicy.FIRE_ONCE).inputTemplateJson(json.createObjectNode()).build();
            when(repository.lockDue(any(),any())).thenReturn(List.of(schedule));when(repository.findById(schedule.getScheduleId())).thenReturn(Optional.of(schedule));
            var accounts=mock(AccountRepository.class);when(accounts.findById(actor.accountId())).thenReturn(Optional.of(Account.builder().accountId(actor.accountId()).build()));
            Map<String,Object> fields=new HashMap<>();fields.put("repository",repository);fields.put("jobExecutionService",execution);
            fields.put("clock",Clock.fixed(now,ZoneOffset.UTC));fields.put("staffScope",mock(com.mediassistrover.backend.validation.StaffOperationalScopePolicy.class));
            fields.put("readiness",mock(com.mediassistrover.backend.validation.WorkflowExecutionReadiness.class));fields.put("patrol",mock(PatrolWorkflowService.class));
            fields.put("auditService",mock(AuditService.class));fields.put("accountRepository",accounts);fields.put("mapper",mock(com.mediassistrover.backend.mapper.RequestJobMapper.class));
            fields.forEach((name,value)->ReflectionTestUtils.setField(runner,name,value));
            // runDue's repository only returns ACTIVE rows.
            schedule.setStatus(ScheduleStatus.ACTIVE);
        }
    }
}
