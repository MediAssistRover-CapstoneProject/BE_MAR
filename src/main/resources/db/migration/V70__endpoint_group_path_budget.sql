-- Opt-in only: old room pairs retain the original near-door fallback policy.
ALTER TABLE `EndpointGroup`
    ADD COLUMN `fallback_trigger` VARCHAR(32) NOT NULL DEFAULT 'NEAR_DOOR_OBSTRUCTION',
    ADD COLUMN `path_search_budget_sec` INT NOT NULL DEFAULT 10,
    ADD CONSTRAINT `CK_EndpointGroup_fallback_trigger` CHECK (`fallback_trigger` IN ('NEAR_DOOR_OBSTRUCTION', 'ON_PATH_UNAVAILABLE')),
    ADD CONSTRAINT `CK_EndpointGroup_path_budget` CHECK (`path_search_budget_sec` BETWEEN 5 AND 30);
