package com.mediassistrover.backend.knowledge;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** One bounded external operation per lease; no DB transaction is held over I/O. */
@Component
public class KnowledgeChunkWorker {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(KnowledgeChunkWorker.class);
    private final JdbcTemplate jdbc;
    private final OpenAiKnowledgeClient client;
    private final KnowledgePreprocessor processor;
    private final TransactionTemplate tx;
    record Work(String id, String store, String name, String stage, int attempts, int pipelineVersion) { }
    record Chunk(String id, String text, String file, String status) { }
    record Topic(String id, String key, int ordinal) { }
    record Owner(String chunkId, String documentId, java.time.Instant createdAt, int ordinal) { }
    public KnowledgeChunkWorker(JdbcTemplate jdbc, OpenAiKnowledgeClient client, KnowledgePreprocessor processor,
                                PlatformTransactionManager transactions) {
        this.jdbc=jdbc; this.client=client; this.processor=processor; this.tx=new TransactionTemplate(transactions);
    }

    @Scheduled(fixedDelayString="${app.knowledge.poll-delay-ms:5000}", scheduler="knowledgeScheduler")
    public void tick() {
        if (!client.configured()) return;
        enqueueLegacyDocuments();
        for (String id : jdbc.queryForList("""
            SELECT document_id FROM KnowledgeDocument
            WHERE pipeline_version IN (2,3) AND active=TRUE AND status IN ('QUEUED','ATTACHING','PROCESSING')
              AND next_attempt_at <= UTC_TIMESTAMP(6) AND (lease_until IS NULL OR lease_until < UTC_TIMESTAMP(6))
            ORDER BY next_attempt_at LIMIT 4
            """, String.class)) {
            try { process(id); }
            catch (RuntimeException error) {
                log.error("Knowledge processing failed id={} type={}", id, error.getClass().getSimpleName());
            }
        }
        cleanup();
    }

    private void enqueueLegacyDocuments() {
        // Retained v2 originals are reindexed once as independently replaceable items.
        // A v2 file completed after deployment is picked up by the next poll.
        jdbc.update("""
            INSERT IGNORE INTO KnowledgeDocument
              (document_id,vector_store_id,filename,content_type,size_bytes,sha256,uploaded_by,
               status,payload,pipeline_version,replaces_document_id,migration_source_id,next_attempt_at,created_at,updated_at)
            SELECT UUID(),d.vector_store_id,d.filename,d.content_type,d.size_bytes,d.sha256,d.uploaded_by,
                   'QUEUED',d.payload,3,d.document_id,d.document_id,UTC_TIMESTAMP(6),d.created_at,UTC_TIMESTAMP(6)
            FROM KnowledgeDocument d
            WHERE d.pipeline_version=2 AND d.active=TRUE AND d.status='COMPLETED' AND d.payload IS NOT NULL
              AND NOT EXISTS (SELECT 1 FROM KnowledgeDocument n WHERE n.migration_source_id=d.document_id)
              AND NOT EXISTS (SELECT 1 FROM KnowledgeDocument n WHERE n.replaces_document_id=d.document_id
                              AND n.active=TRUE AND n.status IN ('QUEUED','ATTACHING','PROCESSING','COMPLETED'))
            """);
    }

