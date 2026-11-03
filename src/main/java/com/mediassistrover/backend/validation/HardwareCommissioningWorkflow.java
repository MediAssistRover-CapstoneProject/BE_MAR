package com.mediassistrover.backend.validation;

import com.mediassistrover.backend.entity.Step;
import com.mediassistrover.backend.enums.StepType;
import java.util.Set;

/** Explicit administrator-authored bench flow; never inferred from request data. */
public final class HardwareCommissioningWorkflow {
    private HardwareCommissioningWorkflow() { }
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
