package com.mediassistrover.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.exception.BusinessRuleException;
import com.mediassistrover.backend.media.*;
import com.mediassistrover.backend.mqtt.MqttSessionRegistry;
import com.mediassistrover.backend.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class JobSuspensionServiceTest {
    final ObjectMapper json=new ObjectMapper();
    final Instant now=Instant.parse("2026-11-16T01:00:00Z");
    final JobRepository jobs=mock(JobRepository.class);
    final RobotRepository robots=mock(RobotRepository.class);
    final JobSuspensionHoldRepository holds=mock(JobSuspensionHoldRepository.class);
    final RobotCommandOutboxRepository commands=mock(RobotCommandOutboxRepository.class);
    final RobotMqttSessionRepository sessions=mock(RobotMqttSessionRepository.class);
    final MqttSessionRegistry registry=mock(MqttSessionRegistry.class);
    final NavigationProfileService navigation=mock(NavigationProfileService.class);
    final NavigationSessionService localization=mock(NavigationSessionService.class);
    final VoiceInteractionRepository voices=mock(VoiceInteractionRepository.class);
    final EmergencyIncidentRepository incidents=mock(EmergencyIncidentRepository.class);
    final MediaSessionRepository media=mock(MediaSessionRepository.class);
    final OrchestrationWorkService wakeups=mock(OrchestrationWorkService.class);
    final JobSuspensionService service=mock(JobSuspensionService.class,CALLS_REAL_METHODS);
    final Map<String,JobSuspensionHold> store=new HashMap<>();
    final FacilityMap map=FacilityMap.builder().mapId(UUID.randomUUID()).status(MapStatus.ACTIVE).assetHash("a".repeat(64)).build();
    final Robot robot=Robot.builder().robotId(UUID.randomUUID()).status(RobotStatus.BUSY).currentMap(map).mapAssetHash(map.getAssetHash()).build();
    final Workflow wf=Workflow.builder().serviceType("PATROL").maxDurationSec(60).build();
    final Job job=Job.builder().jobId(UUID.randomUUID()).workflow(wf).map(map).robot(robot).status(JobStatus.RUNNING)
        .schemaVersion(3).beOrchestrated(true).startedAt(now.minusSeconds(10)).patrolRepeatCount(3).patrolRound(2).build();
    final Step definition=Step.builder().stepId(UUID.randomUUID()).stepType(StepType.MOVE).paramsJson(json.createObjectNode()).build();
    final JobStep move=JobStep.builder().jobStepId(UUID.randomUUID()).step(definition).status(JobStepStatus.RUNNING).stepTypeSnapshot("MOVE")
        .resolvedInputsJson(json.createObjectNode().put("map_id",map.getMapId().toString()).put("endpoint_id",UUID.randomUUID().toString())).build();

    JobSuspensionServiceTest(){
        job.setSelectedJobStep(move);
        Map.ofEntries(Map.entry("jobs",jobs),Map.entry("robots",robots),Map.entry("holds",holds),Map.entry("commands",commands),
            Map.entry("sessions",sessions),Map.entry("registry",registry),Map.entry("navigation",navigation),Map.entry("navigationSessions",localization),
            Map.entry("voices",voices),Map.entry("incidents",incidents),Map.entry("media",media),Map.entry("events",mock(JobEventRepository.class)),
            Map.entry("updates",mock(ApplicationEventPublisher.class)),Map.entry("wakeups",wakeups),Map.entry("json",json),
            Map.entry("clock",Clock.fixed(now,ZoneOffset.UTC))).forEach((key,value)->ReflectionTestUtils.setField(service,key,value));
        when(holds.findByJobJobIdAndSourceAndSourceId(any(),anyString(),any())).thenAnswer(i->Optional.ofNullable(store.get(i.getArgument(1)+":"+i.getArgument(2))));
        when(holds.save(any())).thenAnswer(i->{JobSuspensionHold hold=i.getArgument(0);store.put(hold.getSource()+":"+hold.getSourceId(),hold);return hold;});
        when(holds.existsByJobJobIdAndReleasedAtIsNull(any())).thenAnswer(i->store.values().stream().anyMatch(h->h.getReleasedAt()==null));
        when(robots.findByIdForUpdate(robot.getRobotId())).thenReturn(Optional.of(robot));
        var link=new RobotMqttSession();link.setHeartbeatJson(json.createObjectNode().put("status","IDLE").putNull("activeJobStepId"));
        when(sessions.findById(robot.getRobotId())).thenReturn(Optional.of(link));when(registry.fresh(link)).thenReturn(true);
        when(localization.localized(robot.getRobotId(),link)).thenReturn(true);when(navigation.ready(eq(robot),eq(link),any())).thenReturn("profile");
    }

    @Test void independentReasonsKeepSameJobSuspendedUntilAllClear(){
        UUID original=job.getJobId(),voice=UUID.randomUUID(),incident=UUID.randomUUID();
        service.request(job,"VOICE",voice);service.request(job,"INCIDENT",incident);
        assertEquals(JobStatus.SUSPENDING,job.getStatus());assertEquals(RobotStatus.BUSY,robot.getStatus());
        service.settled(job);assertEquals(JobStatus.SUSPENDED,job.getStatus());assertEquals(RobotStatus.IDLE,robot.getStatus());
        service.release(job,"VOICE",voice);assertFalse(service.tryResume(job));
        service.release(job,"INCIDENT",incident);
        ReflectionTestUtils.setField(service,"clock",Clock.fixed(now.plusSeconds(600),ZoneOffset.UTC));
        assertTrue(service.tryResume(job));assertEquals(original,job.getJobId());assertEquals(JobStatus.RUNNING,job.getStatus());
        assertSame(move,job.getSelectedJobStep());assertEquals(2,job.getPatrolRound());
        assertEquals(600_000,job.getSuspendedDurationMs());
        assertEquals(now.plusSeconds(650),service.deadline(job));
    }

    @Test void publishedMoveRequiresStopEvidenceBeforeOwnershipCanTransfer(){
        service.request(job,"MANUAL",job.getJobId());
        var command=RobotCommandOutbox.builder().sendAttempts(1).physicalStopConfirmed(false).build();
        when(commands.findByJobStepId(move.getJobStepId())).thenReturn(Optional.of(command));
        assertThrows(BusinessRuleException.class,()->service.settled(job));
        assertEquals(JobStatus.SUSPENDING,job.getStatus());assertEquals(RobotStatus.BUSY,robot.getStatus());
        command.setPhysicalStopConfirmed(true);service.settled(job);
        assertEquals(JobStatus.SUSPENDED,job.getStatus());
    }

    @Test void newerIncidentBlocksResumeBeforeItsHoldCallback(){
        job.setStatus(JobStatus.SUSPENDED);job.setResumeRequested(true);robot.setStatus(RobotStatus.IDLE);
        when(incidents.findByRobotRobotIdAndStatusInAndArchivedAtIsNull(eq(robot.getRobotId()),any())).thenReturn(List.of(new EmergencyIncident()));
        assertEquals("INCIDENT_ACTIVE",service.resumeBlockReason(job));assertFalse(service.tryResume(job));
    }

    @Test void stoppedFallSensingContinuesWhetherThePiErrorOrCancelJournalArrivesFirst(){
        var snapshot = json.createObjectNode().put("status", "FAILED")
                .put("errorCode", "PATROL_FALL_REVIEW_REQUIRED").put("physicalStopConfirmed", true);
        var sensing = JobStep.builder().status(JobStepStatus.FAILED).stepTypeSnapshot("EXECUTE_SENSING")
                .resolvedInputsJson(json.createObjectNode().put("fall_detection", true)).errorDetailsJson(snapshot).build();
        assertTrue(JobSuspensionService.suspendedExecution(sensing));
        sensing.setInterruptionReason("JOB_SUSPEND");
        sensing.setErrorCode("BE_JOB_SUSPEND");
        sensing.setErrorDetailsJson(json.createObjectNode().set("result", snapshot));
        assertTrue(JobSuspensionService.suspendedExecution(sensing));

        snapshot.put("physicalStopConfirmed", false);
        assertFalse(JobSuspensionService.suspendedExecution(sensing));
        snapshot.put("physicalStopConfirmed", true).put("errorCode", "PATROL_FALL_REPORT_UNCONFIRMED");
        assertFalse(JobSuspensionService.suspendedExecution(sensing));
        snapshot.put("errorCode", "PATROL_FALL_REVIEW_REQUIRED");
        sensing.setInterruptionReason("JOB_CANCEL");
        assertFalse(JobSuspensionService.suspendedExecution(sensing));
    }

    @Test void foregroundJobAndPermanentCancelBothPreventResume(){
        job.setStatus(JobStatus.SUSPENDED);job.setResumeRequested(true);robot.setStatus(RobotStatus.IDLE);
        var foreground=Job.builder().jobId(UUID.randomUUID()).status(JobStatus.QUEUED).build();
        when(jobs.findByRobotRobotIdAndStatusIn(eq(robot.getRobotId()),any())).thenReturn(List.of(foreground));
        assertEquals("FOREGROUND_JOB_PENDING",service.resumeBlockReason(job));
        job.setCancelRequestedAt(now);assertEquals("RESUME_DISABLED",service.resumeBlockReason(job));
    }

    @Test void releasedAutomaticSourceCannotBeResurrectedByDuplicateStart(){
        UUID voice=UUID.randomUUID();service.request(job,"VOICE",voice);service.settled(job);service.release(job,"VOICE",voice);
        service.request(job,"VOICE",voice);assertFalse(service.hasHolds(job));
        assertEquals(JobStatus.SUSPENDED,job.getStatus());assertEquals(now,job.getSuspendedAt());
    }

    @Test void settlingPatrolDoesNotReleaseAnotherForegroundOwner(){
        service.request(job,"VOICE",UUID.randomUUID());
        when(jobs.findByRobotRobotIdAndStatusIn(eq(robot.getRobotId()),any())).thenReturn(List.of(Job.builder().jobId(UUID.randomUUID()).status(JobStatus.RUNNING).build()));
        service.settled(job);assertEquals(RobotStatus.BUSY,robot.getStatus());
    }

    @Test void perRoundDeadlineExcludesSuspensionFromEarlierRounds(){
        job.setPatrolRoundStartedAt(now);job.setSuspendedDurationMs(700_000);job.setPatrolRoundSuspendedBaseMs(600_000);
        assertEquals(now.plusSeconds(160),service.deadline(job));
    }
}
