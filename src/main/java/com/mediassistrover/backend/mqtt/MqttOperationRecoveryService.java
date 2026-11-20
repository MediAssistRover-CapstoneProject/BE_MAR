package com.mediassistrover.backend.mqtt;

import com.fasterxml.jackson.databind.JsonNode;
import com.mediassistrover.backend.dto.job.request.*;
import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.exception.*;
import com.mediassistrover.backend.repository.*;
import com.mediassistrover.backend.security.AccountPrincipal;
import com.mediassistrover.backend.security.DevicePrincipal;
import com.mediassistrover.backend.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Recovery closes an inspected operation/Job. It never replays an old physical action or declares delivery successful. */
@Service @RequiredArgsConstructor
public class MqttOperationRecoveryService {
    private final JobRepository jobs;
    private final JobStepRepository steps;
    private final JobTaskRepository tasks;
    private final RobotRepository robots;
    private final RobotMqttSessionRepository sessions;
    private final RobotCommandOutboxRepository commands;
    private final MqttOperationRecoveryReceiptRepository receipts;
    private final RobotMqttInboxRepository inbox;
    private final JobEventRepository events;
    private final com.mediassistrover.backend.validation.StaffOperationalScopePolicy scopePolicy;
    private final MqttSessionRegistry registry;
    private final MqttWire wire;
    private final AuditService audit;
    private final OrchestrationWorkService wakeups;
    private final org.springframework.context.ApplicationEventPublisher updates;
    private final Clock clock;
    private final com.mediassistrover.backend.mapping.MappingLifecycleService mapping;
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager em;

