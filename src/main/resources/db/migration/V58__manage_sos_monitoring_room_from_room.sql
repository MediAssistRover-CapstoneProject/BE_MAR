ALTER TABLE `Room`
    ADD COLUMN `sos_original_room_name` VARCHAR(160) NULL AFTER `room_type`,
    ADD COLUMN `sos_original_room_type` VARCHAR(64) NULL AFTER `sos_original_room_name`;

ALTER TABLE `Endpoint`
    ADD COLUMN `sos_original_endpoint_name` VARCHAR(160) NULL AFTER `endpoint_name`;

INSERT INTO `SystemSetting`
    (`system_setting_id`, `updated_by_account_id`, `setting_key`, `setting_value_json`,
     `data_type`, `category`, `description`, `is_public`, `is_active`, `updated_at`)
SELECT UNHEX(MD5('sos.monitoring-room-id')), NULL, 'sos.monitoring-room-id', JSON_OBJECT('value', ''),
       'STRING', 'SOS', 'Room assigned to receive robot SOS calls', FALSE, TRUE, UTC_TIMESTAMP(6)
WHERE NOT EXISTS (
    SELECT 1 FROM `SystemSetting` WHERE `setting_key` = 'sos.monitoring-room-id'
);

UPDATE `Room` r
JOIN `SystemSetting` s
  ON s.`setting_key` = 'sos.monitoring-room-id'
 AND s.`is_active` = TRUE
SET r.`sos_original_room_name` = COALESCE(r.`sos_original_room_name`, r.`room_name`),
    r.`sos_original_room_type` = COALESCE(r.`sos_original_room_type`,
        CASE WHEN UPPER(r.`room_type`) = 'SOS' THEN 'GENERAL' ELSE r.`room_type` END),
    r.`room_type` = 'SOS'
WHERE JSON_UNQUOTE(JSON_EXTRACT(s.`setting_value_json`, '$.value'))
          REGEXP '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$'
  AND r.`room_id` = UNHEX(REPLACE(JSON_UNQUOTE(JSON_EXTRACT(s.`setting_value_json`, '$.value')), '-', ''));

UPDATE `Endpoint` e
JOIN `Room` r ON r.`room_id` = e.`room_id`
SET e.`sos_original_endpoint_name` = e.`endpoint_name`
WHERE UPPER(r.`room_type`) = 'SOS'
  AND e.`sos_original_endpoint_name` IS NULL;
