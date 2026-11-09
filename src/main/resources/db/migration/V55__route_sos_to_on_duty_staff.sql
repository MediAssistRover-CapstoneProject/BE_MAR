ALTER TABLE `EmergencyIncident`
    ADD COLUMN `sos_responder_account_id` BINARY(16) NULL,
    ADD COLUMN `sos_routing_status` VARCHAR(32) NULL,
    ADD CONSTRAINT `fk_incident_sos_responder` FOREIGN KEY (`sos_responder_account_id`)
        REFERENCES `Account` (`account_id`) ON DELETE SET NULL;
