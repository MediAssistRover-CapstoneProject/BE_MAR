package com.mediassistrover.backend.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.mediassistrover.backend.dto.job.response.JobResponses;
import com.mediassistrover.backend.dto.request.response.ServiceRequestResponses;
import com.mediassistrover.backend.dto.schedule.response.ScheduleResponse;
import com.mediassistrover.backend.entity.Account;
import com.mediassistrover.backend.entity.FacilityMap;
import com.mediassistrover.backend.entity.Job;
import com.mediassistrover.backend.entity.JobEvent;
import com.mediassistrover.backend.entity.JobStep;
import com.mediassistrover.backend.entity.JobTask;
import com.mediassistrover.backend.entity.Robot;
import com.mediassistrover.backend.entity.Schedule;
import com.mediassistrover.backend.entity.ServiceRequest;
import com.mediassistrover.backend.entity.Step;
import com.mediassistrover.backend.entity.Task;
import com.mediassistrover.backend.entity.Workflow;
import com.mediassistrover.backend.enums.JobEventProducerType;
import com.mediassistrover.backend.enums.JobStatus;
import com.mediassistrover.backend.enums.JobStepStatus;
import com.mediassistrover.backend.enums.JobTaskStatus;
import com.mediassistrover.backend.enums.MisfirePolicy;
import com.mediassistrover.backend.enums.RequestSourceType;
import com.mediassistrover.backend.enums.ScheduleStatus;
import com.mediassistrover.backend.enums.ScheduleType;
import com.mediassistrover.backend.enums.ServiceRequestStatus;
import com.mediassistrover.backend.enums.ServiceRequestType;
import java.time.Instant;
import java.util.UUID;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-10-13T11:40:02+0700",
    comments = "version: 1.6.3, compiler: javac, environment: Java 21.0.12.1 (Eclipse Adoptium)"
)
@Component
public class RequestJobMapperImpl implements RequestJobMapper {

    @Override
    public ServiceRequestResponses.ServiceRequestResponse toResponse(ServiceRequest value) {
        if ( value == null ) {
            return null;
        }

        UUID requestedByAccountId = null;
        UUID requestedRobotId = null;
        UUID serviceRequestId = null;
        String requestCode = null;
        ServiceRequestType requestType = null;
        RequestSourceType sourceType = null;
        int priority = 0;
        ServiceRequestStatus status = null;
        String requestNote = null;
        JsonNode requestDataJson = null;
        String idempotencyKey = null;
        Instant requestedAt = null;
        Instant scheduledAt = null;
        Instant cancelledAt = null;
        Instant completedAt = null;
        long rowVersion = 0L;
        Instant updatedAt = null;

        requestedByAccountId = valueRequestedByAccountAccountId( value );
        requestedRobotId = valueRequestedRobotRobotId( value );
        serviceRequestId = value.getServiceRequestId();
        requestCode = value.getRequestCode();
        requestType = value.getRequestType();
        sourceType = value.getSourceType();
        priority = value.getPriority();
        status = value.getStatus();
        requestNote = value.getRequestNote();
        requestDataJson = value.getRequestDataJson();
        idempotencyKey = value.getIdempotencyKey();
        requestedAt = value.getRequestedAt();
        scheduledAt = value.getScheduledAt();
        cancelledAt = value.getCancelledAt();
        completedAt = value.getCompletedAt();
        rowVersion = value.getRowVersion();
        updatedAt = value.getUpdatedAt();

        ServiceRequestResponses.ServiceRequestResponse serviceRequestResponse = new ServiceRequestResponses.ServiceRequestResponse( serviceRequestId, requestedByAccountId, requestedRobotId, requestCode, requestType, sourceType, priority, status, requestNote, requestDataJson, idempotencyKey, requestedAt, scheduledAt, cancelledAt, completedAt, rowVersion, updatedAt );

        return serviceRequestResponse;
    }

