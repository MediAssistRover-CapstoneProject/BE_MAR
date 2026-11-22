package com.mediassistrover.backend.service;

import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.media.*;
import com.mediassistrover.backend.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.transaction.*;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PatrolInterruptionTest {
    final JobRepository jobs=mock(JobRepository.class);
    final JobSuspensionHoldRepository holds=mock(JobSuspensionHoldRepository.class);
    final VoiceInteractionRepository voice=mock(VoiceInteractionRepository.class);
    final MediaSessionRepository media=mock(MediaSessionRepository.class);
    final EmergencyIncidentRepository incidents=mock(EmergencyIncidentRepository.class);
    final AlertRepository alerts=mock(AlertRepository.class);
    final JobSuspensionService suspensions=mock(JobSuspensionService.class);
    final PlatformTransactionManager transactions=mock(PlatformTransactionManager.class);
    final ApplicationEventPublisher publisher=mock(ApplicationEventPublisher.class);
    final PatrolInterruptionReconciler reconciler=new PatrolInterruptionReconciler(jobs,holds,voice,media,incidents,alerts,suspensions,transactions);
    final PatrolInterruptionEvents events=new PatrolInterruptionEvents(publisher,suspensions,transactions,voice,media,incidents,alerts);
    final Robot robot=Robot.builder().robotId(UUID.randomUUID()).build();
    final Job job=Job.builder().jobId(UUID.randomUUID()).robot(robot).status(JobStatus.SUSPENDED).build();

    PatrolInterruptionTest(){
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(jobs.lockById(job.getJobId())).thenReturn(Optional.of(job));
        when(suspensions.isPatrol(job)).thenReturn(true);
    }
    JobSuspensionHold hold(String source,UUID id){return JobSuspensionHold.builder().job(job).source(source).sourceId(id).createdAt(Instant.now()).build();}

    @Test void acknowledgingRestrictedReviewKeepsPatrolHeldUntilResolution(){
        UUID id=UUID.randomUUID();var alert=Alert.builder().alertId(id).notificationType("PATROL_RESTRICTED_INTRUSION")
                .status(AlertStatus.ACKNOWLEDGED).detectionEvent(DetectionEvent.builder().robot(robot).build()).build();
        when(alerts.findById(id)).thenReturn(Optional.of(alert));
        events.apply(new PatrolInterruptionEvents.Changed(robot.getRobotId(),"RESTRICTED_ALERT",id,true));
        verify(suspensions).holdRobot(robot.getRobotId(),"RESTRICTED_ALERT",id);
        alert.setStatus(AlertStatus.RESOLVED);
        events.apply(new PatrolInterruptionEvents.Changed(robot.getRobotId(),"RESTRICTED_ALERT",id,false));
        verify(suspensions).releaseRobot(robot.getRobotId(),"RESTRICTED_ALERT",id);
    }
    @Test void restrictedEventRetryReturnsReceiptAfterPatrolHasAlreadySuspended(){
        var service=mock(com.mediassistrover.backend.ai.RestrictedZoneEventService.class,CALLS_REAL_METHODS);
        var scope=mock(com.mediassistrover.backend.validation.DeviceResourceScopePolicy.class);
        var robots=mock(RobotRepository.class);var receipts=mock(com.mediassistrover.backend.ai.MonitoringReceiptService.class);
        var wire=new com.mediassistrover.backend.mqtt.MqttWire();
        Map.of("scope",scope,"jobs",jobs,"robots",robots,"receipts",receipts,"wire",wire)
                .forEach((k,v)->org.springframework.test.util.ReflectionTestUtils.setField(service,k,v));
        var device=new com.mediassistrover.backend.security.DevicePrincipal(robot.getRobotId(),"MAR",1,"id",Instant.now().plusSeconds(60));
        when(scope.requireCurrentDevice(device)).thenReturn(robot);
        when(robots.findByIdForUpdate(robot.getRobotId())).thenReturn(Optional.of(robot));
        UUID event=UUID.randomUUID(),alert=UUID.randomUUID();
        when(receipts.duplicateRestrictedZone(eq(event),eq(robot),any())).thenReturn(Optional.of(wire.object()
                .put("detection_event_id",event.toString()).put("alert_id",alert.toString())));
        var point=new com.mediassistrover.backend.ai.PatrolFallEventService.PatientLocation(
                java.math.BigDecimal.ONE,java.math.BigDecimal.ONE,java.math.BigDecimal.ONE,Instant.now(),null,null,null,null,null);
        var request=new com.mediassistrover.backend.ai.RestrictedZoneEventService.Request(event,job.getJobId(),UUID.randomUUID(),
                Instant.now(),"shared",java.math.BigDecimal.ONE,UUID.randomUUID(),1L,point);
        var result=service.record(request,device);
        assertTrue(result.duplicate());assertEquals(alert,result.alertId());
        verify(receipts,never()).recordRestrictedZone(any(),any(),any(),any());
    }

    @Test void rebuildsAllActiveSourcesAfterMissedCallbacksAndKeepsIndependentHolds(){
        UUID voiceId=UUID.randomUUID(),incidentId=UUID.randomUUID(),mediaId=UUID.randomUUID();
        when(voice.findByRobotRobotIdAndStatus(robot.getRobotId(),VoiceInteractionStatus.ACTIVE))
                .thenReturn(List.of(VoiceInteraction.builder().voiceInteractionId(voiceId).build()));
        var incident=EmergencyIncident.builder().incidentId(incidentId).build();
        when(incidents.findByRobotRobotIdAndStatusInAndArchivedAtIsNull(eq(robot.getRobotId()),anyCollection())).thenReturn(List.of(incident));
        var call=new MediaSession();call.setSessionId(mediaId);call.setMode(MediaSession.Mode.ASSIST);
        when(media.findByActiveRobotId(robot.getRobotId())).thenReturn(Optional.of(call));
        UUID oldVoice=UUID.randomUUID(),priority=UUID.randomUUID();
        when(holds.findByJobJobIdOrderByCreatedAt(job.getJobId())).thenReturn(List.of(hold("VOICE",oldVoice),hold("JOB",priority),hold("MANUAL",job.getJobId()),hold("INCIDENT",incidentId)));
        reconciler.reconcileOne(job.getJobId());
        verify(suspensions).request(job,"VOICE",voiceId);
        verify(suspensions).request(job,"MEDIA",mediaId);
        verify(suspensions).request(job,"INCIDENT",incidentId);
        verify(suspensions).release(job,"VOICE",oldVoice);
        verify(suspensions,never()).release(job,"JOB",priority);
        verify(suspensions,never()).release(job,"INCIDENT",incidentId);
        verify(suspensions,never()).tryResume(any());
    }
    @Test void monitorDoesNotCreateConversationHold(){
        var call=new MediaSession();call.setMode(MediaSession.Mode.MONITOR);
        when(media.findByActiveRobotId(robot.getRobotId())).thenReturn(Optional.of(call));
        reconciler.reconcileOne(job.getJobId());
        verify(suspensions,never()).request(any(),eq("MEDIA"),any());
    }
    @Test void schedulerOnlyReadsDetachedIdentifiersBeforeItsTransaction(){
        Job detached=mock(Job.class);when(detached.getJobId()).thenReturn(job.getJobId());
        when(jobs.findByBeOrchestratedTrueAndStatus(any(),any())).thenReturn(new PageImpl<>(List.of(detached)));
        reconciler.reconcile();
        verify(detached,never()).getWorkflow();verify(detached,never()).getRobot();
        verify(jobs).lockById(job.getJobId());
    }
    @Test void delayedVoiceStartCannotRecreateHoldAfterConversationFinished(){
        UUID id=UUID.randomUUID();
        when(voice.findById(id)).thenReturn(Optional.of(VoiceInteraction.builder().voiceInteractionId(id).robot(robot).status(VoiceInteractionStatus.COMPLETED).build()));
        events.apply(new PatrolInterruptionEvents.Changed(robot.getRobotId(),"VOICE",id,true));
        verify(suspensions).releaseRobot(robot.getRobotId(),"VOICE",id);
        verify(suspensions,never()).holdRobot(any(),any(),any());
    }
    @Test void oldReleaseCannotClearStillActiveDurableIncident(){
        UUID id=UUID.randomUUID();
        when(incidents.findById(id)).thenReturn(Optional.of(EmergencyIncident.builder().incidentId(id).robot(robot).status(EmergencyIncidentStatus.IN_PROGRESS).build()));
        events.apply(new PatrolInterruptionEvents.Changed(robot.getRobotId(),"INCIDENT",id,false));
        verify(suspensions).holdRobot(robot.getRobotId(),"INCIDENT",id);
        verify(suspensions,never()).releaseRobot(any(),any(),any());
    }
    @Test void failedAfterCommitCallbackLeavesDurableSourceForReconciliation(){
        UUID id=UUID.randomUUID();
        when(voice.findById(id)).thenReturn(Optional.of(VoiceInteraction.builder().robot(robot).status(VoiceInteractionStatus.ACTIVE).build()));
        doThrow(new IllegalStateException("database retry")).when(suspensions).holdRobot(robot.getRobotId(),"VOICE",id);
        assertDoesNotThrow(()->events.apply(new PatrolInterruptionEvents.Changed(robot.getRobotId(),"VOICE",id,true)));
        verify(transactions).rollback(any());
    }
}
