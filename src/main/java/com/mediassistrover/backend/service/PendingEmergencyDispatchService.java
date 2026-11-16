package com.mediassistrover.backend.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Retries a committed automatic SOS admission; staff-review suspicions never create automatic jobs. */
@Service @RequiredArgsConstructor
public class PendingEmergencyDispatchService {
    private final ServiceRequestRepository requests;
    private final EmergencyIncidentRepository incidents;
    private final JobRepository jobs;
    private final JobExecutionService execution;

    @Transactional
    public boolean dispatch(UUID requestId) {
        var request=requests.lockById(requestId).orElse(null);
        if(request==null || request.getStatus()!=ServiceRequestStatus.SUBMITTED
                || request.getRequestType()!=ServiceRequestType.EMERGENCY_SOS || request.getRelatedIncidentId()==null
                || request.getRequestedRobot()==null || request.getArchivedAt()!=null
                || request.getRequestDataJson()==null || !request.getRequestDataJson().path("dispatch_pending").asBoolean()
                || request.getRequestDataJson().path("staff_review_required").asBoolean())return false;
        if(!jobs.findByServiceRequestServiceRequestId(requestId).isEmpty())return false;
        var incident=incidents.findById(request.getRelatedIncidentId()).orElse(null);
        if(incident==null || incident.isSuspectedFall() || incident.getArchivedAt()!=null || incident.getSosResponder()==null
                || incident.getRobot()==null || !incident.getRobot().getRobotId().equals(request.getRequestedRobot().getRobotId())
                || !Set.of(EmergencyIncidentStatus.OPEN,EmergencyIncidentStatus.ACKNOWLEDGED,EmergencyIncidentStatus.IN_PROGRESS).contains(incident.getStatus()))return false;
        var job=execution.createForRequest(request);
        job.setAssignedStaffAccount(incident.getSosResponder());
        request.setStatus(ServiceRequestStatus.QUEUED);
        var data=((ObjectNode)request.getRequestDataJson()).deepCopy();data.remove("dispatch_pending");request.setRequestDataJson(data);
        return true;
    }
}
