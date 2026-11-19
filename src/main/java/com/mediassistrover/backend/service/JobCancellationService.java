package com.mediassistrover.backend.service;

import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.repository.*;
import com.mediassistrover.backend.mqtt.MqttOperationControlService;
import com.mediassistrover.backend.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

/** Logical cancellation never substitutes for physical stop evidence. Caller owns the Job lock. */
@Service @RequiredArgsConstructor
public class JobCancellationService {
    private final JobRepository jobs;
    private final JobStepRepository steps;
    private final JobTaskRepository tasks;
    private final RobotRepository robots;
    private final RobotCommandOutboxRepository commands;
    private final RobotOperationAuthorizationRepository authorizations;
    private final MqttOperationControlService control;
    private final com.mediassistrover.backend.mapping.MappingLifecycleService mapping;
    private final AuditService audit;
    private final org.springframework.context.ApplicationEventPublisher events;
    private final Clock clock;
    @org.springframework.beans.factory.annotation.Autowired private com.mediassistrover.backend.mqtt.MqttStepDispatchService dispatch;

    @Transactional
    public void cancel(Job job,Account actor,String reason,RequestMetadata metadata) {
        if(job.getStatus()==JobStatus.CANCELLED && job.getStopStatus()!=null) {
            settleObsoleteNonphysicalCancellations(job,actor,reason,metadata);
            return;
        }
        if(Set.of(JobStatus.SUCCEEDED,JobStatus.FAILED).contains(job.getStatus()))
            throw new BusinessRuleException("Job is already terminal");
        String before=job.getStatus().name();Instant now=clock.instant();
        if(job.getRobot()!=null)robots.findByIdForUpdate(job.getRobot().getRobotId()).orElseThrow();
        job.setStatus(JobStatus.CANCELLED);job.setCancelRequestedAt(now);job.setEndedAt(now);
        job.setCancelRequestedByAccount(actor);job.setCancelCommandId(UUID.randomUUID());
        job.setResumeRequested(false);job.setStopRequestedAt(now);job.setStopStatus(JobStopStatus.PENDING);
        if(!job.isBeOrchestrated() && !"QUEUED".equals(before))job.setStopStatus(JobStopStatus.RECOVERY_REQUIRED);
        if(job.getServiceRequest()!=null) {
            job.getServiceRequest().setStatus(ServiceRequestStatus.CANCELLED);
            job.getServiceRequest().setCancelledAt(now);job.getServiceRequest().setCompletedAt(now);
        }
        if(mapping.open(job.getJobId())) {
            try {mapping.deferTerminal(job,TerminalStatus.CANCELLED);}
            catch(BusinessRuleException unavailable) {job.setStopStatus(JobStopStatus.RECOVERY_REQUIRED);}
        }
        for(var grant:authorizations.findByVerificationJobJobId(job.getJobId()))grant.setValidUntil(now);
        for(var step:steps.findByJobId(job.getJobId())) {
            if(step.isMappingCleanup())continue;
            var execute=commands.findByJobStepId(step.getJobStepId()).orElse(null);
            if(execute!=null && execute.getSendAttempts()>0 && !settled(execute,job)) {
                step.setInterruptionReason("JOB_CANCEL");step.setInterruptionRequestedAt(now);
                execute.setInterruptionReason("JOB_CANCEL");
            } else if(Set.of(JobStepStatus.PENDING,JobStepStatus.RUNNING,JobStepStatus.RETRYING).contains(step.getStatus())) {
                step.setStatus(JobStepStatus.CANCELLED);step.setEndedAt(now);
            }
            if(execute!=null && execute.getSendAttempts()==0) {
                execute.setState("ABANDONED");execute.setPhysicalStopConfirmed(true);
            }
        }
        for(var task:tasks.findByJobJobIdOrderBySequenceNoAsc(job.getJobId()))
            if(Set.of(JobTaskStatus.PENDING,JobTaskStatus.RUNNING).contains(task.getStatus())) {
                task.setStatus(JobTaskStatus.CANCELLED);task.setEndedAt(now);
            }
        advanceLocked(job);
        audit.record(actor,"JOB_CANCELLED","Job",job.getJobId(),Map.of("status",before),
                Map.of("status","CANCELLED","reason",reason,"stopStatus",job.getStopStatus().name()),metadata);
        events.publishEvent(new RuntimeChanged(job.getJobId()));
    }

    @Transactional
    public void advance(UUID id) {
        Job job=jobs.lockById(id).orElseThrow();
        if(job.getStatus()!=JobStatus.CANCELLED || !job.stopOutstanding())return;
        if(job.getRobot()!=null)robots.findByIdForUpdate(job.getRobot().getRobotId()).orElseThrow();
        advanceLocked(job);
    }

