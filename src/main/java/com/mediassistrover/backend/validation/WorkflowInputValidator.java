package com.mediassistrover.backend.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.mediassistrover.backend.exception.BusinessRuleException;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Local-only JSON Schema assertions for inputs to an already published workflow. */
@Component
public class WorkflowInputValidator {
    private static final Set<String> TYPES = Set.of("object", "array", "string", "number", "integer", "boolean", "null");
    private static final Set<String> KEYWORDS = Set.of("$ref", "$defs", "type", "const", "enum", "required", "properties",
            "additionalProperties", "minProperties", "maxProperties", "items", "minItems", "maxItems", "uniqueItems",
            "minLength", "maxLength", "pattern", "format", "minimum", "maximum", "exclusiveMinimum", "exclusiveMaximum",
            "multipleOf", "allOf", "anyOf", "oneOf", "not", "if", "then", "else");
    private static final Set<String> ANNOTATIONS = Set.of("$schema", "$id", "$comment", "title", "description", "default",
            "examples", "readOnly", "writeOnly", "deprecated");
    private static final Set<String> COUNTS = Set.of("minProperties", "maxProperties", "minItems", "maxItems", "minLength", "maxLength");
    private static final Set<String> NUMBERS = Set.of("minimum", "maximum", "exclusiveMinimum", "exclusiveMaximum", "multipleOf");

    public void validate(JsonNode schema, JsonNode inputs) {
        if (inputs == null || !inputs.isObject()) throw new BusinessRuleException("Workflow inputs must be a JSON object");
        inspect(schema, schema, 0);
        check(schema, inputs, schema, "$", 0, new int[]{20_000});
    }

    private void inspect(JsonNode schema, JsonNode root, int depth) {
        if (depth > 64) throw schemaError("schema nesting exceeds 64 levels");
        if (schema != null && schema.isBoolean()) return;
        if (schema == null || !schema.isObject()) throw schemaError("each schema must be an object or boolean");
        schema.fieldNames().forEachRemaining(key -> {
            if (!KEYWORDS.contains(key) && !ANNOTATIONS.contains(key) && !key.startsWith("x-"))
                throw schemaError("unsupported keyword " + key);
        });
        if (schema.has("$ref")) {
            JsonNode ref = schema.get("$ref");
            if (!ref.isTextual() || !ref.asText().startsWith("#/$defs/"))
                throw schemaError("$ref must reference a local #/$defs/ definition");
            if (root.at(ref.asText().substring(1)).isMissingNode()) throw schemaError("unresolved $ref " + ref.asText());
        }
        if (schema.has("type")) {
            JsonNode type = schema.get("type");
            if (type.isArray()) {
                if (type.isEmpty()) throw schemaError("type must not be empty");
                for (JsonNode option : type) requireType(option);
            } else requireType(type);
        }
        for (String key : new String[]{"properties", "$defs"}) if (schema.has(key)) {
            if (!schema.get(key).isObject()) throw schemaError(key + " must be an object");
            for (JsonNode child : schema.get(key)) inspect(child, root, depth + 1);
        }
        for (String key : new String[]{"additionalProperties", "items", "not", "if", "then", "else"})
            if (schema.has(key)) inspect(schema.get(key), root, depth + 1);
        for (String key : new String[]{"allOf", "anyOf", "oneOf", "enum", "required"}) if (schema.has(key)) {
            JsonNode children = schema.get(key);
            if (!children.isArray() || !"required".equals(key) && children.isEmpty()) throw schemaError(key + " must be an array" );
            if (Set.of("allOf", "anyOf", "oneOf").contains(key)) for (JsonNode child : children) inspect(child, root, depth + 1);
            if ("required".equals(key)) for (JsonNode child : children)
                if (!child.isTextual()) throw schemaError("required entries must be strings");
        }
        for (String key : COUNTS) if (schema.has(key) && (!schema.get(key).isIntegralNumber()
                || !schema.get(key).canConvertToInt() || schema.get(key).intValue() < 0)) throw schemaError(key + " must be a nonnegative integer");
        for (String key : NUMBERS) if (schema.has(key) && !schema.get(key).isNumber()) throw schemaError(key + " must be a number");
        if (schema.has("multipleOf") && schema.get("multipleOf").decimalValue().signum() <= 0) throw schemaError("multipleOf must be positive");
        if (schema.has("uniqueItems") && !schema.get("uniqueItems").isBoolean()) throw schemaError("uniqueItems must be boolean");
        if (schema.has("format") && (!schema.get("format").isTextual()
                || !Set.of("uuid", "date-time").contains(schema.get("format").asText()))) throw schemaError("unsupported format " + schema.get("format"));
        if (schema.has("pattern")) {
            if (!schema.get("pattern").isTextual()) throw schemaError("pattern must be a string");
            try { Pattern.compile(schema.get("pattern").asText()); }
            catch (PatternSyntaxException error) { throw schemaError("pattern is invalid"); }
        }
    }

