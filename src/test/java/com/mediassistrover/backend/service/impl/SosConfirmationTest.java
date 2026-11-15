package com.mediassistrover.backend.service.impl;

import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.exception.BusinessRuleException;
import com.mediassistrover.backend.media.MediaSessionService;
import com.mediassistrover.backend.repository.*;
import com.mediassistrover.backend.security.AccountPrincipal;
import com.mediassistrover.backend.service.AuditService;
import com.mediassistrover.backend.validation.StaffOperationalScopePolicy;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.security.access.AccessDeniedException;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SosConfirmationTest {
    final EmergencyServiceImpl service = mock(EmergencyServiceImpl.class,CALLS_REAL_METHODS);
    final EmergencyIncidentRepository incidents = mock(EmergencyIncidentRepository.class);
    final StaffOperationalScopePolicy scope = mock(StaffOperationalScopePolicy.class);
    final AuditService audit = mock(AuditService.class);
    final MediaSessionService media = mock(MediaSessionService.class);
    final Account staff = Account.builder().accountId(UUID.randomUUID()).role(Role.STAFF).build();
    final AccountPrincipal actor = AccountPrincipal.fromAccount(staff);
    final EmergencyIncident incident = EmergencyIncident.builder().incidentId(UUID.randomUUID())
            .incidentType("SOS").status(EmergencyIncidentStatus.OPEN).sosResponder(staff).build();

    SosConfirmationTest() {
        ReflectionTestUtils.setField(service,"incidentRepository",incidents);
        ReflectionTestUtils.setField(service,"staffScope",scope);
        ReflectionTestUtils.setField(service,"alertRepository",mock(AlertRepository.class));
        ReflectionTestUtils.setField(service,"auditService",audit);
        ReflectionTestUtils.setField(service,"mediaSessions",media);
        ReflectionTestUtils.setField(service,"clock",Clock.systemUTC());
        when(incidents.lockById(incident.getIncidentId())).thenReturn(Optional.of(incident));
        when(scope.requireCurrentStaffOrAdmin(actor)).thenReturn(staff);
    }

    @Test void assignedStaffConfirmsAndRetryIsIdempotent() {
        service.confirm(incident.getIncidentId(),actor,null);
        assertEquals(EmergencyIncidentStatus.IN_PROGRESS,incident.getStatus());
        assertEquals(staff,incident.getAcknowledgedByAccount());
        assertNotNull(incident.getAcknowledgedAt());
        clearInvocations(audit);
        service.confirm(incident.getIncidentId(),actor,null);
        verifyNoInteractions(audit);
    }

    @Test void rejectsOtherStaffAndClosedOrFalseAlarmIncidents() {
        incident.setSosResponder(Account.builder().accountId(UUID.randomUUID()).build());
        assertThrows(AccessDeniedException.class,() -> service.confirm(incident.getIncidentId(),actor,null));
        incident.setSosResponder(staff);
        for (var status : List.of(EmergencyIncidentStatus.FALSE_ALARM,EmergencyIncidentStatus.RESOLVED,EmergencyIncidentStatus.CLOSED)) {
            incident.setStatus(status);
            assertThrows(BusinessRuleException.class,() -> service.confirm(incident.getIncidentId(),actor,null));
        }
        verifyNoInteractions(audit);
    }

    @Test void fallMustBeConfirmedFromAnActiveCameraReview() {
        incident.setIncidentType(EmergencyIncident.SUSPECTED_FALL_TYPE);
        incident.setStatus(EmergencyIncidentStatus.ACKNOWLEDGED);
        incident.setAcknowledgedByAccount(staff);
        assertThrows(BusinessRuleException.class, () -> service.start(incident.getIncidentId(), actor, null));
        doThrow(new BusinessRuleException("MEDIA_FALL_REVIEW_NOT_ACTIVE"))
                .doNothing().when(media).requireActiveFallReview(incident, actor);
        assertThrows(BusinessRuleException.class, () -> service.confirm(incident.getIncidentId(), actor, null));
        assertEquals(EmergencyIncidentStatus.ACKNOWLEDGED, incident.getStatus());
        service.confirm(incident.getIncidentId(), actor, null);
        assertEquals(EmergencyIncidentStatus.IN_PROGRESS, incident.getStatus());
        service.confirm(incident.getIncidentId(), actor, null);
        verify(media, times(2)).requireActiveFallReview(incident, actor);
        verify(audit).record(eq(staff), eq("FALL_CONFIRMED"), eq("EmergencyIncident"),
                eq(incident.getIncidentId()), isNull(), anyMap(), isNull());
    }
}
