package com.mediassistrover.backend.mqtt;

import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.exception.BusinessRuleException;
import com.mediassistrover.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class MqttInboxProcessor {
    private final RobotMqttInboxRepository inbox;
    private final JobRepository jobs;
    private final RobotRepository robots;
    private final RobotMqttSessionRepository sessions;
    private final RobotCommandOutboxRepository commands;
    private final MqttSessionRegistry registry;
    private final MqttRuntimeEvents runtime;
    private final MqttJobSafety safety;
    private final MqttWire wire;
    private final Clock clock;
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public MqttPublicationPlans.Publication process(UUID id) {
        RobotMqttInbox initial=inbox.findById(id).orElseThrow();MqttWire.Envelope e=wire.envelope((com.fasterxml.jackson.databind.node.ObjectNode)initial.getRawEnvelope());
        List<Job> active=jobs.lockRobotJobs(e.robotId(),List.of(JobStatus.QUEUED,JobStatus.ACCEPTED,JobStatus.RUNNING,JobStatus.RECOVERY_REQUIRED));
        UUID boundJob=e.jobId();
        if(boundJob==null && "COMMAND_ACK".equals(e.messageType())) {
            var command=commands.findById(UUID.fromString(e.payload().path("commandId").asText())).orElse(null);
            if(command!=null && "CANCEL_STEP".equals(command.getCommandType()))boundJob=command.getJobId();
        }
        UUID scopedJob=boundJob;
        Job job=boundJob==null?null:active.stream().filter(j->j.getJobId().equals(scopedJob)).findFirst()
                .orElseGet(()->jobs.lockById(scopedJob).orElseThrow(()->new BusinessRuleException("MQTT Job not found")));
        robots.findByIdForUpdate(e.robotId()).orElseThrow();
        RobotMqttSession session=sessions.lock(initial.getRobotId()).orElse(null);
        RobotMqttInbox row=initial;entityManager.refresh(row,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (!"PENDING".equals(row.getState())) return acknowledgement(row,session);
        if ("HELLO".equals(e.messageType())) {
            if (e.jobId()!=null || e.jobStepId()!=null) throw new BusinessRuleException("HELLO cannot bind a JobStep");
            registry.hello(e,active);row.setResponseJson(wire.helloAck(e.robotId(),e.transportSessionId(),true));
        } else {
            e=wire.envelope((com.fasterxml.jackson.databind.node.ObjectNode)row.getRawEnvelope());
            // Session was already locked above in the shared Job -> Robot -> Session order.
            if(!registry.owned(session)||!e.transportSessionId().equals(session.getTransportSessionId()))
                throw new MqttSessionUnavailableException("Stale MQTT session");
            runtime.apply(job,e);row.setResponseJson(wire.eventAck(e.robotId(),e.transportSessionId(),id,true));
        }
        row.setAccepted(true);row.setState("PROCESSED");row.setProcessedAt(Instant.now(clock));
        return acknowledgement(row,session);
    }
    /** Business rejection follows rollback of the processing transaction; infrastructure failures remain PENDING. */
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public MqttPublicationPlans.Publication reject(UUID id,String reason) {
        RobotMqttInbox initial=inbox.findById(id).orElseThrow();
        List<Job> active=jobs.lockRobotJobs(initial.getRobotId(),List.of(JobStatus.QUEUED,JobStatus.ACCEPTED,JobStatus.RUNNING,JobStatus.RECOVERY_REQUIRED));
        robots.findByIdForUpdate(initial.getRobotId()).orElseThrow();
        RobotMqttSession session=sessions.lock(initial.getRobotId()).orElse(null);
        RobotMqttInbox row=initial;entityManager.refresh(row,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (!"PENDING".equals(row.getState())) return acknowledgement(row,session);
        if (!"HELLO".equals(row.getMessageType())) for (Job job:active) safety.recovery(job,"Rejected MQTT event: "+reason);
        row.setAccepted(false);row.setState("REJECTED");row.setFailureReason(reason.length()>2000?reason.substring(0,2000):reason);row.setProcessedAt(Instant.now(clock));
        row.setResponseJson("HELLO".equals(row.getMessageType())?wire.helloAck(row.getRobotId(),row.getTransportSessionId(),false):wire.eventAck(row.getRobotId(),row.getTransportSessionId(),id,false));
        return acknowledgement(row,session);
    }
    /** The proxy returns this immutable ACK only AFTER the transaction commits. */
    private MqttPublicationPlans.Publication acknowledgement(RobotMqttInbox row,RobotMqttSession session) {
        // HELLO can create/replace the locked session; use the existing durable lookup there.
        if("HELLO".equals(row.getMessageType()) || row.getResponseJson()==null || !registry.owned(session)
                || !row.getTransportSessionId().equals(session.getTransportSessionId()))return null;
        return new MqttPublicationPlans.Publication(row.getMessageId(),row.getRobotId(),row.getTransportSessionId(),row.getResponseJson().deepCopy());
    }
    /** Preserve the original receipt, but stop graph advancement after conflicting producer identity. */
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void protocolConflict(UUID robotId) {
        List<Job> active=jobs.lockRobotJobs(robotId,List.of(JobStatus.QUEUED,JobStatus.ACCEPTED,JobStatus.RUNNING,JobStatus.RECOVERY_REQUIRED));
        robots.findByIdForUpdate(robotId).orElseThrow();sessions.lock(robotId);
        for(Job job:active)safety.recovery(job,"MQTT identity reused with conflicting business content");
    }
}
