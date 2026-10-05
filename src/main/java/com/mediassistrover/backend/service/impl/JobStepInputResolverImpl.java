package com.mediassistrover.backend.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediassistrover.backend.entity.Job;
import com.mediassistrover.backend.entity.JobStep;
import com.mediassistrover.backend.enums.JobStepStatus;
import com.mediassistrover.backend.exception.BusinessRuleException;
import com.mediassistrover.backend.repository.JobStepRepository;
import com.mediassistrover.backend.service.JobStepInputResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class JobStepInputResolverImpl implements JobStepInputResolver {
    private final JobStepRepository jobStepRepository;
    private final ObjectMapper objectMapper;

    @Override
    public JsonNode resolve(JobStep current) {
        ObjectNode root = (ObjectNode) runtimeContext(current);

        JsonNode params = resolveNode(current.getStep().getParamsJson(), root);
        JsonNode mapping = resolveNode(current.getStep().getInputMappingJson(), root);
        if (!params.isObject() || !mapping.isObject()) {
            throw new BusinessRuleException("Step params and inputMapping must resolve to JSON objects");
        }
        ObjectNode resolved = ((ObjectNode) params).deepCopy();
        resolved.setAll((ObjectNode) mapping);
        return resolved;
    }

    @Override
    public JsonNode runtimeContext(JobStep current) {
        Job job = current.getJobTask().getJob();
        ObjectNode root = objectMapper.createObjectNode();
        root.set("inputs", copy(job.getInputJson()));
        root.set("vars", copy(job.getContextJson()));
        ObjectNode outputs = root.putObject("outputs");

        Map<String, JobStep> latest = new LinkedHashMap<>();
        jobStepRepository.findByJobId(job.getJobId()).stream()
                .filter(step -> step.getStatus() == JobStepStatus.SUCCEEDED && step.getResultJson() != null)
                .sorted(Comparator.comparingLong(JobStep::getExecutionSequenceNo))
                .forEach(step -> latest.put(step.getStepCodeSnapshot(), step));
        latest.forEach((code, step) -> outputs.set(code, step.getResultJson().deepCopy()));
        return root;
    }

    private JsonNode resolveNode(JsonNode node, ObjectNode root) {
        if (node == null || node.isNull()) return objectMapper.nullNode();
        if (node.isObject() && node.size() == 1 && node.has("$ref")) {
            String pointer = node.path("$ref").asText();
            JsonNode value = root.at(pointer);
            if (value.isMissingNode()) {
                throw new BusinessRuleException("Cannot resolve Step input reference: " + pointer);
            }
            return value.deepCopy();
        }
        if (node.isObject()) {
            ObjectNode result = objectMapper.createObjectNode();
            node.fields().forEachRemaining(entry -> result.set(entry.getKey(), resolveNode(entry.getValue(), root)));
            return result;
        }
        if (node.isArray()) {
            var result = objectMapper.createArrayNode();
            node.forEach(item -> result.add(resolveNode(item, root)));
            return result;
        }
        return node.deepCopy();
    }

    private JsonNode copy(JsonNode node) {
        return node == null ? objectMapper.createObjectNode() : node.deepCopy();
    }
}
