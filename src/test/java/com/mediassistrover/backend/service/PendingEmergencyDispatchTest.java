package com.mediassistrover.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.repository.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PendingEmergencyDispatchTest {
    final ServiceRequestRepository requests=mock(ServiceRequestRepository.class);
    final EmergencyIncidentRepository incidents=mock(EmergencyIncidentRepository.class);
    final JobRepository jobs=mock(JobRepository.class);
    final JobExecutionService execution=mock(JobExecutionService.class);
    final PendingEmergencyDispatchService service=new PendingEmergencyDispatchService(requests,incidents,jobs,execution);
    final ObjectMapper json=new ObjectMapper();
    final Robot robot=Robot.builder().robotId(UUID.randomUUID()).build();
    final Account staff=Account.builder().accountId(UUID.randomUUID()).role(Role.STAFF).build();
    final EmergencyIncident incident=EmergencyIncident.builder().incidentId(UUID.randomUUID()).robot(robot)
            .incidentType("SOS").sosResponder(staff).status(EmergencyIncidentStatus.OPEN).build();
    final ServiceRequest request=ServiceRequest.builder().serviceRequestId(UUID.randomUUID()).requestedRobot(robot)
            .requestType(ServiceRequestType.EMERGENCY_SOS).status(ServiceRequestStatus.SUBMITTED)
            .relatedIncidentId(incident.getIncidentId()).requestDataJson(json.createObjectNode().put("dispatch_pending",true)).build();
    PendingEmergencyDispatchTest(){
        when(requests.lockById(request.getServiceRequestId())).thenReturn(Optional.of(request));
        when(incidents.findById(incident.getIncidentId())).thenReturn(Optional.of(incident));
    }
    @Test void deferredAutomaticSosIsQueuedOnceForItsOriginalRobotAndResponder(){
        var job=Job.builder().jobId(UUID.randomUUID()).robot(robot).build();
        when(execution.createForRequest(request)).thenReturn(job);
        assertTrue(service.dispatch(request.getServiceRequestId()));
        assertEquals(ServiceRequestStatus.QUEUED,request.getStatus());assertSame(staff,job.getAssignedStaffAccount());
        assertFalse(request.getRequestDataJson().has("dispatch_pending"));
        assertFalse(service.dispatch(request.getServiceRequestId()));verify(execution,times(1)).createForRequest(request);
    }
    @Test void staffReviewAndResolvedIncidentNeverBecomeAutomaticJobs(){
        ((com.fasterxml.jackson.databind.node.ObjectNode)request.getRequestDataJson()).put("staff_review_required",true);
        assertFalse(service.dispatch(request.getServiceRequestId()));
        ((com.fasterxml.jackson.databind.node.ObjectNode)request.getRequestDataJson()).remove("staff_review_required");
        incident.setStatus(EmergencyIncidentStatus.RESOLVED);
        assertFalse(service.dispatch(request.getServiceRequestId()));verifyNoInteractions(execution);
    }
    @Test void anExistingJobPreventsDuplicateExecutionAfterRetry(){
        when(jobs.findByServiceRequestServiceRequestId(request.getServiceRequestId())).thenReturn(List.of(new Job()));
        assertFalse(service.dispatch(request.getServiceRequestId()));verifyNoInteractions(execution);
    }
    @Test void pendingRequestCannotMoveAnotherRobot(){
        incident.setRobot(Robot.builder().robotId(UUID.randomUUID()).build());
        assertFalse(service.dispatch(request.getServiceRequestId()));verifyNoInteractions(execution);
    }
}
