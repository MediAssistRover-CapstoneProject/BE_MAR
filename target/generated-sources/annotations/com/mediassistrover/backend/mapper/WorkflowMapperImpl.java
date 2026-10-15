package com.mediassistrover.backend.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.mediassistrover.backend.dto.workflow.request.WorkflowRequests;
import com.mediassistrover.backend.dto.workflow.response.WorkflowResponses;
import com.mediassistrover.backend.entity.Account;
import com.mediassistrover.backend.entity.Step;
import com.mediassistrover.backend.entity.StepTransition;
import com.mediassistrover.backend.entity.Task;
import com.mediassistrover.backend.entity.Workflow;
import com.mediassistrover.backend.enums.StepAuditLevel;
import com.mediassistrover.backend.enums.StepType;
import com.mediassistrover.backend.enums.TerminalStatus;
import com.mediassistrover.backend.enums.TransitionOutcome;
import com.mediassistrover.backend.enums.WorkflowStatus;
import java.time.Instant;
import java.util.UUID;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-10-13T11:40:03+0700",
    comments = "version: 1.6.3, compiler: javac, environment: Java 21.0.12.1 (Eclipse Adoptium)"
)
@Component
public class WorkflowMapperImpl implements WorkflowMapper {

    @Override
    public WorkflowResponses.WorkflowResponse toResponse(Workflow entity) {
        if ( entity == null ) {
            return null;
        }

        UUID startStepId = null;
        UUID publishedByAccountId = null;
        UUID workflowId = null;
        String workflowCode = null;
        int versionNo = 0;
        String workflowName = null;
        String serviceType = null;
        String description = null;
        JsonNode inputSchemaJson = null;
        JsonNode inputUiJson = null;
        JsonNode defaultInputsJson = null;
        JsonNode outputMappingJson = null;
        JsonNode requiredCapabilitiesJson = null;
        int maxDurationSec = 0;
        int maxTotalVisits = 0;
        int graphVersion = 0;
        int schemaVersion = 0;
        String definitionHash = null;
        WorkflowStatus status = null;
        boolean acceptsNewJobs = false;
        Instant publishedAt = null;
        long rowVersion = 0L;
        Instant createdAt = null;
        Instant updatedAt = null;

        startStepId = entityStartStepStepId( entity );
        publishedByAccountId = entityPublishedByAccountAccountId( entity );
        workflowId = entity.getWorkflowId();
        workflowCode = entity.getWorkflowCode();
        versionNo = entity.getVersionNo();
        workflowName = entity.getWorkflowName();
        serviceType = entity.getServiceType();
        description = entity.getDescription();
        inputSchemaJson = entity.getInputSchemaJson();
        inputUiJson = entity.getInputUiJson();
        defaultInputsJson = entity.getDefaultInputsJson();
        outputMappingJson = entity.getOutputMappingJson();
        requiredCapabilitiesJson = entity.getRequiredCapabilitiesJson();
        maxDurationSec = entity.getMaxDurationSec();
        maxTotalVisits = entity.getMaxTotalVisits();
        graphVersion = entity.getGraphVersion();
        schemaVersion = entity.getSchemaVersion();
        definitionHash = entity.getDefinitionHash();
        status = entity.getStatus();
        acceptsNewJobs = entity.isAcceptsNewJobs();
        publishedAt = entity.getPublishedAt();
        rowVersion = entity.getRowVersion();
        createdAt = entity.getCreatedAt();
        updatedAt = entity.getUpdatedAt();

        WorkflowResponses.WorkflowResponse workflowResponse = new WorkflowResponses.WorkflowResponse( workflowId, startStepId, publishedByAccountId, workflowCode, versionNo, workflowName, serviceType, description, inputSchemaJson, inputUiJson, defaultInputsJson, outputMappingJson, requiredCapabilitiesJson, maxDurationSec, maxTotalVisits, graphVersion, schemaVersion, definitionHash, status, acceptsNewJobs, publishedAt, rowVersion, createdAt, updatedAt );

        return workflowResponse;
    }

