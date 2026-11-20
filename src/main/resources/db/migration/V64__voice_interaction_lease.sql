ALTER TABLE `VoiceInteraction`
    ADD COLUMN `lease_expires_at` DATETIME(6) NULL,
    ADD INDEX `ix_voice_active_lease` (`status`, `lease_expires_at`);
