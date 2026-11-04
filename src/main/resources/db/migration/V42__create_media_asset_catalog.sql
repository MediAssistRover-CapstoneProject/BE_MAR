CREATE TABLE `MediaAsset` (
    `media_asset_id` BINARY(16) NOT NULL,
    `object_key` VARCHAR(255) NOT NULL,
    `url` TEXT NOT NULL,
    `media_type` VARCHAR(16) NOT NULL,
    `content_type` VARCHAR(100) NOT NULL,
    `size_bytes` BIGINT NOT NULL,
    `sha256` CHAR(64) NULL,
    `created_at` DATETIME(6) NOT NULL,
    CONSTRAINT `pk_media_asset` PRIMARY KEY (`media_asset_id`),
    CONSTRAINT `uq_media_asset_object_key` UNIQUE (`object_key`),
    CONSTRAINT `ck_media_asset_type` CHECK (`media_type` IN ('IMAGE','VIDEO')),
    CONSTRAINT `ck_media_asset_size` CHECK (`size_bytes` >= 0),
    INDEX `ix_media_asset_type_created` (`media_type`,`created_at`),
    INDEX `ix_media_asset_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