    @Override
    public WorkflowResponses.TaskResponse toResponse(Task entity) {
        if ( entity == null ) {
            return null;
        }

        UUID workflowId = null;
        UUID taskId = null;
        String taskCode = null;
        String taskName = null;
        int sequenceNo = 0;
        String description = null;
        JsonNode configJson = null;
        Instant createdAt = null;

        workflowId = entityWorkflowWorkflowId( entity );
        taskId = entity.getTaskId();
        taskCode = entity.getTaskCode();
        taskName = entity.getTaskName();
        sequenceNo = entity.getSequenceNo();
        description = entity.getDescription();
        configJson = entity.getConfigJson();
        createdAt = entity.getCreatedAt();

        WorkflowResponses.TaskResponse taskResponse = new WorkflowResponses.TaskResponse( taskId, workflowId, taskCode, taskName, sequenceNo, description, configJson, createdAt );

        return taskResponse;
    }

    @Override
    public WorkflowResponses.StepResponse toResponse(Step entity) {
        if ( entity == null ) {
            return null;
        }

        UUID workflowId = null;
        UUID taskId = null;
        String stepTypeCode = null;
        UUID stepId = null;
        String stepCode = null;
        String stepName = null;
        int sequenceNo = 0;
        String description = null;
        boolean enabled = false;
        JsonNode paramsJson = null;
        JsonNode inputMappingJson = null;
        int timeoutSec = 0;
        int maxAttempts = 0;
        int retryDelaySec = 0;
        JsonNode retryCodesJson = null;
        int maxVisits = 0;
        StepAuditLevel auditLevel = null;
        Instant createdAt = null;

        workflowId = entityWorkflowWorkflowId1( entity );
        taskId = entityTaskTaskId( entity );
        stepTypeCode = entityStepTypeStepTypeCode( entity );
        stepId = entity.getStepId();
        stepCode = entity.getStepCode();
        stepName = entity.getStepName();
        sequenceNo = entity.getSequenceNo();
        description = entity.getDescription();
        enabled = entity.isEnabled();
        paramsJson = entity.getParamsJson();
        inputMappingJson = entity.getInputMappingJson();
        timeoutSec = entity.getTimeoutSec();
        maxAttempts = entity.getMaxAttempts();
        retryDelaySec = entity.getRetryDelaySec();
        retryCodesJson = entity.getRetryCodesJson();
        maxVisits = entity.getMaxVisits();
        auditLevel = entity.getAuditLevel();
        createdAt = entity.getCreatedAt();

        WorkflowResponses.StepResponse stepResponse = new WorkflowResponses.StepResponse( stepId, workflowId, taskId, stepTypeCode, stepCode, stepName, sequenceNo, description, enabled, paramsJson, inputMappingJson, timeoutSec, maxAttempts, retryDelaySec, retryCodesJson, maxVisits, auditLevel, createdAt );

        return stepResponse;
    }

    @Override
    public WorkflowResponses.TransitionResponse toResponse(StepTransition entity) {
        if ( entity == null ) {
            return null;
        }

        UUID workflowId = null;
        UUID fromStepId = null;
        UUID toStepId = null;
        UUID transitionId = null;
        TransitionOutcome outcome = null;
        JsonNode conditionJson = null;
        int priority = 0;
        boolean defaultTransition = false;
        TerminalStatus terminalStatus = null;

        workflowId = entityWorkflowWorkflowId2( entity );
        fromStepId = entityFromStepStepId( entity );
        toStepId = entityToStepStepId( entity );
        transitionId = entity.getTransitionId();
        outcome = entity.getOutcome();
        conditionJson = entity.getConditionJson();
        priority = entity.getPriority();
        defaultTransition = entity.isDefaultTransition();
        terminalStatus = entity.getTerminalStatus();

        WorkflowResponses.TransitionResponse transitionResponse = new WorkflowResponses.TransitionResponse( transitionId, workflowId, fromStepId, toStepId, outcome, conditionJson, priority, defaultTransition, terminalStatus );

        return transitionResponse;
    }

