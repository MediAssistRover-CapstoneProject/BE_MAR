package com.mediassistrover.backend.service.impl;
import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.repository.*;
import com.mediassistrover.backend.service.*;
import com.mediassistrover.backend.security.AccountPrincipal;
import com.mediassistrover.backend.validation.StaffOperationalScopePolicy;
import com.mediassistrover.backend.mapper.RequestJobMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FallCancellationTest {
    final EmergencyServiceImpl service=mock(EmergencyServiceImpl.class,CALLS_REAL_METHODS);
    final EmergencyIncidentRepository incidents=mock(EmergencyIncidentRepository.class);
    final ServiceRequestRepository requests=mock(ServiceRequestRepository.class);
    final JobRepository jobs=mock(JobRepository.class);
    final AlertRepository alerts=mock(AlertRepository.class);
    final JobCancellationService cancellation=mock(JobCancellationService.class);
    final StaffOperationalScopePolicy scope=mock(StaffOperationalScopePolicy.class);
    final Account account=Account.builder().accountId(UUID.randomUUID()).role(Role.STAFF).build();
    final AccountPrincipal actor=AccountPrincipal.fromAccount(account);
    final EmergencyIncident incident=EmergencyIncident.builder().incidentId(UUID.randomUUID())
            .incidentType(EmergencyIncident.SUSPECTED_FALL_TYPE).status(EmergencyIncidentStatus.OPEN).build();
    FallCancellationTest() {
        Map.ofEntries(Map.entry("incidentRepository",incidents),Map.entry("requestRepository",requests),Map.entry("jobRepository",jobs),
            Map.entry("alertRepository",alerts),Map.entry("cancellation",cancellation),Map.entry("staffScope",scope),
            Map.entry("auditService",mock(AuditService.class)),Map.entry("jobMapper",mock(RequestJobMapper.class)),
            Map.entry("clock",Clock.systemUTC())).forEach((k,v)->ReflectionTestUtils.setField(service,k,v));
        when(scope.requireCurrentStaffOrAdmin(actor)).thenReturn(account);
        when(incidents.lockById(incident.getIncidentId())).thenReturn(Optional.of(incident));
    }
    @Test void anyActiveStaffCanCloseFallWithoutAssignedResponderOrJob() {
        service.cancelFall(incident.getIncidentId(),"handled manually",actor,null);
        assertEquals(EmergencyIncidentStatus.CLOSED,incident.getStatus());assertNotNull(incident.getClosedAt());
        assertSame(account,incident.getResolvedByAccount());assertNull(incident.getResolvedAt());
        verify(scope,never()).requireIncidentAccess(any(),any());
        service.cancelFall(incident.getIncidentId(),"replacement",actor,null);
        assertEquals("handled manually",incident.getResolutionNote());
    }
    @Test void onlyIncidentResponseJobsAreCancelled() {
        var request=ServiceRequest.builder().serviceRequestId(UUID.randomUUID()).status(ServiceRequestStatus.QUEUED).build();
        var job=Job.builder().jobId(UUID.randomUUID()).status(JobStatus.RUNNING).build();
        when(requests.findByRelatedIncidentId(incident.getIncidentId())).thenReturn(Optional.of(request));
        when(requests.lockById(request.getServiceRequestId())).thenReturn(Optional.of(request));
        when(jobs.findByServiceRequestServiceRequestId(request.getServiceRequestId())).thenReturn(List.of(job));
        when(jobs.lockById(job.getJobId())).thenReturn(Optional.of(job));
        service.cancelFall(incident.getIncidentId(),"stop",actor,null);
        verify(cancellation).cancel(job,account,"stop",null);
        assertEquals(ServiceRequestStatus.CANCELLED,request.getStatus());
    }
    @Test void sosAndInactiveStaffAreDenied() {
        incident.setIncidentType(EmergencyIncident.SOS_TYPE);
        assertThrows(com.mediassistrover.backend.exception.BusinessRuleException.class,()->service.cancelFall(incident.getIncidentId(),"stop",actor,null));
        incident.setIncidentType(EmergencyIncident.SUSPECTED_FALL_TYPE);
        when(scope.requireCurrentStaffOrAdmin(actor)).thenThrow(new org.springframework.security.access.AccessDeniedException("inactive"));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->service.cancelFall(incident.getIncidentId(),"stop",actor,null));
        assertEquals(EmergencyIncidentStatus.OPEN,incident.getStatus());verifyNoInteractions(cancellation);
    }
}
