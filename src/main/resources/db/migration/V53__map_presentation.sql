CREATE TABLE `MapPresentation` (
    `map_id` BINARY(16) NOT NULL,
    `revision` BIGINT NOT NULL,
    `source_asset_hash` CHAR(64) NOT NULL,
    `source_json` JSON NOT NULL,
    `draft_json` JSON NOT NULL,
    `published_json` JSON NULL,
    `published_revision` BIGINT NULL,
    `published_at` DATETIME(6) NULL,
    `updated_at` DATETIME(6) NOT NULL,
    PRIMARY KEY (`map_id`),
    CONSTRAINT `fk_map_presentation_map` FOREIGN KEY (`map_id`) REFERENCES `Map` (`map_id`) ON DELETE CASCADE
) ENGINE=InnoDB;