    @Override
    public Workflow toEntity(WorkflowRequests.WorkflowUpsertRequest request) {
        if ( request == null ) {
            return null;
        }

        Workflow.WorkflowBuilder workflow = Workflow.builder();

        workflow.workflowCode( request.workflowCode() );
        workflow.versionNo( request.versionNo() );
        workflow.workflowName( request.workflowName() );
        workflow.serviceType( request.serviceType() );
        workflow.description( request.description() );
        workflow.inputSchemaJson( request.inputSchemaJson() );
        workflow.inputUiJson( request.inputUiJson() );
        workflow.defaultInputsJson( request.defaultInputsJson() );
        workflow.outputMappingJson( request.outputMappingJson() );
        workflow.requiredCapabilitiesJson( request.requiredCapabilitiesJson() );
        workflow.maxDurationSec( request.maxDurationSec() );
        workflow.maxTotalVisits( request.maxTotalVisits() );
        workflow.graphVersion( request.graphVersion() );
        workflow.schemaVersion( request.schemaVersion() );

        return workflow.build();
    }

    @Override
    public void update(WorkflowRequests.WorkflowUpsertRequest request, Workflow entity) {
        if ( request == null ) {
            return;
        }

        if ( request.workflowCode() != null ) {
            entity.setWorkflowCode( request.workflowCode() );
        }
        entity.setVersionNo( request.versionNo() );
        if ( request.workflowName() != null ) {
            entity.setWorkflowName( request.workflowName() );
        }
        if ( request.serviceType() != null ) {
            entity.setServiceType( request.serviceType() );
        }
        if ( request.description() != null ) {
            entity.setDescription( request.description() );
        }
        if ( request.inputSchemaJson() != null ) {
            entity.setInputSchemaJson( request.inputSchemaJson() );
        }
        if ( request.inputUiJson() != null ) {
            entity.setInputUiJson( request.inputUiJson() );
        }
        if ( request.defaultInputsJson() != null ) {
            entity.setDefaultInputsJson( request.defaultInputsJson() );
        }
        if ( request.outputMappingJson() != null ) {
            entity.setOutputMappingJson( request.outputMappingJson() );
        }
        if ( request.requiredCapabilitiesJson() != null ) {
            entity.setRequiredCapabilitiesJson( request.requiredCapabilitiesJson() );
        }
        entity.setMaxDurationSec( request.maxDurationSec() );
        entity.setMaxTotalVisits( request.maxTotalVisits() );
        entity.setGraphVersion( request.graphVersion() );
        entity.setSchemaVersion( request.schemaVersion() );
    }

    private UUID entityStartStepStepId(Workflow workflow) {
        Step startStep = workflow.getStartStep();
        if ( startStep == null ) {
            return null;
        }
        return startStep.getStepId();
    }

    private UUID entityPublishedByAccountAccountId(Workflow workflow) {
        Account publishedByAccount = workflow.getPublishedByAccount();
        if ( publishedByAccount == null ) {
            return null;
        }
        return publishedByAccount.getAccountId();
    }

    private UUID entityWorkflowWorkflowId(Task task) {
        Workflow workflow = task.getWorkflow();
        if ( workflow == null ) {
            return null;
        }
        return workflow.getWorkflowId();
    }

    private UUID entityWorkflowWorkflowId1(Step step) {
        Workflow workflow = step.getWorkflow();
        if ( workflow == null ) {
            return null;
        }
        return workflow.getWorkflowId();
    }

    private UUID entityTaskTaskId(Step step) {
        Task task = step.getTask();
        if ( task == null ) {
            return null;
        }
        return task.getTaskId();
    }

    private String entityStepTypeStepTypeCode(Step step) {
        StepType stepType = step.getStepType();
        if ( stepType == null ) {
            return null;
        }
        return stepType.getStepTypeCode();
    }

    private UUID entityWorkflowWorkflowId2(StepTransition stepTransition) {
        Workflow workflow = stepTransition.getWorkflow();
        if ( workflow == null ) {
            return null;
        }
        return workflow.getWorkflowId();
    }

    private UUID entityFromStepStepId(StepTransition stepTransition) {
        Step fromStep = stepTransition.getFromStep();
        if ( fromStep == null ) {
            return null;
        }
        return fromStep.getStepId();
    }

    private UUID entityToStepStepId(StepTransition stepTransition) {
        Step toStep = stepTransition.getToStep();
        if ( toStep == null ) {
            return null;
        }
        return toStep.getStepId();
    }
}
