package com.mediassistrover.backend.knowledge;

import com.mediassistrover.backend.exception.BusinessRuleException;
import com.mediassistrover.backend.exception.FileTooLargeException;
import com.mediassistrover.backend.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipInputStream;

@Service
public class KnowledgeDocumentService {
    public static final int MAX_BYTES = 20 * 1024 * 1024;
    private static final Map<String, String> TYPES = Map.of(
        "pdf", "application/pdf", "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "txt", "text/plain", "md", "text/markdown");
    private static final String COLUMNS = "document_id,vector_store_id,filename,content_type,size_bytes,sha256,"
        + "BIN_TO_UUID(uploaded_by) AS uploaded_by,openai_file_id,status,error_code,created_at,updated_at";
    public record Document(UUID id, String vectorStoreId, String filename, String contentType,
                           long sizeBytes, String sha256, UUID uploadedBy, String openaiFileId,
                           String status, String errorCode, Instant createdAt, Instant updatedAt) { }
    public record DocumentPage(List<Document> content, int page, int size, long totalElements) { }
    record Upload(String filename, String contentType, byte[] bytes, String sha256) { }
    private static final RowMapper<Document> MAPPER = (rs, index) -> new Document(
        UUID.fromString(rs.getString("document_id")), rs.getString("vector_store_id"),
        rs.getString("filename"), rs.getString("content_type"), rs.getLong("size_bytes"),
        rs.getString("sha256"), rs.getString("uploaded_by") == null ? null : UUID.fromString(rs.getString("uploaded_by")),
        rs.getString("openai_file_id"), rs.getString("status"), rs.getString("error_code"),
        rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    private final JdbcTemplate jdbc;
    private final OpenAiKnowledgeClient client;
    private final String vectorStoreId;

    public KnowledgeDocumentService(JdbcTemplate jdbc, OpenAiKnowledgeClient client,
            @Value("${app.knowledge.vector-store-id}") String vectorStoreId) {
        if (!vectorStoreId.matches("vs_[a-zA-Z0-9]+")) throw new IllegalArgumentException("Invalid vector store ID");
        this.jdbc = jdbc;
        this.client = client;
        this.vectorStoreId = vectorStoreId;
    }

    public Document upload(MultipartFile file, UUID actor) {
        Upload upload = validate(file);
        client.requireConfigured();
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("""
                INSERT INTO KnowledgeDocument
                (document_id,vector_store_id,filename,content_type,size_bytes,sha256,uploaded_by,status,payload,
                 next_attempt_at,created_at,updated_at)
                VALUES (?,?,?,?,?,?,UUID_TO_BIN(?),'QUEUED',?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
                """, id.toString(), vectorStoreId, upload.filename(), upload.contentType(), upload.bytes().length,
                upload.sha256(), actor.toString(), upload.bytes());
        } catch (DuplicateKeyException duplicate) {
            // The database unique constraint also handles concurrent uploads of identical bytes.
            return jdbc.queryForObject("SELECT " + COLUMNS
                + " FROM KnowledgeDocument WHERE vector_store_id=? AND sha256=?", MAPPER, vectorStoreId, upload.sha256());
        }
        return get(id);
    }

    public Document get(UUID id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM KnowledgeDocument WHERE document_id=?", MAPPER, id.toString())
            .stream().findFirst().orElseThrow(() -> new ResourceNotFoundException("Knowledge document not found"));
    }

    public DocumentPage list(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new BusinessRuleException("Use page >= 0 and size between 1 and 100");
        List<Document> rows = jdbc.query("SELECT " + COLUMNS
            + " FROM KnowledgeDocument ORDER BY created_at DESC,document_id DESC LIMIT ? OFFSET ?",
            MAPPER, size, (long) page * size);
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM KnowledgeDocument", Long.class);
        return new DocumentPage(rows, page, size, count == null ? 0 : count);
    }

    static Upload validate(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BusinessRuleException("KNOWLEDGE_FILE_EMPTY");
        if (file.getSize() > MAX_BYTES) throw new FileTooLargeException("Knowledge document exceeds 20 MB");
        String name = file.getOriginalFilename();
        if (name == null || name.isBlank() || name.length() > 255 || name.matches(".*[\\\\/\\p{Cntrl}].*"))
            throw new BusinessRuleException("KNOWLEDGE_FILENAME_INVALID");
        String extension = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        String type = TYPES.get(extension);
        if (type == null) throw new BusinessRuleException("KNOWLEDGE_FILE_TYPE_UNSUPPORTED: use PDF, DOCX, TXT or MD");
        try (var input = file.getInputStream()) {
            byte[] bytes = input.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) throw new FileTooLargeException("Knowledge document exceeds 20 MB");
            if (bytes.length == 0) throw new BusinessRuleException("KNOWLEDGE_FILE_EMPTY");
            switch (extension) {
                case "pdf" -> {
                    if (bytes.length < 5 || !new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-"))
                        throw new BusinessRuleException("KNOWLEDGE_PDF_INVALID");
                }
                case "docx" -> validateDocx(bytes);
                default -> {
                    String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
                    if (text.replace("\uFEFF", "").isBlank() || text.indexOf('\0') >= 0)
                        throw new BusinessRuleException("KNOWLEDGE_TEXT_INVALID");
                }
            }
            return new Upload(name, type, bytes, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        } catch (CharacterCodingException ex) {
            throw new BusinessRuleException("KNOWLEDGE_TEXT_REQUIRES_UTF8");
        } catch (IOException ex) {
            throw new BusinessRuleException("KNOWLEDGE_FILE_UNREADABLE");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static void validateDocx(byte[] bytes) throws IOException {
        boolean document = false;
        boolean contentTypes = false;
        long expanded = 0;
        int entries = 0;
        byte[] buffer = new byte[8192];
        try (var zip = new ZipInputStream(new java.io.ByteArrayInputStream(bytes))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > 10000) throw new BusinessRuleException("KNOWLEDGE_DOCX_TOO_COMPLEX");
                document |= entry.getName().equals("word/document.xml");
                contentTypes |= entry.getName().equals("[Content_Types].xml");
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    expanded += read;
                    if (expanded > 100L * 1024 * 1024) throw new BusinessRuleException("KNOWLEDGE_DOCX_TOO_COMPLEX");
                }
            }
        }
        if (!document || !contentTypes) throw new BusinessRuleException("KNOWLEDGE_DOCX_INVALID");
    }
}
