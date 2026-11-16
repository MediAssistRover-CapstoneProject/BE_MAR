package com.mediassistrover.backend.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediassistrover.backend.dto.emergency.response.EmergencyResponses.SosResponse;
import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.exception.BusinessRuleException;
import com.mediassistrover.backend.media.MediaSessionService;
import com.mediassistrover.backend.repository.*;
import com.mediassistrover.backend.validation.WorkflowExecutionReadiness;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class EmergencyFlowService {
    public record Command(String operationKey, Robot robot, FacilityMap map, Endpoint endpoint,
                          DetectionEvent detectionEvent, String incidentType,
                          EmergencyIncidentSeverity severity, String description,
                          BigDecimal xM, BigDecimal yM, Instant occurredAt,
                          RequestSourceType sourceType, ObjectNode data,
                          String auditAction, RequestMetadata metadata) { }

    public record Result(EmergencyIncident incident, ServiceRequest request, Alert alert, Job job) { }

    private final EmergencyIncidentRepository incidentRepository;
    private final ServiceRequestRepository requestRepository;
    private final AlertService alertService;
    private final JobExecutionService jobExecutionService;
    private final JobRepository jobRepository;
    private final WorkflowRepository workflowRepository;
    private final WorkflowExecutionReadiness readiness;
    private final MediaSessionService mediaSessions;
    private final SosResponderRouting sosRouting;
    private final AuditService auditService;
    private final Clock clock;
    private final PatrolInterruptionEvents patrolInterruptions;

    @Transactional
    public Result create(Command command) {
        return create(command, true);
    }

    @Transactional
    public Result createForStaffReview(Command command) {
        return create(command, false);
    }

    private Result create(Command command, boolean automaticDispatch) {
        Instant now = Instant.now(clock);
        EmergencyIncident incident = EmergencyIncident.builder()
                .detectionEvent(command.detectionEvent())
                .robot(command.robot())
                .map(command.map())
                .endpoint(command.endpoint())
                .incidentType(command.incidentType())
                .severity(command.severity())
                .status(EmergencyIncidentStatus.OPEN)
                .description(command.description())
                .xM(command.xM())
                .yM(command.yM())
                .occurredAt(command.occurredAt())
                .build();
        sosRouting.assign(incident, now);
        incidentRepository.saveAndFlush(incident);
        patrolInterruptions.hold(command.robot().getRobotId(), "INCIDENT", incident.getIncidentId());

        ObjectNode data = command.data().deepCopy();
        data.put("incident_id", incident.getIncidentId().toString());
        data.put("message", command.description());
        data.put("severity", command.severity().name());
        if (command.endpoint() != null) data.put("endpoint_id", command.endpoint().getEndpointId().toString());
        if (command.detectionEvent() != null)
            data.put("detection_event_id", command.detectionEvent().getDetectionEventId().toString());

        UUID mediaSessionId = mediaSessions.openSos(incident);
        if (mediaSessionId != null) data.put("media_session_id", mediaSessionId.toString());

        ServiceRequest request = requestRepository.saveAndFlush(ServiceRequest.builder()
                .requestedRobot(command.robot())
                .relatedIncidentId(incident.getIncidentId())
                .requestCode(code("REQ"))
                .requestType(ServiceRequestType.EMERGENCY_SOS)
                .sourceType(command.sourceType())
                .priority(command.severity() == EmergencyIncidentSeverity.CRITICAL ? 100 : 80)
                .status(ServiceRequestStatus.SUBMITTED)
                .requestNote(command.description())
                .requestDataJson(data)
                .idempotencyKey(command.operationKey())
                .requestedAt(now)
                .build());

        Zone zone = command.endpoint() != null ? command.endpoint().getZone()
                : command.detectionEvent() == null ? null : command.detectionEvent().getZone();
        Alert alert = alertService.createSystemAlert(command.operationKey(), "SOS",
                command.severity() == EmergencyIncidentSeverity.CRITICAL
                        ? AlertSeverity.CRITICAL : AlertSeverity.WARNING,
                command.description(),
                data.deepCopy().put("sos_routing_status", incident.getSosRoutingStatus().name()),
                incident, request, null, zone, true, AlertAckPolicy.ANY, now.plusSeconds(300));

        Job job = null;
        if (automaticDispatch && incident.getSosResponder() != null) {
            if (canAcceptEmergency(command.robot()) && canDispatch(command.robot())) {
                job = jobExecutionService.createForRequest(request);
                job.setAssignedStaffAccount(incident.getSosResponder());
                request.setStatus(ServiceRequestStatus.QUEUED);
            } else {
                request.setRequestDataJson(data.deepCopy().put("dispatch_pending", true));
            }
        }

        Map<String, Object> auditData = new LinkedHashMap<>();
        auditData.put("robotId", command.robot().getRobotId());
        auditData.put("serviceRequestId", request.getServiceRequestId());
        auditData.put("sosRoutingStatus", incident.getSosRoutingStatus().name());
        if (command.detectionEvent() != null)
            auditData.put("detectionEventId", command.detectionEvent().getDetectionEventId());
        auditService.record(null, command.auditAction(), "EmergencyIncident", incident.getIncidentId(),
                null, auditData, command.metadata());
        return new Result(incident, request, alert, job);
    }

    private boolean canAcceptEmergency(Robot robot) {
        if (!Set.of(RobotStatus.IDLE, RobotStatus.BUSY).contains(robot.getStatus())) return false;
        var active = jobRepository.findByRobotRobotIdAndStatusIn(robot.getRobotId(),
                List.of(JobStatus.QUEUED, JobStatus.ACCEPTED, JobStatus.RUNNING,
                        JobStatus.SUSPENDING, JobStatus.RECOVERY_REQUIRED));
        // Admission may queue behind interruptible patrol, but never interrupt
        // an unrelated foreground task or recovery merely by creating an SOS.
        return active.isEmpty() || active.stream().allMatch(job -> job.isBeOrchestrated()
                && job.getSchemaVersion() == 3 && job.getWorkflow() != null
                && "PATROL".equalsIgnoreCase(job.getWorkflow().getServiceType())
                && job.getStatus() != JobStatus.RECOVERY_REQUIRED);
    }

    private boolean canDispatch(Robot robot) {
        var workflow = workflowRepository
                .findFirstByServiceTypeIgnoreCaseAndStatusAndAcceptsNewJobsTrueOrderByVersionNoDesc(
                        "EMERGENCY", WorkflowStatus.PUBLISHED)
                .orElse(null);
        if (workflow == null || !readiness.acceptsRobot(workflow, robot)) return false;
        try {
            readiness.requireReady(workflow);
            return true;
        } catch (BusinessRuleException error) {
            return false;
        }
    }

    private String code(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 20).toUpperCase(Locale.ROOT);
    }
}