    public record CancelledMoveSettlement(@jakarta.validation.constraints.NotNull UUID commandId,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=150) String operationKey) { }

    @Transactional
    public JsonNode settleCancelledMove(UUID jobId,UUID stepId,CancelledMoveSettlement request,DevicePrincipal device) {
        if(device==null)throw new AccessDeniedException("DEVICE identity required");
        Job job=jobs.lockById(jobId).orElseThrow(()->new ResourceNotFoundException("Job not found"));
        if(job.getRobot()==null||!job.getRobot().getRobotId().equals(device.robotId()))
            throw new AccessDeniedException("Job is outside this device");
        Robot robot=robots.findByIdForUpdate(device.robotId()).orElseThrow();
        Instant now=Instant.now(clock);
        if(!Objects.equals(robot.getRobotCode(),device.robotCode())||robot.getDeviceTokenVersion()!=device.tokenVersion()
                ||device.expiresAt()==null||!device.expiresAt().isAfter(now))
            throw new AccessDeniedException("Current DEVICE token required");
        boolean reconciledFailure=job.getStatus()==JobStatus.FAILED&&job.getReconciledAt()!=null
                &&job.getReconciledByAccount()!=null&&job.getReconciliationNote()!=null&&!job.getReconciliationNote().isBlank();
        if(!job.isBeOrchestrated()||job.getStatus()!=JobStatus.CANCELLED&&!reconciledFailure
                ||job.getEndedAt()==null||mapping.open(jobId))
            throw new BusinessRuleException("Settlement requires a cancelled or explicitly reconciled failed Job with no open mapping session");
        JobStep initial=steps.findById(stepId).orElseThrow(()->new ResourceNotFoundException("JobStep not found"));
        if(!initial.getJobTask().getJob().getJobId().equals(jobId))throw new AccessDeniedException("JobStep is outside this Job");
        RobotCommandOutbox selected=commands.findByJobStepId(stepId)
                .orElseThrow(()->new BusinessRuleException("No executed MQTT operation"));
        if(request==null||!selected.getCommandId().equals(request.commandId())
                ||!jobId.equals(selected.getJobId())||!device.robotId().equals(selected.getRobotId()))
            throw new BusinessRuleException("Settlement command does not match the terminal operation");
        RobotCommandOutbox command=commands.lock(selected.getCommandId()).orElseThrow();
        JobStep step=steps.lockById(stepId).orElseThrow();
        JobStepStatus settledStatus=reconciledFailure?JobStepStatus.FAILED:JobStepStatus.CANCELLED;
        JobStepStatus previousStatus=step.getStatus();
        // Job cancellation/reconciliation can leave the selected Pi step in
        // RECOVERY_REQUIRED. Retire it only after the same committed stop proof.
        boolean selectedRecovery=previousStatus==JobStepStatus.RECOVERY_REQUIRED
                &&job.getSelectedJobStep()!=null&&stepId.equals(job.getSelectedJobStep().getJobStepId());
        boolean compatibleStep=previousStatus==settledStatus
                ||selectedRecovery;
        if(!compatibleStep||!"MOVE".equals(step.getStepTypeSnapshot())
                ||reconciledFailure&&(job.getSelectedJobStep()==null||!stepId.equals(job.getSelectedJobStep().getJobStepId()))
                ||!Objects.equals(step.getOperationKey(),request.operationKey())
                ||!Objects.equals(command.getOperationKey(),request.operationKey())
                ||!"EXECUTE_STEP".equals(command.getCommandType())||command.getSendAttempts()<1
                ||!Boolean.TRUE.equals(command.getPhysicalStopConfirmed()))
            throw new BusinessRuleException("Settlement requires a matching terminal MOVE with committed physical stop");
        JsonNode stop=command.getResultEnvelopeJson();
        if(stop==null||!"STEP_ERROR".equals(stop.path("messageType").asText())
                ||!device.robotId().toString().equals(stop.path("robotId").asText())
                ||!jobId.toString().equals(stop.path("jobId").asText())
                ||!stepId.toString().equals(stop.path("jobStepId").asText())
                ||!command.getCommandId().toString().equals(stop.path("correlationId").asText())
                ||!request.operationKey().equals(stop.path("operationKey").asText())
                ||!stop.path("payload").path("physicalStopConfirmed").asBoolean())
            throw new BusinessRuleException("No matching committed Pi stop receipt for this MOVE");
        UUID stopId;
        try {stopId=UUID.fromString(stop.path("messageId").asText());}
        catch(IllegalArgumentException error) {throw new BusinessRuleException("Pi stop receipt identity is invalid");}
        var accepted=inbox.findById(stopId).orElse(null);
        if(accepted==null||!Boolean.TRUE.equals(accepted.getAccepted())
                ||!device.robotId().equals(accepted.getRobotId())||!"STEP_ERROR".equals(accepted.getMessageType()))
            throw new BusinessRuleException("Pi stop receipt has not been accepted");
        String eventType=reconciledFailure?"BE_FAILED_OPERATION_CONFIRMED":"BE_CANCELLED_OPERATION_CONFIRMED";
        UUID receiptId=UUID.nameUUIDFromBytes((command.getCommandId()+":"+eventType)
                .getBytes(StandardCharsets.UTF_8));
        var prior=events.findById(receiptId).orElse(null);
        if(prior!=null)return prior.getEventDataJson().deepCopy();
        var response=wire.object().put("receiptId",receiptId.toString()).put("robotId",device.robotId().toString())
                .put("jobId",jobId.toString()).put("jobStepId",stepId.toString()).put("commandId",command.getCommandId().toString())
                .put("operationKey",step.getOperationKey()).put("status",settledStatus.name())
                .put("physicalStopConfirmed",true).put("stopEvidenceMessageId",stopId.toString()).put("settledAt",now.toString());
        if(reconciledFailure)response.put("reconciliationOutcome","CONFIRM_FAILED").put("reconciledAt",job.getReconciledAt().toString());
        command.setState("COMPLETED");
        if (previousStatus!=settledStatus) {
            step.setStatus(settledStatus);
            if (step.getEndedAt()==null) step.setEndedAt(now);
        }
        events.save(JobEvent.builder().jobEventId(receiptId).job(job).robot(robot).jobTask(step.getJobTask()).jobStep(step)
                .producerType(JobEventProducerType.ROBOT).producerSessionId(UUID.randomUUID()).sequenceNo(1)
                .eventType(eventType).correlationKey(command.getCommandId().toString())
                .statusScope("JOB_STEP").previousStatus(previousStatus.name()).newStatus(settledStatus.name())
                .eventDataJson(response.deepCopy()).occurredAt(now).receivedAt(now).persistedAt(now).build());
        updates.publishEvent(new RuntimeChanged(jobId));
        return response;
    }

    @Transactional(readOnly=true)
    public JsonNode evidence(UUID jobId,AccountPrincipal actor) {
        Job job=jobs.findById(jobId).orElseThrow(()->new ResourceNotFoundException("Job not found"));authorize(job,actor);
        var result=wire.object().put("jobId",jobId.toString()).put("status",job.getStatus().name())
                .put("recoveryRequired",job.getStatus()==JobStatus.RECOVERY_REQUIRED || job.stopOutstanding());
        if(job.getSelectedJobStep()!=null)result.put("jobStepId",job.getSelectedJobStep().getJobStepId().toString()).put("operationKey",job.getSelectedJobStep().getOperationKey());
        boolean uncertain=false;
        var list=result.putArray("commands");for(var command:commands.findByJobIdOrderByCreatedAt(jobId)) {
            var row=list.addObject().put("commandId",command.getCommandId().toString()).put("jobStepId",command.getJobStepId().toString())
                    .put("commandType",command.getCommandType()).put("state",command.getState()).put("sendAttempts",command.getSendAttempts())
                    .put("createdAt",command.getCreatedAt().toString());
            row.set("physicalStopConfirmed",wire.tree(command.getPhysicalStopConfirmed()));
            JsonNode receipt=command.getResultEnvelopeJson();
            boolean operatorReconciled=receipt!=null && "OPERATOR_RECONCILED".equals(receipt.path("messageType").asText())
                    && inbox.findById(UUID.fromString(receipt.path("messageId").asText()))
                    .map(r->Boolean.TRUE.equals(r.getAccepted())).orElse(false);
            row.put("operatorReconciled",operatorReconciled);
            if(command.getLastPublishAt()!=null)row.put("lastPublishedAt",command.getLastPublishAt().toString());
            if(command.getAcknowledgedAt()!=null)row.put("acknowledgedAt",command.getAcknowledgedAt().toString());
            if("EXECUTE_STEP".equals(command.getCommandType()) && command.getSendAttempts()>0
                    && !Boolean.TRUE.equals(command.getPhysicalStopConfirmed()) && !"RESOLVED_BY_STAFF".equals(command.getState())
                    && !MqttJobSafety.completedWithoutPhysicalStop(command,job.getCancelRequestedAt()))uncertain=true;
        }
        var eventList=result.putArray("events");events.findByJobJobId(jobId,PageRequest.of(0,50,Sort.by(Sort.Direction.DESC,"occurredAt"))).forEach(event->eventList.addObject()
                .put("jobEventId",event.getJobEventId().toString()).put("eventType",event.getEventType())
                .put("jobStepId",event.getJobStep()==null?null:event.getJobStep().getJobStepId().toString())
                .put("previousStatus",event.getPreviousStatus()).put("newStatus",event.getNewStatus())
                .put("occurredAt",event.getOccurredAt().toString()));
        var session=job.getRobot()==null?null:sessions.findById(job.getRobot().getRobotId()).orElse(null);
        var physical=result.putObject("physicalUncertainty");physical.put("stopUnconfirmed",uncertain);
        if(session!=null){boolean owned=registry.owned(session);boolean fresh=registry.fresh(session);JsonNode heartbeat=session.getHeartbeatJson();boolean idle=heartbeat!=null&&"IDLE".equals(heartbeat.path("status").asText());boolean active=heartbeat!=null&&!heartbeat.path("activeJobStepId").isNull();
            result.put("transportSessionId",session.getTransportSessionId().toString());physical.put("sessionOwned",owned).put("heartbeatFresh",fresh).put("robotReportsIdle",idle).put("activeOperationReported",active).put("mappingOpen",heartbeat!=null&&com.mediassistrover.backend.mapping.MappingLifecycleService.reportsOpen(heartbeat.path("mappingSession")));
            physical.put("requiresStaffReconciliation",job.getStatus()==JobStatus.RECOVERY_REQUIRED||uncertain||!owned||!fresh||!idle||active);
        }else physical.put("sessionAvailable",false).put("requiresStaffReconciliation",true);
        return result;
    }

    @Transactional
    public JsonNode reconcile(UUID jobId,UUID stepId,String key,MqttOperationRecoveryRequest request,AccountPrincipal principal,RequestMetadata metadata) {
        if(key==null || !key.matches("[A-Za-z0-9._:-]{1,150}"))throw new BusinessRuleException("Idempotency-Key must contain 1-150 safe characters");
        Job job=jobs.lockById(jobId).orElseThrow(()->new ResourceNotFoundException("Job not found"));Account actor=authorize(job,principal);
        JobStep step=steps.lockById(stepId).orElseThrow(()->new ResourceNotFoundException("JobStep not found"));
        if(!step.getJobTask().getJob().getJobId().equals(jobId))throw new AccessDeniedException("JobStep is outside this Job");
        JsonNode body=wire.tree(request);var previous=receipts.findByJobStepIdAndIdempotencyKey(stepId,key).orElse(null);
        if(previous!=null){em.refresh(previous,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
            if(!actor.getAccountId().equals(previous.getActorAccountId()) || !body.equals(previous.getRequestJson()))throw new ConflictException("Recovery key reused with different actor/body");
            return previous.getResponseJson().deepCopy();}
        // Physical stop may settle before the selected Pi journal recovery is reviewed.
        boolean cancelledRecovery=job.getStatus()==JobStatus.CANCELLED && (job.stopOutstanding()
                || step.getStatus()==JobStepStatus.RECOVERY_REQUIRED && job.getSelectedJobStep()!=null
                && stepId.equals(job.getSelectedJobStep().getJobStepId()));
        if(!job.isBeOrchestrated() || (!cancelledRecovery && (job.getStatus()!=JobStatus.RECOVERY_REQUIRED || job.getSelectedJobStep()==null || !stepId.equals(job.getSelectedJobStep().getJobStepId()))))
            throw new BusinessRuleException("Recovery requires the selected operation of a BE recovery Job");
        boolean rejectedOnly=request.evidence()==MqttOperationRecoveryRequest.Evidence.COMMAND_NOT_ACCEPTED;
        if(request.commandId()==null || !step.getOperationKey().equals(request.operationKey()) || (!rejectedOnly && !Boolean.TRUE.equals(request.confirmedStopped()))
                || request.outcome()==null || request.evidence()==null || request.note()==null || request.note().isBlank())throw new BusinessRuleException("Recovery requires operation identity, stop evidence and note");
        RobotCommandOutbox execute=commands.findByJobStepId(stepId).orElseThrow(()->new BusinessRuleException("No executed MQTT operation"));
        if(!execute.getCommandId().equals(request.commandId()))throw new BusinessRuleException("Recovery commandId does not match operation");
        robots.findByIdForUpdate(job.getRobot().getRobotId()).orElseThrow();var session=sessions.lock(job.getRobot().getRobotId()).orElse(null);Instant now=Instant.now(clock);
        if(rejectedOnly) {
            verifyRejected(job,step,execute,now);
        } else {
        if(!registry.owned(session) || !session.isHandshakeAcked() || !session.getTransportSessionId().equals(request.transportSessionId())
                || session.getLastHeartbeatAt()==null || !session.getLastHeartbeatAt().isAfter(now.minusSeconds(15))
                || session.getLastHeartbeatAt().isBefore(session.getHelloReceivedAt()))throw new BusinessRuleException("Recovery requires current HELLO and fresh heartbeat after HELLO");
        JsonNode heartbeat=session.getHeartbeatJson();
        if(heartbeat==null || !"IDLE".equals(heartbeat.path("status").asText()) || !heartbeat.path("activeJobStepId").isNull()
                || com.mediassistrover.backend.mapping.MappingLifecycleService.reportsOpen(heartbeat.path("mappingSession"))
                || com.mediassistrover.backend.mapping.MappingLifecycleService.reportsOpen(session.getHelloJson().path("mappingSession")))
            throw new BusinessRuleException("Pi still reports an active operation/mapping or error");
        JsonNode journal=null;for(JsonNode entry:session.getHelloJson().path("states")) {
            if(Set.of("ACCEPTED","RUNNING","RECOVERY_REQUIRED").contains(entry.path("status").asText()))
                throw new BusinessRuleException("HELLO journal must be terminal for every operation before robot recovery");
            JobStep known=steps.findById(UUID.fromString(entry.path("jobStepId").asText())).orElse(null);
            if(known==null || !known.getOperationKey().equals(entry.path("operationKey").asText())
                    || known.getJobTask().getJob().getRobot()==null || !known.getJobTask().getJob().getRobot().getRobotId().equals(job.getRobot().getRobotId()))
                throw new BusinessRuleException("Unknown/foreign journal operation must be reconciled locally first");
            if(step.getOperationKey().equals(entry.path("operationKey").asText()))journal=entry;
        }
        if(journal==null || !Set.of("SUCCEEDED","FAILED","TIMED_OUT","CANCELLED").contains(journal.path("status").asText()))
            throw new BusinessRuleException("HELLO journal must report this operation terminal; local Pi reconciliation is required first");
        boolean piStop=Boolean.TRUE.equals(execute.getPhysicalStopConfirmed());
        if(request.evidence()==MqttOperationRecoveryRequest.Evidence.PI_STOP_CONFIRMED && !piStop)
            throw new BusinessRuleException("No committed Pi stop confirmation for this operation");
        if(request.evidence()==MqttOperationRecoveryRequest.Evidence.STAFF_INSPECTED_STOP) {
            JsonNode receipt=execute.getResultEnvelopeJson();
            if(receipt==null || !"OPERATOR_RECONCILED".equals(receipt.path("messageType").asText()) || !"CANCELLED".equals(journal.path("status").asText())
                    || !inbox.findById(UUID.fromString(receipt.path("messageId").asText())).map(r->Boolean.TRUE.equals(r.getAccepted())).orElse(false))
                throw new BusinessRuleException("Staff inspection requires committed Pi OPERATOR_RECONCILED and CANCELLED journal");
        }
        }
        JobStatus status=job.getCancelRequestedAt()!=null || request.outcome()==JobRequests.ReconcileOutcome.CONFIRM_CANCELLED?JobStatus.CANCELLED:JobStatus.FAILED;
        if(Set.of(JobStepStatus.PENDING,JobStepStatus.RUNNING,JobStepStatus.RECOVERY_REQUIRED).contains(step.getStatus())) {
            step.setStatus(status==JobStatus.CANCELLED?JobStepStatus.CANCELLED:JobStepStatus.FAILED);step.setEndedAt(now);
        }
        mapping.reconciled(job);job.setStatus(status);job.setEndedAt(now);job.setReconciledByAccount(actor);job.setReconciledAt(now);job.setReconciliationNote(request.note());
        if(job.getServiceRequest()!=null){job.getServiceRequest().setStatus(ServiceRequestStatus.valueOf(status.name()));if(status==JobStatus.CANCELLED)job.getServiceRequest().setCancelledAt(now);else job.getServiceRequest().setCompletedAt(now);}
        for(JobTask task:tasks.findByJobJobIdOrderBySequenceNoAsc(jobId))if(task.getStartedAt()!=null && task.getEndedAt()==null){task.setStatus(status==JobStatus.CANCELLED?JobTaskStatus.CANCELLED:JobTaskStatus.FAILED);task.setEndedAt(now);}
        for(var command:commands.findByJobIdOrderByCreatedAt(jobId))if(command.getJobStepId().equals(stepId))command.setState("RESOLVED_BY_STAFF");
        if(job.getStopStatus()!=null) {
            boolean outstanding=commands.findByJobIdOrderByCreatedAt(jobId).stream().anyMatch(command ->
                "EXECUTE_STEP".equals(command.getCommandType()) && command.getSendAttempts()>0
                && !Boolean.TRUE.equals(command.getPhysicalStopConfirmed()) && !"RESOLVED_BY_STAFF".equals(command.getState())
                && !MqttJobSafety.completedWithoutPhysicalStop(command,job.getCancelRequestedAt()));
            job.setStopStatus(outstanding?JobStopStatus.RECOVERY_REQUIRED:JobStopStatus.CONFIRMED);
            if(!outstanding)job.setStopConfirmedAt(now);
        }
        // An inspection is separate evidence. Never set Pi hardware verified/physicalStopConfirmed from client data.
        if(!rejectedOnly) {job.getRobot().setStatus(RobotStatus.STARTING);session.setDispatchReady(false);session.setClosed(true);session.setFailureReason("Recovery closed; new HELLO/heartbeat required before dispatch");}
        UUID id=UUID.randomUUID();var response=wire.object().put("receiptId",id.toString()).put("jobId",jobId.toString()).put("jobStepId",stepId.toString())
                .put("status",status.name()).put("stepStatus",step.getStatus().name()).put("evidence",request.evidence().name()).put("actorAccountId",actor.getAccountId().toString()).put("requiresNewHello",!rejectedOnly);
        receipts.save(MqttOperationRecoveryReceipt.builder().receiptId(id).jobStepId(stepId).actorAccountId(actor.getAccountId()).idempotencyKey(key).requestJson(body).responseJson(response).createdAt(now).build());
        events.save(JobEvent.builder().jobEventId(UUID.randomUUID()).job(job).jobStep(step).jobTask(step.getJobTask()).robot(job.getRobot()).actorAccount(actor)
                .producerType(JobEventProducerType.valueOf(actor.getRole().name())).producerSessionId(UUID.randomUUID()).sequenceNo(1).eventType("MQTT_OPERATION_RECONCILED")
                .statusScope("JOB").previousStatus(JobStatus.RECOVERY_REQUIRED.name()).newStatus(status.name()).eventDataJson(body).occurredAt(now).receivedAt(now).persistedAt(now).build());
        audit.record(actor,"MQTT_OPERATION_RECONCILED","Job",jobId,null,Map.of("receipt",response),metadata);wakeups.request(job);updates.publishEvent(new RuntimeChanged(jobId));return response;
    }
    private void verifyRejected(Job job,JobStep step,RobotCommandOutbox command,Instant now) {
        if(!"REJECTED".equals(command.getState()) || command.getSendAttempts()!=1 || command.getAcknowledgedAt()!=null || step.getStartedAt()!=null
                || command.getExpiresAt()==null || !command.getExpiresAt().isBefore(now)
                || !"MOVE".equals(step.getStepTypeSnapshot()) || !"LOCALIZATION_NOT_READY".equals(step.getErrorCode()))
            throw new BusinessRuleException("Requires expired MOVE rejected before acceptance with LOCALIZATION_NOT_READY");
        var all=commands.findByJobIdOrderByCreatedAt(job.getJobId());
        if(all.size()!=1 || !all.getFirst().getCommandId().equals(command.getCommandId()))
            throw new BusinessRuleException("Other commands require normal operation recovery");
        var ackEvents=events.findByJobJobIdAndEventType(job.getJobId(),"COMMAND_ACK");
        boolean proved=false;
        for(var event:ackEvents) {
            var stored=inbox.findById(event.getJobEventId()).orElse(null);
            if(stored!=null && RejectedCommandEvidence.matches(stored,command)) proved=true;
        }
        if(!proved)throw new BusinessRuleException("No committed matching rejection ACK; cannot close as unexecuted");
    }
    private Account authorize(Job job,AccountPrincipal actor) {
        scopePolicy.requireJobAccess(job,actor);
        return scopePolicy.requireCurrentStaffOrAdmin(actor);
    }
}
