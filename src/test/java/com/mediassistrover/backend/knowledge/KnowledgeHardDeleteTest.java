package com.mediassistrover.backend.knowledge;

import com.mediassistrover.backend.exception.ConflictException;
import com.mediassistrover.backend.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

public class KnowledgeHardDeleteTest {
    private JdbcTemplate jdbc;
    private TransactionTemplate tx;
    private KnowledgeDocumentService service;

    @BeforeEach void setup() {
        var source=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+
            ";MODE=MySQL;DATABASE_TO_UPPER=FALSE;DB_CLOSE_DELAY=-1","sa","");
        jdbc=new JdbcTemplate(source);
        tx=new TransactionTemplate(new DataSourceTransactionManager(source));
        jdbc.execute("CREATE ALIAS UTC_TIMESTAMP FOR \"com.mediassistrover.backend.knowledge.KnowledgeHardDeleteTest.utcTimestamp\"");
        jdbc.execute("""
            CREATE TABLE KnowledgeDocument (
                document_id VARCHAR(36) PRIMARY KEY, vector_store_id VARCHAR(100) NOT NULL,
                filename VARCHAR(255) NOT NULL, status VARCHAR(40) NOT NULL,
                pipeline_version INT NOT NULL, active BOOLEAN NOT NULL,
                lease_until TIMESTAMP NULL, openai_file_id VARCHAR(100) NULL,
                replaces_document_id VARCHAR(36) NULL,
                FOREIGN KEY (replaces_document_id) REFERENCES KnowledgeDocument(document_id))
            """);
        jdbc.execute("""
            CREATE TABLE KnowledgeDocumentChunk (
                chunk_id VARCHAR(36) PRIMARY KEY, document_id VARCHAR(36) NOT NULL,
                openai_file_id VARCHAR(100), status VARCHAR(40) NOT NULL,
                active BOOLEAN NOT NULL, content VARCHAR(1000) NOT NULL,
                FOREIGN KEY (document_id) REFERENCES KnowledgeDocument(document_id))
            """);
        jdbc.execute("CREATE TABLE KnowledgeTopicOwner (document_id VARCHAR(36),chunk_id VARCHAR(36))");
        jdbc.execute("""
            CREATE TABLE KnowledgeRemoteCleanup (
                vector_store_id VARCHAR(100) NOT NULL,openai_file_id VARCHAR(100) NOT NULL,
                PRIMARY KEY(vector_store_id,openai_file_id))
            """);
        service=new KnowledgeDocumentService(jdbc,mock(OpenAiKnowledgeClient.class),"vs_test");
    }

    public static Timestamp utcTimestamp(int precision) {
        return Timestamp.from(Instant.now());
    }

    private UUID document(String state,UUID replaces) {
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO KnowledgeDocument(document_id,vector_store_id,filename,status,pipeline_version,active,replaces_document_id) VALUES(?,'vs_test','guide.pdf',?,3,TRUE,?)",
            id.toString(),state,replaces==null?null:replaces.toString());
        return id;
    }

    private UUID chunk(UUID document,String file) {
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO KnowledgeDocumentChunk(chunk_id,document_id,openai_file_id,status,active,content) VALUES(?,?,?,'COMPLETED',TRUE,'text')",
            id.toString(),document.toString(),file);
        return id;
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Long.class);
    }

    @Test void documentAndChunksAreDeletedWhileRemoteIdsRemainInCleanupQueue() {
        UUID id=document("COMPLETED",null);
        UUID fileChunk=chunk(id,"file-first");
        UUID replacement=document("COMPLETED",id);
        jdbc.update("INSERT INTO KnowledgeTopicOwner VALUES(?,?)",id.toString(),fileChunk.toString());

        tx.executeWithoutResult(s->service.delete(id));

        assertEquals(1,count("KnowledgeDocument"));
        assertEquals(0,count("KnowledgeDocumentChunk"));
        assertEquals(0,count("KnowledgeTopicOwner"));
        assertEquals(1,count("KnowledgeRemoteCleanup"));
        assertNull(jdbc.queryForObject("SELECT replaces_document_id FROM KnowledgeDocument WHERE document_id=?",
            String.class,replacement.toString()));
        assertThrows(ResourceNotFoundException.class,()->tx.executeWithoutResult(s->service.delete(id)));
    }

    @Test void deletingOneChunkKeepsTheDocumentAndOtherChunk() {
        UUID id=document("COMPLETED",null);
        UUID first=chunk(id,"file-first"),second=chunk(id,"file-second");
        jdbc.update("INSERT INTO KnowledgeTopicOwner VALUES(?,?)",id.toString(),first.toString());

        tx.executeWithoutResult(s->service.deleteChunk(id,first));

        assertEquals(1,count("KnowledgeDocument"));
        assertEquals(1,count("KnowledgeDocumentChunk"));
        assertEquals(second.toString(),jdbc.queryForObject("SELECT chunk_id FROM KnowledgeDocumentChunk",String.class));
        assertEquals(0,count("KnowledgeTopicOwner"));
        assertEquals("file-first",jdbc.queryForObject("SELECT openai_file_id FROM KnowledgeRemoteCleanup",String.class));
    }

    @Test void deletingDocumentAlsoRemovesItsPendingReplacement() {
        UUID original=document("COMPLETED",null);
        UUID replacement=document("PROCESSING",original);
        chunk(original,"file-original");
        chunk(replacement,"file-replacement");

        tx.executeWithoutResult(s->service.delete(original));

        assertEquals(0,count("KnowledgeDocument"));
        assertEquals(0,count("KnowledgeDocumentChunk"));
        assertEquals(2,count("KnowledgeRemoteCleanup"));
    }

    @Test void activeLeaseBlocksDeletionWithoutRemovingAnyRows() {
        UUID id=document("PROCESSING",null);
        jdbc.update("UPDATE KnowledgeDocument SET lease_until=? WHERE document_id=?",
            Timestamp.from(Instant.now().plusSeconds(120)),id.toString());
        assertThrows(ConflictException.class,()->tx.executeWithoutResult(s->service.delete(id)));
        assertEquals(1,count("KnowledgeDocument"));
        assertEquals(0,count("KnowledgeRemoteCleanup"));
    }

    @Test void chunkDeletionRequiresCompletedDocumentAndMatchingChunkId() {
        UUID id=document("PROCESSING",null);
        UUID chunk=chunk(id,"file-processing");
        assertThrows(ConflictException.class,()->tx.executeWithoutResult(s->service.deleteChunk(id,chunk)));
        jdbc.update("UPDATE KnowledgeDocument SET status='COMPLETED' WHERE document_id=?",id.toString());
        assertThrows(ResourceNotFoundException.class,()->tx.executeWithoutResult(
            s->service.deleteChunk(id,UUID.randomUUID())));
        assertEquals(1,count("KnowledgeDocumentChunk"));
        assertEquals(0,count("KnowledgeRemoteCleanup"));
    }
}
