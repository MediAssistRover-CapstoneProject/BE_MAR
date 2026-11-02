-- The same DB transaction now owns both the upload receipt and its bytes.
CREATE TABLE `MapBinaryAsset` (
 `asset_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 `content_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 `byte_size` INT NOT NULL,
 `payload` LONGBLOB NOT NULL,
 `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY (`asset_key`),
 CONSTRAINT `ck_map_binary_size` CHECK (`byte_size` > 0 AND `byte_size` <= 16777216)
) ENGINE=InnoDB;
