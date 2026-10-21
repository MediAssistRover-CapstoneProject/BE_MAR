package com.mediassistrover.backend.validation;

import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.repository.StepRepository;
import com.mediassistrover.backend.repository.RobotMqttSessionRepository;
import com.mediassistrover.backend.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

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
        return robotCapabilityBlocker(workflow,robot).isEmpty();
    }
    /** A selected robot must advertise every Pi capability before a Job can be created or started. */
    public void requireRobotCapabilities(Workflow workflow,Robot robot) {
        robotCapabilityBlocker(workflow,robot).ifPresent(reason->{throw new BusinessRuleException(reason);});
    }
    /** Missing/stale transport is retryable: keep the Job QUEUED and never emit a command. */
    public boolean transportReady(Workflow workflow,Robot robot) {
        if (workflow.getSchemaVersion()!=3 || requiredRobotCapabilities(workflow).isEmpty()) return true;
        RobotMqttSession session=sessions.findById(robot.getRobotId()).orElse(null);
        return sessionRegistry.fresh(session);
    }
    public Optional<String> robotCapabilityBlocker(Workflow workflow,Robot robot) {
        if (workflow.getSchemaVersion()==2)
            return mqttProperties.manages(robot.getRobotId())?Optional.of("Selected robot uses MQTT and requires a schema 3 workflow"):Optional.empty();
        Set<String> required=requiredRobotCapabilities(workflow);
        if(required.isEmpty())return Optional.empty();
        if(!mqttProperties.manages(robot.getRobotId()))
            return Optional.of("Robot "+robot.getRobotId()+" is not enrolled for MQTT capabilities "+required);
        Set<String> advertised=advertised(robot);
        Set<String> missing=new LinkedHashSet<>(required);missing.removeAll(advertised);
        return missing.isEmpty()?Optional.empty():Optional.of("Robot "+robot.getRobotId()+" does not advertise required workflow capabilities "+missing+"; advertised="+advertised);
    }
    private Set<String> requiredRobotCapabilities(Workflow workflow) {
        Set<String> required=new LinkedHashSet<>();
        for(Step step:steps.findByWorkflowWorkflowIdOrderBySequenceNoAsc(workflow.getWorkflowId()))
            if(step.isEnabled() && executionPolicy.requiresMqtt(step))required.add(step.getStepType().name());
        if(workflow.getRequiredCapabilitiesJson()!=null && workflow.getRequiredCapabilitiesJson().isArray())
            workflow.getRequiredCapabilitiesJson().forEach(value->{
                String capability=value.asText("").toUpperCase(Locale.ROOT);
                if(com.mediassistrover.backend.mqtt.MqttProperties.ROBOT_STEP_TYPES.stream().anyMatch(type->type.name().equals(capability)))required.add(capability);
            });
        return required;
    }
    private Set<String> advertised(Robot robot) {
        Set<String> advertised=new LinkedHashSet<>();
        RobotMqttSession session=sessions.findById(robot.getRobotId()).orElse(null);
        if(session!=null && session.getHelloJson()!=null)
            session.getHelloJson().path("enabledStepTypes").forEach(value->advertised.add(value.asText().toUpperCase(Locale.ROOT)));
        else if(robot.getCapabilitiesJson()!=null && robot.getCapabilitiesJson().isArray())
            robot.getCapabilitiesJson().forEach(value->advertised.add(value.asText().toUpperCase(Locale.ROOT)));
        else if(robot.getCapabilitiesJson()!=null && robot.getCapabilitiesJson().isObject())
            robot.getCapabilitiesJson().properties().forEach(entry->{if(entry.getValue().asBoolean())advertised.add(entry.getKey().toUpperCase(Locale.ROOT));});
        return advertised;
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
