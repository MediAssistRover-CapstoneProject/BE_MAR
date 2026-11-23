package com.mediassistrover.backend.knowledge;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Runs untrusted document parsers outside the request/JVM with a hard deadline. */
@Component
public class KnowledgePreprocessor {
    public record Chunk(String text, String sha256, String topicKey) {
        public Chunk(String text, String sha256) { this(text, sha256, null); }
    }
    public static class Failure extends RuntimeException {
        public Failure(String code) { super(code); }
    }
    private final ObjectMapper json;
    private final String python;
    private final String script;
    public KnowledgePreprocessor(ObjectMapper json,
            @Value("${app.knowledge.python:python}") String python,
            @Value("${app.knowledge.processor:scripts/knowledge/process_document.py}") String script) {
        this.json = json; this.python = python; this.script = script;
    }
    public List<Chunk> extract(String filename, byte[] bytes) {
        if (bytes == null || bytes.length == 0) throw new Failure("KNOWLEDGE_ORIGINAL_MISSING");
        Path temp = null;
        Process process = null;
        try {
            temp = Files.createTempDirectory("mar-knowledge-");
            String ext = filename.substring(filename.lastIndexOf('.')).toLowerCase(Locale.ROOT);
            if (!Set.of(".pdf", ".docx", ".doc", ".txt", ".md").contains(ext))
                throw new Failure("KNOWLEDGE_FILE_TYPE_UNSUPPORTED");
            Path input = temp.resolve("input" + ext), output = temp.resolve("result.json");
            Files.write(input, bytes);
            var builder = new ProcessBuilder(python, Path.of(script).toAbsolutePath().toString(), input.toString(), output.toString())
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD);
            // Child temp files live in the same private tree, including on timeout.
            builder.environment().put("TMPDIR", temp.toString());
            builder.environment().put("TEMP", temp.toString());
            builder.environment().put("TMP", temp.toString());
            process = builder.start();
            if (!process.waitFor(120, TimeUnit.SECONDS)) throw new Failure("KNOWLEDGE_EXTRACTION_TIMEOUT");
            if (process.exitValue() != 0 || !Files.exists(output) || Files.size(output) > 16_000_000)
                throw new Failure("KNOWLEDGE_PROCESSOR_FAILED");
            var result = json.readTree(Files.readAllBytes(output));
            if (result.has("error")) {
                String code = result.path("error").asText();
                throw new Failure(code.matches("KNOWLEDGE_[A-Z_]{1,80}") ? code : "KNOWLEDGE_EXTRACTION_FAILED");
            }
            var nodes = result.path("chunks");
            if (result.path("version").asInt() != 3 || !nodes.isArray() || nodes.isEmpty() || nodes.size() > 1000)
                throw new Failure("KNOWLEDGE_PROCESSOR_INVALID");
            List<Chunk> chunks = new ArrayList<>();
            int chars = 0;
            for (var node : nodes) {
                String text = node.path("text").asText();
                String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
                chars += text.length();
                if (text.isBlank() || text.length() > 20000 || chars > 3_000_000 || !hash.equals(node.path("sha256").asText()))
                    throw new Failure("KNOWLEDGE_PROCESSOR_INVALID");
                String topicKey = node.path("topicKey").asText();
                if (!topicKey.matches("[0-9a-f]{64}")) throw new Failure("KNOWLEDGE_PROCESSOR_INVALID");
                chunks.add(new Chunk(text, hash, topicKey));
            }
            return chunks;
        } catch (Failure failure) { throw failure; }
        catch (InterruptedException error) {
            Thread.currentThread().interrupt(); throw new Failure("KNOWLEDGE_EXTRACTION_INTERRUPTED");
        } catch (Exception error) { throw new Failure("KNOWLEDGE_PROCESSOR_UNAVAILABLE"); }
        finally {
            if (process != null && process.isAlive()) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                try { process.waitFor(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            if (temp != null) {
                try (var paths = Files.walk(temp)) {
                    for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
                } catch (Exception ignored) { /* Do not log paths or extracted content. */ }
            }
        }
    }
}
