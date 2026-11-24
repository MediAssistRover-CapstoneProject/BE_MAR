-- Existing generic groups retain their map-only scope. New room groups are explicit.
ALTER TABLE `EndpointGroup`
    ADD COLUMN `room_id` BINARY(16) NULL,
    ADD COLUMN `fallback_radius_m` DECIMAL(5,3) NOT NULL DEFAULT 0.750,
    ADD CONSTRAINT `FK_EndpointGroup_room` FOREIGN KEY (`room_id`) REFERENCES `Room` (`room_id`),
    ADD CONSTRAINT `CK_EndpointGroup_fallback_radius` CHECK (`fallback_radius_m` >= 0.100 AND `fallback_radius_m` <= 2.000),
    ADD CONSTRAINT `CK_EndpointGroup_room_priority` CHECK (`room_id` IS NULL OR `selection_policy` = 'PRIORITY');
