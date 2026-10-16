package com.mediassistrover.backend.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.mqtt.MqttProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Version-aware assignment: existing schema 2 jobs retain their original executor. */
@Component @RequiredArgsConstructor
public class StepExecutionPolicy {
    private final MqttProperties mqtt;

    public boolean routesMqtt(StepType type) { return mqtt.dispatches(type); }

    public boolean requiresMqtt(Step step) {
        // Event waits are resumed by a durable backend event, regardless of
        // whether WAIT is enabled as a Pi step type for duration waits.
        if (step.getStepType() == StepType.WAIT
                && "EVENT".equals(step.getParamsJson().path("wait_type").asText())) {
            return false;
        }
        if (routesMqtt(step.getStepType())) return true;
        return switch (step.getStepType()) {
            case MOVE, CONTROL_HARDWARE, EXECUTE_SENSING -> true;
            case WAIT -> true;
            default -> false;
        };
    }

    public boolean isServer(JobStep step, JsonNode inputs) {
        if (!step.getJobTask().getJob().isBeOrchestrated())
            return step.getStep().getStepType().getExecutorKind(step.getJobTask().getJob().getSchemaVersion())!=StepExecutorKind.PI;
        StepType type=StepType.valueOf(step.getStepTypeSnapshot());
        if (type == StepType.WAIT
                && "EVENT".equals(inputs.path("wait_type").asText())) {
            return true;
        }
        if (routesMqtt(type)) return false;
        return switch (type) {
            case MOVE, CONTROL_HARDWARE, EXECUTE_SENSING -> false;
            case WAIT -> "EVENT".equals(inputs.path("wait_type").asText());
            default -> true;
        };
    }
}