    @Override
    public ScheduleResponse toResponse(Schedule value) {
        if ( value == null ) {
            return null;
        }

        UUID workflowId = null;
        UUID robotId = null;
        UUID createdByAccountId = null;
        UUID scheduleId = null;
        String scheduleCode = null;
        String scheduleName = null;
        ScheduleType scheduleType = null;
        String timezone = null;
        Instant startsAt = null;
        Instant endsAt = null;
        Integer intervalSec = null;
        String cronExpression = null;
        JsonNode inputTemplateJson = null;
        int priority = 0;
        MisfirePolicy misfirePolicy = null;
        ScheduleStatus status = null;
        Instant nextRunAt = null;
        Instant lastRunAt = null;
        long rowVersion = 0L;
        Instant createdAt = null;
        Instant updatedAt = null;

        workflowId = valueWorkflowWorkflowId( value );
        robotId = valueRobotRobotId( value );
        createdByAccountId = valueCreatedByAccountAccountId( value );
        scheduleId = value.getScheduleId();
        scheduleCode = value.getScheduleCode();
        scheduleName = value.getScheduleName();
        scheduleType = value.getScheduleType();
        timezone = value.getTimezone();
        startsAt = value.getStartsAt();
        endsAt = value.getEndsAt();
        intervalSec = value.getIntervalSec();
        cronExpression = value.getCronExpression();
        inputTemplateJson = value.getInputTemplateJson();
        priority = value.getPriority();
        misfirePolicy = value.getMisfirePolicy();
        status = value.getStatus();
        nextRunAt = value.getNextRunAt();
        lastRunAt = value.getLastRunAt();
        rowVersion = value.getRowVersion();
        createdAt = value.getCreatedAt();
        updatedAt = value.getUpdatedAt();

        ScheduleResponse scheduleResponse = new ScheduleResponse( scheduleId, workflowId, robotId, createdByAccountId, scheduleCode, scheduleName, scheduleType, timezone, startsAt, endsAt, intervalSec, cronExpression, inputTemplateJson, priority, misfirePolicy, status, nextRunAt, lastRunAt, rowVersion, createdAt, updatedAt );

        return scheduleResponse;
    }

    @Override
    public JobResponses.JobResponse toResponse(Job value) {
        if ( value == null ) {
            return null;
        }

        UUID workflowId = null;
        UUID serviceRequestId = null;
        UUID scheduleId = null;
        UUID robotId = null;
        UUID mapId = null;
        UUID assignedStaffAccountId = null;
        UUID selectedJobStepId = null;
        UUID jobId = null;
        String jobCode = null;
        JobStatus status = null;
        int priority = 0;
        JsonNode inputJson = null;
        JsonNode contextJson = null;
        JsonNode resultJson = null;
        String workflowHash = null;
        int protocolVersion = 0;
        int schemaVersion = 0;
        int graphVersion = 0;
        UUID dispatchCommandId = null;
        Instant dispatchExpiresAt = null;
        Instant queuedAt = null;
        Instant acceptedAt = null;
        Instant startedAt = null;
        UUID cancelCommandId = null;
        Instant cancelRequestedAt = null;
        Instant endedAt = null;
        String errorCode = null;
        String failureReason = null;
        Instant reconciledAt = null;
        String reconciliationNote = null;
        long rowVersion = 0L;
        boolean beOrchestrated = false;

        workflowId = valueWorkflowWorkflowId1( value );
        serviceRequestId = valueServiceRequestServiceRequestId( value );
        scheduleId = valueScheduleScheduleId( value );
        robotId = valueRobotRobotId1( value );
        mapId = valueMapMapId( value );
        assignedStaffAccountId = valueAssignedStaffAccountAccountId( value );
        selectedJobStepId = valueSelectedJobStepJobStepId( value );
        jobId = value.getJobId();
        jobCode = value.getJobCode();
        status = value.getStatus();
        priority = value.getPriority();
        inputJson = value.getInputJson();
        contextJson = value.getContextJson();
        resultJson = value.getResultJson();
        workflowHash = value.getWorkflowHash();
        protocolVersion = value.getProtocolVersion();
        schemaVersion = value.getSchemaVersion();
        graphVersion = value.getGraphVersion();
        dispatchCommandId = value.getDispatchCommandId();
        dispatchExpiresAt = value.getDispatchExpiresAt();
        queuedAt = value.getQueuedAt();
        acceptedAt = value.getAcceptedAt();
        startedAt = value.getStartedAt();
        cancelCommandId = value.getCancelCommandId();
        cancelRequestedAt = value.getCancelRequestedAt();
        endedAt = value.getEndedAt();
        errorCode = value.getErrorCode();
        failureReason = value.getFailureReason();
        reconciledAt = value.getReconciledAt();
        reconciliationNote = value.getReconciliationNote();
        rowVersion = value.getRowVersion();
        beOrchestrated = value.isBeOrchestrated();

        JobResponses.JobResponse jobResponse = new JobResponses.JobResponse( jobId, workflowId, serviceRequestId, scheduleId, robotId, mapId, assignedStaffAccountId, jobCode, status, priority, inputJson, contextJson, resultJson, workflowHash, protocolVersion, schemaVersion, graphVersion, dispatchCommandId, dispatchExpiresAt, queuedAt, acceptedAt, startedAt, cancelCommandId, cancelRequestedAt, endedAt, errorCode, failureReason, reconciledAt, reconciliationNote, rowVersion, beOrchestrated, selectedJobStepId );

        return jobResponse;
    }

