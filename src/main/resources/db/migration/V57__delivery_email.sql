CREATE TABLE delivery_email_outbox (
 id VARCHAR(36) PRIMARY KEY,
 alert_id VARCHAR(36) NOT NULL,
 recipient_id VARCHAR(36) NOT NULL,
 recipient_email VARCHAR(254) NOT NULL,
 job_id VARCHAR(36) NOT NULL,
 event_type VARCHAR(64) NOT NULL,
 subject VARCHAR(255) NOT NULL,
 body TEXT NOT NULL,
 attempts INT NOT NULL DEFAULT 0,
 available_at TIMESTAMP(6) NOT NULL,
 expires_at TIMESTAMP(6) NOT NULL,
 lease_token VARCHAR(36) NULL,
 state VARCHAR(16) NOT NULL DEFAULT 'PENDING',
 last_error VARCHAR(100) NULL,
 UNIQUE KEY uq_delivery_email_alert_recipient (alert_id, recipient_id),
 INDEX ix_delivery_email_pending (state, available_at)
);
