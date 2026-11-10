package com.mediassistrover.backend.mqtt;

import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.repository.JobEventRepository;
import com.mediassistrover.backend.repository.JobTaskRepository;
import com.mediassistrover.backend.repository.RobotCommandOutboxRepository;
import com.mediassistrover.backend.service.OrchestrationWorkService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;

/** Called with the Job lock held. No recovery path releases a potentially moving robot. */
@Service @RequiredArgsConstructor
public class MqttJobSafety {
    private final JobEventRepository events;
    private final JobTaskRepository tasks;
    private final RobotCommandOutboxRepository commands;
    private final OrchestrationWorkService wakeups;
    private final MqttWire wire;
    private final Clock clock;
    private final org.springframework.context.ApplicationEventPublisher updates;

    /** The caller holds the Job lock and has verified that no mapping session is open. */
    public void failSettledRecovery(Job job) {
        if (!job.isBeOrchestrated() || job.getStatus()!=JobStatus.RECOVERY_REQUIRED) return;
        var history=commands.findByJobIdOrderByCreatedAt(job.getJobId());
        if (!history.stream().allMatch(MqttJobSafety::settledForDisconnect)) return;
        JobStep selected=job.getSelectedJobStep();
        if (selected!=null && Set.of(JobStepStatus.RUNNING,JobStepStatus.RECOVERY_REQUIRED).contains(selected.getStatus())
                && Set.of("MOVE","CONTROL_HARDWARE","EXECUTE_SENSING").contains(selected.getStepTypeSnapshot())
                && history.stream().noneMatch(c->selected.getJobStepId().equals(c.getJobStepId())
                        && "EXECUTE_STEP".equals(c.getCommandType()))) return;

        Instant now=Instant.now(clock);
        String note="MQTT session ended with settled commands and no open mapping session";
        job.setStatus(JobStatus.FAILED);
        job.setEndedAt(now);
        job.setReconciledAt(now);
        job.setReconciliationNote(note);
        if (selected!=null && Set.of(JobStepStatus.PENDING,JobStepStatus.RUNNING,JobStepStatus.RETRYING,JobStepStatus.RECOVERY_REQUIRED)
                .contains(selected.getStatus())) {
            selected.setStatus(JobStepStatus.FAILED);
            selected.setEndedAt(now);
            if (selected.getErrorCode()==null) selected.setErrorCode(job.getErrorCode());
        }
        for (JobTask task : tasks.findByJobJobIdOrderBySequenceNoAsc(job.getJobId())) {
            if (task.getStatus()==JobTaskStatus.RUNNING) {
                task.setStatus(JobTaskStatus.FAILED);
                task.setEndedAt(now);
            } else if (task.getStatus()==JobTaskStatus.PENDING) {
                task.setStatus(JobTaskStatus.NOT_VISITED);
            }
        }
        if (job.getServiceRequest()!=null) {
            job.getServiceRequest().setStatus(ServiceRequestStatus.FAILED);
            job.getServiceRequest().setCompletedAt(now);
        }
        events.save(JobEvent.builder().jobEventId(UUID.randomUUID()).job(job).robot(job.getRobot())
                .producerType(JobEventProducerType.SERVER).producerSessionId(UUID.randomUUID()).sequenceNo(1)
                .eventType("MQTT_RECOVERY_AUTO_FAILED").statusScope("JOB")
                .previousStatus(JobStatus.RECOVERY_REQUIRED.name()).newStatus(JobStatus.FAILED.name())
                .eventDataJson(wire.object().put("reason",note)).occurredAt(now).receivedAt(now).persistedAt(now).build());
        wakeups.request(job);
        updates.publishEvent(new com.mediassistrover.backend.service.RuntimeChanged(job.getJobId()));
    }

    private static boolean settledForDisconnect(RobotCommandOutbox command) {
        String state=command.getState();
        if (command.getSendAttempts()==0) return Set.of("ABANDONED","EXPIRED").contains(state);
        boolean stopped=Boolean.TRUE.equals(command.getPhysicalStopConfirmed());
        if ("CANCEL_STEP".equals(command.getCommandType())) return "STOP_CONFIRMED".equals(state) && stopped;
        if (!"EXECUTE_STEP".equals(command.getCommandType()) || !"COMPLETED".equals(state)) return false;
        if (command.getInterruptionReason()!=null && !stopped) return false;
        var step=command.getPayloadJson().path("step");
        return switch (step.path("stepType").asText()) {
            case "MOVE" -> stopped;
            case "EXECUTE_SENSING" -> !"MAPPING".equals(step.path("params").path("sensing_type").asText()) || stopped;
            case "WAIT", "INTERACT" -> true;
            // A sent servo command requires review of the compartment state.
            default -> false;
        };
    }

    public void recovery(Job job,String reason) {
        if (!job.isBeOrchestrated() || Set.of(JobStatus.SUCCEEDED,JobStatus.FAILED,JobStatus.CANCELLED).contains(job.getStatus())) return;
        boolean changed=job.getStatus()!=JobStatus.RECOVERY_REQUIRED;
        job.setStatus(JobStatus.RECOVERY_REQUIRED);job.setErrorCode("MQTT_RECOVERY_REQUIRED");job.setFailureReason(reason);
        if (job.getRobot()!=null) job.getRobot().setStatus(RobotStatus.ERROR);
        if (job.getServiceRequest()!=null) job.getServiceRequest().setStatus(ServiceRequestStatus.RECOVERY_REQUIRED);
        if (changed) {
            Instant now=Instant.now(clock);
            events.save(JobEvent.builder().jobEventId(UUID.randomUUID()).job(job).robot(job.getRobot()).producerType(JobEventProducerType.SERVER)
                    .producerSessionId(UUID.randomUUID()).sequenceNo(1).eventType("MQTT_RECOVERY_REQUIRED").statusScope("JOB")
                    .newStatus(JobStatus.RECOVERY_REQUIRED.name()).eventDataJson(wire.object().put("reason",reason)).occurredAt(now).receivedAt(now).persistedAt(now).build());
        }
        wakeups.request(job);
        updates.publishEvent(new com.mediassistrover.backend.service.RuntimeChanged(job.getJobId()));
    }
}