    @Override
    public JobResponses.JobTaskResponse toResponse(JobTask value) {
        if ( value == null ) {
            return null;
        }

        UUID jobId = null;
        UUID taskId = null;
        UUID jobTaskId = null;
        int sequenceNo = 0;
        String taskNameSnapshot = null;
        JsonNode configSnapshotJson = null;
        JobTaskStatus status = null;
        Instant startedAt = null;
        Instant endedAt = null;
        String errorCode = null;
        String errorDetails = null;

        jobId = valueJobJobId( value );
        taskId = valueTaskTaskId( value );
        jobTaskId = value.getJobTaskId();
        sequenceNo = value.getSequenceNo();
        taskNameSnapshot = value.getTaskNameSnapshot();
        configSnapshotJson = value.getConfigSnapshotJson();
        status = value.getStatus();
        startedAt = value.getStartedAt();
        endedAt = value.getEndedAt();
        errorCode = value.getErrorCode();
        errorDetails = value.getErrorDetails();

        JobResponses.JobTaskResponse jobTaskResponse = new JobResponses.JobTaskResponse( jobTaskId, jobId, taskId, sequenceNo, taskNameSnapshot, configSnapshotJson, status, startedAt, endedAt, errorCode, errorDetails );

        return jobTaskResponse;
    }

