package com.mediassistrover.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediassistrover.backend.dto.workflow.request.WorkflowRequests.*;
import com.mediassistrover.backend.enums.*;

import java.util.List;
import java.util.Locale;

/** Builds a bounded candidate loop. NavigationTargetService owns permission to advance it. */
public final class EndpointGroupWorkflowSupport {
    public static final int MAX_CANDIDATES = 10;
    private EndpointGroupWorkflowSupport() { }

    public record Branch(String startStepCode, String moveStepCode, String selectorStepCode, int maxVisits) { }

    public static Branch append(ObjectMapper json, List<GraphStepRequest> steps,
                                List<GraphTransitionRequest> routes, String task, String prefix,
                                JsonNode groupId, JsonNode mapId, String moveMode, double speed,
                                int timeout, int maxCandidates, String onReached, TerminalStatus failure) {
        return append(json,steps,routes,task,prefix,groupId,mapId,moveMode,speed,timeout,maxCandidates,onReached,failure,false,null);
    }

    public static Branch append(ObjectMapper json, List<GraphStepRequest> steps,
                                List<GraphTransitionRequest> routes, String task, String prefix,
                                JsonNode groupId, JsonNode mapId, String moveMode, double speed,
                                int timeout, int maxCandidates, String onReached, TerminalStatus failure,
                                boolean skipBlockedCheckpoint, String onBlocked) {
        if (maxCandidates < 1 || maxCandidates > MAX_CANDIDATES)
            throw new IllegalArgumentException("Endpoint group candidate budget must be 1.." + MAX_CANDIDATES);
        String resolve = prefix + "_RESOLVE", init = prefix + "_INIT", select = prefix + "_SELECT";
        String has = prefix + "_HAS", move = prefix + "_MOVE", next = prefix + "_NEXT";
        String index = "/vars/" + prefix.toLowerCase(Locale.ROOT) + "_target_index";
        var resolveInputs = json.createObjectNode(); resolveInputs.set("endpoint_group_id", groupId.deepCopy());
        add(json, steps, task, StepType.DATA_SERVICE, resolve, 30, 1,
                json.createObjectNode().put("service_key", "RESOLVE_TARGETS"), resolveInputs);
        var initialize = json.createObjectNode(); initialize.putArray("operations").addObject().put("path", index).put("value", 0);
        add(json, steps, task, StepType.SET_CONTEXT, init, 30, 1, initialize, json.createObjectNode());
        var selection = json.createObjectNode();
        selection.set("targets", reference(json, "/outputs/" + resolve + "/data/targets"));
        selection.set("index", reference(json, index));
        add(json, steps, task, StepType.DATA_SERVICE, select, 30, maxCandidates + 1,
                json.createObjectNode().put("service_key", "SELECT_TARGET"), selection);
        var available = json.createObjectNode(); available.putObject("expression")
                .put("path", "/outputs/" + select + "/data/has_target").put("operator", "EQ").put("value", true);
        add(json, steps, task, StepType.CHECK_CONDITION, has, 30, maxCandidates + 1, available, json.createObjectNode());
        var moveInputs = json.createObjectNode(); moveInputs.set("map_id", mapId.deepCopy());
        moveInputs.set("endpoint_id", reference(json, "/outputs/" + select + "/data/endpoint_id"));
        add(json, steps, task, StepType.MOVE, move, timeout, maxCandidates,
                json.createObjectNode().put("mode", moveMode).put("max_speed_mps", speed), moveInputs);
        var advance = json.createObjectNode(); advance.putArray("operations").addObject()
                .put("path", index).put("operation", "INCREMENT").put("value", 1);
        add(json, steps, task, StepType.SET_CONTEXT, next, 30, maxCandidates, advance, json.createObjectNode());
        route(routes, resolve, init, TransitionOutcome.SUCCESS, null, 1, true, null);
        route(routes, init, select, TransitionOutcome.SUCCESS, null, 1, true, null);
        route(routes, select, has, TransitionOutcome.SUCCESS, null, 1, true, null);
        route(routes, has, move, TransitionOutcome.SUCCESS,
                condition(json, "/result/value", true), 1, false, null);
        route(routes, has, skipBlockedCheckpoint ? onBlocked : null, TransitionOutcome.SUCCESS, null, 2, true,
                skipBlockedCheckpoint ? onBlocked==null ? TerminalStatus.SUCCEEDED : null : failure);
        route(routes, move, onReached, TransitionOutcome.SUCCESS, null, 1, true,
                onReached == null ? TerminalStatus.SUCCEEDED : null);
        route(routes, move, next, TransitionOutcome.FAILURE,
                condition(json, "/error/errorCode", "PATH_UNAVAILABLE"), 1, false, null);
        route(routes, next, select, TransitionOutcome.SUCCESS, null, 1, true, null);
        for (String from : List.of(resolve, init, select, has, move, next)) {
            route(routes, from, null, TransitionOutcome.FAILURE, null, from.equals(move) ? 2 : 1, true, failure);
            route(routes, from, null, TransitionOutcome.TIMEOUT, null, 1, true, failure);
        }
        return new Branch(resolve, move, select, 4 * maxCandidates + 4);
    }

    public static ObjectNode reference(ObjectMapper json, String path) {
        return json.createObjectNode().put("$ref", path);
    }

    private static ObjectNode condition(ObjectMapper json, String path, Object value) {
        var condition = json.createObjectNode().put("path", path).put("operator", "EQ");
        condition.set("value", json.valueToTree(value)); return condition;
    }

    private static void add(ObjectMapper json, List<GraphStepRequest> steps, String task, StepType type,
                            String code, int timeout, int visits, JsonNode params, JsonNode mapping) {
        steps.add(new GraphStepRequest(task, type, code, code, steps.size() + 1, null, params, mapping,
                timeout, 1, 0, json.createArrayNode(), visits, StepAuditLevel.FULL));
    }

    private static void route(List<GraphTransitionRequest> routes, String from, String to,
                              TransitionOutcome outcome, JsonNode condition, int priority,
                              boolean defaultRoute, TerminalStatus terminal) {
        routes.add(new GraphTransitionRequest(from, to, outcome, condition, priority, defaultRoute, terminal));
    }
}
