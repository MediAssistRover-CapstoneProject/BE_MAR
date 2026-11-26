package com.mediassistrover.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediassistrover.backend.dto.workflow.request.WorkflowRequests.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.exception.BusinessRuleException;

import java.util.ArrayList;
import java.util.List;

/** A bounded, route-independent patrol round. All destination authority comes from frozen Job inputs. */
final class PatrolTemplateDefinition {
    static final int VERSION = 2;
    static final String CODE_PREFIX = "WF_PATROL_TEMPLATE_V2_";
    private PatrolTemplateDefinition() { }

    static String code(int count) { requireCount(count); return CODE_PREFIX + count; }
    static String code(int count,int version) { requireCount(count); return "WF_PATROL_TEMPLATE_V"+version+"_"+count; }

    static void requireCount(int count) {
        if (count < 3 || count > 11) throw new BusinessRuleException("Patrol templates require 3..11 checkpoints");
    }

    static GraphCreateRequest graph(ObjectMapper json, int count, String code) {
        return graph(json,count,code,VERSION);
    }

    static GraphCreateRequest graph(ObjectMapper json, int count, String code, int version) {
        requireCount(count);
        if(version!=1 && version!=VERSION) throw new BusinessRuleException("Unsupported patrol template version");
        List<GraphStepRequest> actions = new ArrayList<>();
        List<GraphTransitionRequest> routes = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            int number = index + 1;
            String choose = "CHECKPOINT_" + number, direct = "MOVE_" + number, group = "GROUP_" + number;
            String sense = "SENSE_" + number;
            String path = "/inputs/checkpoints/" + index;
            var condition = json.createObjectNode();
            condition.putObject("expression").put("path", path + "/endpoint_group_id").put("operator", "EXISTS");
            add(json, actions, StepType.CHECK_CONDITION, choose, 30, condition, json.createObjectNode());
            var trueCondition = json.createObjectNode().put("path", "/result/value").put("operator", "EQ").put("value", true);
            routes.add(new GraphTransitionRequest(choose, group + "_RESOLVE", TransitionOutcome.SUCCESS,
                    trueCondition, 1, false, null));
            routes.add(new GraphTransitionRequest(choose, direct, TransitionOutcome.SUCCESS, null, 2, true, null));
            failures(routes, choose);
            var directInputs = json.createObjectNode();
            directInputs.set("map_id", ref(json, "/inputs/map_id"));
            directInputs.set("endpoint_id", ref(json, path + "/endpoint_id"));
            directInputs.set("max_speed_mps", ref(json, "/inputs/max_speed_mps"));
            add(json, actions, StepType.MOVE, direct, 60, json.createObjectNode().put("mode", "PATROL"), directInputs);
            success(routes, direct, sense); failures(routes, direct);
            EndpointGroupWorkflowSupport.append(json, actions, routes, "PATROL", group,
                    ref(json, path + "/endpoint_group_id"), ref(json, "/inputs/map_id"), "PATROL", .25,
                    60, 2, sense, TerminalStatus.FAILED, version>=2,
                    number==count ? null : "CHECKPOINT_"+(number+1));
            // Keep the shared group's committed SELECT_TARGET reference, while speed remains a Job input.
            for (int position = 0; position < actions.size(); position++) {
                var action = actions.get(position);
                if (!(group + "_MOVE").equals(action.stepCode())) continue;
                ObjectNode params = action.paramsJson().deepCopy(); params.remove("max_speed_mps");
                ObjectNode mapping = action.inputMappingJson().deepCopy();
                mapping.set("max_speed_mps", ref(json, "/inputs/max_speed_mps"));
                actions.set(position, new GraphStepRequest(action.taskCode(), action.stepType(), action.stepCode(),
                        action.stepName(), action.sequenceNo(), action.description(), params, mapping, action.timeoutSec(),
                        action.maxAttempts(), action.retryDelaySec(), action.retryCodesJson(), action.maxVisits(), action.auditLevel()));
            }
            add(json, actions, StepType.EXECUTE_SENSING, sense, 13, json.createObjectNode()
                    .put("sensing_type", "PATROL_FALL").put("window_sec", 3).put("fall_detection", true), json.createObjectNode());
            success(routes, sense, number == count ? null : "CHECKPOINT_" + (number + 1)); failures(routes, sense);
        }
        var schema = json.createObjectNode().put("type", "object").put("additionalProperties", false);
        schema.putArray("required").add("map_id").add("checkpoints");
        var properties = schema.putObject("properties");
        properties.putObject("map_id").put("type", "string").put("format", "uuid");
        properties.putObject("navigation_session_id").put("type", "string").put("format", "uuid");
        properties.putObject("patrol_repeat_count").put("type", "integer").put("minimum", 0).put("maximum", 1000);
        properties.putObject("max_speed_mps").put("type", "number").put("minimum", .05).put("maximum", .35);
        var items = properties.putObject("checkpoints").put("type", "array").put("minItems", count)
                .put("maxItems", count).putObject("items").put("type", "object").put("additionalProperties", false);
        var fields = items.putObject("properties");
        fields.putObject("endpoint_id").put("type", "string").put("format", "uuid");
        fields.putObject("endpoint_group_id").put("type", "string").put("format", "uuid");
        var choices = items.putArray("oneOf");
        choices.addObject().putArray("required").add("endpoint_id");
        choices.addObject().putArray("required").add("endpoint_group_id");
        var ui = json.createObjectNode();
        ui.putObject("patrol_template").put("version", version).put("checkpointCount", count);
        var defaults = json.createObjectNode().put("patrol_repeat_count", 1).put("max_speed_mps", .25);
        int duration = actions.stream().mapToInt(action -> action.timeoutSec() * action.maxVisits()).sum() + 300;
        var workflow = new WorkflowUpsertRequest(code, 1, "Tuần tra " + count + " điểm", "PATROL",
                "Reusable patrol with destinations selected per job or schedule and a 3-second checkpoint observation",
                schema, ui, defaults, json.createObjectNode(), json.createArrayNode(), duration,
                actions.stream().mapToInt(GraphStepRequest::maxVisits).sum(), 3, 3);
        return new GraphCreateRequest(workflow, new GraphRequest("CHECKPOINT_1",
                List.of(new TaskRequest("PATROL", "Patrol checkpoints", 1, null, json.createObjectNode())), actions, routes));
    }

    private static ObjectNode ref(ObjectMapper json, String path) { return EndpointGroupWorkflowSupport.reference(json, path); }
    private static void add(ObjectMapper json, List<GraphStepRequest> actions, StepType type, String code, int timeout,
                            JsonNode params, JsonNode mapping) {
        actions.add(new GraphStepRequest("PATROL", type, code, code, actions.size() + 1, null, params, mapping,
                timeout, 1, 0, json.createArrayNode(), 1, StepAuditLevel.FULL));
    }
    private static void success(List<GraphTransitionRequest> routes, String from, String to) {
        routes.add(new GraphTransitionRequest(from, to, TransitionOutcome.SUCCESS, null, 1, true,
                to == null ? TerminalStatus.SUCCEEDED : null));
    }
    private static void failures(List<GraphTransitionRequest> routes, String from) {
        for (var outcome : List.of(TransitionOutcome.FAILURE, TransitionOutcome.TIMEOUT))
            routes.add(new GraphTransitionRequest(from, null, outcome, null, 1, true, TerminalStatus.FAILED));
    }
}
