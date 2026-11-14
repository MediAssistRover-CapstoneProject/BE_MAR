package com.mediassistrover.backend.knowledge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediassistrover.backend.exception.BusinessRuleException;
import com.mediassistrover.backend.exception.FileTooLargeException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KnowledgeDocumentTest {
    @Test void validateSupportedFilesAndRejectInvalidInputs() throws Exception {
        for (String name : new String[]{"document.txt", "document.md"}) {
            var result = KnowledgeDocumentService.validate(new MockMultipartFile("file", name, "text/plain", "Giờ khám 7h".getBytes(StandardCharsets.UTF_8)));
            assertEquals(64, result.sha256().length());
        }
        KnowledgeDocumentService.validate(new MockMultipartFile("file", "document.pdf", "application/pdf", "%PDF-1.7\n".getBytes()));
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            for (String entry : new String[]{"[Content_Types].xml", "word/document.xml"}) {
                zip.putNextEntry(new ZipEntry(entry));
                zip.write("<document>Knowledge</document>".getBytes());
                zip.closeEntry();
            }
        }
        KnowledgeDocumentService.validate(new MockMultipartFile("file", "document.docx", "application/octet-stream", bytes.toByteArray()));
        assertThrows(BusinessRuleException.class, () -> KnowledgeDocumentService.validate(new MockMultipartFile("file", "bad.exe", "text/plain", new byte[]{1})));
        assertThrows(BusinessRuleException.class, () -> KnowledgeDocumentService.validate(new MockMultipartFile("file", "bad.pdf", "application/pdf", "not PDF".getBytes())));
        assertThrows(BusinessRuleException.class, () -> KnowledgeDocumentService.validate(new MockMultipartFile("file", "bad.txt", "text/plain", new byte[]{(byte) 0xff})));
        MultipartFile large = mock(MultipartFile.class);
        when(large.getSize()).thenReturn((long) KnowledgeDocumentService.MAX_BYTES + 1);
        assertThrows(FileTooLargeException.class, () -> KnowledgeDocumentService.validate(large));
    }

    @Test void providerUploadAttachStatusAndSanitizedFailure() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.contains("vector_stores")) {
                assertEquals("assistants=v2", exchange.getRequestHeaders().getFirst("OpenAI-Beta"));
            }
            String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            int code = 200;
            String response;
            if (path.endsWith("/files") && path.contains("vector_stores")) {
                assertTrue(request.contains("file-test"));
                assertTrue(request.contains("title"));
                response = "{\"status\":\"in_progress\"}";
            } else if (path.equals("/v1/files")) {
                assertTrue(request.contains("assistants"));
                assertTrue(request.contains("Knowledge"));
                response = "{\"id\":\"file-test\"}";
            } else if (path.endsWith("file-test")) {
                response = "{\"status\":\"completed\"}";
            } else {
                code = 401;
                response = "private provider detail";
            }
            byte[] raw = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(code, raw.length);
            exchange.getResponseBody().write(raw);
            exchange.close();
        });
        server.start();
        try {
            var client = new OpenAiKnowledgeClient(true, "mock-key", new ObjectMapper(),
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/"));
            assertEquals("file-test", client.upload("Tài liệu.txt", "text/plain", "Knowledge".getBytes()));
            client.attach("vs_test", "file-test", "Tài liệu.txt");
            assertEquals("completed", client.status("vs_test", "file-test"));
            var error = assertThrows(OpenAiKnowledgeClient.Failure.class, () -> client.status("vs_test", "file-other"));
            assertEquals("OPENAI_HTTP_401", error.getMessage());
        } finally {
            server.stop(0);
        }
    }
}
