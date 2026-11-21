package com.mediassistrover.backend.knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@Component
public class OpenAiKnowledgeClient {
    public static class Failure extends RuntimeException {
        final int status;
        final boolean ambiguous;
        Failure(String code, int status, boolean ambiguous) {
            super(code);
            this.status = status;
            this.ambiguous = ambiguous;
        }
        boolean transientFailure() { return status == 0 || status == 429 || status >= 500; }
    }
    private final boolean enabled;
    private final String key;
    private final ObjectMapper json;
    private final URI base;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NEVER).build();

    @Autowired
    public OpenAiKnowledgeClient(@Value("${app.knowledge.enabled:false}") boolean enabled,
                                 @Value("${app.knowledge.api-key:}") String key, ObjectMapper json) {
        this(enabled, key, json, URI.create("https://api.openai.com/v1/"));
    }
    OpenAiKnowledgeClient(boolean enabled, String key, ObjectMapper json, URI base) {
        this.enabled = enabled;
        this.key = key;
        this.json = json;
        this.base = base;
    }
    public boolean configured() { return enabled && !key.isBlank(); }
    public void requireConfigured() {
        if (!configured()) throw new Failure("KNOWLEDGE_NOT_CONFIGURED", 503, false);
    }

    public String upload(String filename, String contentType, byte[] bytes) {
        String boundary = "mar-" + UUID.randomUUID();
        // Header filename is generated, so original Unicode names cannot inject multipart headers.
        String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(java.util.Locale.ROOT);
        String safeName = "document-" + UUID.randomUUID() + "." + extension;
        var body = new ByteArrayOutputStream(bytes.length + 512);
        body.writeBytes(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"purpose\"\r\n\r\nassistants\r\n"
            + "--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + safeName
            + "\"\r\nContent-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.writeBytes(bytes);
        body.writeBytes(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        JsonNode result = send("POST", "files", "multipart/form-data; boundary=" + boundary, body.toByteArray());
        String id = result.path("id").asText();
        if (!id.matches("file-[A-Za-z0-9_-]+")) throw new Failure("OPENAI_UPLOAD_INVALID_RESPONSE", 0, true);
        return id;
    }

    public String status(String store, String file) {
        return send("GET", path(store, file), null, null).path("status").asText();
    }

    public void attach(String store, String file, String title) {
        attach(store, file, Map.of("title", title), false);
    }

    public void attachChunk(String store, String file, String documentId) {
        attach(store, file, Map.of("knowledge_document_id", documentId, "pipeline_version", 2), true);
    }

    private void attach(String store, String file, Map<String, Object> attributes, boolean chunk) {
        try {
            var body = new java.util.HashMap<String, Object>();
            body.put("file_id", file);
            body.put("attributes", attributes);
            // Processor caps chunks at 700 cl100k tokens; avoid another arbitrary split.
            if (chunk) body.put("chunking_strategy", Map.of("type", "static", "static",
                Map.of("max_chunk_size_tokens", 800, "chunk_overlap_tokens", 0)));
            byte[] bytes = json.writeValueAsBytes(body);
            send("POST", "vector_stores/" + segment(store) + "/files", "application/json", bytes);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public void detach(String store, String file) {
        try { send("DELETE", path(store, file), null, null); }
        catch (Failure failure) { if (failure.status != 404) throw failure; }
    }

    private String path(String store, String file) {
        return "vector_stores/" + segment(store) + "/files/" + segment(file);
    }
    private String segment(String value) {
        if (!value.matches("[A-Za-z0-9_-]+")) throw new IllegalArgumentException("Invalid OpenAI resource ID");
        return value;
    }
    private JsonNode send(String method, String path, String contentType, byte[] bytes) {
        requireConfigured();
        var request = HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(45))
            .header("Authorization", "Bearer " + key);
        if (path.startsWith("vector_stores/")) request.header("OpenAI-Beta", "assistants=v2");
        if (contentType != null) request.header("Content-Type", contentType);
        request.method(method, bytes == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(bytes));
        try {
            var response = http.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                int status = response.statusCode();
                throw new Failure("OPENAI_HTTP_" + status, status, status >= 500);
            }
            byte[] raw = response.body();
            if (raw.length > 1024 * 1024) throw new Failure("OPENAI_RESPONSE_TOO_LARGE", 0, true);
            JsonNode result = json.readTree(raw);
            if (result == null || !result.isObject()) throw new Failure("OPENAI_RESPONSE_INVALID", 0, true);
            return result;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new Failure("OPENAI_INTERRUPTED", 0, true);
        } catch (IOException exception) {
            throw new Failure("OPENAI_UNREACHABLE", 0, true);
        }
    }
}
