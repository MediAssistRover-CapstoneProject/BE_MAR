package com.mediassistrover.backend.media;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.exception.*;
import com.mediassistrover.backend.repository.*;
import com.mediassistrover.backend.security.*;
import com.mediassistrover.backend.service.OrchestrationWorkService;
import com.mediassistrover.backend.service.RuntimeSettingService;
import com.mediassistrover.backend.service.SosResponderRouting;
import com.mediassistrover.backend.service.AuditService;
import com.mediassistrover.backend.service.RuntimeStepCompletionService;
import com.mediassistrover.backend.service.WorkflowEventService;
import com.mediassistrover.backend.service.impl.AlertServiceImpl;
import jakarta.persistence.EntityManager;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.data.domain.PageRequest;
import com.mediassistrover.backend.validation.*;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.security.access.AccessDeniedException;
import javax.crypto.spec.SecretKeySpec;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MediaSessionTest {
    @Test void mediaControllerDoesNotConflictWithExistingFileUploadController() {
        try(var context=new AnnotationConfigApplicationContext()) {
            context.scan("com.mediassistrover.backend.media", "com.mediassistrover.backend.controller.media");
            assertTrue(context.containsBeanDefinition("robotMediaSessionController"));
            assertTrue(context.containsBeanDefinition("mediaController"));
        }
    }

    final Instant instant=Instant.now();
    final Clock clock=Clock.fixed(instant,ZoneOffset.UTC);
    final MediaProperties properties=new MediaProperties();
    final LiveKitGateway gateway=mock(LiveKitGateway.class);
    final MediaSessionRepository sessions=mock(MediaSessionRepository.class);
    final RobotRepository robots=mock(RobotRepository.class);
    final EmergencyIncidentRepository incidents=mock(EmergencyIncidentRepository.class);
    final StaffOperationalScopePolicy staff=mock(StaffOperationalScopePolicy.class);
    final DeviceResourceScopePolicy device=mock(DeviceResourceScopePolicy.class);
    final JobRepository jobs=mock(JobRepository.class);
    final OrchestrationWorkService wakeups=mock(OrchestrationWorkService.class);
    final MediaEvents events=mock(MediaEvents.class);
    final MediaSessionService service=new MediaSessionService(properties,gateway,sessions,robots,incidents,staff,device,jobs,wakeups,events,clock);

    MediaSession session() {
        var s=new MediaSession();s.setSessionId(UUID.randomUUID());
        s.setRobot(Robot.builder().robotId(UUID.randomUUID()).build());s.setActiveRobotId(s.getRobot().getRobotId());
        s.setStaff(Account.builder().accountId(UUID.randomUUID()).role(Role.STAFF).build());
        s.setState(MediaSession.State.ACTIVE);s.setMode(MediaSession.Mode.ASSIST);s.setCreatedAt(instant);
        s.setAcceptedAt(instant);s.setExpiresAt(instant.plusSeconds(300));
        when(sessions.lockById(s.getSessionId())).thenReturn(Optional.of(s));return s;
    }
    AccountPrincipal actor(Account account) {
        var p=AccountPrincipal.fromAccount(account);
        when(staff.requireCurrentStaffOrAdmin(p)).thenReturn(account);return p;
    }
    DevicePrincipal device(Robot robot) {
        var p=new DevicePrincipal(robot.getRobotId(),"MAR",1,"device",instant.plusSeconds(600));
        when(device.requireCurrentDevice(p)).thenReturn(robot);return p;
    }
    @Test void anotherDeviceCannotGetRoomCredentials() {
        var s=session();var robot=Robot.builder().robotId(UUID.randomUUID()).build();var principal=device(robot);
        assertThrows(AccessDeniedException.class,()->service.deviceToken(s.getSessionId(),principal));
        verify(gateway,never()).createRoom(any());
    }
    @Test void closedSessionCannotIssueCredentials() {
        var s=session();s.setActiveRobotId(null);s.setState(MediaSession.State.ENDED);
        assertThrows(BusinessRuleException.class,()->service.deviceToken(s.getSessionId(),device(s.getRobot())));
        verify(gateway,never()).createRoom(any());
    }
    @Test void secondStaffCannotStealAcceptedSession() {
        var s=session();var incident=new EmergencyIncident();incident.setStatus(EmergencyIncidentStatus.OPEN);s.setIncident(incident);
        var second=Account.builder().accountId(UUID.randomUUID()).role(Role.STAFF).build();var actor=actor(second);
        when(staff.canAccessIncident(incident,actor)).thenReturn(true);
        assertThrows(ConflictException.class,()->service.accept(s.getSessionId(),actor));
        assertNotEquals(second.getAccountId(),s.getStaff().getAccountId());
    }
    @Test void routedSosRingsAndIssuesCredentialsOnlyForDesignatedResponder() {
        var session = session();
        var responder = session.getStaff();
        var target = actor(responder);
        var other = actor(Account.builder().accountId(UUID.randomUUID()).role(Role.STAFF).build());
        var admin = actor(Account.builder().accountId(UUID.randomUUID()).role(Role.ADMIN).build());
        var incident = EmergencyIncident.builder().robot(session.getRobot()).status(EmergencyIncidentStatus.OPEN)
                .sosRoutingStatus(EmergencyIncident.SosRoutingStatus.ASSIGNED).sosResponder(responder).build();
        session.setIncident(incident);
        session.setStaff(null);
        session.setState(MediaSession.State.RINGING);
        when(sessions.findTop100ByActiveRobotIdIsNotNullOrderByCreatedAtDesc()).thenReturn(List.of(session));
        when(events.isListening(session.getRobot().getRobotId())).thenReturn(true);
        when(staff.canAccessIncident(incident, other)).thenReturn(true);

        assertTrue(service.list(other).isEmpty());
        assertEquals(session.getSessionId(), service.list(target).getFirst().sessionId());
        assertThrows(AccessDeniedException.class, () -> service.accept(session.getSessionId(), other));
        assertThrows(AccessDeniedException.class, () -> service.accept(session.getSessionId(), admin));
        assertThrows(AccessDeniedException.class, () -> service.staffToken(session.getSessionId(), target));
        assertEquals(MediaSession.State.CONNECTING, service.accept(session.getSessionId(), target).state());
        assertThrows(AccessDeniedException.class, () -> service.staffToken(session.getSessionId(), other));
        assertEquals("staff-" + responder.getAccountId(), service.staffToken(session.getSessionId(), target).identity());
    }

    @Test void noSingleOnDutyStaffKeepsSosUnassigned() {
        var settings = mock(RuntimeSettingService.class);
        var shifts = mock(ShiftRepository.class);
        var rooms = mock(RoomRepository.class);
        var room = Room.builder().roomId(UUID.randomUUID()).active(true).floor(Floor.builder().active(true).build()).build();
        var incident = EmergencyIncident.builder().robot(Robot.builder().robotId(UUID.randomUUID()).build()).build();
        var routing = new SosResponderRouting(settings, rooms, shifts);
        when(settings.getString(SosResponderRouting.MONITORING_ROOM_SETTING, "")).thenReturn(room.getRoomId().toString());
        when(rooms.findById(room.getRoomId())).thenReturn(Optional.of(room));
        var responder = Account.builder().accountId(UUID.randomUUID()).role(Role.STAFF).build();
        when(shifts.findOnDutySosResponders(room.getRoomId(), instant, PageRequest.of(0, 2)))
                .thenReturn(List.of(), List.of(responder, Account.builder().accountId(UUID.randomUUID()).build()), List.of(responder));

        routing.assign(incident, instant);
        assertEquals(EmergencyIncident.SosRoutingStatus.NO_ACTIVE_SHIFT, incident.getSosRoutingStatus());
        assertNull(incident.getSosResponder());
        routing.assign(incident, instant);
        assertEquals(EmergencyIncident.SosRoutingStatus.AMBIGUOUS_SHIFT, incident.getSosRoutingStatus());
        assertNull(incident.getSosResponder());
        properties.setEnabled(true);
        when(events.isListening(incident.getRobot().getRobotId())).thenReturn(true);
        assertNull(service.openSos(incident));

        routing.assign(incident, instant);
        assertEquals(EmergencyIncident.SosRoutingStatus.ASSIGNED, incident.getSosRoutingStatus());
        assertEquals(responder, incident.getSosResponder());
    }

    @Test void sosNotifyOverridesBroadWorkflowRecipientsAndSupportsManualTriageWithoutBroadcast() {
        var alerts = mock(AlertRepository.class);
        var recipients = mock(AlertRecipientRepository.class);
        var accounts = mock(AccountRepository.class);
        var json = new ObjectMapper();
        var service = new AlertServiceImpl(alerts, recipients, accounts, mock(AuditService.class), json, clock,
                mock(RuntimeStepCompletionService.class), jobs, mock(WorkflowEventService.class), staff);
        ReflectionTestUtils.setField(service, "em", mock(EntityManager.class));
        when(alerts.saveAndFlush(any())).thenAnswer(call -> {
            var alert = call.<Alert>getArgument(0);
            alert.setAlertId(UUID.randomUUID());
            return alert;
        });

        var responder = Account.builder().accountId(UUID.randomUUID()).role(Role.STAFF).status(AccountStatus.ACTIVE).build();
        var assigned = EmergencyIncident.builder().sosResponder(responder)
                .sosRoutingStatus(EmergencyIncident.SosRoutingStatus.ASSIGNED).build();
        service.createSystemAlert("assigned", "SOS", AlertSeverity.CRITICAL, "Help", null, assigned,
                null, null, null, true, AlertAckPolicy.ANY, instant.plusSeconds(60),
                json.createArrayNode().add("STAFF").add("ADMIN"));
        var delivered = ArgumentCaptor.forClass(AlertRecipient.class);
        verify(recipients).save(delivered.capture());
        assertEquals(responder, delivered.getValue().getAccount());
        verifyNoInteractions(accounts);

        var unassigned = EmergencyIncident.builder().sosRoutingStatus(EmergencyIncident.SosRoutingStatus.NO_ACTIVE_SHIFT).build();
        var alert = service.createSystemAlert("unassigned", "SOS", AlertSeverity.CRITICAL, "Help", null, unassigned,
                null, null, null, true, AlertAckPolicy.ANY, instant.minusSeconds(1));
        when(alerts.lockById(alert.getAlertId())).thenReturn(Optional.of(alert));
        var supervisor = Account.builder().accountId(UUID.randomUUID()).role(Role.ADMIN).status(AccountStatus.ACTIVE).build();
        when(accounts.findById(supervisor.getAccountId())).thenReturn(Optional.of(supervisor));
        assertEquals(AlertStatus.ACKNOWLEDGED, service.acknowledge(alert.getAlertId(), actor(supervisor), null).status());
        verify(recipients, times(1)).save(any());
        verify(recipients, never()).saveAndFlush(any());
    }

    @Test void endingCallDoesNotResolveEmergencyIncident() {
        var s=session();var incident=new EmergencyIncident();incident.setStatus(EmergencyIncidentStatus.OPEN);s.setIncident(incident);
        var actor=actor(s.getStaff());service.end(s.getSessionId(),"STAFF_ENDED",actor);
        assertEquals(MediaSession.State.ENDED,s.getState());assertNull(s.getActiveRobotId());assertTrue(s.isCleanupPending());
        assertEquals(EmergencyIncidentStatus.OPEN,incident.getStatus());
    }
    @Test void deviceReportingAfterCloseCannotResurrectSession() {
        var s=session();s.setState(MediaSession.State.FAILED);s.setActiveRobotId(null);
        var result=service.deviceState(s.getSessionId(),"CONNECTED",null,device(s.getRobot()));
        assertEquals(MediaSession.State.FAILED,result.state());assertNull(s.getActiveRobotId());
    }
    @Test void disabledMediaDoesNotChangeSosOrStartNetworkWork() {
        var incident=new EmergencyIncident();assertNull(service.openSos(incident));verifyNoInteractions(sessions,gateway);
    }
    @Test void sosFromRobotWithoutMediaListenerDoesNotOpenCall() {
        properties.setEnabled(true);
        var incident=new EmergencyIncident();
        incident.setRobot(Robot.builder().robotId(UUID.randomUUID()).build());
        assertNull(service.openSos(incident));
        verifyNoInteractions(sessions,gateway,jobs,wakeups);
    }
    @Test void monitorDoesNotCancelMovementJobs() {
        var s=session();s.setMode(MediaSession.Mode.MONITOR);
        service.deviceToken(s.getSessionId(),device(s.getRobot()));verifyNoInteractions(jobs,wakeups);
    }
    @Test void reconnectDeadlineIsNotExtendedByRepeatedReports() {
        var s=session();var started=instant.minusSeconds(20);s.setReconnectStartedAt(started);
        service.deviceState(s.getSessionId(),"RECONNECTING",null,device(s.getRobot()));assertEquals(started,s.getReconnectStartedAt());
    }
    LiveKitGateway signer() {
        properties.setEnabled(true);properties.setUrl("wss://media.example.org");properties.setApiKey("TEST_API");
        properties.setApiSecret("0123456789abcdef".repeat(4));return new LiveKitGateway(properties,new ObjectMapper(),clock);
    }
    Map<?,?> grant(String token) {
        return (Map<?,?>)Jwts.parser().verifyWith(new SecretKeySpec(properties.getApiSecret().getBytes(java.nio.charset.StandardCharsets.UTF_8),"HmacSHA256"))
                .build().parseSignedClaims(token).getPayload().get("video");
    }
    @Test void monitoringNeverGrantsStaffPublishingOrRobotMicrophone() {
        var g=signer();var s=session();s.setMode(MediaSession.Mode.MONITOR);
        var robot=grant(g.join(s,"robot",true));assertEquals(List.of("camera"),robot.get("canPublishSources"));
        var staff=grant(g.join(s,"staff",false));assertEquals(false,staff.get("canPublish"));assertEquals(false,staff.get("canPublishData"));
    }
    @Test void assistOnlyGrantsStaffMicrophoneAndScopedRoom() {
        var g=signer();var s=session();var staff=grant(g.join(s,"staff",false));
        assertEquals(List.of("microphone"),staff.get("canPublishSources"));assertEquals(s.room(),staff.get("room"));
        assertNull(staff.get("roomCreate"));assertNull(staff.get("roomAdmin"));assertNull(staff.get("roomRecord"));
    }
    @Test void plainHttpControlIsRestrictedToPrivateAddresses() {
        var g=signer();properties.setInternalUrl("http://172.31.42.25:7880");assertDoesNotThrow(g::ready);
        properties.setInternalUrl("http://8.8.8.8:7880");assertThrows(BusinessRuleException.class,g::ready);
        properties.setInternalUrl("http://172.31.42.25:7880/redirect");assertThrows(BusinessRuleException.class,g::ready);
    }
}
