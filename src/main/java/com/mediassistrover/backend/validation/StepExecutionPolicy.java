package com.mediassistrover.backend.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;

/** Version-aware assignment: existing schema 2 jobs retain their original executor. */
public final class StepExecutionPolicy {
    private StepExecutionPolicy() { }
    public static boolean isServer(JobStep step, JsonNode inputs) {
        if (!step.getJobTask().getJob().isBeOrchestrated())
            return step.getStep().getStepType().getExecutorKind(step.getJobTask().getJob().getSchemaVersion())!=StepExecutorKind.PI;
        return switch (StepType.valueOf(step.getStepTypeSnapshot())) {
            case MOVE, CONTROL_HARDWARE, EXECUTE_SENSING -> false;
            case WAIT -> "EVENT".equals(inputs.path("wait_type").asText());
            default -> true;
        };
    }
}
