package com.mediassistrover.backend.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.mediassistrover.backend.entity.Step;
import com.mediassistrover.backend.enums.StepType;
import java.util.Set;

/** Explicit administrator-authored bench flow; never inferred from request data. */
public final class HardwareCommissioningWorkflow {
    public static final String PI_CAPABILITY = "commissioning-unlock-v1";

    private HardwareCommissioningWorkflow() { }

    public static boolean piSupportsUnlock(JsonNode hello) {
        if ("COMMISSIONING_ONLY".equals(hello.path("unlockPolicy").asText())) return true;
        for (JsonNode capability : hello.path("capabilities")) {
            if (PI_CAPABILITY.equals(capability.asText())) return true;
        }
        return false;
    }

    public static boolean unlockWithoutGrant(Step step) {
        var workflow=step.getWorkflow();
        return workflow!=null && workflow.getSchemaVersion()==3
            && step.getStepType()==StepType.CONTROL_HARDWARE
            && workflow.getDefaultInputsJson()!=null
            && "COMMISSIONING_ONLY".equals(workflow.getDefaultInputsJson().path("hardware_test_mode").asText())
            && Set.of("UNLOCK","UNLOCK_COMPARTMENT").contains(step.getParamsJson().path("action").asText())
            && !step.getInputMappingJson().has("action")
            && !step.getParamsJson().has("authorization") && !step.getInputMappingJson().has("authorization");
    }
}
