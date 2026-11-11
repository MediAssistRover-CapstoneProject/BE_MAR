ALTER TABLE `DetectionEvent` DROP CHECK `CK_DetectionEvent_type`;

ALTER TABLE `DetectionEvent`
    ADD CONSTRAINT `CK_DetectionEvent_type`
        CHECK (`event_type` IN ('SUSPECTED_FALL','SUSPECTED_SEIZURE','RESTRICTED_INTRUSION','SOS_SIGNAL','OTHER'));
