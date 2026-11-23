package com.mediassistrover.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediassistrover.backend.dto.job.request.JobRequests.DeliveryRecoveryConfirmation;
import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.exception.BusinessRuleException;
import com.mediassistrover.backend.mqtt.MqttJobSafety;
import com.mediassistrover.backend.mqtt.MqttConnectionState;
import com.mediassistrover.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;

/** A stopped motor does not prove that delivery cargo was removed or its door secured. Caller holds Job. */
@Service @RequiredArgsConstructor
public class DeliveryReleasePolicy {
    private final DeliveryRequestRepository deliveries;
    private final RobotCommandOutboxRepository commands;
    private final RobotRepository robots;
    private final RobotMqttSessionRepository sessions;
    private final MqttConnectionState connection;
    private final AuditService audit;
    private final ObjectMapper json;
    private final Clock clock;

    public boolean requiresReconciliation(Job job) {
        if(!delivery(job) || cleared(job))return false;
        DeliveryRequest request=deliveries.findById(job.getServiceRequest().getServiceRequestId()).orElse(null);
        if(request==null)return true;
        if(request.getDeliveredAt()!=null && request.getDeliveryPayloadConfirmedAt()!=null
                && request.getDeliveryCompartmentConfirmedAt()!=null)return false;
        if(request.getPickedUpAt()!=null || request.getPickupPayloadConfirmedAt()!=null
                || request.getPickupCompartmentConfirmedAt()!=null || request.getDeliveryPayloadConfirmedAt()!=null)return true;
        return commands.findByJobIdOrderByCreatedAt(job.getJobId()).stream().anyMatch(command -> {
            if(command.getSendAttempts()<1 || !"EXECUTE_STEP".equals(command.getCommandType()) || command.getPayloadJson()==null)return false;
            var step=command.getPayloadJson().path("step");
            return "CONTROL_HARDWARE".equals(step.path("stepType").asText())
                    && Set.of("UNLOCK","UNLOCK_COMPARTMENT").contains(step.path("params").path("action").asText());
        });
    }

    public void retain(Job job) {
        job.setErrorCode("DELIVERY_HANDOVER_RECOVERY_REQUIRED");
        job.setFailureReason("Delivery cargo and compartment require staff recovery before this robot can take another job");
        if(job.getStatus()==JobStatus.CANCELLED)job.setStopStatus(JobStopStatus.RECOVERY_REQUIRED);
        else job.setStatus(JobStatus.RECOVERY_REQUIRED);
        if(job.getServiceRequest()!=null)job.getServiceRequest().setStatus(ServiceRequestStatus.RECOVERY_REQUIRED);
        if(job.getRobot()!=null)job.getRobot().setStatus(RobotStatus.ERROR);
    }

