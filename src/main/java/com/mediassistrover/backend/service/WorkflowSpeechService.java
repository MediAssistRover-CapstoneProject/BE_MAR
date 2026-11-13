package com.mediassistrover.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediassistrover.backend.dto.workflow.request.WorkflowRequests.*;
import com.mediassistrover.backend.dto.workflow.response.WorkflowResponses.WorkflowDetailResponse;
import com.mediassistrover.backend.entity.Step;
import com.mediassistrover.backend.entity.Workflow;
import com.mediassistrover.backend.enums.StepType;
import com.mediassistrover.backend.enums.WorkflowStatus;
import com.mediassistrover.backend.exception.*;
import com.mediassistrover.backend.mqtt.MqttRolloutService;
import com.mediassistrover.backend.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class WorkflowSpeechService {
    private static final Set<String> FIELDS = Set.of("text", "language_code", "speed_wpm", "volume");
    private final WorkflowService workflows;
    private final WorkflowRepository workflowRepository;
    private final StepRepository stepRepository;
    private final WorkflowValidationService validation;
    private final MqttRolloutService rollout;
    private final EntityManager entityManager;
    private final Clock clock;

    @Transactional
    public WorkflowDetailResponse save(UUID id, WorkflowSpeechRequest request, UUID actorId, RequestMetadata metadata) {
        // Use the same lock order as publishing and runtime creation. All step edits,
        // cloning, validation and publishing commit together.
        rollout.lockForWorkflowMaintenance();
        Workflow source = entityManager.find(Workflow.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (source == null) throw new ResourceNotFoundException("Workflow not found: " + id);
        if (source.getRowVersion() != request.expectedRowVersion())
            throw new ConflictException("Workflow đã thay đổi. Tải lại trước khi lưu lời nói.");
        if (source.getStatus() == WorkflowStatus.ARCHIVED)
            throw new BusinessRuleException("Workflow đã ngừng sử dụng. Chọn phiên bản đang dùng hoặc bản nháp.");
        if (source.getStatus() == WorkflowStatus.PUBLISHED && !request.publish())
            throw new BusinessRuleException("Chọn Lưu và áp dụng để tạo phiên bản mới cho workflow đã phát hành.");

        Map<String, Step> sourceSteps = stepsByCode(id);
        Set<String> changed = new HashSet<>();
        for (SpeechStepChange change : request.steps()) {
            if (!changed.add(change.stepCode())) throw new BusinessRuleException("Bước bị trùng: " + change.stepCode());
            Step step = sourceSteps.get(change.stepCode());
            if (step == null || !Objects.equals(step.getParamsJson(), change.expectedParamsJson())
                    || !Objects.equals(step.getInputMappingJson(), change.expectedInputMappingJson()))
                throw new ConflictException("Bước " + change.stepCode() + " đã thay đổi. Tải lại trước khi lưu.");
            validateChange(step, change.data());
        }

        UUID targetId = id;
        if (source.getStatus() == WorkflowStatus.PUBLISHED) {
            Workflow latest = workflowRepository.findFirstByWorkflowCodeIgnoreCaseAndStatusAndAcceptsNewJobsTrueOrderByVersionNoDesc(
                    source.getWorkflowCode(), WorkflowStatus.PUBLISHED).orElse(source);
            if (!latest.getWorkflowId().equals(id))
                throw new ConflictException("Đã có phiên bản phát hành mới hơn. Tải lại và chọn phiên bản mới nhất.");
            targetId = workflows.cloneVersion(id, null, actorId, metadata).workflow().workflowId();
        }
        Map<String, Step> targetSteps = stepsByCode(targetId);
        for (SpeechStepChange change : request.steps()) {
            Step step = targetSteps.get(change.stepCode());
            // Input mappings replace top-level params, including the entire data object.
            boolean mapped = step.getInputMappingJson().has("data");
            ObjectNode container = (ObjectNode) (mapped ? step.getInputMappingJson() : step.getParamsJson()).deepCopy();
            container.set("data", change.data().deepCopy());
            if (mapped) step.setInputMappingJson(container);
            else step.setParamsJson(container);
            workflows.updateStep(targetId, step.getStepId(), stepRequest(step), actorId, metadata);
        }
        if (request.publish()) {
            workflows.validate(targetId, actorId, metadata);
            workflows.publish(targetId, actorId, metadata);
        } else {
            Workflow target = entityManager.find(Workflow.class, targetId);
            validation.validateAndCompile(target);
            target.setUpdatedAt(Instant.now(clock));
        }
        workflowRepository.flush();
        return workflows.get(targetId);
    }

    private Map<String, Step> stepsByCode(UUID id) {
        Map<String, Step> result = new HashMap<>();
        for (Step step : stepRepository.findByWorkflowWorkflowIdOrderBySequenceNoAsc(id)) result.put(step.getStepCode(), step);
        return result;
    }

    private void validateChange(Step step, JsonNode data) {
        JsonNode params = step.getParamsJson(), mapping = step.getInputMappingJson();
        JsonNode action = mapping.has("action") ? mapping.get("action") : params.path("action");
        JsonNode previous = mapping.has("data") ? mapping.get("data") : params.path("data");
        if (step.getStepType() != StepType.INTERACT || !"SPEAK".equals(action.asText())
                || !previous.isObject() || previous.has("$ref") || previous.has("$template") || !data.isObject())
            throw new BusinessRuleException("Bước " + step.getStepCode() + " cần chỉnh nguồn dữ liệu trong trình thiết kế workflow.");
        Set<String> keys = new HashSet<>();
        previous.fieldNames().forEachRemaining(keys::add);
        data.fieldNames().forEachRemaining(keys::add);
        for (String field : keys) {
            JsonNode before = previous.get(field), value = data.get(field);
            if (Objects.equals(before, value)) continue;
            if (!FIELDS.contains(field) || before != null && before.isContainerNode())
                throw new BusinessRuleException("Giữ nguyên nguồn dữ liệu của " + field + " trong bước " + step.getStepCode());
            if (value == null && !field.equals("text")) continue;
            boolean valid = switch (field) {
                case "text" -> value != null && value.isTextual() && !value.asText().isBlank() && value.asText().length() <= 500;
                case "language_code" -> value != null && value.isTextual() && Set.of("vi", "en").contains(value.asText());
                case "speed_wpm" -> value != null && value.isIntegralNumber() && value.canConvertToInt() && value.intValue() >= 80 && value.intValue() <= 220;
                case "volume" -> value != null && value.isIntegralNumber() && value.canConvertToInt() && value.intValue() >= 0 && value.intValue() <= 100;
                default -> false;
            };
            if (!valid) throw new BusinessRuleException("Giá trị " + field + " không hợp lệ ở bước " + step.getStepCode());
        }
    }

    private StepRequest stepRequest(Step step) {
        return new StepRequest(step.getTask().getTaskId(), step.getStepType().name(), step.getStepCode(), step.getStepName(),
                step.getSequenceNo(), step.getDescription(), step.isEnabled(), step.getParamsJson(), step.getInputMappingJson(),
                step.getTimeoutSec(), step.getMaxAttempts(), step.getRetryDelaySec(), step.getRetryCodesJson(),
                step.getMaxVisits(), step.getAuditLevel());
    }
}
