-- STAGING had no dedicated robot runtime behavior. Preserve every endpoint
-- and its room/group/coordinate links, using the ordinary destination type.
UPDATE `Endpoint`
SET `endpoint_type` = 'WAYPOINT'
WHERE `endpoint_type` = 'STAGING';

ALTER TABLE `Endpoint`
    DROP CHECK `CK_Endpoint_type_v31`,
    ADD CONSTRAINT `CK_Endpoint_type_v73`
        CHECK (`endpoint_type` IN ('WAYPOINT', 'CHARGER', 'PARK'));