    /** Explicit staff evidence is stored separately from normal delivery milestones and Pi stop evidence. */
    public void confirmCleared(Job job,DeliveryRecoveryConfirmation confirmation,Account actor,RequestMetadata metadata) {
        if(!requiresReconciliation(job))return;
        if(confirmation==null || !Boolean.TRUE.equals(confirmation.cargoRemoved())
                || !Boolean.TRUE.equals(confirmation.compartmentClosed()) || !Boolean.TRUE.equals(confirmation.compartmentLocked()))
            throw new BusinessRuleException("Delivery recovery requires cargoRemoved, compartmentClosed and compartmentLocked confirmations");
        if(actor==null || actor.getStatus()!=AccountStatus.ACTIVE || actor.isMustChangePassword()
                || !Set.of(Role.STAFF,Role.ADMIN).contains(actor.getRole()))
            throw new org.springframework.security.access.AccessDeniedException("Delivery recovery requires active authorized Staff/Admin");
        if(job.getRobot()==null)throw new BusinessRuleException("Delivery recovery requires its assigned robot");
        robots.findByIdForUpdate(job.getRobot().getRobotId()).orElseThrow();
        var session=sessions.lock(job.getRobot().getRobotId()).orElse(null);
        Instant now=clock.instant();
        if(session==null || connection.connectionId()==null || session.isClosed() || !session.isHandshakeAccepted()
                || !connection.instanceId().equals(session.getOwnerInstanceId()) || !connection.connectionId().equals(session.getConnectionId())
                || !session.isHandshakeAcked() || session.getHelloJson()==null || session.getHelloReceivedAt()==null
                || session.getLastHeartbeatAt()==null || !session.getLastHeartbeatAt().isAfter(now.minusSeconds(15))
                || session.getLastHeartbeatAt().isBefore(session.getHelloReceivedAt()))
            throw new BusinessRuleException("Delivery recovery requires a current HELLO and fresh heartbeat");
        var heartbeat=session.getHeartbeatJson();
        if(heartbeat==null || !"IDLE".equals(heartbeat.path("status").asText()) || !heartbeat.path("activeJobStepId").isNull()
                || com.mediassistrover.backend.mapping.MappingLifecycleService.reportsOpen(heartbeat.path("mappingSession"))
                || com.mediassistrover.backend.mapping.MappingLifecycleService.reportsOpen(session.getHelloJson().path("mappingSession")))
            throw new BusinessRuleException("Delivery recovery requires an idle Pi with no active operation or mapping");
        var odom=heartbeat.path("telemetry").path("odom_speed");
        if(!odom.path("value").isNumber() || odom.path("value").asDouble()<0 || odom.path("value").asDouble()>.01
                || !odom.path("age_sec").isNumber() || odom.path("age_sec").asDouble()<0 || odom.path("age_sec").asDouble()>.5)
            throw new BusinessRuleException("Delivery recovery requires fresh stationary odometry");
        for(var command:commands.findByJobIdOrderByCreatedAt(job.getJobId())) {
            boolean resolved="RESOLVED_BY_STAFF".equals(command.getState());
            if(!resolved && (Set.of("PENDING","PUBLISHING","PUBLISHED","ACKNOWLEDGED","STARTED","RECOVERY_REQUIRED").contains(command.getState())
                    || command.getSendAttempts()>0 && !Boolean.TRUE.equals(command.getPhysicalStopConfirmed())
                    && !MqttJobSafety.completedWithoutPhysicalStop(command,job.getCancelRequestedAt())))
                throw new BusinessRuleException("Delivery recovery requires committed stop/reconciliation for every Pi operation");
        }
        for(var entry:session.getHelloJson().path("states"))
            if(Set.of("ACCEPTED","RUNNING","RECOVERY_REQUIRED").contains(entry.path("status").asText()))
                throw new BusinessRuleException("Delivery recovery requires a terminal Pi journal");
        ObjectNode context=job.getContextJson()!=null && job.getContextJson().isObject()
                ?(ObjectNode)job.getContextJson().deepCopy():json.createObjectNode();
        var evidence=context.putObject("delivery_recovery").put("job_id",job.getJobId().toString())
                .put("cargo_removed",true).put("compartment_closed",true).put("compartment_locked",true)
                .put("actor_account_id",actor.getAccountId().toString()).put("confirmed_at",now.toString())
                .put("transport_session_id",session.getTransportSessionId().toString());
        job.setContextJson(context);
        audit.record(actor,"DELIVERY_CARGO_RECOVERY_CONFIRMED","Job",job.getJobId(),null,Map.of("clearance",evidence),metadata);
    }

    private boolean cleared(Job job) {
        if(job.getContextJson()==null || job.getReconciledByAccount()==null || job.getReconciledAt()==null)return false;
        var evidence=job.getContextJson().path("delivery_recovery");
        return job.getJobId()!=null && job.getJobId().toString().equals(evidence.path("job_id").asText())
                && job.getReconciledByAccount().getAccountId().toString().equals(evidence.path("actor_account_id").asText())
                && evidence.path("cargo_removed").asBoolean() && evidence.path("compartment_closed").asBoolean()
                && evidence.path("compartment_locked").asBoolean();
    }
    private boolean delivery(Job job) {
        return job!=null && job.isBeOrchestrated() && job.getSchemaVersion()==3 && job.getServiceRequest()!=null
                && job.getServiceRequest().getRequestType()==ServiceRequestType.DELIVERY;
    }
}
