package com.mediassistrover.backend.service;

import com.mediassistrover.backend.dto.pi.request.PiEnvelope;
import com.mediassistrover.backend.dto.voice.request.VoiceRequests.*;
import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.exception.BusinessRuleException;
import com.mediassistrover.backend.repository.*;
import com.mediassistrover.backend.security.DevicePrincipal;
import com.mediassistrover.backend.service.impl.VoiceServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VoicePatrolLifecycleTest {
    final VoiceServiceImpl service=mock(VoiceServiceImpl.class,CALLS_REAL_METHODS);
    final VoiceInteractionRepository interactions=mock(VoiceInteractionRepository.class);
    final VoiceCommandRepository commands=mock(VoiceCommandRepository.class);
    final RobotRepository robots=mock(RobotRepository.class);
    final PatrolInterruptionEvents events=mock(PatrolInterruptionEvents.class);
    final Instant now=Instant.parse("2026-11-16T05:00:00Z");
    final Robot robot=Robot.builder().robotId(UUID.randomUUID()).build();
    final DevicePrincipal device=new DevicePrincipal(robot.getRobotId(),"MAR",1,"test",now.plusSeconds(3600));
    VoicePatrolLifecycleTest(){
        ReflectionTestUtils.setField(service,"interactionRepository",interactions);
        ReflectionTestUtils.setField(service,"commandRepository",commands);
        ReflectionTestUtils.setField(service,"robotRepository",robots);
        ReflectionTestUtils.setField(service,"patrolInterruptions",events);
        ReflectionTestUtils.setField(service,"clock",Clock.fixed(now,ZoneOffset.UTC));
        when(robots.findByIdForUpdate(robot.getRobotId())).thenReturn(Optional.of(robot));
    }
    <T> PiEnvelope<T> envelope(UUID id,String type,T payload){return new PiEnvelope<>("1.0",id,"test:"+id,UUID.randomUUID(),robot.getRobotId(),null,null,type,now,UUID.randomUUID(),1,payload);}
    @Test void startsVoiceHoldAtConversationBoundary(){
        UUID id=UUID.randomUUID();
        service.startInteraction(envelope(id,"VOICE_INTERACTION_STARTED",new InteractionStartPayload("vi",now)),device);
        verify(events).hold(robot.getRobotId(),"VOICE",id);
        verify(interactions).save(argThat(row->row.getVoiceInteractionId().equals(id)&&row.getStatus()==VoiceInteractionStatus.ACTIVE));
    }
    @Test void lateDuplicateStartDoesNotResurrectCompletedConversation(){
        UUID id=UUID.randomUUID();
        when(interactions.findById(id)).thenReturn(Optional.of(VoiceInteraction.builder().voiceInteractionId(id).robot(robot).status(VoiceInteractionStatus.COMPLETED).build()));
        service.startInteraction(envelope(id,"VOICE_INTERACTION_STARTED",new InteractionStartPayload("vi",now)),device);
        verifyNoInteractions(events);verify(interactions,never()).save(any());
    }
    @Test void pendingCommandPreventsPrematureConversationRelease(){
        UUID id=UUID.randomUUID();var active=VoiceInteraction.builder().voiceInteractionId(id).robot(robot).startedAt(now.minusSeconds(5)).status(VoiceInteractionStatus.ACTIVE).build();
        when(interactions.findById(id)).thenReturn(Optional.of(active));
        when(commands.existsByVoiceInteractionVoiceInteractionIdAndStatus(id,VoiceCommandStatus.RECEIVED)).thenReturn(true);
        assertThrows(BusinessRuleException.class,()->service.completeInteraction(id,envelope(UUID.randomUUID(),"VOICE_INTERACTION_COMPLETED",new InteractionCompletionPayload(VoiceInteractionStatus.COMPLETED,now,null,null,null)),device));
        assertEquals(VoiceInteractionStatus.ACTIVE,active.getStatus());verifyNoInteractions(events);
    }
    @Test void completionOnlyReleasesItsOwnInteraction(){
        UUID id=UUID.randomUUID();var active=VoiceInteraction.builder().voiceInteractionId(id).robot(robot).startedAt(now.minusSeconds(5)).status(VoiceInteractionStatus.ACTIVE).build();
        when(interactions.findById(id)).thenReturn(Optional.of(active));
        service.completeInteraction(id,envelope(UUID.randomUUID(),"VOICE_INTERACTION_COMPLETED",new InteractionCompletionPayload(VoiceInteractionStatus.COMPLETED,now,null,null,null)),device);
        assertEquals(VoiceInteractionStatus.COMPLETED,active.getStatus());
        verify(events).release(robot.getRobotId(),"VOICE",id);verifyNoMoreInteractions(events);
    }
    @Test void sharedSpeechProfileRevalidatesRevisionWithoutReturningProviderSecrets(){
        var settings=mock(com.mediassistrover.backend.speech.SpeechSettingsService.class);
        var first=new com.mediassistrover.backend.speech.SpeechSettingsService.Profile("nova",1.2,"Speak naturally.","revision-one");
        var next=new com.mediassistrover.backend.speech.SpeechSettingsService.Profile("nova",1.2,"Connect whole phrases.","revision-two");
        when(settings.current()).thenReturn(first,first,next);
        var controller=new com.mediassistrover.backend.controller.robot.RobotSpeechAudioController(
                mock(com.mediassistrover.backend.speech.OpenAiTtsService.class),
                mock(com.mediassistrover.backend.speech.OpenAiSttService.class),settings);
        var request=new org.springframework.mock.web.MockHttpServletRequest("GET","/api/v1/pi/speech/profile");
        var initial=controller.profile(new org.springframework.web.context.request.ServletWebRequest(request));
        assertEquals(first,initial.getBody());assertEquals("\"revision-one\"",initial.getHeaders().getETag());
        request.addHeader("If-None-Match",initial.getHeaders().getETag());
        var unchanged=controller.profile(new org.springframework.web.context.request.ServletWebRequest(request));
        assertEquals(304,unchanged.getStatusCode().value());assertNull(unchanged.getBody());
        var changed=controller.profile(new org.springframework.web.context.request.ServletWebRequest(request));
        assertEquals(200,changed.getStatusCode().value());assertEquals(next,changed.getBody());
        assertEquals("private, no-cache",changed.getHeaders().getCacheControl());
    }
    @Test void catalogRevisionDoesNotCacheLiveSessionOrJobAvailability(){
        var sessions=mock(NavigationSessionService.class);
        var scope=mock(com.mediassistrover.backend.validation.DeviceResourceScopePolicy.class);
        var endpoints=mock(EndpointRepository.class);var workflows=mock(WorkflowRepository.class);
        var jobs=mock(JobRepository.class);var settings=mock(com.mediassistrover.backend.speech.SpeechSettingsService.class);
        var wire=new com.mediassistrover.backend.mqtt.MqttWire();UUID mapId=UUID.randomUUID();
        var session=wire.object().put("sessionId",UUID.randomUUID().toString()).put("mapId",mapId.toString())
                .put("state","READY");
        session.putObject("initialization").put("revision",1);
        when(sessions.device(device)).thenReturn(session);
        when(settings.current()).thenReturn(new com.mediassistrover.backend.speech.SpeechSettingsService.Profile("nova",1.2,"Natural.","speech-revision"));
        when(endpoints.findByMapMapIdOrderByEndpointCodeAsc(mapId)).thenReturn(List.of());
        when(workflows.findFirstByWorkflowCodeIgnoreCaseAndStatusAndAcceptsNewJobsTrueOrderByVersionNoDesc(anyString(),any()))
                .thenReturn(Optional.empty());
        when(jobs.findRobotJobIds(eq(robot.getRobotId()),any())).thenReturn(List.of(),List.of(UUID.randomUUID()),List.of());
        var navigation=new VoiceNavigationService(sessions,scope,endpoints,workflows,mock(ServiceRequestRepository.class),
                mock(ServiceRequestService.class),jobs,wire,settings);
        var first=navigation.destinations(device);var busy=navigation.destinations(device);
        assertTrue(first.path("robotAvailable").asBoolean());assertFalse(busy.path("robotAvailable").asBoolean());
        assertEquals(first.path("catalogRevision"),busy.path("catalogRevision"));
        session.withObject("initialization").put("revision",2);
        assertNotEquals(first.path("catalogRevision"),navigation.destinations(device).path("catalogRevision"));
        verify(sessions,times(3)).device(device);verify(scope,times(3)).requireCurrentDevice(device);
    }
}