    @Override
    public JobResponses.JobStepResponse toResponse(JobStep value) {
        if ( value == null ) {
            return null;
        }

        UUID jobTaskId = null;
        UUID stepId = null;
        UUID jobStepId = null;
        int visitNo = 0;
        long executionSequenceNo = 0L;
        String stepCodeSnapshot = null;
        String stepTypeSnapshot = null;
        JsonNode resolvedInputsJson = null;
        JsonNode resultJson = null;
        JobStepStatus status = null;
        int attemptNo = 0;
        String operationKey = null;
        int timeoutSecSnapshot = 0;
        Instant startedAt = null;
        Instant endedAt = null;
        String errorCode = null;
        JsonNode errorDetailsJson = null;
        long rowVersion = 0L;
        Instant selectedAt = null;
        String interruptionReason = null;
        Instant interruptionRequestedAt = null;

        jobTaskId = valueJobTaskJobTaskId( value );
        stepId = valueStepStepId( value );
        jobStepId = value.getJobStepId();
        visitNo = value.getVisitNo();
        executionSequenceNo = value.getExecutionSequenceNo();
        stepCodeSnapshot = value.getStepCodeSnapshot();
        stepTypeSnapshot = value.getStepTypeSnapshot();
        resolvedInputsJson = value.getResolvedInputsJson();
        resultJson = value.getResultJson();
        status = value.getStatus();
        attemptNo = value.getAttemptNo();
        operationKey = value.getOperationKey();
        timeoutSecSnapshot = value.getTimeoutSecSnapshot();
        startedAt = value.getStartedAt();
        endedAt = value.getEndedAt();
        errorCode = value.getErrorCode();
        errorDetailsJson = value.getErrorDetailsJson();
        rowVersion = value.getRowVersion();
        selectedAt = value.getSelectedAt();
        interruptionReason = value.getInterruptionReason();
        interruptionRequestedAt = value.getInterruptionRequestedAt();

        JobResponses.JobStepResponse jobStepResponse = new JobResponses.JobStepResponse( jobStepId, jobTaskId, stepId, visitNo, executionSequenceNo, stepCodeSnapshot, stepTypeSnapshot, resolvedInputsJson, resultJson, status, attemptNo, operationKey, timeoutSecSnapshot, startedAt, endedAt, errorCode, errorDetailsJson, rowVersion, selectedAt, interruptionReason, interruptionRequestedAt );

        return jobStepResponse;
    }

    @Override
    public JobResponses.JobEventResponse toResponse(JobEvent value) {
        if ( value == null ) {
            return null;
        }

        UUID jobId = null;
        UUID robotId = null;
        UUID jobTaskId = null;
        UUID jobStepId = null;
        UUID actorAccountId = null;
        UUID jobEventId = null;
        JobEventProducerType producerType = null;
        UUID producerSessionId = null;
        long sequenceNo = 0L;
        String eventType = null;
        String correlationKey = null;
        Integer attemptNo = null;
        String previousStatus = null;
        String newStatus = null;
        String statusScope = null;
        JsonNode eventDataJson = null;
        Instant occurredAt = null;
        Instant receivedAt = null;
        Instant persistedAt = null;

        jobId = valueJobJobId1( value );
        robotId = valueRobotRobotId2( value );
        jobTaskId = valueJobTaskJobTaskId1( value );
        jobStepId = valueJobStepJobStepId( value );
        actorAccountId = valueActorAccountAccountId( value );
        jobEventId = value.getJobEventId();
        producerType = value.getProducerType();
        producerSessionId = value.getProducerSessionId();
        sequenceNo = value.getSequenceNo();
        eventType = value.getEventType();
        correlationKey = value.getCorrelationKey();
        attemptNo = value.getAttemptNo();
        previousStatus = value.getPreviousStatus();
        newStatus = value.getNewStatus();
        statusScope = value.getStatusScope();
        eventDataJson = value.getEventDataJson();
        occurredAt = value.getOccurredAt();
        receivedAt = value.getReceivedAt();
        persistedAt = value.getPersistedAt();

        JobResponses.JobEventResponse jobEventResponse = new JobResponses.JobEventResponse( jobEventId, jobId, robotId, jobTaskId, jobStepId, actorAccountId, producerType, producerSessionId, sequenceNo, eventType, correlationKey, attemptNo, previousStatus, newStatus, statusScope, eventDataJson, occurredAt, receivedAt, persistedAt );

        return jobEventResponse;
    }

    private UUID valueRequestedByAccountAccountId(ServiceRequest serviceRequest) {
        Account requestedByAccount = serviceRequest.getRequestedByAccount();
        if ( requestedByAccount == null ) {
            return null;
        }
        return requestedByAccount.getAccountId();
    }

    private UUID valueRequestedRobotRobotId(ServiceRequest serviceRequest) {
        Robot requestedRobot = serviceRequest.getRequestedRobot();
        if ( requestedRobot == null ) {
            return null;
        }
        return requestedRobot.getRobotId();
    }

    private UUID valueWorkflowWorkflowId(Schedule schedule) {
        Workflow workflow = schedule.getWorkflow();
        if ( workflow == null ) {
            return null;
        }
        return workflow.getWorkflowId();
    }

