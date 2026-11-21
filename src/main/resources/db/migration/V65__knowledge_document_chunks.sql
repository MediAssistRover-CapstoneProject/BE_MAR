ALTER TABLE KnowledgeDocument
    ADD COLUMN pipeline_version INT NOT NULL DEFAULT 1,
    ADD COLUMN processing_stage VARCHAR(40) NULL,
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN replaces_document_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    DROP INDEX uq_knowledge_document_content,
    ADD CONSTRAINT uq_knowledge_document_content UNIQUE(vector_store_id, sha256, pipeline_version),
    ADD CONSTRAINT fk_knowledge_replaces FOREIGN KEY(replaces_document_id)
        REFERENCES KnowledgeDocument(document_id);

CREATE TABLE KnowledgeDocumentChunk (
    chunk_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    document_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    ordinal_no INT NOT NULL,
    content MEDIUMTEXT NOT NULL,
    sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    openai_file_id VARCHAR(100) NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'QUEUED',
    error_code VARCHAR(100) NULL,
    CONSTRAINT fk_knowledge_chunk_document FOREIGN KEY(document_id) REFERENCES KnowledgeDocument(document_id),
    CONSTRAINT uq_knowledge_chunk_ordinal UNIQUE(document_id, ordinal_no),
    CONSTRAINT uq_knowledge_chunk_file UNIQUE(openai_file_id),
    INDEX ix_knowledge_chunk_pending(document_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Durable remote cleanup: detach from this store only (never delete a file that
-- another store might reference). Original payloads are retained in BE.
CREATE TABLE KnowledgeRemoteCleanup (
    vector_store_id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    openai_file_id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    next_attempt_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    attempts INT NOT NULL DEFAULT 0,
    PRIMARY KEY(vector_store_id, openai_file_id)
) ENGINE=InnoDB;
