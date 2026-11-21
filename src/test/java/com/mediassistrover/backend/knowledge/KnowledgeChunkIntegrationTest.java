package com.mediassistrover.backend.knowledge;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Runs only against the explicit disposable database. Never application DB config. */
@EnabledIfEnvironmentVariable(named="KNOWLEDGE_TEST_JDBC_URL",matches=".*mar_knowledge_test.*")
class KnowledgeChunkIntegrationTest {
    static JdbcTemplate jdbc;
    static DataSourceTransactionManager transactions;
    static UUID actor=UUID.randomUUID();
    OpenAiKnowledgeClient client;
    KnowledgePreprocessor processor;
    KnowledgeDocumentService service;
    KnowledgeChunkWorker worker;
    Set<String> attached;
    String store;

    @BeforeAll static void schema() {
        var ds=new DriverManagerDataSource(System.getenv("KNOWLEDGE_TEST_JDBC_URL"),"root","knowledge-local-test");
        jdbc=new JdbcTemplate(ds); transactions=new DataSourceTransactionManager(ds);
        jdbc.execute("CREATE TABLE Account(account_id BINARY(16) PRIMARY KEY)");
        new ResourceDatabasePopulator(new FileSystemResource("src/main/resources/db/migration/V60__knowledge_documents.sql"),
            new FileSystemResource("src/main/resources/db/migration/V65__knowledge_document_chunks.sql")).execute(ds);
        jdbc.update("INSERT INTO Account VALUES(UUID_TO_BIN(?))",actor.toString());
    }
    @BeforeEach void setup() {
        store="vs_"+UUID.randomUUID().toString().replace("-","");
        client=mock(OpenAiKnowledgeClient.class); processor=mock(KnowledgePreprocessor.class);
        service=new KnowledgeDocumentService(jdbc,client,store);
        worker=new KnowledgeChunkWorker(jdbc,client,processor,transactions);
        when(client.configured()).thenReturn(true);
        when(processor.extract(anyString(),any())).thenReturn(List.of(new KnowledgePreprocessor.Chunk("Giờ khám 7 giờ.","a".repeat(64)),
            new KnowledgePreprocessor.Chunk("Nghỉ Chủ nhật.","b".repeat(64))));
        when(client.upload(anyString(),anyString(),any())).thenAnswer(a->"file-"+UUID.randomUUID().toString());
        attached=new HashSet<>();
        when(client.status(anyString(),anyString())).thenAnswer(a->{
            if (!attached.contains(a.getArgument(1))) throw new OpenAiKnowledgeClient.Failure("OPENAI_HTTP_404",404,false);
            return "completed";
        });
        doAnswer(a->{attached.add(a.getArgument(1));return null;}).when(client).attachChunk(anyString(),anyString(),anyString());
    }
    MockMultipartFile file(String text) { return new MockMultipartFile("file","guide.txt","text/plain",text.getBytes(StandardCharsets.UTF_8)); }
    UUID upload() { return service.upload(file(UUID.randomUUID().toString()),actor).id(); }
    void step(UUID id) {
        jdbc.update("UPDATE KnowledgeDocument SET next_attempt_at=UTC_TIMESTAMP(6) WHERE document_id=?",id.toString());
        worker.process(id.toString());
    }
    List<String> files(UUID id) { return jdbc.queryForList("SELECT openai_file_id FROM KnowledgeDocumentChunk WHERE document_id=? AND openai_file_id IS NOT NULL",String.class,id.toString()); }
    void ready(UUID id) { for(int n=0;n<12 && !service.get(id).status().equals("COMPLETED");n++) step(id); assertEquals("COMPLETED",service.get(id).status()); }