    private UUID valueRobotRobotId(Schedule schedule) {
        Robot robot = schedule.getRobot();
        if ( robot == null ) {
            return null;
        }
        return robot.getRobotId();
    }

    private UUID valueCreatedByAccountAccountId(Schedule schedule) {
        Account createdByAccount = schedule.getCreatedByAccount();
        if ( createdByAccount == null ) {
            return null;
        }
        return createdByAccount.getAccountId();
    }

    private UUID valueWorkflowWorkflowId1(Job job) {
        Workflow workflow = job.getWorkflow();
        if ( workflow == null ) {
            return null;
        }
        return workflow.getWorkflowId();
    }

    private UUID valueServiceRequestServiceRequestId(Job job) {
        ServiceRequest serviceRequest = job.getServiceRequest();
        if ( serviceRequest == null ) {
            return null;
        }
        return serviceRequest.getServiceRequestId();
    }

    private UUID valueScheduleScheduleId(Job job) {
        Schedule schedule = job.getSchedule();
        if ( schedule == null ) {
            return null;
        }
        return schedule.getScheduleId();
    }

    private UUID valueRobotRobotId1(Job job) {
        Robot robot = job.getRobot();
        if ( robot == null ) {
            return null;
        }
        return robot.getRobotId();
    }

    private UUID valueMapMapId(Job job) {
        FacilityMap map = job.getMap();
        if ( map == null ) {
            return null;
        }
        return map.getMapId();
    }

    private UUID valueAssignedStaffAccountAccountId(Job job) {
        Account assignedStaffAccount = job.getAssignedStaffAccount();
        if ( assignedStaffAccount == null ) {
            return null;
        }
        return assignedStaffAccount.getAccountId();
    }

    private UUID valueSelectedJobStepJobStepId(Job job) {
        JobStep selectedJobStep = job.getSelectedJobStep();
        if ( selectedJobStep == null ) {
            return null;
        }
        return selectedJobStep.getJobStepId();
    }

    private UUID valueJobJobId(JobTask jobTask) {
        Job job = jobTask.getJob();
        if ( job == null ) {
            return null;
        }
        return job.getJobId();
    }

    private UUID valueTaskTaskId(JobTask jobTask) {
        Task task = jobTask.getTask();
        if ( task == null ) {
            return null;
        }
        return task.getTaskId();
    }

    private UUID valueJobTaskJobTaskId(JobStep jobStep) {
        JobTask jobTask = jobStep.getJobTask();
        if ( jobTask == null ) {
            return null;
        }
        return jobTask.getJobTaskId();
    }

    private UUID valueStepStepId(JobStep jobStep) {
        Step step = jobStep.getStep();
        if ( step == null ) {
            return null;
        }
        return step.getStepId();
    }

    private UUID valueJobJobId1(JobEvent jobEvent) {
        Job job = jobEvent.getJob();
        if ( job == null ) {
            return null;
        }
        return job.getJobId();
    }

    private UUID valueRobotRobotId2(JobEvent jobEvent) {
        Robot robot = jobEvent.getRobot();
        if ( robot == null ) {
            return null;
        }
        return robot.getRobotId();
    }

    private UUID valueJobTaskJobTaskId1(JobEvent jobEvent) {
        JobTask jobTask = jobEvent.getJobTask();
        if ( jobTask == null ) {
            return null;
        }
        return jobTask.getJobTaskId();
    }

    private UUID valueJobStepJobStepId(JobEvent jobEvent) {
        JobStep jobStep = jobEvent.getJobStep();
        if ( jobStep == null ) {
            return null;
        }
        return jobStep.getJobStepId();
    }

    private UUID valueActorAccountAccountId(JobEvent jobEvent) {
        Account actorAccount = jobEvent.getActorAccount();
        if ( actorAccount == null ) {
            return null;
        }
        return actorAccount.getAccountId();
    }
}
