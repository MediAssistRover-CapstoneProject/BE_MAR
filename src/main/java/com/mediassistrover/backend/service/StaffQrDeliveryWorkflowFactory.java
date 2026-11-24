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

    public GraphCreateRequest build() { return build(DeliveryAuthenticationMethod.QR_CODE); }
    public GraphCreateRequest build(DeliveryAuthenticationMethod method) {
        var schema = json.createObjectNode().put("type", "object").put("additionalProperties", true);
        var properties = schema.putObject("properties");
        var required = schema.putArray("required");
        for (String field : List.of("map_id", "pickup_endpoint_id", "service_request_id",
                "expected_sender_account_id", "expected_receiver_account_id")) {
            properties.putObject(field).put("type", "string").put("format", "uuid");
            required.add(field);
        }
        properties.putObject("delivery_endpoint_id").put("type", "string").put("format", "uuid");
        properties.putObject("delivery_group_id").put("type", "string").put("format", "uuid");
        properties.putObject("target_mode").put("type", "string").putArray("enum").add("ENDPOINT").add("ENDPOINT_GROUP");
        var targets = schema.putArray("oneOf");
        var direct = targets.addObject(); direct.putObject("properties").putObject("target_mode").put("const", "ENDPOINT");
        direct.putArray("required").add("delivery_endpoint_id"); direct.putObject("not").putArray("required").add("delivery_group_id");
        var grouped = targets.addObject(); grouped.putObject("properties").putObject("target_mode").put("const", "ENDPOINT_GROUP");
        grouped.putArray("required").add("target_mode").add("delivery_group_id");
        grouped.putObject("not").putArray("required").add("delivery_endpoint_id");
        properties.putObject("compartment_id").put("const", "CART_MAIN");
        properties.putObject("items").put("type", "array").put("minItems", 1);
        required.add("compartment_id").add("items");
        for (String field : List.of("pickup_arrival_text", "delivery_arrival_text", "delivery_completed_text"))
            properties.putObject(field).put("type", "string").put("minLength", 1).put("maxLength", 500);
        var defaults = json.createObjectNode()
                .put("target_mode", "ENDPOINT")
                .put("pickup_arrival_text", "Đã tới điểm nhận hàng. Mời nhân viên giao hàng chuẩn bị mã QR.")
                .put("delivery_arrival_text", method == DeliveryAuthenticationMethod.QR_CODE
                        ? "Đã tới điểm giao hàng. Mời nhân viên nhận hàng chuẩn bị mã QR."
                        : "Đã tới điểm giao hàng. Mời nhân viên nhận hàng nhìn vào camera để xác thực khuôn mặt.")
                .put("delivery_completed_text", "Đã giao vật phẩm thành công.");

        List<GraphStepRequest> steps = new ArrayList<>();
        addHandover(steps, "SENDER", "pickup_endpoint_id", "LOAD", DeliveryAuthenticationMethod.QR_CODE);
        addHandover(steps, "RECEIVER", "delivery_endpoint_id", "UNLOAD", method);
        addAnnouncement(steps, "RECEIVER", "ANNOUNCE", "Thông báo giao hàng thành công",
                "DELIVERY_COMPLETED", "delivery_completed_text");
        List<GraphTransitionRequest> transitions = transitions(steps);
        // Both navigation branches join before arrival/authentication, so a failed candidate never
        // repeats pickup or obtains a receiver grant before actually reaching its selected endpoint.
        transitions.replaceAll(route -> "MOVE_RECEIVER".equals(route.toStepCode())
                ? new GraphTransitionRequest(route.fromStepCode(), "RECEIVER_TARGET_MODE", route.outcome(),
                    route.conditionJson(), route.priority(), route.defaultTransition(), route.terminalStatus()) : route);
        var mode = json.createObjectNode(); mode.putObject("expression")
                .put("path", "/inputs/target_mode").put("operator", "EQ").put("value", "ENDPOINT_GROUP");
        add(steps, "RECEIVER", "RECEIVER_TARGET", StepType.CHECK_CONDITION, "Chọn đích giao hàng", 30,
                mode, json.createObjectNode());
        // Use a stable branch name for notices and published-workflow compatibility checks.
        int modeIndex = steps.size() - 1;
        var modeStep = steps.get(modeIndex);
        steps.set(modeIndex, new GraphStepRequest(modeStep.taskCode(), modeStep.stepType(), "RECEIVER_TARGET_MODE",
                modeStep.stepName(), modeStep.sequenceNo(), null, modeStep.paramsJson(), modeStep.inputMappingJson(),
                modeStep.timeoutSec(), 1, 0, json.createArrayNode(), 1, StepAuditLevel.FULL));
        var branch = EndpointGroupWorkflowSupport.append(json, steps, transitions, "RECEIVER", "GROUP_RECEIVER",
                EndpointGroupWorkflowSupport.reference(json, "/inputs/delivery_group_id"),
                EndpointGroupWorkflowSupport.reference(json, "/inputs/map_id"), "NORMAL", .35, 180, 2,
                "ARRIVAL_RECEIVER", TerminalStatus.FAILED);
        var groupedMode = json.createObjectNode().put("path", "/result/value").put("operator", "EQ").put("value", true);
        transitions.add(new GraphTransitionRequest("RECEIVER_TARGET_MODE", branch.startStepCode(), TransitionOutcome.SUCCESS,
                groupedMode, 1, false, null));
        transitions.add(new GraphTransitionRequest("RECEIVER_TARGET_MODE", "MOVE_RECEIVER", TransitionOutcome.SUCCESS,
                null, 2, true, null));
        for (var outcome : List.of(TransitionOutcome.FAILURE, TransitionOutcome.TIMEOUT))
            transitions.add(new GraphTransitionRequest("RECEIVER_TARGET_MODE", null, outcome, null, 1, true, TerminalStatus.FAILED));
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(java.util.Locale.ROOT);
        var workflow = new WorkflowUpsertRequest((method==DeliveryAuthenticationMethod.QR_CODE?"WF_STAFF_QR_DELIVERY_":"WF_STAFF_FACE_DELIVERY_") + suffix, 1, method==DeliveryAuthenticationMethod.QR_CODE?"Giao hàng A → B bằng QR staff":"Giao hàng quan trọng: gửi QR, nhận khuôn mặt",
                "DELIVERY", "Robot xác thực người giao và người nhận, sau đó xác nhận hàng và khóa ngăn.",
                schema, json.createObjectNode(), defaults, json.createObjectNode(),
                json.createArrayNode().add("NAVIGATION").add("HARDWARE_CONTROL"), 2400,
                steps.stream().mapToInt(GraphStepRequest::maxVisits).sum(), 3, 3);
        var tasks = List.of(new TaskRequest("SENDER", "Nhận hàng", 1, null, json.createObjectNode()),
                new TaskRequest("RECEIVER", "Bàn giao", 2, null, json.createObjectNode()));
        return new GraphCreateRequest(workflow, new GraphRequest("MOVE_SENDER", tasks, steps, transitions));
    }

    private void addHandover(List<GraphStepRequest> steps, String stage, String endpoint, String payloadAction, DeliveryAuthenticationMethod method) {
        String purpose = "DELIVERY_" + stage;
        add(steps, stage, "MOVE", StepType.MOVE, "Đi tới điểm giao nhận", 180,
                json.createObjectNode().put("mode", "NORMAL").put("max_speed_mps", 0.35),
                inputs(Map.of("map_id", "map_id", "endpoint_id", endpoint)));
        addAnnouncement(steps, stage, "ARRIVAL", "Thông báo đã tới điểm giao nhận",
                "DELIVERY_ARRIVAL_" + stage, "SENDER".equals(stage) ? "pickup_arrival_text" : "delivery_arrival_text");
        add(steps, stage, "AUTH", StepType.AUTHENTICATE, method==DeliveryAuthenticationMethod.QR_CODE?"Xác thực QR staff":"Xác thực khuôn mặt staff", 120,
                json.createObjectNode().put("method", method.name()).put("purpose", purpose),
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
            if (step.stepType() == StepType.INTERACT && "SPEAK".equals(step.paramsJson().path("action").asText())) {
                // Audio failure must not repeat an arrived MOVE or reopen a confirmed handover.
                String next = i + 1 < steps.size() ? steps.get(i + 1).stepCode() : null;
                for (var outcome : List.of(TransitionOutcome.SUCCESS, TransitionOutcome.FAILURE, TransitionOutcome.TIMEOUT))
                    result.add(new GraphTransitionRequest(from, next, outcome, null, 1, true,
                            next == null ? TerminalStatus.SUCCEEDED : null));
                continue;
            }
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

    private void addAnnouncement(List<GraphStepRequest> steps, String stage, String code, String name,
                                 String prompt, String textInput) {
        var mapping = json.createObjectNode();
        var data = mapping.putObject("data").put("language_code", "vi");
        data.putObject("text").put("$ref", "/inputs/" + textInput);
        add(steps, stage, code, StepType.INTERACT, name, 25,
                json.createObjectNode().put("action", "SPEAK").put("prompt_key", prompt), mapping);
    }

    private ObjectNode inputs(Map<String, String> fields) {
        var out = json.createObjectNode();
        fields.forEach((key, input) -> out.putObject(key).put("$ref", "/inputs/" + input));
        return out;
    }
}
