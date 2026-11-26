-- V70 is already deployed. Preserve its checksum and retire its old policy here.
ALTER TABLE `EndpointGroup`
    DROP CHECK `CK_EndpointGroup_fallback_trigger`;

-- Keep the existing allow_fallback choice and per-group time budget unchanged.
UPDATE `EndpointGroup`
SET `fallback_trigger` = 'ON_PATH_UNAVAILABLE'
WHERE `fallback_trigger` <> 'ON_PATH_UNAVAILABLE';

ALTER TABLE `EndpointGroup`
    ALTER COLUMN `fallback_trigger` SET DEFAULT 'ON_PATH_UNAVAILABLE',
    ADD CONSTRAINT `CK_EndpointGroup_fallback_trigger`
        CHECK (`fallback_trigger` = 'ON_PATH_UNAVAILABLE');

-- fallback_radius_m remains compatibility metadata; navigation no longer gates
-- fallback on the robot's distance to the doorway. Historical Job JSON is immutable.
