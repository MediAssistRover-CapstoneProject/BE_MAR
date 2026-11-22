package com.mediassistrover.backend.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediassistrover.backend.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowInputValidatorTest {
    private final ObjectMapper json = new ObjectMapper();
    private final WorkflowInputValidator validator = new WorkflowInputValidator();

    @Test void acceptsThePublishedVoiceWorkflowNullableTypes() throws Exception {
        try (var source = getClass().getResourceAsStream("/static/voice-navigation-workflow.json")) {
            JsonNode schema = json.readTree(source).at("/workflow/inputSchemaJson");
            assertDoesNotThrow(() -> validator.validate(schema, tree("""
                    {"map_id":"00000000-0000-0000-0000-000000000001","target_mode":"ENDPOINT",
                     "endpoint_id":"00000000-0000-0000-0000-000000000002","max_speed_mps":null,"arrival_text":null}
                    """)));
        }
    }

    @Test void enforcesRequiredAndConstantsWithJsonNumericEquality() throws Exception {
        JsonNode schema = tree("""
                {"type":"object","required":["mode","version"],"properties":{
                  "mode":{"const":"ENDPOINT"},"version":{"type":"integer","const":1,"enum":[1.0]}}}
                """);
        validator.validate(schema, tree("{\"mode\":\"ENDPOINT\",\"version\":1.0}"));
        rejects(schema, "{\"mode\":\"ENDPOINT\"}", "$.version is required");
        rejects(schema, "{\"mode\":\"GROUP\",\"version\":1}", "$.mode does not match const");
    }

    @Test void rejectsUnknownPropertiesAndInvalidArrayItems() throws Exception {
        JsonNode schema = tree("""
                {"type":"object","additionalProperties":false,"properties":{"checkpoints":{
                  "type":"array","minItems":1,"maxItems":2,"uniqueItems":true,"items":{"type":"integer"}}}}
                """);
        validator.validate(schema, tree("{\"checkpoints\":[1,2]}"));
        rejects(schema, "{\"authority\":true}", "$.authority is forbidden");
        rejects(schema, "{\"checkpoints\":[\"one\"]}", "$.checkpoints[0] must match type");
        rejects(schema, "{\"checkpoints\":[1,1.0]}", "duplicate items");
    }

    @Test void checksUuidAndCountsUnicodeCodePoints() throws Exception {
        JsonNode schema = tree("""
                {"properties":{"map_id":{"type":"string","format":"uuid"},
                  "text":{"type":"string","minLength":1,"maxLength":1}}}
                """);
        validator.validate(schema, tree("{\"map_id\":\"AAAAAAAA-0000-0000-0000-000000000001\",\"text\":\"😀\"}"));
        rejects(schema, "{\"map_id\":\"1-1-1-1-1\"}", "$.map_id must be a valid uuid");
        rejects(schema, "{\"text\":\"ab\"}", "$.text violates maxLength");
    }

    @Test void enforcesNumericBoundsWithoutRounding() throws Exception {
        JsonNode schema = tree("""
                {"properties":{"speed":{"type":"number","minimum":0.1,"maximum":0.35},
                  "count":{"type":"integer","exclusiveMinimum":0,"exclusiveMaximum":4,"multipleOf":1}}}
                """);
        validator.validate(schema, tree("{\"speed\":0.35,\"count\":3.0}"));
        rejects(schema, "{\"speed\":0.3500000001}", "$.speed violates maximum");
        rejects(schema, "{\"count\":0}", "$.count violates exclusiveMinimum");
        rejects(schema, "{\"count\":4}", "$.count violates exclusiveMaximum");
        rejects(schema, "{\"count\":1.5}", "$.count must match type");
    }

    @Test void supportsLocalDefinitionsAndRejectsUnsafeOrUnsupportedSchemas() throws Exception {
        JsonNode schema = tree("""
                {"$defs":{"target":{"oneOf":[{"type":"null"},{"type":"string","format":"uuid"}]}},
                  "properties":{"endpoint":{"$ref":"#/$defs/target"}}}
                """);
        validator.validate(schema, tree("{\"endpoint\":null}"));
        rejects(schema, "{\"endpoint\":false}", "does not match oneOf");
        for (String unsafe : new String[]{
                "{\"properties\":{\"unused\":{\"$ref\":\"https://example.org/schema\"}}}",
                "{\"properties\":{\"unused\":{\"unevaluatedProperties\":false}}}",
                "{\"$defs\":{\"loop\":{\"$ref\":\"#/$defs/loop\"}},\"not\":{\"$ref\":\"#/$defs/loop\"}}"}) {
            rejects(tree(unsafe), "{}", "Unsupported workflow input schema");
        }
    }

    private JsonNode tree(String source) throws Exception { return json.readTree(source); }
    private void rejects(JsonNode schema, String input, String message) throws Exception {
        JsonNode value = tree(input);
        var error = assertThrows(BusinessRuleException.class, () -> validator.validate(schema, value));
        assertTrue(error.getMessage().contains(message), error::getMessage);
    }
}
