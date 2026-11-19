package com.mediassistrover.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.exception.ConflictException;
import com.mediassistrover.backend.media.MediaSessionService;
import com.mediassistrover.backend.repository.*;
import com.mediassistrover.backend.security.AccountPrincipal;
import com.mediassistrover.backend.validation.StaffOperationalScopePolicy;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.time.*;
import java.util.*;

import static com.mediassistrover.backend.entity.EmergencyIncident.SosRoutingStatus.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SosIncidentRoutingTest {
    private final EmergencyIncidentRepository incidents = mock(EmergencyIncidentRepository.class);
    private final SosResponderRouting routing = mock(SosResponderRouting.class);
    private final StaffOperationalScopePolicy scope = mock(StaffOperationalScopePolicy.class);
    private final EmergencyService emergencies = mock(EmergencyService.class);
    private final AlertRepository alerts = mock(AlertRepository.class);
    private final AlertRecipientRepository recipients = mock(AlertRecipientRepository.class);
    private final ServiceRequestRepository requests = mock(ServiceRequestRepository.class);
    private final JobRepository jobs = mock(JobRepository.class);
    private final MediaSessionService media = mock(MediaSessionService.class);
    private final AuditService audit = mock(AuditService.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final Instant now = Instant.parse("2026-11-19T11:05:00Z");
    private final SosIncidentRoutingService service = new SosIncidentRoutingService(incidents, routing, scope,
            emergencies, alerts, recipients, requests, jobs, media, audit, mapper, Clock.fixed(now, ZoneOffset.UTC));
    private final Account admin = Account.builder().accountId(UUID.randomUUID()).role(Role.ADMIN).build();
    private final Account responder = Account.builder().accountId(UUID.randomUUID()).role(Role.STAFF).username("on-duty").build();
    private final AccountPrincipal actor = AccountPrincipal.fromAccount(admin);
    private final EmergencyIncident incident = EmergencyIncident.builder().incidentId(UUID.randomUUID())
            .status(EmergencyIncidentStatus.OPEN).incidentType("SOS").sosRoutingStatus(ROOM_UNAVAILABLE).rowVersion(3).build();
    private final UUID roomId = UUID.randomUUID();

    SosIncidentRoutingTest() {
        when(scope.requireCurrentStaffOrAdmin(actor)).thenReturn(admin);
        when(incidents.findById(incident.getIncidentId())).thenReturn(Optional.of(incident));
        when(incidents.lockById(incident.getIncidentId())).thenReturn(Optional.of(incident));
        when(routing.resolve(null, now, null)).thenReturn(new SosResponderRouting.Assignment(ASSIGNED, responder, roomId, "SOS", "READY"));
    }

    @Test void inspectionExplainsCurrentEligibilityWithoutAssigningAnyone() {
        var result = service.inspect(incident.getIncidentId(), actor);
        assertEquals("READY", result.eligibility().reason());
        assertTrue(result.canRetry());
        assertNull(incident.getSosResponder());
        assertEquals(ROOM_UNAVAILABLE, incident.getSosRoutingStatus());
        verifyNoInteractions(alerts, recipients, requests, media, audit);
        verify(incidents, never()).flush();
    }

    @Test void retryAssignsNotifiesAndPreparesExistingSosRequestWithoutDuplicateSideEffects() {
        var alert = Alert.builder().alertId(UUID.randomUUID()).status(AlertStatus.OPEN).notificationType("SOS")
                .dataJson(mapper.createObjectNode()).build();
        var request = ServiceRequest.builder().serviceRequestId(UUID.randomUUID()).status(ServiceRequestStatus.SUBMITTED)
                .requestType(ServiceRequestType.EMERGENCY_SOS).requestDataJson(mapper.createObjectNode()).build();
        when(alerts.findByIncidentIncidentId(incident.getIncidentId())).thenReturn(List.of(alert));
        when(alerts.lockById(alert.getAlertId())).thenReturn(Optional.of(alert));
        when(requests.findByRelatedIncidentId(incident.getIncidentId())).thenReturn(Optional.of(request));
        when(requests.lockById(request.getServiceRequestId())).thenReturn(Optional.of(request));
        var result = service.retry(incident.getIncidentId(), 3, actor, null);
        assertSame(responder, incident.getSosResponder());
        assertEquals(ASSIGNED, incident.getSosRoutingStatus());
        assertEquals(EmergencyIncidentStatus.OPEN, incident.getStatus());
        assertFalse(result.canRetry());
        assertTrue(request.getRequestDataJson().path("dispatch_pending").asBoolean());
        assertEquals(now.plusSeconds(300), alert.getExpiresAt());
        verify(recipients).save(argThat(value -> value.getAccount() == responder && value.getAlert() == alert));
        verify(media).openSos(incident);
        verify(audit).record(eq(admin), eq("SOS_ROUTING_RETRIED"), eq("EmergencyIncident"), eq(incident.getIncidentId()), anyMap(), anyMap(), isNull());
        service.retry(incident.getIncidentId(), 3, actor, null);
        verify(media, times(1)).openSos(incident);
        verify(recipients, times(1)).save(any());
    }

    @Test void unavailableRoomRemainsUnassignedAndReturnsActionableReason() {
        when(routing.resolve(null, now, null)).thenReturn(new SosResponderRouting.Assignment(ROOM_UNAVAILABLE, null,
                roomId, "SOS old map", "NO_ENDPOINT_ON_ACTIVE_MAP"));
        var result = service.retry(incident.getIncidentId(), 3, actor, null);
        assertEquals("NO_ENDPOINT_ON_ACTIVE_MAP", result.eligibility().reason());
        assertTrue(result.canRetry());
        assertNull(incident.getSosResponder());
        verifyNoInteractions(alerts, recipients, media, requests, jobs);
    }

    @Test void claimedTerminalArchivedAndStaleIncidentsCannotBeReassigned() {
        for (var status : List.of(EmergencyIncidentStatus.ACKNOWLEDGED, EmergencyIncidentStatus.IN_PROGRESS,
                EmergencyIncidentStatus.RESOLVED, EmergencyIncidentStatus.CLOSED, EmergencyIncidentStatus.FALSE_ALARM)) {
            incident.setStatus(status);
            assertThrows(ConflictException.class, () -> service.retry(incident.getIncidentId(), 3, actor, null));
        }
        incident.setStatus(EmergencyIncidentStatus.OPEN);
        incident.setAcknowledgedByAccount(responder);
        assertThrows(ConflictException.class, () -> service.retry(incident.getIncidentId(), 3, actor, null));
        incident.setAcknowledgedByAccount(null);
        incident.setArchivedAt(now);
        assertThrows(ConflictException.class, () -> service.retry(incident.getIncidentId(), 3, actor, null));
        incident.setArchivedAt(null);
        assertThrows(ConflictException.class, () -> service.retry(incident.getIncidentId(), 2, actor, null));
        verifyNoInteractions(media, alerts, recipients, requests, audit);
    }

    @Test void staffCannotInspectOrRetryRouting() {
        var staffActor = AccountPrincipal.fromAccount(responder);
        when(scope.requireCurrentStaffOrAdmin(staffActor)).thenReturn(responder);
        assertThrows(AccessDeniedException.class, () -> service.inspect(incident.getIncidentId(), staffActor));
        assertThrows(AccessDeniedException.class, () -> service.retry(incident.getIncidentId(), 3, staffActor, null));
        verifyNoInteractions(incidents, routing, media, alerts);
    }

    @Test void assigningFallDoesNotEnableAutomaticRobotDispatch() {
        incident.setIncidentType(EmergencyIncident.SUSPECTED_FALL_TYPE);
        var request = ServiceRequest.builder().serviceRequestId(UUID.randomUUID()).status(ServiceRequestStatus.SUBMITTED)
                .requestType(ServiceRequestType.EMERGENCY_SOS).requestDataJson(mapper.createObjectNode().put("staff_review_required", true)).build();
        when(requests.findByRelatedIncidentId(incident.getIncidentId())).thenReturn(Optional.of(request));
        when(requests.lockById(request.getServiceRequestId())).thenReturn(Optional.of(request));
        service.retry(incident.getIncidentId(), 3, actor, null);
        assertFalse(request.getRequestDataJson().path("dispatch_pending").asBoolean());
        assertEquals(EmergencyIncidentStatus.OPEN, incident.getStatus());
        verifyNoInteractions(jobs);
    }
}
