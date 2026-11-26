package com.mediassistrover.backend.knowledge;

import com.mediassistrover.backend.exception.BusinessRuleException;
import com.mediassistrover.backend.exception.ConflictException;
import com.mediassistrover.backend.exception.FileTooLargeException;
import com.mediassistrover.backend.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;

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
        "doc", "application/msword", "txt", "text/plain", "md", "text/markdown");
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
        Long unreindexable = jdbc.queryForObject("""
            SELECT COUNT(*) FROM KnowledgeDocument
            WHERE vector_store_id=? AND active=TRUE AND pipeline_version<3
              AND (pipeline_version=1 OR payload IS NULL)
            """,Long.class,vectorStoreId);
        if (unreindexable != null && unreindexable > 0)
            throw new BusinessRuleException("KNOWLEDGE_LEGACY_REPLACE_REQUIRED");
        Long legacy = jdbc.queryForObject("""
            SELECT COUNT(*) FROM KnowledgeDocument
            WHERE vector_store_id=? AND active=TRUE AND pipeline_version=2
            """,Long.class,vectorStoreId);
        if (legacy != null && legacy > 0) throw new BusinessRuleException("KNOWLEDGE_REINDEX_PENDING");
        return create(upload, actor, null);
    }

    private Document create(Upload upload, UUID actor, UUID replaces) {
        client.requireConfigured();
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO KnowledgeDocument
            (document_id,vector_store_id,filename,content_type,size_bytes,sha256,uploaded_by,status,payload,
             pipeline_version,replaces_document_id,next_attempt_at,created_at,updated_at)
            VALUES (?,?,?,?,?,?,UUID_TO_BIN(?),'QUEUED',?,3,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
            """, id.toString(), vectorStoreId, upload.filename(), upload.contentType(), upload.bytes().length,
            upload.sha256(), actor.toString(), upload.bytes(), replaces == null ? null : replaces.toString());
        return get(id);
    }

    @Transactional
    public Document replace(UUID id, MultipartFile file, UUID actor) {
        Upload upload = validate(file);
        if (jdbc.queryForList("SELECT document_id FROM KnowledgeDocument WHERE document_id=? AND active=TRUE AND status='COMPLETED' FOR UPDATE",
                String.class, id.toString()).isEmpty()) throw new BusinessRuleException("KNOWLEDGE_REPLACEMENT_REQUIRES_ACTIVE_DOCUMENT");
        Long pending = jdbc.queryForObject("SELECT COUNT(*) FROM KnowledgeDocument WHERE replaces_document_id=? AND active=TRUE AND status IN ('QUEUED','ATTACHING','PROCESSING')",
            Long.class,id.toString());
        if (pending != null && pending > 0) throw new BusinessRuleException("KNOWLEDGE_REPLACEMENT_PENDING");
        return create(upload,actor,id);
    }

    /** Re-split edited extraction/OCR text without reading the uploaded binary again. */
    @Transactional
    public Document updateText(UUID documentId,String content,UUID actor) {
        if (content==null || content.isBlank() || content.length()>3_000_000 || content.indexOf('\0')>=0)
            throw new BusinessRuleException("KNOWLEDGE_TEXT_INVALID");
        String old=documentId.toString();
        var source=jdbc.query("""
            SELECT filename,content_type,payload,pipeline_version,status,active,extracted_text
            FROM KnowledgeDocument WHERE document_id=? FOR UPDATE
            """,(rs,n)->new Source(rs.getString(1),rs.getString(2),rs.getBytes(3),rs.getInt(4),
                rs.getString(5),rs.getBoolean(6),rs.getString(7)),old);
        if (source.isEmpty()) throw new ResourceNotFoundException("Knowledge document not found");
        Source original=source.getFirst();
        if (!original.active() || !original.status().equals("COMPLETED") || original.pipelineVersion()!=3
                || original.bytes()==null)
            throw new ConflictException("KNOWLEDGE_TEXT_UPDATE_REQUIRES_READY_V3_DOCUMENT");
        String currentText=extractedTextOrRecover(old,original.extractedText());
        if (content.equals(currentText)) throw new BusinessRuleException("KNOWLEDGE_TEXT_UNCHANGED");
        requireNoPendingReplacement(old);
        Upload upload=new Upload(original.filename(),original.contentType(),original.bytes(),sha256(original.bytes()));
        Document revision=create(upload,actor,documentId);
        jdbc.update("""
            UPDATE KnowledgeDocument SET extracted_text=?,ocr_edited=TRUE,processing_stage='RECHUNKING',status='PROCESSING'
            WHERE document_id=?
            """,content,revision.id().toString());
        return get(revision.id());
    }

    private void requireNoPendingReplacement(String old) {
        Long pending=jdbc.queryForObject("""
            SELECT COUNT(*) FROM KnowledgeDocument WHERE replaces_document_id=? AND active=TRUE
              AND status IN ('QUEUED','ATTACHING','PROCESSING')
            """,Long.class,old);
        if (pending!=null && pending>0) throw new ConflictException("KNOWLEDGE_REPLACEMENT_PENDING");
    }

    /** Publish an edited extracted/OCR text revision while retaining the uploaded binary. */
    @Transactional
    public Document updateChunk(UUID documentId, UUID chunkId, String content, UUID actor) {
        if (content==null || content.isBlank() || content.length()>20_000 || content.indexOf('\0')>=0)
            throw new BusinessRuleException("KNOWLEDGE_CHUNK_CONTENT_INVALID");
        String old=documentId.toString();
        var source=jdbc.query("""
            SELECT filename,content_type,payload,pipeline_version,status,active,extracted_text
            FROM KnowledgeDocument WHERE document_id=? FOR UPDATE
            """,(rs,n)->new Source(rs.getString(1),rs.getString(2),rs.getBytes(3),rs.getInt(4),
                rs.getString(5),rs.getBoolean(6),rs.getString(7)),old);
        if (source.isEmpty()) throw new ResourceNotFoundException("Knowledge document not found");
        Source original=source.getFirst();
        if (!original.active() || !original.status().equals("COMPLETED") || original.pipelineVersion()!=3
                || original.bytes()==null)
            throw new ConflictException("KNOWLEDGE_CHUNK_UPDATE_REQUIRES_READY_V3_DOCUMENT");
        requireNoPendingReplacement(old);
        var chunks=jdbc.query("""
            SELECT chunk_id,ordinal_no,content,topic_key,active,status FROM KnowledgeDocumentChunk
            WHERE document_id=? ORDER BY ordinal_no FOR UPDATE
            """,(rs,n)->new EditableChunk(rs.getString(1),rs.getInt(2),rs.getString(3),
                rs.getString(4),rs.getBoolean(5),rs.getString(6)),old);
        if (chunks.isEmpty()) throw new ConflictException("KNOWLEDGE_DOCUMENT_HAS_NO_CHUNKS");
        EditableChunk target=chunks.stream().filter(c->c.id().equals(chunkId.toString())).findFirst()
            .orElseThrow(()->new ResourceNotFoundException("Knowledge chunk not found"));
        if (!target.active() || !target.status().equals("COMPLETED"))
            throw new ConflictException("KNOWLEDGE_CHUNK_NOT_PUBLISHED");
        if (target.content().equals(content)) throw new BusinessRuleException("KNOWLEDGE_CHUNK_UNCHANGED");
        // The extracted text is the editable source. The original PDF/DOC remains an immutable reference.
        var edited=chunks.stream().map(c->c.id().equals(target.id()) ? content : c.content()).toList();
        String extracted=String.join("\n\n",edited);
        if (extracted.length()>3_000_000) throw new BusinessRuleException("KNOWLEDGE_TEXT_LIMIT");
        Upload upload=new Upload(original.filename(),original.contentType(),original.bytes(),sha256(original.bytes()));
        Document revision=create(upload,actor,documentId);
        String next=revision.id().toString();
        jdbc.update("""
            UPDATE KnowledgeDocument SET extracted_text=?,ocr_edited=TRUE,processing_stage='INDEXING',status='PROCESSING'
            WHERE document_id=?
            """,extracted,next);
        for (EditableChunk chunk:chunks) {
            String text=chunk.id().equals(target.id()) ? content : chunk.content();
            jdbc.update("""
                INSERT INTO KnowledgeDocumentChunk(chunk_id,document_id,ordinal_no,content,sha256,topic_key,status,active)
                VALUES(?,?,?,?,?,?,?,?)
                """,UUID.randomUUID().toString(),next,chunk.ordinal(),text,sha256(text.getBytes(StandardCharsets.UTF_8)),
                chunk.topicKey(),chunk.active()?"QUEUED":"COMPLETED",chunk.active());
        }
        return get(revision.id());
    }

    private record Source(String filename,String contentType,byte[] bytes,int pipelineVersion,String status,
                          boolean active,String extractedText) { }
    private record EditableChunk(String id,int ordinal,String content,String topicKey,boolean active,String status) { }

    private static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }

    public String extractedText(UUID id) {
        Document document=get(id);
        String text=jdbc.queryForObject("SELECT extracted_text FROM KnowledgeDocument WHERE document_id=?",String.class,id.toString());
        if (text==null && !document.status().equals("COMPLETED"))
            throw new ConflictException("KNOWLEDGE_EXTRACTION_NOT_READY");
        return extractedTextOrRecover(id.toString(),text);
    }

    private String extractedTextOrRecover(String documentId,String stored) {
        if (stored!=null) return stored;
        // V68 added extracted_text after some v3 documents were already indexed.
        // Their ordered chunks preserve the normalized OCR text used by voice.
        List<String> pieces=jdbc.queryForList("""
            SELECT content FROM KnowledgeDocumentChunk
            WHERE document_id=? AND status IN ('COMPLETED','DETACHED') ORDER BY ordinal_no
            """,String.class,documentId);
        if (pieces.isEmpty()) throw new ConflictException("KNOWLEDGE_EXTRACTION_NOT_READY");
        String recovered=String.join("\n\n",pieces);
        if (recovered.length()>3_000_000) throw new ConflictException("KNOWLEDGE_TEXT_LIMIT");
        int written=jdbc.update("""
            UPDATE KnowledgeDocument SET extracted_text=?
            WHERE document_id=? AND extracted_text IS NULL AND status='COMPLETED'
            """,recovered,documentId);
        if (written==0) {
            String latest=jdbc.queryForObject("SELECT extracted_text FROM KnowledgeDocument WHERE document_id=?",
                String.class,documentId);
            if (latest!=null) return latest;
        }
        return recovered;
    }

    @Transactional
    public void delete(UUID id) {
        String key=id.toString();
        List<DeleteTarget> root=lockDeleteTargets("document_id=?",key);
        if (root.isEmpty()) throw new ResourceNotFoundException("Knowledge document not found");
        // A pending replacement must not publish after its parent is removed.
        List<DeleteTarget> pending=lockDeleteTargets("replaces_document_id=? AND active=TRUE AND status IN ('QUEUED','ATTACHING','PROCESSING')",key);
        List<DeleteTarget> targets=new java.util.ArrayList<>(pending);
        targets.add(root.getFirst());
        for (DeleteTarget target:targets) requireSafeToDelete(target);
        for (DeleteTarget target:targets) {
            queueRemoteCleanup(target.id());
            jdbc.update("DELETE FROM KnowledgeTopicOwner WHERE document_id=?",target.id());
            // Completed replacements may retain a historical FK to this document.
            jdbc.update("UPDATE KnowledgeDocument SET replaces_document_id=NULL WHERE replaces_document_id=?",target.id());
            jdbc.update("DELETE FROM KnowledgeDocumentChunk WHERE document_id=?",target.id());
            jdbc.update("DELETE FROM KnowledgeDocument WHERE document_id=?",target.id());
        }
    }

    @Transactional
    public void deleteChunk(UUID documentId, UUID chunkId) {
        String document=documentId.toString(), chunk=chunkId.toString();
        List<DeleteTarget> rows=lockDeleteTargets("document_id=?",document);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Knowledge document not found");
        DeleteTarget target=rows.getFirst();
        requireSafeToDelete(target);
        if (!target.status().equals("COMPLETED") || target.pipelineVersion()<2)
            throw new ConflictException("KNOWLEDGE_CHUNK_DELETE_REQUIRES_COMPLETED_DOCUMENT");
        List<String> found=jdbc.queryForList("SELECT chunk_id FROM KnowledgeDocumentChunk WHERE document_id=? AND chunk_id=? FOR UPDATE",
            String.class,document,chunk);
        if (found.isEmpty()) throw new ResourceNotFoundException("Knowledge chunk not found");
        jdbc.update("""
            INSERT IGNORE INTO KnowledgeRemoteCleanup(vector_store_id,openai_file_id)
            SELECT d.vector_store_id,c.openai_file_id FROM KnowledgeDocument d
            JOIN KnowledgeDocumentChunk c ON c.document_id=d.document_id
            WHERE c.chunk_id=? AND c.openai_file_id IS NOT NULL
            """,chunk);
        jdbc.update("DELETE FROM KnowledgeTopicOwner WHERE chunk_id=?",chunk);
        jdbc.update("DELETE FROM KnowledgeDocumentChunk WHERE chunk_id=?",chunk);
        if (target.pipelineVersion()==3) {
            var remaining=jdbc.queryForList("SELECT content FROM KnowledgeDocumentChunk WHERE document_id=? ORDER BY ordinal_no",
                String.class,document);
            jdbc.update("UPDATE KnowledgeDocument SET extracted_text=? WHERE document_id=?",
                remaining.isEmpty()?null:String.join("\n\n",remaining),document);
        }
    }

    private record DeleteTarget(String id,String status,int pipelineVersion,boolean leased) { }

    private List<DeleteTarget> lockDeleteTargets(String condition,String id) {
        return jdbc.query("""
            SELECT document_id,status,pipeline_version,
                   (lease_until IS NOT NULL AND lease_until>UTC_TIMESTAMP(6)) AS leased
            FROM KnowledgeDocument WHERE
            """+condition+" FOR UPDATE",
            (rs,n)->new DeleteTarget(rs.getString(1),rs.getString(2),rs.getInt(3),rs.getBoolean(4)),id);
    }

    private void requireSafeToDelete(DeleteTarget target) {
        if (target.leased() || target.status().equals("UPLOADING"))
            throw new ConflictException("KNOWLEDGE_DELETE_IN_PROGRESS");
        if (target.status().equals("RECONCILIATION_REQUIRED"))
            throw new ConflictException("KNOWLEDGE_UPLOAD_OUTCOME_UNKNOWN");
        Long uploading=jdbc.queryForObject("SELECT COUNT(*) FROM KnowledgeDocumentChunk WHERE document_id=? AND status='UPLOADING'",
            Long.class,target.id());
        if (uploading!=null && uploading>0)
            throw new ConflictException("KNOWLEDGE_UPLOAD_OUTCOME_UNKNOWN");
    }

    private void queueRemoteCleanup(String documentId) {
        jdbc.update("""
            INSERT IGNORE INTO KnowledgeRemoteCleanup(vector_store_id,openai_file_id)
            SELECT d.vector_store_id,c.openai_file_id FROM KnowledgeDocument d
            JOIN KnowledgeDocumentChunk c ON c.document_id=d.document_id
            WHERE d.document_id=? AND c.openai_file_id IS NOT NULL
            """,documentId);
        jdbc.update("""
            INSERT IGNORE INTO KnowledgeRemoteCleanup(vector_store_id,openai_file_id)
            SELECT vector_store_id,openai_file_id FROM KnowledgeDocument
            WHERE document_id=? AND openai_file_id IS NOT NULL
            """,documentId);
    }

    public record ChunkView(String id, int ordinal, String content, String status, String errorCode, boolean active) { }

    @Transactional
    public Document retry(UUID id) {
        var found=jdbc.queryForList("SELECT document_id FROM KnowledgeDocument WHERE document_id=? AND pipeline_version IN (2,3) AND active=TRUE AND status='FAILED' AND payload IS NOT NULL AND (lease_until IS NULL OR lease_until<UTC_TIMESTAMP(6)) FOR UPDATE",String.class,id.toString());
        if (found.isEmpty()) throw new BusinessRuleException("KNOWLEDGE_RETRY_REQUIRES_FAILED_DOCUMENT");
        var revision=jdbc.queryForMap("SELECT ocr_edited,processing_stage FROM KnowledgeDocument WHERE document_id=?",id.toString());
        boolean edited=Boolean.TRUE.equals(revision.get("ocr_edited"));
        boolean rechunk="RECHUNKING".equals(revision.get("processing_stage"));
        jdbc.update("""
            INSERT IGNORE INTO KnowledgeRemoteCleanup(vector_store_id,openai_file_id)
            SELECT d.vector_store_id,c.openai_file_id FROM KnowledgeDocument d JOIN KnowledgeDocumentChunk c ON c.document_id=d.document_id
            WHERE d.document_id=? AND c.openai_file_id IS NOT NULL
            """,id.toString());
        if (edited && !rechunk)
            jdbc.update("UPDATE KnowledgeDocumentChunk SET status='QUEUED',openai_file_id=NULL,error_code=NULL WHERE document_id=? AND active=TRUE",id.toString());
        else jdbc.update("DELETE FROM KnowledgeDocumentChunk WHERE document_id=?",id.toString());
        jdbc.update("UPDATE KnowledgeDocument SET status='QUEUED',processing_stage=?,error_code=NULL,attempts=0,next_attempt_at=UTC_TIMESTAMP(6),updated_at=UTC_TIMESTAMP(6) WHERE document_id=?",
            edited?(rechunk?"RECHUNKING":"INDEXING"):null,id.toString());
        return get(id);
    }

    public List<ChunkView> chunks(UUID id) {
        get(id);
        return jdbc.query("SELECT chunk_id,ordinal_no,content,status,error_code,active FROM KnowledgeDocumentChunk WHERE document_id=? ORDER BY ordinal_no",
            (rs,n)->new ChunkView(rs.getString(1),rs.getInt(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getBoolean(6)),id.toString());
    }

    public record ResolvedFile(String fileId, String documentId, String title, String text, int pipelineVersion) { }
    public List<ResolvedFile> resolve(String store, List<String> files) {
        if (!vectorStoreId.equals(store)) throw new BusinessRuleException("KNOWLEDGE_VECTOR_STORE_MISMATCH");
        if (files == null || files.isEmpty() || files.size()>20 || files.stream().anyMatch(f->f==null || !f.matches("file-[A-Za-z0-9_-]+")))
            throw new BusinessRuleException("KNOWLEDGE_FILE_IDS_INVALID");
        String placeholders=String.join(",",java.util.Collections.nCopies(files.size(),"?"));
        var args=new java.util.ArrayList<Object>(); args.add(store); args.addAll(files);
        var resolved=new java.util.ArrayList<>(jdbc.query("""
            SELECT c.openai_file_id,d.document_id,d.filename,c.content,d.pipeline_version
            FROM KnowledgeDocumentChunk c JOIN KnowledgeDocument d ON d.document_id=c.document_id
            WHERE d.vector_store_id=? AND d.active=TRUE AND d.status='COMPLETED' AND c.status='COMPLETED' AND c.active=TRUE
              AND c.openai_file_id IN (
            """+placeholders+")",(rs,n)->new ResolvedFile(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getInt(5)),args.toArray()));
        resolved.addAll(jdbc.query("SELECT openai_file_id,document_id,filename FROM KnowledgeDocument WHERE vector_store_id=? AND active=TRUE AND status='COMPLETED' AND pipeline_version=1 AND openai_file_id IN ("+placeholders+")",
            (rs,n)->new ResolvedFile(rs.getString(1),rs.getString(2),rs.getString(3),null,1),args.toArray()));
        return resolved;
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
        if (type == null) throw new BusinessRuleException("KNOWLEDGE_FILE_TYPE_UNSUPPORTED: use PDF, DOCX, DOC, TXT or MD");
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
                case "doc" -> {
                    if (bytes.length<8 || !HexFormat.of().formatHex(java.util.Arrays.copyOf(bytes,8)).equals("d0cf11e0a1b11ae1"))
                        throw new BusinessRuleException("KNOWLEDGE_DOC_INVALID");
                }
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