    private void check(JsonNode schema, JsonNode value, JsonNode root, String path, int depth, int[] budget) {
        if (depth > 64 || --budget[0] < 0) throw schemaError("validation exceeds its complexity limit");
        if (schema.isBoolean()) { if (!schema.booleanValue()) invalid(path, "is forbidden"); return; }
        if (schema.has("$ref")) check(root.at(schema.get("$ref").asText().substring(1)), value, root, path, depth + 1, budget);
        if (schema.has("type")) {
            JsonNode types = schema.get("type");
            boolean matches = types.isArray() ? false : matchesType(types.asText(), value);
            if (types.isArray()) for (JsonNode type : types) matches |= matchesType(type.asText(), value);
            if (!matches) invalid(path, "must match type " + types);
        }
        if (schema.has("const") && !equivalent(schema.get("const"), value)) invalid(path, "does not match const");
        if (schema.has("enum")) {
            boolean found = false; for (JsonNode option : schema.get("enum")) found |= equivalent(option, value);
            if (!found) invalid(path, "does not match enum");
        }
        for (JsonNode child : schema.path("allOf")) check(child, value, root, path, depth + 1, budget);
        for (String key : new String[]{"anyOf", "oneOf"}) if (schema.has(key)) {
            int matches = 0;
            for (JsonNode child : schema.get(key)) if (matches(child, value, root, path, depth, budget)) matches++;
            if (matches == 0 || "oneOf".equals(key) && matches != 1) invalid(path, "does not match " + key);
        }
        if (schema.has("not") && matches(schema.get("not"), value, root, path, depth, budget)) invalid(path, "matches a forbidden shape");
        if (schema.has("if")) {
            String branch = matches(schema.get("if"), value, root, path, depth, budget) ? "then" : "else";
            if (schema.has(branch)) check(schema.get(branch), value, root, path, depth + 1, budget);
        }
        if (value.isObject()) {
            for (JsonNode required : schema.path("required")) if (!value.has(required.asText())) invalid(path + "." + required.asText(), "is required");
            size(schema, value.size(), "Properties", path);
            value.properties().forEach(field -> {
                JsonNode child = schema.path("properties").get(field.getKey());
                if (child == null) child = schema.get("additionalProperties");
                if (child != null) check(child, field.getValue(), root, path + "." + field.getKey(), depth + 1, budget);
            });
        }
        if (value.isArray()) {
            size(schema, value.size(), "Items", path);
            for (int i = 0; i < value.size(); i++) {
                if (schema.has("items")) check(schema.get("items"), value.get(i), root, path + "[" + i + "]", depth + 1, budget);
                if (schema.path("uniqueItems").asBoolean()) for (int j = 0; j < i; j++)
                    if (equivalent(value.get(i), value.get(j))) invalid(path, "contains duplicate items");
            }
        }
        if (value.isTextual()) {
            String text = value.textValue(); size(schema, text.codePointCount(0, text.length()), "Length", path);
            if (schema.has("pattern") && !Pattern.compile(schema.get("pattern").asText()).matcher(text).find()) invalid(path, "does not match pattern");
            if (schema.has("format")) try {
                if ("uuid".equals(schema.get("format").asText())) {
                    if (!UUID.fromString(text).toString().equalsIgnoreCase(text)) throw new IllegalArgumentException();
                } else OffsetDateTime.parse(text);
            } catch (IllegalArgumentException | java.time.DateTimeException error) { invalid(path, "must be a valid " + schema.get("format").asText()); }
        }
        if (value.isNumber()) {
            if ((value.isDouble() || value.isFloat()) && !Double.isFinite(value.doubleValue())) invalid(path, "must be finite");
            var number = value.decimalValue();
            for (String key : NUMBERS) if (schema.has(key)) {
                var bound = schema.get(key).decimalValue(); int comparison = number.compareTo(bound);
                boolean rejected = switch (key) {
                    case "minimum" -> comparison < 0; case "maximum" -> comparison > 0;
                    case "exclusiveMinimum" -> comparison <= 0; case "exclusiveMaximum" -> comparison >= 0;
                    default -> number.remainder(bound).signum() != 0;
                };
                if (rejected) invalid(path, "violates " + key);
            }
        }
    }

    private boolean matches(JsonNode schema, JsonNode value, JsonNode root, String path, int depth, int[] budget) {
        try { check(schema, value, root, path, depth + 1, budget); return true; }
        catch (InputMismatch error) { return false; }
    }
    private void requireType(JsonNode type) {
        if (!type.isTextual() || !TYPES.contains(type.asText())) throw schemaError("unsupported type " + type);
    }
    private boolean matchesType(String type, JsonNode value) {
        return switch (type) {
            case "object" -> value.isObject(); case "array" -> value.isArray(); case "string" -> value.isTextual();
            case "boolean" -> value.isBoolean(); case "null" -> value.isNull(); case "number" -> value.isNumber();
            case "integer" -> value.isNumber() && value.decimalValue().stripTrailingZeros().scale() <= 0;
            default -> false;
        };
    }
    private void size(JsonNode schema, int size, String suffix, String path) {
        if (schema.has("min" + suffix) && size < schema.get("min" + suffix).intValue()) invalid(path, "violates min" + suffix);
        if (schema.has("max" + suffix) && size > schema.get("max" + suffix).intValue()) invalid(path, "violates max" + suffix);
    }
    private boolean equivalent(JsonNode first, JsonNode second) {
        if (first.isNumber() && second.isNumber()) return first.decimalValue().compareTo(second.decimalValue()) == 0;
        if (first.isContainerNode() && second.getNodeType() == first.getNodeType() && first.size() == second.size()) {
            if (first.isArray()) {
                for (int i = 0; i < first.size(); i++) if (!equivalent(first.get(i), second.get(i))) return false;
            } else for (var field : first.properties()) {
                if (!second.has(field.getKey()) || !equivalent(field.getValue(), second.get(field.getKey()))) return false;
            }
            return true;
        }
        return first.equals(second);
    }
    private BusinessRuleException schemaError(String reason) { return new BusinessRuleException("Unsupported workflow input schema: " + reason); }
    private void invalid(String path, String reason) { throw new InputMismatch("Invalid workflow inputs: " + path + " " + reason); }
    private static final class InputMismatch extends BusinessRuleException {
        private InputMismatch(String message) { super(message); }
    }
}
