package com.mediassistrover.backend.knowledge;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.UUID;

@Component
public class KnowledgeDocumentWorker {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(KnowledgeDocumentWorker.class);
    private final JdbcTemplate jdbc;
    private final OpenAiKnowledgeClient client;
    record Work(String id, String store, String filename, String contentType,
                String file, String status, int attempts) { }

    public KnowledgeDocumentWorker(JdbcTemplate jdbc, OpenAiKnowledgeClient client) {
        this.jdbc = jdbc;
        this.client = client;
    }

    @Scheduled(fixedDelayString = "${app.knowledge.poll-delay-ms:5000}", scheduler = "knowledgeScheduler")
    public void runPending() {
        if (!client.configured()) return;
        // A crash between OpenAI creating the file and recording its ID has an unknown outcome.
        jdbc.update("""
            UPDATE KnowledgeDocument SET status='RECONCILIATION_REQUIRED',error_code='UPLOAD_OUTCOME_UNKNOWN',
                payload=NULL,lease_token=NULL,lease_until=NULL,updated_at=UTC_TIMESTAMP(6)
            WHERE pipeline_version=1 AND status='UPLOADING' AND lease_until < UTC_TIMESTAMP(6)
            """);
        List<String> ids = jdbc.queryForList("""
            SELECT document_id FROM KnowledgeDocument WHERE pipeline_version=1 AND active=TRUE AND status IN ('QUEUED','ATTACHING','PROCESSING')
                AND next_attempt_at <= UTC_TIMESTAMP(6) AND (lease_until IS NULL OR lease_until < UTC_TIMESTAMP(6))
            ORDER BY next_attempt_at LIMIT 4
            """, String.class);
        for (String id : ids) {
            try { process(id); }
            catch (RuntimeException exception) {
                // DB failures leave a lease that can be recovered by a later worker, including after restart.
                log.error("Knowledge document processing failed id={} type={}", id, exception.getClass().getSimpleName());
            }
        }
    }

    void process(String id) {
        String token = UUID.randomUUID().toString();
        int claimed = jdbc.update("""
            UPDATE KnowledgeDocument SET lease_token=?,lease_until=DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 180 SECOND)
            WHERE document_id=? AND pipeline_version=1 AND active=TRUE AND status IN ('QUEUED','ATTACHING','PROCESSING')
                AND (lease_until IS NULL OR lease_until < UTC_TIMESTAMP(6))
                AND next_attempt_at <= UTC_TIMESTAMP(6)
            """, token, id);
        if (claimed == 0) return;
        Work work = jdbc.queryForObject("""
            SELECT document_id,vector_store_id,filename,content_type,openai_file_id,status,attempts
            FROM KnowledgeDocument WHERE document_id=? AND lease_token=?
            """, (rs, row) -> new Work(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
            rs.getString(5), rs.getString(6), rs.getInt(7)), id, token);
        if (work == null) return;
        String stage = work.status();
        try {
            String file = work.file();
            if (stage.equals("QUEUED")) {
                jdbc.update("UPDATE KnowledgeDocument SET status='UPLOADING',updated_at=UTC_TIMESTAMP(6) WHERE document_id=? AND lease_token=?", id, token);
                stage = "UPLOADING";
                byte[] bytes = jdbc.queryForObject("SELECT payload FROM KnowledgeDocument WHERE document_id=?", byte[].class, id);
                file = client.upload(work.filename(), work.contentType(), bytes);
                int saved = jdbc.update("""
                    UPDATE KnowledgeDocument SET openai_file_id=?,payload=NULL,updated_at=UTC_TIMESTAMP(6)
                    WHERE document_id=? AND lease_token=?
                    """, file, id, token);
                if (saved == 0) return;
                if (jdbc.update("UPDATE KnowledgeDocument SET status='ATTACHING' WHERE document_id=? AND lease_token=? AND active=TRUE",id,token)==0) return;
                stage = "ATTACHING";
            }
            String status;
            try {
                status = client.status(work.store(), file);
            } catch (OpenAiKnowledgeClient.Failure failure) {
                if (!stage.equals("ATTACHING") || failure.status != 404) throw failure;
                client.attach(work.store(), file, work.filename());
                status = "in_progress";
            }
            switch (status) {
                case "completed" -> finish(id, token, "COMPLETED", null);
                case "failed" -> finish(id, token, "FAILED", "OPENAI_INDEXING_FAILED");
                case "cancelled" -> finish(id, token, "CANCELLED", "OPENAI_INDEXING_CANCELLED");
                case "in_progress" -> retry(id, token, "PROCESSING", null, true);
                default -> throw new OpenAiKnowledgeClient.Failure("OPENAI_STATUS_INVALID", 0, false);
            }
        } catch (OpenAiKnowledgeClient.Failure failure) {
            if (stage.equals("UPLOADING") && failure.ambiguous) {
                finish(id, token, "RECONCILIATION_REQUIRED", failure.getMessage());
            } else if ((failure.transientFailure() || stage.equals("ATTACHING") && failure.status == 409)
                       && work.attempts() < 20) {
                retry(id, token, stage.equals("UPLOADING") ? "QUEUED" : stage, failure.getMessage(), false);
            } else {
                finish(id, token, "FAILED", failure.getMessage());
            }
        }
    }

    private void finish(String id, String token, String state, String error) {
        jdbc.update("""
            UPDATE KnowledgeDocument SET status=?,error_code=?,payload=NULL,lease_token=NULL,lease_until=NULL,
                updated_at=UTC_TIMESTAMP(6) WHERE document_id=? AND lease_token=? AND active=TRUE
            """, state, error, id, token);
    }
    private void retry(String id, String token, String state, String error, boolean healthy) {
        jdbc.update("""
            UPDATE KnowledgeDocument SET status=?,error_code=?,lease_token=NULL,lease_until=NULL,
                attempts=CASE WHEN ? THEN 0 ELSE attempts+1 END,
                next_attempt_at=DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 30 SECOND),updated_at=UTC_TIMESTAMP(6)
            WHERE document_id=? AND lease_token=? AND active=TRUE
            """, state, error, healthy, id, token);
    }
}
