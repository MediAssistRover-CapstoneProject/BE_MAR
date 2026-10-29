package com.mediassistrover.backend.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediassistrover.backend.dto.job.request.BulkRecoveryRequest;
import com.mediassistrover.backend.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BulkRecoveryServiceTest {
    private final MqttOperationRecoveryService recovery = mock(MqttOperationRecoveryService.class);
    private final ObjectMapper json = new ObjectMapper();
    private final BulkRecoveryService service = new BulkRecoveryService(recovery, json);

    private BulkRecoveryRequest.Item item() {
        return new BulkRecoveryRequest.Item(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID().toString(), null);
    }

    @Test void previewNeverReconciles() {
        var item = item();
        when(recovery.evidence(item.jobId(), null)).thenReturn(json.createObjectNode().put("status", "RECOVERY_REQUIRED"));
        var result = service.close(new BulkRecoveryRequest(true, List.of(item)), null, null);
        assertEquals("EVIDENCE_ONLY", result.path("results").get(0).path("result").asText());
        assertEquals(0, result.path("closed").asInt());
        verify(recovery).evidence(item.jobId(), null);
        verifyNoMoreInteractions(recovery);
    }

    @Test void blockedJobDoesNotPreventNextJobAndKeysArePreserved() {
        var first = item(); var second = item();
        when(recovery.reconcile(first.jobId(), first.jobStepId(), first.idempotencyKey(), null, null, null))
            .thenThrow(new BusinessRuleException("Stop evidence missing"));
        when(recovery.reconcile(second.jobId(), second.jobStepId(), second.idempotencyKey(), null, null, null))
            .thenReturn(json.createObjectNode().put("status", "CANCELLED"));
        var result = service.close(new BulkRecoveryRequest(false, List.of(first, second)), null, null);
        assertEquals(1, result.path("blocked").asInt());
        assertEquals(1, result.path("closed").asInt());
        assertEquals("Stop evidence missing", result.path("results").get(0).path("reason").asText());
        verify(recovery).reconcile(second.jobId(), second.jobStepId(), second.idempotencyKey(), null, null, null);
    }

    @Test void duplicateJobsAreRejectedBeforeAnyWrites() {
        var item = item();
        assertThrows(BusinessRuleException.class, () -> service.close(new BulkRecoveryRequest(false, List.of(item, item)), null, null));
        verifyNoInteractions(recovery);
    }
}
