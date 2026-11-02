ALTER TABLE `StaffProfile`
  ADD COLUMN `url` TEXT NULL AFTER `contact_extension`;

ALTER TABLE `VoiceAssistantTurn`
  ADD COLUMN `audio_url` VARCHAR(1000) NULL AFTER `audio_path`;
