-- A document remains the audit/source unit; each indexed item is published separately.
ALTER TABLE KnowledgeDocumentChunk
    ADD COLUMN topic_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE,
    ADD INDEX ix_knowledge_chunk_topic (topic_key, active);

-- A later upload of identical bytes can restore content previously superseded
-- by another document. Deduplication now happens at the published item level.
ALTER TABLE KnowledgeDocument
    DROP INDEX uq_knowledge_document_content,
    ADD INDEX ix_knowledge_document_sha (vector_store_id, sha256, pipeline_version),
    ADD COLUMN migration_source_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD UNIQUE INDEX uq_knowledge_migration_source (migration_source_id);

-- Serialize competing uploads of the same item. The latest upload timestamp wins
-- even if an older upload finishes indexing afterwards.
CREATE TABLE KnowledgeTopicOwner (
    vector_store_id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    topic_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    chunk_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    document_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    upload_created_at DATETIME(6) NOT NULL,
    ordinal_no INT NOT NULL,
    PRIMARY KEY (vector_store_id, topic_key)
) ENGINE=InnoDB;
