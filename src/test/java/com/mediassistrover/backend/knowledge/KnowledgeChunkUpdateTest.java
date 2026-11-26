package com.mediassistrover.backend.knowledge;

import com.mediassistrover.backend.exception.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

public class KnowledgeChunkUpdateTest {
    private JdbcTemplate jdbc;
    private TransactionTemplate tx;
    private KnowledgeDocumentService service;

    @BeforeEach void setup() {
        var dataSource=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+
            ";MODE=MySQL;DATABASE_TO_UPPER=FALSE;DB_CLOSE_DELAY=-1","sa","");
        jdbc=new JdbcTemplate(dataSource);
        tx=new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        jdbc.execute("CREATE ALIAS UTC_TIMESTAMP FOR \"com.mediassistrover.backend.knowledge.KnowledgeChunkUpdateTest.utcTimestamp\"");
        jdbc.execute("CREATE ALIAS UUID_TO_BIN FOR \"com.mediassistrover.backend.knowledge.KnowledgeChunkUpdateTest.identity\"");
        jdbc.execute("CREATE ALIAS BIN_TO_UUID FOR \"com.mediassistrover.backend.knowledge.KnowledgeChunkUpdateTest.identity\"");
        jdbc.execute("""
            CREATE TABLE KnowledgeDocument (
              document_id VARCHAR(36) PRIMARY KEY,vector_store_id VARCHAR(100),filename VARCHAR(255),
              content_type VARCHAR(100),size_bytes BIGINT,sha256 VARCHAR(64),uploaded_by VARCHAR(36),
              openai_file_id VARCHAR(100),status VARCHAR(40),error_code VARCHAR(100),payload BLOB,
              pipeline_version INT,active BOOLEAN DEFAULT TRUE,replaces_document_id VARCHAR(36),
              next_attempt_at TIMESTAMP,created_at TIMESTAMP,updated_at TIMESTAMP,
              processing_stage VARCHAR(40),extracted_text CLOB,ocr_edited BOOLEAN DEFAULT FALSE,
              attempts INT DEFAULT 0,lease_until TIMESTAMP,
              FOREIGN KEY(replaces_document_id) REFERENCES KnowledgeDocument(document_id))
            """);
        jdbc.execute("""
            CREATE TABLE KnowledgeDocumentChunk (
              chunk_id VARCHAR(36) PRIMARY KEY,document_id VARCHAR(36),ordinal_no INT,content CLOB,
              sha256 VARCHAR(64),topic_key VARCHAR(64),openai_file_id VARCHAR(100),
              status VARCHAR(40),error_code VARCHAR(100),active BOOLEAN,
              FOREIGN KEY(document_id) REFERENCES KnowledgeDocument(document_id))
            """);
        jdbc.execute("CREATE TABLE KnowledgeRemoteCleanup(vector_store_id VARCHAR(100),openai_file_id VARCHAR(100),PRIMARY KEY(vector_store_id,openai_file_id))");
        service=new KnowledgeDocumentService(jdbc,mock(OpenAiKnowledgeClient.class),"vs_test");
    }

    public static Timestamp utcTimestamp(int precision) { return Timestamp.from(Instant.now()); }
    public static String identity(String value) { return value; }

    @Test void editCreatesOcrRevisionAndRetryKeepsTheEditedText() {
        UUID old=UUID.randomUUID(),chunk=UUID.randomUUID(),actor=UUID.randomUUID();
        byte[] original="%PDF- original bytes".getBytes(StandardCharsets.UTF_8);
        jdbc.update("""
            INSERT INTO KnowledgeDocument(document_id,vector_store_id,filename,content_type,size_bytes,sha256,
              status,payload,pipeline_version,active,next_attempt_at,created_at,updated_at,extracted_text)
            VALUES(?,'vs_test','guide.pdf','application/pdf',?,?,'COMPLETED',?,3,TRUE,
              UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),'old OCR')
            """,old.toString(),original.length,"a".repeat(64),original);
        jdbc.update("""
            INSERT INTO KnowledgeDocumentChunk(chunk_id,document_id,ordinal_no,content,sha256,topic_key,status,active)
            VALUES(?,?,0,'old OCR',? ,?,'COMPLETED',TRUE)
            """,chunk.toString(),old.toString(),"b".repeat(64),"c".repeat(64));

        UUID revision=tx.execute(s->service.updateChunk(old,chunk,"corrected OCR",actor).id());
        assertEquals("COMPLETED",service.get(old).status());
        assertEquals("PROCESSING",service.get(revision).status());
        assertEquals("corrected OCR",service.extractedText(revision));
        assertEquals("corrected OCR",service.chunks(revision).getFirst().content());
        assertArrayEquals(original,jdbc.queryForObject("SELECT payload FROM KnowledgeDocument WHERE document_id=?",
            byte[].class,revision.toString()));
        assertThrows(ConflictException.class,()->tx.execute(s->service.updateChunk(old,chunk,"second edit",actor)));

        jdbc.update("UPDATE KnowledgeDocument SET status='FAILED' WHERE document_id=?",revision.toString());
        tx.execute(s->service.retry(revision));
        assertEquals("INDEXING",jdbc.queryForObject("SELECT processing_stage FROM KnowledgeDocument WHERE document_id=?",
            String.class,revision.toString()));
        assertEquals("corrected OCR",service.chunks(revision).getFirst().content());
        assertEquals("corrected OCR",service.extractedText(revision));
    }

    @Test void wholeTextEditQueuesRechunkingAndRetryPreservesTheNewText() {
        UUID old=UUID.randomUUID(),actor=UUID.randomUUID();
        byte[] original="%PDF- source".getBytes(StandardCharsets.UTF_8);
        jdbc.update("""
            INSERT INTO KnowledgeDocument(document_id,vector_store_id,filename,content_type,size_bytes,sha256,
              status,payload,pipeline_version,active,next_attempt_at,created_at,updated_at,extracted_text)
            VALUES(?,'vs_test','guide.pdf','application/pdf',?,?,'COMPLETED',?,3,TRUE,
              UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),'old extracted text')
            """,old.toString(),original.length,"a".repeat(64),original);

        UUID revision=tx.execute(s->service.updateText(old,"new extracted text",actor).id());
        assertEquals("RECHUNKING",jdbc.queryForObject("SELECT processing_stage FROM KnowledgeDocument WHERE document_id=?",
            String.class,revision.toString()));
        assertEquals("new extracted text",service.extractedText(revision));
        assertArrayEquals(original,jdbc.queryForObject("SELECT payload FROM KnowledgeDocument WHERE document_id=?",
            byte[].class,revision.toString()));
        assertTrue(service.chunks(revision).isEmpty());
        assertThrows(ConflictException.class,()->tx.execute(s->service.updateText(old,"another text",actor)));

        jdbc.update("UPDATE KnowledgeDocument SET status='FAILED' WHERE document_id=?",revision.toString());
        tx.execute(s->service.retry(revision));
        assertEquals("RECHUNKING",jdbc.queryForObject("SELECT processing_stage FROM KnowledgeDocument WHERE document_id=?",
            String.class,revision.toString()));
        assertEquals("new extracted text",service.extractedText(revision));
    }

    @Test void completedDocumentFromBeforeOcrColumnCanRecoverTextAndBeEdited() {
        UUID old=UUID.randomUUID(),actor=UUID.randomUUID();
        byte[] original="%PDF- historic".getBytes(StandardCharsets.UTF_8);
        jdbc.update("""
            INSERT INTO KnowledgeDocument(document_id,vector_store_id,filename,content_type,size_bytes,sha256,
              status,payload,pipeline_version,active,next_attempt_at,created_at,updated_at)
            VALUES(?,'vs_test','old.pdf','application/pdf',?,?,'COMPLETED',?,3,TRUE,
              UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
            """,old.toString(),original.length,"a".repeat(64),original);
        jdbc.update("""
            INSERT INTO KnowledgeDocumentChunk(chunk_id,document_id,ordinal_no,content,sha256,status,active)
            VALUES(?,?,0,'Địa chỉ cũ',?,'COMPLETED',TRUE)
            """,UUID.randomUUID().toString(),old.toString(),"b".repeat(64));
        jdbc.update("""
            INSERT INTO KnowledgeDocumentChunk(chunk_id,document_id,ordinal_no,content,sha256,status,active)
            VALUES(?,?,1,'Giờ làm việc',?,'DETACHED',FALSE)
            """,UUID.randomUUID().toString(),old.toString(),"c".repeat(64));

        assertEquals("Địa chỉ cũ\n\nGiờ làm việc",service.extractedText(old));
        assertEquals("Địa chỉ cũ\n\nGiờ làm việc",jdbc.queryForObject(
            "SELECT extracted_text FROM KnowledgeDocument WHERE document_id=?",String.class,old.toString()));
        assertEquals(1,new KnowledgeSearchService(jdbc,"vs_test").documents("Địa chỉ cũ",0,20).totalElements());
        jdbc.update("UPDATE KnowledgeDocument SET extracted_text=NULL WHERE document_id=?",old.toString());
        UUID revision=tx.execute(s->service.updateText(old,"Địa chỉ mới\n\nGiờ làm việc",actor).id());
        assertEquals("Địa chỉ mới\n\nGiờ làm việc",service.extractedText(revision));
    }
}
