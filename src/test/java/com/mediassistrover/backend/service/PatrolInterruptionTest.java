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
    final JobSuspensionService suspensions=mock(JobSuspensionService.class);
    final PlatformTransactionManager transactions=mock(PlatformTransactionManager.class);
    final ApplicationEventPublisher publisher=mock(ApplicationEventPublisher.class);
    final PatrolInterruptionReconciler reconciler=new PatrolInterruptionReconciler(jobs,holds,voice,media,incidents,suspensions,transactions);
    final PatrolInterruptionEvents events=new PatrolInterruptionEvents(publisher,suspensions,transactions,voice,media,incidents);
    final Robot robot=Robot.builder().robotId(UUID.randomUUID()).build();
    final Job job=Job.builder().jobId(UUID.randomUUID()).robot(robot).status(JobStatus.SUSPENDED).build();

    PatrolInterruptionTest(){
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(jobs.lockById(job.getJobId())).thenReturn(Optional.of(job));
        when(suspensions.isPatrol(job)).thenReturn(true);
    }
    JobSuspensionHold hold(String source,UUID id){return JobSuspensionHold.builder().job(job).source(source).sourceId(id).createdAt(Instant.now()).build();}

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