    @Test void publishOnlyAfterAllChunksAndRetainOriginal() {
        UUID id=upload();step(id);step(id);
        assertEquals(2,service.chunks(id).size());
        assertTrue(service.resolve(store,files(id)).isEmpty());
        ready(id);
        assertEquals(2,service.resolve(store,files(id)).size());
        assertNotNull(jdbc.queryForObject("SELECT payload FROM KnowledgeDocument WHERE document_id=?",byte[].class,id.toString()));
        verify(client,times(2)).upload(eq("chunk.txt"),eq("text/plain"),any());
        step(id); // terminal job must not upload again
        verify(client,times(2)).upload(anyString(),anyString(),any());
    }

    @Test void uncertainUploadIsNotRetriedAndDeletionDuringUploadCannotPublish() {
        UUID id=upload();step(id);
        when(client.upload(anyString(),anyString(),any())).thenThrow(new OpenAiKnowledgeClient.Failure("OPENAI_UNREACHABLE",0,true));
        step(id);step(id);
        assertEquals("RECONCILIATION_REQUIRED",service.get(id).status());
        verify(client,times(1)).upload(anyString(),anyString(),any());
        assertThrows(RuntimeException.class,()->service.retry(id));

        UUID cancelled=upload();step(cancelled);
        doAnswer(a->{service.delete(cancelled);return "file-late";}).when(client).upload(anyString(),anyString(),any());
        step(cancelled);
        assertEquals("CANCELLED",service.get(cancelled).status());
        assertTrue(service.resolve(store,List.of("file-late")).isEmpty());
        worker.cleanup();
        verify(client).detach(store,"file-late");
        verify(client,never()).attachChunk(eq(store),eq("file-late"),anyString());
    }

    @Test void replacementPublishesAtomicallyAndOldFileIsDetached() {
        UUID old=upload();ready(old);
        var oldFiles=files(old);
        UUID replacement=new TransactionTemplate(transactions).execute(s->service.replace(old,file("New policy"),actor).id());
        assertEquals(2,service.resolve(store,oldFiles).size());
        ready(replacement);
        assertEquals("CANCELLED",service.get(old).status());
        assertTrue(service.resolve(store,oldFiles).isEmpty());
        assertEquals(2,service.resolve(store,files(replacement)).size());
        worker.cleanup();
        for(String file:oldFiles) verify(client).detach(store,file);
    }

    @Test void cancelledExtractionAndFailedExtractionNeverPublish() {
        UUID id=upload();
        when(processor.extract(anyString(),any())).thenAnswer(a->{service.delete(id);return List.of(new KnowledgePreprocessor.Chunk("Late result","a".repeat(64)));});
        step(id);
        assertEquals("CANCELLED",service.get(id).status());
        assertTrue(service.chunks(id).isEmpty());
        UUID failed=upload();
        when(processor.extract(anyString(),any())).thenThrow(new KnowledgePreprocessor.Failure("KNOWLEDGE_OCR_LOW_QUALITY"));
        step(failed);
        assertEquals("FAILED",service.get(failed).status());
        assertEquals("KNOWLEDGE_OCR_LOW_QUALITY",service.get(failed).errorCode());
        new TransactionTemplate(transactions).executeWithoutResult(s->service.retry(failed));
        assertEquals("QUEUED",service.get(failed).status());
        verify(client,never()).upload(anyString(),anyString(),any());
    }

    @Test void leasePreventsConcurrentWorkerAndExpiredUploadNeedsReconciliation() {
        UUID id=upload();step(id);
        jdbc.update("UPDATE KnowledgeDocument SET lease_token='other',lease_until=DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 120 SECOND) WHERE document_id=?",id.toString());
        step(id);verify(client,never()).upload(anyString(),anyString(),any());
        jdbc.update("UPDATE KnowledgeDocument SET lease_until=DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 1 SECOND) WHERE document_id=?",id.toString());
        jdbc.update("UPDATE KnowledgeDocumentChunk SET status='UPLOADING' WHERE document_id=? AND ordinal_no=0",id.toString());
        step(id);assertEquals("RECONCILIATION_REQUIRED",service.get(id).status());
    }
}
