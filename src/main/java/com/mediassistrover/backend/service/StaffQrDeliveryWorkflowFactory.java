package com.mediassistrover.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediassistrover.backend.dto.workflow.request.WorkflowRequests.*;
import com.mediassistrover.backend.enums.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Trusted schema 3 delivery draft. Every unlock is scoped to its preceding AUTH output. */
@Component @RequiredArgsConstructor
public class StaffQrDeliveryWorkflowFactory {
    private final ObjectMapper json;

    public GraphCreateRequest build() {
        var schema = json.createObjectNode().put("type", "object").put("additionalProperties", true);
        var properties = schema.putObject("properties");
        var required = schema.putArray("required");
        for (String field : List.of("map_id", "pickup_endpoint_id", "delivery_endpoint_id", "service_request_id",
                "expected_sender_account_id", "expected_receiver_account_id")) {
            properties.putObject(field).put("type", "string").put("format", "uuid");
            required.add(field);
        }
        properties.putObject("compartment_id").put("const", "CART_MAIN");
        properties.putObject("items").put("type", "array").put("minItems", 1);
        required.add("compartment_id").add("items");

        List<GraphStepRequest> steps = new ArrayList<>();
        addHandover(steps, "SENDER", "pickup_endpoint_id", "LOAD");
        addHandover(steps, "RECEIVER", "delivery_endpoint_id", "UNLOAD");
        List<GraphTransitionRequest> transitions = transitions(steps);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(java.util.Locale.ROOT);
        var workflow = new WorkflowUpsertRequest("WF_STAFF_QR_DELIVERY_" + suffix, 1, "Giao hàng A → B bằng QR staff",
                "DELIVERY", "Robot quét QR của người giao và người nhận, sau đó xác nhận hàng và khóa ngăn.",
                schema, json.createObjectNode(), json.createObjectNode(), json.createObjectNode(),
                json.createArrayNode().add("NAVIGATION").add("HARDWARE_CONTROL"), 2400, steps.size(), 3, 3);
        var tasks = List.of(new TaskRequest("SENDER", "Nhận hàng", 1, null, json.createObjectNode()),
                new TaskRequest("RECEIVER", "Bàn giao", 2, null, json.createObjectNode()));
        return new GraphCreateRequest(workflow, new GraphRequest("MOVE_SENDER", tasks, steps, transitions));
    }

    private void addHandover(List<GraphStepRequest> steps, String stage, String endpoint, String payloadAction) {
        String purpose = "DELIVERY_" + stage;
        add(steps, stage, "MOVE", StepType.MOVE, "Đi tới điểm giao nhận", 180,
                json.createObjectNode().put("mode", "NORMAL").put("max_speed_mps", .2),
                inputs(Map.of("map_id", "map_id", "endpoint_id", endpoint)));
        add(steps, stage, "AUTH", StepType.AUTHENTICATE, "Xác thực QR staff", 120,
                json.createObjectNode().put("method", "QR_CODE").put("purpose", purpose),
                inputs(Map.of("service_request_id", "service_request_id")));

        var unlock = inputs(Map.of("compartment_id", "compartment_id"));
        var grant = unlock.putObject("authorization").put("mode", "STAFF_VERIFIED").put("purpose", purpose);
        for (String field : List.of("verification_id", "actor_account_id", "valid_until"))
            grant.putObject(field).put("$ref", "/outputs/AUTH_" + stage + "/" + field);
        add(steps, stage, "UNLOCK", StepType.CONTROL_HARDWARE, "Mở ngăn", 30,
                json.createObjectNode().put("action", "UNLOCK_COMPARTMENT"), unlock);
        add(steps, stage, "PAYLOAD", StepType.HANDLE_PAYLOAD, "Xác nhận hàng", 180,
                json.createObjectNode().put("action", payloadAction),
                inputs(Map.of("items", "items", "service_request_id", "service_request_id", "compartment_id", "compartment_id")));
        var close = json.createObjectNode().put("action", "CAPTURE_INPUT").put("prompt_key", "CLOSE_" + stage);
        close.putObject("data");
        add(steps, stage, "CLOSE", StepType.INTERACT, "Xác nhận đóng nắp", 120, close, json.createObjectNode());
        add(steps, stage, "LOCK", StepType.CONTROL_HARDWARE, "Khóa ngăn", 30,
                json.createObjectNode().put("action", "LOCK_COMPARTMENT"), inputs(Map.of("compartment_id", "compartment_id")));
        var verify = json.createObjectNode().put("action", "CAPTURE_INPUT").put("prompt_key", "VERIFY_" + stage);
        verify.putObject("data");
        add(steps, stage, "VERIFY", StepType.INTERACT, "Xác nhận đã khóa", 120, verify, json.createObjectNode());
    }

    private List<GraphTransitionRequest> transitions(List<GraphStepRequest> steps) {
        List<GraphTransitionRequest> result = new ArrayList<>();
        for (int i = 0; i < steps.size(); i++) {
            GraphStepRequest step = steps.get(i);
            String from = step.stepCode();
            String next = i + 1 < steps.size() ? steps.get(i + 1).stepCode() : null;
            TerminalStatus terminal = next == null ? TerminalStatus.SUCCEEDED : null;
            String required = switch (step.stepType()) {
                case AUTHENTICATE -> "verified";
                case HANDLE_PAYLOAD -> "confirmed";
                case INTERACT -> from.startsWith("CLOSE_") ? "closed" : "locked";
                default -> null;
            };
            boolean beforeUnlock = step.stepType() == StepType.MOVE || step.stepType() == StepType.AUTHENTICATE;
            TerminalStatus fault = beforeUnlock ? TerminalStatus.FAILED : TerminalStatus.RECOVERY_REQUIRED;
            if (required == null) {
                result.add(new GraphTransitionRequest(from, next, TransitionOutcome.SUCCESS, null, 1, true, terminal));
            } else {
                var condition = json.createObjectNode().put("path", "/result/" + required).put("operator", "EQ").put("value", true);
                result.add(new GraphTransitionRequest(from, next, TransitionOutcome.SUCCESS, condition, 1, false, terminal));
                result.add(new GraphTransitionRequest(from, null, TransitionOutcome.SUCCESS, null, 2, true, fault));
            }
            result.add(new GraphTransitionRequest(from, null, TransitionOutcome.FAILURE, null, 1, true, fault));
            TerminalStatus timeout = step.stepType() == StepType.AUTHENTICATE ? TerminalStatus.FAILED : TerminalStatus.RECOVERY_REQUIRED;
            result.add(new GraphTransitionRequest(from, null, TransitionOutcome.TIMEOUT, null, 1, true, timeout));
        }
        return result;
    }

    private void add(List<GraphStepRequest> steps, String stage, String code, StepType type, String name,
                     int timeout, ObjectNode params, ObjectNode mapping) {
        steps.add(new GraphStepRequest(stage, type, code + "_" + stage, name, steps.size() + 1, null,
                params, mapping, timeout, 1, 0, json.createArrayNode(), 1, StepAuditLevel.FULL));
    }

    private ObjectNode inputs(Map<String, String> fields) {
        var out = json.createObjectNode();
        fields.forEach((key, input) -> out.putObject(key).put("$ref", "/inputs/" + input));
        return out;
    }
}
