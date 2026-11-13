package com.mediassistrover.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediassistrover.backend.dto.workflow.request.WorkflowRequests.*;
import com.mediassistrover.backend.dto.workflow.response.WorkflowResponses.*;
import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.exception.*;
import com.mediassistrover.backend.mqtt.MqttRolloutService;
import com.mediassistrover.backend.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkflowSpeechServiceTest {
    final ObjectMapper json = new ObjectMapper();
    final WorkflowService workflows = mock(WorkflowService.class);
    final WorkflowRepository workflowRepository = mock(WorkflowRepository.class);
    final StepRepository steps = mock(StepRepository.class);
    final WorkflowValidationService validation = mock(WorkflowValidationService.class);
    final MqttRolloutService rollout = mock(MqttRolloutService.class);
    final EntityManager em = mock(EntityManager.class);
    final Clock clock = Clock.fixed(Instant.parse("2026-11-13T00:00:00Z"), ZoneOffset.UTC);
    final WorkflowSpeechService service = new WorkflowSpeechService(workflows, workflowRepository, steps, validation, rollout, em, clock);
    final UUID id = UUID.randomUUID(), actor = UUID.randomUUID(), taskId = UUID.randomUUID();
    final RequestMetadata metadata = new RequestMetadata("127.0.0.1", "test", "speech");
    final Workflow workflow = Workflow.builder().workflowId(id).workflowCode("DELIVERY").rowVersion(4).status(WorkflowStatus.DRAFT).build();
    Step first, second;

    Step speech(String code) {
        return Step.builder().stepId(UUID.randomUUID()).workflow(workflow).task(Task.builder().taskId(taskId).build())
                .stepType(StepType.INTERACT).stepCode(code).stepName(code).sequenceNo(1).enabled(true)
                .paramsJson(json.createObjectNode().put("action", "SPEAK").put("prompt_key", code)
                        .set("data", json.createObjectNode().put("text", "Original").put("volume", 0)))
                .inputMappingJson(json.createObjectNode()).retryCodesJson(json.createArrayNode())
                .timeoutSec(30).maxAttempts(1).maxVisits(1).auditLevel(StepAuditLevel.BASIC).build();
    }
    SpeechStepChange change(Step step, JsonNode data) {
        return new SpeechStepChange(step.getStepCode(), step.getParamsJson().deepCopy(), step.getInputMappingJson().deepCopy(), data);
    }
    ObjectNode data(String text) { return json.createObjectNode().put("text", text).put("volume", 0); }
    WorkflowSpeechRequest request(boolean publish, SpeechStepChange... changes) {
        return new WorkflowSpeechRequest(4L, publish, List.of(changes));
    }
    @BeforeEach void setup() {
        first = speech("ARRIVE"); second = speech("PICKUP");
        when(em.find(Workflow.class, id, LockModeType.PESSIMISTIC_WRITE)).thenReturn(workflow);
        when(em.find(Workflow.class, id)).thenReturn(workflow);
        when(steps.findByWorkflowWorkflowIdOrderBySequenceNoAsc(id)).thenReturn(List.of(first, second));
    }

    @Test void editsAllDraftSpeechWithoutChangingOtherStepParameters() {
        service.save(id, request(false, change(first, data("Xin chào")), change(second, data("Mời nhận thuốc"))), actor, metadata);
        assertEquals("Xin chào", first.getParamsJson().path("data").path("text").asText());
        assertEquals(0, first.getParamsJson().path("data").path("volume").asInt());
        assertEquals("ARRIVE", first.getParamsJson().path("prompt_key").asText());
        assertEquals("Mời nhận thuốc", second.getParamsJson().path("data").path("text").asText());
        verify(workflows).updateStep(eq(id), eq(first.getStepId()), any(), eq(actor), eq(metadata));
        verify(workflows).updateStep(eq(id), eq(second.getStepId()), any(), eq(actor), eq(metadata));
        verify(validation).validateAndCompile(workflow);
        verify(workflows, never()).publish(any(), any(), any());
        verify(workflowRepository).flush();
        assertEquals(clock.instant(), workflow.getUpdatedAt());
    }

    @Test void validatesEveryChangeBeforeMutatingAnyStep() {
        var before = first.getParamsJson().deepCopy();
        var changes = request(false, change(first, data("Valid")), change(second, data("Invalid").put("volume", 101)));
        assertThrows(BusinessRuleException.class, () -> service.save(id, changes, actor, metadata));
        assertEquals(before, first.getParamsJson());
        verifyNoInteractions(workflows);
    }

    @Test void detectsConcurrentChangesEvenWhenWorkflowRowVersionHasNotChanged() {
        var stale = change(first, data("New"));
        ((ObjectNode) first.getParamsJson().get("data")).put("text", "Another editor");
        assertThrows(ConflictException.class, () -> service.save(id, request(false, stale), actor, metadata));
        verifyNoInteractions(workflows);
        workflow.setRowVersion(5);
        assertThrows(ConflictException.class, () -> service.save(id, request(false, change(first, data("New"))), actor, metadata));
    }

    @Test void updatesMappedDataAndPreservesBoundFieldsAndFallbackParams() {
        ObjectNode mapped = json.createObjectNode().put("volume", 70);
        mapped.set("text", json.createObjectNode().put("$ref", "/inputs/message"));
        ((ObjectNode) first.getInputMappingJson()).set("data", mapped);
        var before = first.getParamsJson().deepCopy();
        service.save(id, request(false, change(first, mapped.deepCopy().put("volume", 50))), actor, metadata);
        assertEquals(before, first.getParamsJson());
        assertEquals(50, first.getInputMappingJson().path("data").path("volume").asInt());
        assertEquals("/inputs/message", first.getInputMappingJson().path("data").path("text").path("$ref").asText());
        assertThrows(BusinessRuleException.class, () -> service.save(id, request(false, change(first, data("Erase binding"))), actor, metadata));
    }

    @Test void refusesDynamicWholeDataNonSpeechDuplicateAndUnknownSteps() {
        ((ObjectNode) first.getInputMappingJson()).set("data", json.createObjectNode().put("$ref", "/inputs/speech"));
        assertThrows(BusinessRuleException.class, () -> service.save(id, request(false, change(first, data("New"))), actor, metadata));
        first.setInputMappingJson(json.createObjectNode());
        ((ObjectNode) first.getParamsJson()).put("action", "CONFIRM");
        assertThrows(BusinessRuleException.class, () -> service.save(id, request(false, change(first, data("New"))), actor, metadata));
        ((ObjectNode) first.getParamsJson()).put("action", "SPEAK");
        var valid = change(first, data("New"));
        assertThrows(BusinessRuleException.class, () -> service.save(id, request(false, valid, valid), actor, metadata));
        assertThrows(ConflictException.class, () -> service.save(id, request(false,
                new SpeechStepChange("MISSING", json.createObjectNode(), json.createObjectNode(), data("New"))), actor, metadata));
        verifyNoInteractions(workflows);
    }

    @Test void publishedWorkflowClonesThenValidatesAndPublishesNewVersion() {
        workflow.setStatus(WorkflowStatus.PUBLISHED);
        UUID nextId = UUID.randomUUID();
        var clonedStep = speech("ARRIVE");
        var nextWorkflow = mock(WorkflowResponse.class);
        when(nextWorkflow.workflowId()).thenReturn(nextId);
        when(workflows.cloneVersion(id, null, actor, metadata)).thenReturn(new WorkflowDetailResponse(nextWorkflow, List.of(), List.of(), List.of(), null));
        when(steps.findByWorkflowWorkflowIdOrderBySequenceNoAsc(nextId)).thenReturn(List.of(clonedStep));
        when(workflowRepository.findFirstByWorkflowCodeIgnoreCaseAndStatusAndAcceptsNewJobsTrueOrderByVersionNoDesc("DELIVERY", WorkflowStatus.PUBLISHED))
                .thenReturn(Optional.of(workflow));
        var before = first.getParamsJson().deepCopy();
        service.save(id, request(true, change(first, data("Published"))), actor, metadata);
        assertEquals(before, first.getParamsJson());
        assertEquals("Published", clonedStep.getParamsJson().path("data").path("text").asText());
        var order = inOrder(workflows);
        order.verify(workflows).cloneVersion(id, null, actor, metadata);
        order.verify(workflows).updateStep(eq(nextId), eq(clonedStep.getStepId()), any(), eq(actor), eq(metadata));
        order.verify(workflows).validate(nextId, actor, metadata);
        order.verify(workflows).publish(nextId, actor, metadata);
        order.verify(workflows).get(nextId);
    }

    @Test void rejectsSupersededPublishedVersionsAndDraftSaveOfPublishedWorkflow() {
        workflow.setStatus(WorkflowStatus.PUBLISHED);
        assertThrows(BusinessRuleException.class, () -> service.save(id, request(false, change(first, data("New"))), actor, metadata));
        when(workflowRepository.findFirstByWorkflowCodeIgnoreCaseAndStatusAndAcceptsNewJobsTrueOrderByVersionNoDesc("DELIVERY", WorkflowStatus.PUBLISHED))
                .thenReturn(Optional.of(Workflow.builder().workflowId(UUID.randomUUID()).build()));
        assertThrows(ConflictException.class, () -> service.save(id, request(true, change(first, data("New"))), actor, metadata));
        verifyNoInteractions(workflows);
    }

    @Test void publicationFailurePropagatesToTransactionInsteadOfReturningSuccess() {
        doThrow(new BusinessRuleException("Robot capability missing")).when(workflows).publish(id, actor, metadata);
        assertThrows(BusinessRuleException.class, () -> service.save(id, request(true, change(first, data("New"))), actor, metadata));
        verify(workflowRepository, never()).flush();
        verify(workflows, never()).get(any());
    }
}
