ALTER TABLE `EmergencyIncident`
    ADD COLUMN `resolution_note` TEXT NULL AFTER `closed_at`,
    ADD COLUMN `resolution_evidence_json` JSON NULL AFTER `resolution_note`;
