package com.mediassistrover.backend.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediassistrover.backend.entity.JobStep;
import com.mediassistrover.backend.entity.StepTransition;
import com.mediassistrover.backend.enums.JobStepStatus;
import com.mediassistrover.backend.enums.TransitionOutcome;
import com.mediassistrover.backend.exception.BusinessRuleException;
import com.mediassistrover.backend.repository.StepTransitionRepository;
import com.mediassistrover.backend.service.WorkflowRuntimeTransitionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WorkflowRuntimeTransitionServiceImpl implements WorkflowRuntimeTransitionService {
    private final StepTransitionRepository transitionRepository;

    @Override
    public StepTransition resolve(JobStep completedStep) {
        TransitionOutcome outcome = switch (completedStep.getStatus()) {
            case SUCCEEDED -> TransitionOutcome.SUCCESS;
            case FAILED -> TransitionOutcome.FAILURE;
            case TIMED_OUT -> TransitionOutcome.TIMEOUT;
            default -> throw new BusinessRuleException("Step has no transition outcome in status " + completedStep.getStatus());
        };
        List<StepTransition> routes = transitionRepository
                .findByFromStepStepIdAndOutcomeOrderByPriorityAsc(completedStep.getStep().getStepId(), outcome);
        ObjectNode root = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        root.set("result", completedStep.getResultJson());
        root.set("error", completedStep.getErrorDetailsJson());
        root.set("inputs", completedStep.getResolvedInputsJson());
        for (StepTransition route : routes) {
            if (!route.isDefaultTransition() && matches(root, route.getConditionJson())) return route;
        }
        return routes.stream().filter(StepTransition::isDefaultTransition).findFirst()
                .orElseThrow(() -> new BusinessRuleException("No matching/default transition for step " + completedStep.getStepCodeSnapshot()));
    }

    private boolean matches(JsonNode root, JsonNode condition) {
        if (condition == null || !condition.isObject()) return false;
        String path = condition.path("path").asText();
        JsonNode actual = path.startsWith("/") ? root.at(path) : dot(root, path);
        String operator = condition.path("operator").asText();
        JsonNode expected = condition.get("value");
        return switch (operator) {
            case "EXISTS" -> !actual.isMissingNode() && !actual.isNull();
            case "EQ" -> equivalent(actual, expected);
            case "NE" -> !equivalent(actual, expected);
            case "GT" -> compare(actual, expected) > 0;
            case "GTE" -> compare(actual, expected) >= 0;
            case "LT" -> compare(actual, expected) < 0;
            case "LTE" -> compare(actual, expected) <= 0;
            case "IN" -> expected != null && expected.isArray()
                    && java.util.stream.StreamSupport.stream(expected.spliterator(), false).anyMatch(v -> equivalent(actual, v));
            default -> throw new BusinessRuleException("Unsupported transition operator: " + operator);
        };
    }

    private JsonNode dot(JsonNode node, String path) {
        JsonNode current = node;
        for (String part : path.split("\\.")) current = current.path(part);
        return current;
    }

    private boolean equivalent(JsonNode left, JsonNode right) {
        if (left == null || left.isMissingNode()) return right == null || right.isNull();
        if (left.isNumber() && right != null && right.isNumber()) return left.decimalValue().compareTo(right.decimalValue()) == 0;
        return left.equals(right);
    }

    private int compare(JsonNode left, JsonNode right) {
        if (left == null || right == null || !left.isNumber() || !right.isNumber())
            throw new BusinessRuleException("Ordered transition comparison requires numeric values");
        return new BigDecimal(left.asText()).compareTo(new BigDecimal(right.asText()));
    }
}
