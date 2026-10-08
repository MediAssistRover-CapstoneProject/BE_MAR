package com.mediassistrover.backend.validation;

import com.mediassistrover.backend.exception.BusinessRuleException;

/** Versions are independent: REST API v1, transport protocol 1.0, step schema 2 and graph 3. */
public final class StepContractVersions {
    public static final String PROTOCOL = "1.0";
    public static final int STEP_SCHEMA = 2;
    public static final int GRAPH = 3;

    private StepContractVersions() {}

    public static void requireWorkflow(int schemaVersion, int graphVersion) {
        if (schemaVersion != STEP_SCHEMA)
            throw new BusinessRuleException("Unsupported Step contract schemaVersion: " + schemaVersion + "; supported: " + STEP_SCHEMA);
        if (graphVersion != GRAPH)
            throw new BusinessRuleException("Unsupported Workflow graphVersion: " + graphVersion + "; supported: " + GRAPH);
    }

    public static void requireJob(int schemaVersion, int graphVersion) {
        requireWorkflow(schemaVersion, graphVersion);
    }
}
