package com.mediassistrover.backend.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.mediassistrover.backend.exception.BusinessRuleException;
import java.util.Set;
import java.util.UUID;

/** Optional capability extension; the published MAR-STEP-2 catalog/hash stays unchanged. */
public final class GroupPathSearchPolicy {
    private GroupPathSearchPolicy() { }
    public static void validate(JsonNode value) {
        if(!value.isObject() || value.size()!=4) fail();
        var names=value.fieldNames();
        while(names.hasNext()) if(!Set.of("budget_sec","max_recovery_attempts","selection_id","fallback_trigger").contains(names.next())) fail();
        if(!value.path("budget_sec").isIntegralNumber() || !value.path("budget_sec").canConvertToInt()
                || value.path("budget_sec").asInt()<5 || value.path("budget_sec").asInt()>30
                || !value.path("max_recovery_attempts").isIntegralNumber() || !value.path("max_recovery_attempts").canConvertToInt()
                || value.path("max_recovery_attempts").asInt()!=1
                || !"ON_PATH_UNAVAILABLE".equals(value.path("fallback_trigger").asText())) fail();
        try { if(!UUID.fromString(value.path("selection_id").asText()).toString().equals(value.path("selection_id").asText())) fail(); }
        catch(IllegalArgumentException invalid) { fail(); }
    }
    private static void fail() { throw new BusinessRuleException("Invalid BE-issued grouped path-search policy"); }
}
