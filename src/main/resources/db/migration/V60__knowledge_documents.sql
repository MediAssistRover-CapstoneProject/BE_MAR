CREATE TABLE KnowledgeDocument (
    document_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    vector_store_id VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    uploaded_by BINARY(16) NULL,
    openai_file_id VARCHAR(100) NULL,
    status VARCHAR(40) NOT NULL,
    error_code VARCHAR(100) NULL,
    payload LONGBLOB NULL,
    lease_token CHAR(36) NULL,
    lease_until DATETIME(6) NULL,
    next_attempt_at DATETIME(6) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uq_knowledge_document_content UNIQUE (vector_store_id, sha256),
    CONSTRAINT fk_knowledge_document_uploader FOREIGN KEY (uploaded_by)
        REFERENCES Account(account_id) ON DELETE SET NULL,
    CONSTRAINT ck_knowledge_document_status CHECK (status IN
        ('QUEUED','UPLOADING','ATTACHING','PROCESSING','COMPLETED','FAILED','CANCELLED','RECONCILIATION_REQUIRED')),
    INDEX ix_knowledge_document_pending (status, next_attempt_at, lease_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