    private void advanceLocked(Job job) {
        boolean sent=false, pending=false;
        for(var command:commands.findByJobIdOrderByCreatedAt(job.getJobId())) {
            if(!"EXECUTE_STEP".equals(command.getCommandType()))continue;
            var visit=steps.findById(command.getJobStepId()).orElse(null);
            if(visit!=null && visit.isMappingCleanup()) {
                sent|=command.getSendAttempts()>0;
                if(!settled(command,job)){control.handle(job,visit);pending=!settled(command,job)||pending;}
                continue;
            }
            if(command.getSendAttempts()==0) {
                command.setState("ABANDONED");command.setPhysicalStopConfirmed(true);continue;
            }
            sent=true;
            if(settled(command,job))continue;
            JobStep step=steps.findById(command.getJobStepId()).orElseThrow();
            step.setInterruptionReason("JOB_CANCEL");
            if(step.getInterruptionRequestedAt()==null)step.setInterruptionRequestedAt(clock.instant());
            control.handle(job,step);
            if(!settled(command,job))pending=true;
        }
        if(mapping.open(job.getJobId())) {
            var cleanup=job.getSelectedJobStep();
            if(!pending && cleanup!=null && cleanup.isMappingCleanup() && cleanup.getStatus()==JobStepStatus.PENDING)dispatch.dispatch(cleanup);
            if(cleanup==null || !cleanup.isMappingCleanup() || Set.of(JobStepStatus.FAILED,JobStepStatus.TIMED_OUT,JobStepStatus.RECOVERY_REQUIRED).contains(cleanup.getStatus()))
                job.setStopStatus(JobStopStatus.RECOVERY_REQUIRED);
            pending=true;
        }
        // Legacy transports and open mapping cannot be declared stopped without their recovery evidence.
        if(!job.isBeOrchestrated() && (job.getStartedAt()!=null || job.getStopStatus()==JobStopStatus.RECOVERY_REQUIRED)) {
            pending=true;job.setStopStatus(JobStopStatus.RECOVERY_REQUIRED);
        }
        if(pending) {
            if(job.getRobot()!=null)job.getRobot().setStatus(job.getStopStatus()==JobStopStatus.RECOVERY_REQUIRED?RobotStatus.ERROR:RobotStatus.BUSY);
        } else {
            job.setStopStatus(sent?JobStopStatus.CONFIRMED:JobStopStatus.NOT_REQUIRED);
            if(sent)job.setStopConfirmedAt(clock.instant());
            if(job.getRobot()!=null) {
                var others=jobs.findByRobotRobotIdAndStatusIn(job.getRobot().getRobotId(),JobOccupancyPolicy.EXECUTING);
                if(others.stream().noneMatch(j->!j.getJobId().equals(job.getJobId())) && job.getRobot().getStatus()==RobotStatus.BUSY)
                    job.getRobot().setStatus(RobotStatus.IDLE);
            }
        }
        events.publishEvent(new RuntimeChanged(job.getJobId()));
    }

    private static boolean settled(RobotCommandOutbox command,Job job) {
        return Boolean.TRUE.equals(command.getPhysicalStopConfirmed()) || "RESOLVED_BY_STAFF".equals(command.getState())
                || com.mediassistrover.backend.mqtt.MqttJobSafety.completedWithoutPhysicalStop(command,job.getCancelRequestedAt());
    }

    /** Retire old cancellation bookkeeping without manufacturing Pi physical-stop evidence. */
    private void settleObsoleteNonphysicalCancellations(Job job,Account actor,String reason,RequestMetadata metadata) {
        if(!job.isBeOrchestrated() || job.getRobot()==null || job.getCancelRequestedAt()==null
                || !Set.of(JobStopStatus.CONFIRMED,JobStopStatus.NOT_REQUIRED).contains(job.getStopStatus()))return;
        robots.findByIdForUpdate(job.getRobot().getRobotId()).orElseThrow();
        boolean changed=false;
        for(var candidate:commands.findByJobIdOrderByCreatedAt(job.getJobId())) {
            if(!"CANCEL_STEP".equals(candidate.getCommandType())
                    || !Set.of("RECOVERY_REQUIRED","REJECTED").contains(candidate.getState()))continue;
            var cancel=commands.lock(candidate.getCommandId()).orElseThrow();
            var selected=commands.findByJobStepId(cancel.getJobStepId()).orElse(null);
            if(selected==null)continue;
            var execute=commands.lock(selected.getCommandId()).orElseThrow();
            var step=steps.lockById(cancel.getJobStepId()).orElse(null);
            if(step==null || step.getStatus()!=JobStepStatus.SUCCEEDED
                    || !step.getJobTask().getJob().getJobId().equals(job.getJobId())
                    || !Set.of("WAIT","INTERACT").contains(step.getStepTypeSnapshot())
                    || !job.getJobId().equals(cancel.getJobId()) || !job.getJobId().equals(execute.getJobId())
                    || !job.getRobot().getRobotId().equals(cancel.getRobotId()) || !cancel.getRobotId().equals(execute.getRobotId())
                    || !step.getJobStepId().equals(execute.getJobStepId())
                    || !step.getOperationKey().equals(cancel.getOperationKey()) || !step.getOperationKey().equals(execute.getOperationKey())
                    || !"JOB_CANCEL".equals(cancel.getInterruptionReason()) || !"JOB_CANCEL".equals(execute.getInterruptionReason())
                    || cancel.getExpiresAt()==null || cancel.getExpiresAt().isAfter(clock.instant())
                    || !com.mediassistrover.backend.mqtt.MqttJobSafety.completedWithoutPhysicalStop(execute,job.getCancelRequestedAt())
                    || !step.getStepTypeSnapshot().equals(execute.getPayloadJson().path("step").path("stepType").asText()))continue;
            String previous=cancel.getState();
            // Keep both original envelopes and nullable physicalStopConfirmed values for audit.
            cancel.setState("RESOLVED_BY_STAFF");
            audit.record(actor,"MQTT_OBSOLETE_CANCEL_RECONCILED","RobotCommandOutbox",cancel.getCommandId(),
                    Map.of("state",previous),Map.of("state","RESOLVED_BY_STAFF","reason",reason,
                            "executeCommandId",execute.getCommandId(),"jobId",job.getJobId(),
                            "jobStepId",step.getJobStepId(),"operationKey",step.getOperationKey(),
                            "successMessageId",execute.getResultEnvelopeJson().path("messageId").asText()),metadata);
            changed=true;
        }
        if(changed)events.publishEvent(new RuntimeChanged(job.getJobId()));
    }
}
