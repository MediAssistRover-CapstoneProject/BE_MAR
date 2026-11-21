package com.mediassistrover.backend.knowledge;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class KnowledgePreprocessorTest {
    @Test @EnabledIfEnvironmentVariable(named="KNOWLEDGE_TEST_PYTHON",matches=".+")
    void javaActuallyInvokesProcessorAndChecksOutputHash() {
        var processor=new KnowledgePreprocessor(new ObjectMapper(),System.getenv("KNOWLEDGE_TEST_PYTHON"),"scripts/knowledge/process_document.py");
        var chunks=processor.extract("hướng dẫn.txt","Giờ khám 07:00–17:00. Nghỉ Chủ nhật.".getBytes(StandardCharsets.UTF_8));
        assertEquals(1,chunks.size());
        assertTrue(chunks.getFirst().text().endsWith("Nghỉ Chủ nhật."));
        assertEquals(64,chunks.getFirst().sha256().length());
    }
    @Test void missingRuntimeReturnsSanitizedErrorAndOriginalIsRequired() {
        var processor=new KnowledgePreprocessor(new ObjectMapper(),"missing-knowledge-python-executable", "scripts/knowledge/process_document.py");
        assertEquals("KNOWLEDGE_PROCESSOR_UNAVAILABLE",assertThrows(KnowledgePreprocessor.Failure.class,
            ()->processor.extract("guide.txt","Private data".getBytes())).getMessage());
        assertEquals("KNOWLEDGE_ORIGINAL_MISSING",assertThrows(KnowledgePreprocessor.Failure.class,
            ()->processor.extract("guide.txt",null)).getMessage());
    }
}
