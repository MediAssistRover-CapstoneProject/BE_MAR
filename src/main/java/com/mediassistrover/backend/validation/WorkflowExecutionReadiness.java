package com.mediassistrover.backend.validation;

import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.repository.StepRepository;
import com.mediassistrover.backend.repository.RobotMqttSessionRepository;
import com.mediassistrover.backend.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Publish and robot allocation gates for the integrated, versioned runtime. */
@Component @RequiredArgsConstructor
public class WorkflowExecutionReadiness {
    private final StepRepository steps;
    private final com.mediassistrover.backend.mqtt.MqttStepDispatchService mqttDispatch;
    private final com.mediassistrover.backend.mqtt.MqttProperties mqttProperties;
    private final com.mediassistrover.backend.mapping.MappingProperties mappingProperties;
    private final StepExecutionPolicy executionPolicy;
    private final RobotMqttSessionRepository sessions;
    private final com.mediassistrover.backend.mqtt.MqttSessionRegistry sessionRegistry;
    public boolean acceptsRobot(Workflow workflow, Robot robot) {
        if (workflow.getSchemaVersion()==2) return !mqttProperties.manages(robot.getRobotId());
        return !requiresMqtt(workflow) || mqttProperties.manages(robot.getRobotId());
    }
    /** A Pi-owned workflow remains queued until its selected robot has completed a fresh handshake. */
    public boolean transportReady(Workflow workflow, Robot robot) {
        // Persisted production Jobs can only originate from a published workflow. Tests and
        // validation tooling also execute synthetic DRAFT graphs directly; those must retain
        // their existing orchestration lifecycle so the transport itself remains the dispatch gate.
        if (workflow.getSchemaVersion()!=3 || workflow.getStatus()!=WorkflowStatus.PUBLISHED || !requiresMqtt(workflow)) return true;
        RobotMqttSession session=sessions.findById(robot.getRobotId()).orElse(null);
        return sessionRegistry.fresh(session);
    }
    private boolean requiresMqtt(Workflow workflow) {
        return steps.findByWorkflowWorkflowIdOrderBySequenceNoAsc(workflow.getWorkflowId()).stream()
                .filter(Step::isEnabled).anyMatch(step -> switch (step.getStepType()) {
                    case MOVE, CONTROL_HARDWARE, EXECUTE_SENSING -> true;
                    case INTERACT -> "SPEAK".equals(step.getParamsJson().path("action").asText()) || step.getInputMappingJson().has("action");
                    case WAIT -> !"EVENT".equals(step.getParamsJson().path("wait_type").asText()) || step.getInputMappingJson().has("wait_type");
                    default -> false;
                });
    }
    public void requireReady(Workflow workflow) {
        StepContractVersions.requireWorkflow(workflow.getSchemaVersion(),workflow.getGraphVersion());
        if (workflow.getSchemaVersion()==2) return;
        for (Step step: steps.findByWorkflowWorkflowIdOrderBySequenceNoAsc(workflow.getWorkflowId())) {
            if (!step.isEnabled()) continue;
            var params=step.getParamsJson(); var mapping=step.getInputMappingJson();
            if (executionPolicy.requiresMqtt(step)) {
                mqttDispatch.requireActionSupported(step);
                continue;
            }
            switch (step.getStepType()) {
                case NOTIFY -> { } // Alert persistence and recipient ACK are integrated; explicit WAIT/EVENT waits for ACK.
                case WAIT -> { if (!"EVENT".equals(params.path("wait_type").asText()) || mapping.has("wait_type")) mqttDispatch.requireActionSupported(step); }
                case INTERACT -> { if (mapping.has("action")) blocked(step,"fixed INTERACT action"); if ("SPEAK".equals(params.path("action").asText())) mqttDispatch.requireActionSupported(step); }
                case DATA_SERVICE -> { if ("COMMIT_MAP_ARTIFACT".equals(params.path("service_key").asText()) && !mappingProperties.isEnabled() || mapping.has("service_key")) blocked(step,"artifact integration / dynamic service contract"); }
                default -> { }
            }
        }
    }
    private void blocked(Step step,String dependency) {
        throw new BusinessRuleException("Step "+step.getStepCode()+" awaits "+dependency+"; keep workflow DRAFT until integration is ready");
    }
}
