ALTER TABLE `DeliveryRequest`
    ADD COLUMN `expected_sender_account_id` BINARY(16) NULL AFTER `resolved_delivery_endpoint_id`,
    ADD COLUMN `expected_receiver_account_id` BINARY(16) NULL AFTER `expected_sender_account_id`,
    ADD CONSTRAINT `fk_delivery_request_expected_sender`
        FOREIGN KEY (`expected_sender_account_id`) REFERENCES `Account` (`account_id`),
    ADD CONSTRAINT `fk_delivery_request_expected_receiver`
        FOREIGN KEY (`expected_receiver_account_id`) REFERENCES `Account` (`account_id`),
    ADD INDEX `ix_delivery_expected_sender` (`expected_sender_account_id`),
    ADD INDEX `ix_delivery_expected_receiver` (`expected_receiver_account_id`);

ALTER TABLE `QrChallenge`
    ADD COLUMN `expected_account_id` BINARY(16) NULL AFTER `used_by_account_id`,
    ADD CONSTRAINT `fk_qr_challenge_expected_account`
        FOREIGN KEY (`expected_account_id`) REFERENCES `Account` (`account_id`),
    ADD INDEX `ix_qr_challenge_expected_account` (`expected_account_id`);

-- Only mutable schema-3 delivery drafts receive the explicit QR contract.
-- Published definitions and inputs already frozen into Jobs remain untouched.
UPDATE `Step` s
JOIN `Workflow` w ON w.`workflow_id` = s.`workflow_id`
SET s.`params_json` = JSON_SET(s.`params_json`, '$.method', 'QR_CODE')
WHERE w.`workflow_code` = 'WF_SECURE_MEDICAL_DELIVERY'
  AND w.`schema_version` = 3
  AND w.`status` = 'DRAFT'
  AND s.`step_code` IN ('AUTH_SENDER', 'AUTH_RECEIVER');

UPDATE `Workflow` w
SET w.`input_schema_json` = JSON_SET(
        w.`input_schema_json`,
        '$.properties.expected_sender_account_id', JSON_OBJECT('type','string','format','uuid'),
        '$.properties.expected_receiver_account_id', JSON_OBJECT('type','string','format','uuid'),
        '$.required', JSON_ARRAY_APPEND(
            COALESCE(JSON_EXTRACT(w.`input_schema_json`, '$.required'), JSON_ARRAY()),
            '$', 'expected_sender_account_id', '$', 'expected_receiver_account_id'
        )
    ),
    w.`compiled_definition_json` = NULL,
    w.`definition_hash` = NULL
WHERE w.`workflow_code` = 'WF_SECURE_MEDICAL_DELIVERY'
  AND w.`schema_version` = 3
  AND w.`status` = 'DRAFT';