    void process(String id) {
        String token=UUID.randomUUID().toString();
        if (jdbc.update("""
            UPDATE KnowledgeDocument SET lease_token=?,lease_until=DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 180 SECOND)
            WHERE document_id=? AND pipeline_version IN (2,3) AND active=TRUE AND status IN ('QUEUED','ATTACHING','PROCESSING')
              AND next_attempt_at <= UTC_TIMESTAMP(6) AND (lease_until IS NULL OR lease_until < UTC_TIMESTAMP(6))
            """, token,id)==0) return;
        Work work=jdbc.queryForObject("""
            SELECT document_id,vector_store_id,filename,processing_stage,attempts,pipeline_version FROM KnowledgeDocument
            WHERE document_id=? AND lease_token=?
            """, (rs,n)->new Work(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getInt(5),rs.getInt(6)),id,token);
        if (work==null) return;
        try {
            if (work.stage()==null || work.stage().equals("EXTRACTING")) {
                jdbc.update("UPDATE KnowledgeDocument SET processing_stage='EXTRACTING' WHERE document_id=? AND lease_token=?",id,token);
                byte[] original=jdbc.queryForObject("SELECT payload FROM KnowledgeDocument WHERE document_id=?",byte[].class,id);
                var chunks=processor.extract(work.name(), original);
                tx.executeWithoutResult(s -> {
                    if (!lock(id,token)) return;
                    for (int i=0;i<chunks.size();i++) {
                        var chunk=chunks.get(i);
                        jdbc.update("""
                            INSERT INTO KnowledgeDocumentChunk(chunk_id,document_id,ordinal_no,content,sha256,topic_key,status)
                            VALUES(?,?,?,?,?,?,'QUEUED')
                            """, UUID.randomUUID().toString(),id,i,chunk.text(),chunk.sha256(),chunk.topicKey());
                    }
                    jdbc.update("UPDATE KnowledgeDocument SET processing_stage='INDEXING',status='PROCESSING' WHERE document_id=?",id);
                });
                release(id,token,null,false);
                return;
            }
            var chunks=jdbc.query("""
                SELECT chunk_id,content,openai_file_id,status FROM KnowledgeDocumentChunk
                WHERE document_id=? AND status<>'COMPLETED' ORDER BY ordinal_no LIMIT 1
                """,(rs,n)->new Chunk(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4)),id);
            if (chunks.isEmpty()) { publish(id,token); return; }
            Chunk chunk=chunks.getFirst();
            if (chunk.status().equals("UPLOADING")) {
                terminal(id,token,"RECONCILIATION_REQUIRED","UPLOAD_OUTCOME_UNKNOWN");
                return;
            }
            if (chunk.status().equals("QUEUED")) {
                boolean allowed=Boolean.TRUE.equals(tx.execute(s->{
                    if (!lock(id,token)) return false;
                    jdbc.update("UPDATE KnowledgeDocumentChunk SET status='UPLOADING' WHERE chunk_id=?",chunk.id());
                    return true;
                }));
                if (!allowed) return;
                String file;
                try { file=client.upload("chunk.txt","text/plain",chunk.text().getBytes(StandardCharsets.UTF_8)); }
                catch (OpenAiKnowledgeClient.Failure failure) {
                    if (failure.ambiguous) {
                        terminal(id,token,"RECONCILIATION_REQUIRED",failure.getMessage()); return;
                    }
                    jdbc.update("UPDATE KnowledgeDocumentChunk SET status='QUEUED' WHERE chunk_id=?",chunk.id());
                    throw failure;
                }
                // Persist even if cancellation happened during HTTP, so cleanup can find it.
                jdbc.update("UPDATE KnowledgeDocumentChunk SET openai_file_id=?,status='ATTACHING' WHERE chunk_id=?",file,chunk.id());
            } else if (chunk.status().equals("ATTACHING")) {
                // Attach may have succeeded before a previous timeout; status makes retries safe.
                try { client.status(work.store(),chunk.file()); }
                catch (OpenAiKnowledgeClient.Failure failure) {
                    if (failure.status!=404) throw failure;
                    client.attachChunk(work.store(),chunk.file(),id,work.pipelineVersion());
                }
                jdbc.update("UPDATE KnowledgeDocumentChunk SET status='PROCESSING' WHERE chunk_id=?",chunk.id());
            } else {
                String status=client.status(work.store(),chunk.file());
                if (status.equals("completed"))
                    jdbc.update("UPDATE KnowledgeDocumentChunk SET status='COMPLETED' WHERE chunk_id=?",chunk.id());
                else if (status.equals("failed") || status.equals("cancelled")) {
                    terminal(id,token,"FAILED","OPENAI_INDEXING_FAILED"); return;
                } else if (!status.equals("in_progress")) throw new OpenAiKnowledgeClient.Failure("OPENAI_STATUS_INVALID",0,false);
            }
            release(id,token,null,false);
        } catch (KnowledgePreprocessor.Failure failure) { terminal(id,token,"FAILED",failure.getMessage()); }
        catch (OpenAiKnowledgeClient.Failure failure) {
            if ((failure.transientFailure() || failure.status==409) && work.attempts()<20)
                release(id,token,failure.getMessage(),true);
            else terminal(id,token,"FAILED",failure.getMessage());
        }
    }

    private boolean lock(String id,String token) {
        return !jdbc.queryForList("""
            SELECT document_id FROM KnowledgeDocument WHERE document_id=? AND lease_token=? AND active=TRUE
              AND lease_until>UTC_TIMESTAMP(6) AND status IN ('QUEUED','ATTACHING','PROCESSING') FOR UPDATE
            """,String.class,id,token).isEmpty();
    }
    private void publish(String id,String token) {
        tx.executeWithoutResult(s->{
            // Deletion and replacement creation lock the old document first.
            // Use the same order here to avoid a parent/child lock inversion.
            var replacing=jdbc.queryForList("SELECT replaces_document_id FROM KnowledgeDocument WHERE document_id=?",String.class,id);
            if (replacing.isEmpty()) return;
            String old=replacing.getFirst();
            var active=old==null ? List.<String>of() : jdbc.queryForList(
                "SELECT document_id FROM KnowledgeDocument WHERE document_id=? AND active=TRUE FOR UPDATE",String.class,old);
            if (!lock(id,token)) return;
            Long total=jdbc.queryForObject("SELECT COUNT(*) FROM KnowledgeDocumentChunk WHERE document_id=?",Long.class,id);
            Long pending=jdbc.queryForObject("SELECT COUNT(*) FROM KnowledgeDocumentChunk WHERE document_id=? AND status<>'COMPLETED'",Long.class,id);
            if (total==null || total==0 || pending==null || pending!=0) throw new IllegalStateException("Incomplete chunks");
            String store=jdbc.queryForObject("SELECT vector_store_id FROM KnowledgeDocument WHERE document_id=?",String.class,id);
            java.time.Instant created=jdbc.queryForObject("SELECT created_at FROM KnowledgeDocument WHERE document_id=?",
                java.sql.Timestamp.class,id).toInstant();
            if (old!=null) {
                if (active.isEmpty()) { terminal(id,token,"CANCELLED","KNOWLEDGE_REPLACEMENT_CANCELLED"); return; }
                jdbc.update("UPDATE KnowledgeDocument SET active=FALSE,status='CANCELLED',lease_token=NULL,lease_until=NULL,updated_at=UTC_TIMESTAMP(6) WHERE document_id=?",old);
                jdbc.update("DELETE FROM KnowledgeTopicOwner WHERE document_id=?",old);
            }
            var topics=jdbc.query("SELECT chunk_id,topic_key,ordinal_no FROM KnowledgeDocumentChunk WHERE document_id=? AND topic_key IS NOT NULL ORDER BY topic_key,ordinal_no",
                (rs,n)->new Topic(rs.getString(1),rs.getString(2),rs.getInt(3)),id);
            for (Topic topic:topics) {
                jdbc.update("""
                    INSERT IGNORE INTO KnowledgeTopicOwner(vector_store_id,topic_key,chunk_id,document_id,upload_created_at,ordinal_no)
                    VALUES(?,?,?,?,?,?)
                    """,store,topic.key(),topic.id(),id,java.sql.Timestamp.from(created),topic.ordinal());
                Owner owner=jdbc.queryForObject("""
                    SELECT chunk_id,document_id,upload_created_at,ordinal_no FROM KnowledgeTopicOwner
                    WHERE vector_store_id=? AND topic_key=? FOR UPDATE
                    """,(rs,n)->new Owner(rs.getString(1),rs.getString(2),rs.getTimestamp(3).toInstant(),rs.getInt(4)),store,topic.key());
                if (owner.chunkId().equals(topic.id())) continue;
                int time=created.compareTo(owner.createdAt());
                boolean newer=time>0 || (time==0 && (id.compareTo(owner.documentId())>0
                    || id.equals(owner.documentId()) && topic.ordinal()>owner.ordinal()));
                if (newer) {
                    jdbc.update("UPDATE KnowledgeDocumentChunk SET active=FALSE WHERE chunk_id=?",owner.chunkId());
                    jdbc.update("""
                        UPDATE KnowledgeTopicOwner SET chunk_id=?,document_id=?,upload_created_at=?,ordinal_no=?
                        WHERE vector_store_id=? AND topic_key=?
                        """,topic.id(),id,java.sql.Timestamp.from(created),topic.ordinal(),store,topic.key());
                } else {
                    jdbc.update("UPDATE KnowledgeDocumentChunk SET active=FALSE WHERE chunk_id=?",topic.id());
                }
            }
            jdbc.update("""
                UPDATE KnowledgeDocument SET status='COMPLETED',processing_stage='READY',error_code=NULL,
                  lease_token=NULL,lease_until=NULL,updated_at=UTC_TIMESTAMP(6) WHERE document_id=?
                """,id);
        });
    }
    private void terminal(String id,String token,String status,String error) {
        jdbc.update("""
            UPDATE KnowledgeDocument SET status=?,error_code=?,lease_token=NULL,lease_until=NULL,updated_at=UTC_TIMESTAMP(6)
            WHERE document_id=? AND lease_token=? AND active=TRUE
            """,status,error,id,token);
    }
    private void release(String id,String token,String error,boolean failed) {
        jdbc.update("""
            UPDATE KnowledgeDocument SET error_code=?,attempts=CASE WHEN ? THEN attempts+1 ELSE 0 END,
              lease_token=NULL,lease_until=NULL,next_attempt_at=DATE_ADD(UTC_TIMESTAMP(6),INTERVAL ? SECOND),updated_at=UTC_TIMESTAMP(6)
            WHERE document_id=? AND lease_token=?
            """,error,failed,failed?30:1,id,token);
    }
    void cleanup() {
        // Rebuild the outbox each tick so cancellation during an HTTP call is covered.
        jdbc.update("""
            INSERT IGNORE INTO KnowledgeRemoteCleanup(vector_store_id,openai_file_id)
            SELECT d.vector_store_id,c.openai_file_id FROM KnowledgeDocumentChunk c JOIN KnowledgeDocument d ON d.document_id=c.document_id
            WHERE c.openai_file_id IS NOT NULL AND c.status<>'DETACHED'
              AND (d.lease_until IS NULL OR d.lease_until<UTC_TIMESTAMP(6))
              AND (d.active=FALSE OR d.status IN ('FAILED','CANCELLED','RECONCILIATION_REQUIRED')
                   OR (d.status='COMPLETED' AND c.active=FALSE))
            """);
        jdbc.update("""
            INSERT IGNORE INTO KnowledgeRemoteCleanup(vector_store_id,openai_file_id)
            SELECT vector_store_id,openai_file_id FROM KnowledgeDocument
            WHERE pipeline_version=1 AND openai_file_id IS NOT NULL AND active=FALSE AND COALESCE(processing_stage,'')<>'DETACHED'
              AND (lease_until IS NULL OR lease_until<UTC_TIMESTAMP(6))
            """);
        for (var row:jdbc.queryForList("SELECT vector_store_id,openai_file_id FROM KnowledgeRemoteCleanup WHERE next_attempt_at<=UTC_TIMESTAMP(6) LIMIT 4")) {
            String store=(String)row.get("vector_store_id"), file=(String)row.get("openai_file_id");
            try {
                client.detach(store,file);
                tx.executeWithoutResult(s->{
                    jdbc.update("UPDATE KnowledgeDocumentChunk SET status='DETACHED' WHERE openai_file_id=?",file);
                    jdbc.update("UPDATE KnowledgeDocument SET processing_stage='DETACHED' WHERE pipeline_version=1 AND active=FALSE AND openai_file_id=?",file);
                    jdbc.update("DELETE FROM KnowledgeRemoteCleanup WHERE vector_store_id=? AND openai_file_id=?",store,file);
                });
            } catch (OpenAiKnowledgeClient.Failure failure) {
                jdbc.update("UPDATE KnowledgeRemoteCleanup SET attempts=attempts+1,next_attempt_at=DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 60 SECOND) WHERE vector_store_id=? AND openai_file_id=?",store,file);
            }
        }
    }
}
