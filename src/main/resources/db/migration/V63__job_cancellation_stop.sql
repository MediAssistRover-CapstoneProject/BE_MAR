ALTER TABLE Job ADD COLUMN stop_status VARCHAR(32) NULL,
 ADD COLUMN stop_requested_at DATETIME(6) NULL, ADD COLUMN stop_confirmed_at DATETIME(6) NULL;
CREATE INDEX IX_Job_stop_status ON Job(stop_status, job_id);
-- Historical rows remain NULL: absence of evidence is not a confirmed stop.
