package com.mediassistrover.backend.knowledge;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mock.web.MockMultipartFile;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class KnowledgeDocumentWorkerTest {
    final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    final OpenAiKnowledgeClient client = mock(OpenAiKnowledgeClient.class);
    final KnowledgeDocumentWorker worker = new KnowledgeDocumentWorker(jdbc, client);
    final String id = UUID.randomUUID().toString();

    @SuppressWarnings("unchecked")
    void work(String state, String file) {
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        when(jdbc.queryForObject(anyString(), any(RowMapper.class), any(Object[].class)))
            .thenReturn(new KnowledgeDocumentWorker.Work(id, "vs_test", "knowledge.txt", "text/plain", file, state, 0));
    }

    @Test void queuedUploadPersistsFileBeforeAttachAndCompletesOnNextPoll() {
        work("QUEUED", null);
        when(jdbc.queryForObject(anyString(), eq(byte[].class), eq(id))).thenReturn("Knowledge".getBytes());
        when(client.upload(anyString(), anyString(), any())).thenReturn("file-test");
        when(client.status("vs_test", "file-test")).thenThrow(new OpenAiKnowledgeClient.Failure("OPENAI_HTTP_404", 404, false));
        worker.process(id);
        var ordered = inOrder(client, jdbc);
        ordered.verify(client).upload(eq("knowledge.txt"), eq("text/plain"), any());
        ordered.verify(jdbc).update(contains("openai_file_id=?"), eq("file-test"), eq(id), anyString());
        ordered.verify(client).attach("vs_test", "file-test", "knowledge.txt");
        verify(jdbc).update(contains("next_attempt_at=DATE_ADD"), eq("PROCESSING"), isNull(), eq(true), eq(id), anyString());

        clearInvocations(jdbc, client);
        work("PROCESSING", "file-test");
        doReturn("completed").when(client).status("vs_test", "file-test");
        worker.process(id);
        verify(client, never()).upload(anyString(), anyString(), any());
        verify(jdbc).update(contains("payload=NULL"), eq("COMPLETED"), isNull(), eq(id), anyString());
    }

    @Test void restartedAttachChecksExistingFileAndAmbiguousUploadIsNotRepeated() {
        work("ATTACHING", "file-test");
        when(client.status("vs_test", "file-test")).thenReturn("completed");
        worker.process(id);
        verify(client, never()).attach(anyString(), anyString(), anyString());
        verify(client, never()).upload(anyString(), anyString(), any());

        clearInvocations(jdbc, client);
        work("QUEUED", null);
        when(jdbc.queryForObject(anyString(), eq(byte[].class), eq(id))).thenReturn(new byte[]{1});
        when(client.upload(anyString(), anyString(), any())).thenThrow(new OpenAiKnowledgeClient.Failure("OPENAI_UNREACHABLE", 0, true));
        worker.process(id);
        verify(jdbc).update(contains("payload=NULL"), eq("RECONCILIATION_REQUIRED"), eq("OPENAI_UNREACHABLE"), eq(id), anyString());
    }

    @Test void indexingFailureIsTerminalAndLockedWorkIsSkipped() {
        work("PROCESSING", "file-test");
        when(client.status("vs_test", "file-test")).thenReturn("failed");
        worker.process(id);
        verify(jdbc).update(contains("payload=NULL"), eq("FAILED"), eq("OPENAI_INDEXING_FAILED"), eq(id), anyString());
        clearInvocations(client);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(0);
        worker.process(id);
        verifyNoInteractions(client);
    }

    @Test @SuppressWarnings("unchecked") void concurrentDuplicateReturnsExistingDocument() {
        var existing = new KnowledgeDocumentService.Document(UUID.randomUUID(), "vs_test", "knowledge.txt", "text/plain",
            9, "a".repeat(64), UUID.randomUUID(), "file-test", "PROCESSING", null, Instant.now(), Instant.now());
        doThrow(new DuplicateKeyException("duplicate hash")).when(jdbc).update(anyString(), any(Object[].class));
        when(jdbc.queryForObject(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(existing);
        var service = new KnowledgeDocumentService(jdbc, client, "vs_test");
        var actual = service.upload(new MockMultipartFile("file", "knowledge.txt", "text/plain", "Knowledge".getBytes()), UUID.randomUUID());
        assertEquals(existing, actual);
        verify(client, never()).upload(anyString(), anyString(), any());
    }
}
