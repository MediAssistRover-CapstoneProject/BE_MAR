ALTER TABLE `Job` DROP CHECK `ck_job_status`;
ALTER TABLE `Job`
    ADD CONSTRAINT `ck_job_status` CHECK (`status` IN ('QUEUED','ACCEPTED','RUNNING','SUSPENDING','SUSPENDED','SUCCEEDED','FAILED','CANCELLED','RECOVERY_REQUIRED')),
    ADD COLUMN `suspend_requested_at` DATETIME(6) NULL,
    ADD COLUMN `suspended_at` DATETIME(6) NULL,
    ADD COLUMN `suspended_duration_ms` BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN `resume_requested` BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN `suspended_map_asset_hash` VARCHAR(64) NULL,
    ADD COLUMN `patrol_repeat_count` INT NOT NULL DEFAULT 1,
    ADD COLUMN `patrol_round` INT NOT NULL DEFAULT 1,
    ADD COLUMN `patrol_round_started_at` DATETIME(6) NULL,
    ADD COLUMN `patrol_round_suspended_base_ms` BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT `ck_job_patrol_round` CHECK (`patrol_repeat_count` BETWEEN 0 AND 1000 AND `patrol_round` >= 1),
    ADD CONSTRAINT `ck_job_suspended_duration` CHECK (`suspended_duration_ms` >= 0 AND `patrol_round_suspended_base_ms` >= 0);

ALTER TABLE `JobStep`
    ADD COLUMN `resumed_from_job_step_id` BINARY(16) NULL,
    ADD COLUMN `patrol_round` INT NOT NULL DEFAULT 1,
    ADD CONSTRAINT `ck_job_step_patrol_round` CHECK (`patrol_round` >= 1);

CREATE TABLE `JobSuspensionHold` (
    `hold_id` BINARY(16) NOT NULL PRIMARY KEY,
    `job_id` BINARY(16) NOT NULL,
    `source` VARCHAR(32) NOT NULL,
    `source_id` BINARY(16) NOT NULL,
    `created_at` DATETIME(6) NOT NULL,
    `released_at` DATETIME(6) NULL,
    CONSTRAINT `uq_job_suspension_source` UNIQUE (`job_id`,`source`,`source_id`),
    CONSTRAINT `fk_job_suspension_job` FOREIGN KEY (`job_id`) REFERENCES `Job` (`job_id`) ON DELETE CASCADE,
    INDEX `ix_job_suspension_open` (`job_id`,`released_at`)
);
