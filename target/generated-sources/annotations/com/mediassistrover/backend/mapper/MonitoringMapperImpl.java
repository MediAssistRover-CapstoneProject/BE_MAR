package com.mediassistrover.backend.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.mediassistrover.backend.dto.monitoring.response.MonitoringResponses;
import com.mediassistrover.backend.entity.CrowdDensityRecord;
import com.mediassistrover.backend.entity.DetectionEvent;
import com.mediassistrover.backend.entity.Endpoint;
import com.mediassistrover.backend.entity.FacilityMap;
import com.mediassistrover.backend.entity.HeatmapRecord;
import com.mediassistrover.backend.entity.JobStep;
import com.mediassistrover.backend.entity.Robot;
import com.mediassistrover.backend.entity.Zone;
import com.mediassistrover.backend.enums.CrowdDensityLevel;
import com.mediassistrover.backend.enums.DetectionEventType;
import com.mediassistrover.backend.enums.DetectionLocationBasis;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-10-13T11:40:03+0700",
    comments = "version: 1.6.3, compiler: javac, environment: Java 21.0.12.1 (Eclipse Adoptium)"
)
@Component
public class MonitoringMapperImpl implements MonitoringMapper {

    @Override
    public MonitoringResponses.DetectionResponse toResponse(DetectionEvent entity) {
        if ( entity == null ) {
            return null;
        }

        UUID robotId = null;
        UUID mapId = null;
        UUID zoneId = null;
        JsonNode eventPayload = null;
        UUID detectionEventId = null;
        DetectionEventType eventType = null;
        BigDecimal confidenceScore = null;
        DetectionLocationBasis locationBasis = null;
        String modelVersion = null;
        String evidenceRef = null;
        Instant detectedAt = null;
        Instant receivedAt = null;

        robotId = entityRobotRobotId( entity );
        mapId = entityMapMapId( entity );
        zoneId = entityZoneZoneId( entity );
        eventPayload = entity.getEventPayloadJson();
        detectionEventId = entity.getDetectionEventId();
        eventType = entity.getEventType();
        confidenceScore = entity.getConfidenceScore();
        locationBasis = entity.getLocationBasis();
        modelVersion = entity.getModelVersion();
        evidenceRef = entity.getEvidenceRef();
        detectedAt = entity.getDetectedAt();
        receivedAt = entity.getReceivedAt();

        BigDecimal xM = null;
        BigDecimal yM = null;

        MonitoringResponses.DetectionResponse detectionResponse = new MonitoringResponses.DetectionResponse( detectionEventId, robotId, mapId, zoneId, eventType, confidenceScore, xM, yM, locationBasis, modelVersion, eventPayload, evidenceRef, detectedAt, receivedAt );

        return detectionResponse;
    }

    @Override
    public MonitoringResponses.CrowdDensityResponse toResponse(CrowdDensityRecord entity) {
        if ( entity == null ) {
            return null;
        }

        UUID robotId = null;
        UUID zoneId = null;
        UUID checkpointId = null;
        UUID jobStepId = null;
        JsonNode metrics = null;
        UUID crowdDensityRecordId = null;
        int personCount = 0;
        CrowdDensityLevel densityLevel = null;
        BigDecimal confidenceScore = null;
        String modelVersion = null;
        String evidenceRef = null;
        Instant capturedAt = null;
        Instant recordedAt = null;

        robotId = entityRobotRobotId1( entity );
        zoneId = entityZoneZoneId1( entity );
        checkpointId = entityCheckpointEndpointId( entity );
        jobStepId = entityJobStepJobStepId( entity );
        metrics = entity.getMetricsJson();
        crowdDensityRecordId = entity.getCrowdDensityRecordId();
        personCount = entity.getPersonCount();
        densityLevel = entity.getDensityLevel();
        confidenceScore = entity.getConfidenceScore();
        modelVersion = entity.getModelVersion();
        evidenceRef = entity.getEvidenceRef();
        capturedAt = entity.getCapturedAt();
        recordedAt = entity.getRecordedAt();

        MonitoringResponses.CrowdDensityResponse crowdDensityResponse = new MonitoringResponses.CrowdDensityResponse( crowdDensityRecordId, robotId, zoneId, checkpointId, jobStepId, personCount, densityLevel, confidenceScore, modelVersion, metrics, evidenceRef, capturedAt, recordedAt );

        return crowdDensityResponse;
    }

    @Override
    public MonitoringResponses.HeatmapResponse toResponse(HeatmapRecord entity) {
        if ( entity == null ) {
            return null;
        }

        UUID mapId = null;
        JsonNode gridData = null;
        UUID heatmapRecordId = null;
        String heatmapType = null;
        Instant periodStart = null;
        Instant periodEnd = null;
        BigDecimal cellSizeM = null;
        String imageUri = null;
        BigDecimal maxValue = null;
        int sourceRecordCount = 0;
        Instant generatedAt = null;

        mapId = entityMapMapId1( entity );
        gridData = entity.getGridDataJson();
        heatmapRecordId = entity.getHeatmapRecordId();
        heatmapType = entity.getHeatmapType();
        periodStart = entity.getPeriodStart();
        periodEnd = entity.getPeriodEnd();
        cellSizeM = entity.getCellSizeM();
        imageUri = entity.getImageUri();
        maxValue = entity.getMaxValue();
        sourceRecordCount = entity.getSourceRecordCount();
        generatedAt = entity.getGeneratedAt();

        MonitoringResponses.HeatmapResponse heatmapResponse = new MonitoringResponses.HeatmapResponse( heatmapRecordId, mapId, heatmapType, periodStart, periodEnd, cellSizeM, gridData, imageUri, maxValue, sourceRecordCount, generatedAt );

        return heatmapResponse;
    }

    private UUID entityRobotRobotId(DetectionEvent detectionEvent) {
        Robot robot = detectionEvent.getRobot();
        if ( robot == null ) {
            return null;
        }
        return robot.getRobotId();
    }

    private UUID entityMapMapId(DetectionEvent detectionEvent) {
        FacilityMap map = detectionEvent.getMap();
        if ( map == null ) {
            return null;
        }
        return map.getMapId();
    }

    private UUID entityZoneZoneId(DetectionEvent detectionEvent) {
        Zone zone = detectionEvent.getZone();
        if ( zone == null ) {
            return null;
        }
        return zone.getZoneId();
    }

    private UUID entityRobotRobotId1(CrowdDensityRecord crowdDensityRecord) {
        Robot robot = crowdDensityRecord.getRobot();
        if ( robot == null ) {
            return null;
        }
        return robot.getRobotId();
    }

    private UUID entityZoneZoneId1(CrowdDensityRecord crowdDensityRecord) {
        Zone zone = crowdDensityRecord.getZone();
        if ( zone == null ) {
            return null;
        }
        return zone.getZoneId();
    }

    private UUID entityCheckpointEndpointId(CrowdDensityRecord crowdDensityRecord) {
        Endpoint checkpoint = crowdDensityRecord.getCheckpoint();
        if ( checkpoint == null ) {
            return null;
        }
        return checkpoint.getEndpointId();
    }

    private UUID entityJobStepJobStepId(CrowdDensityRecord crowdDensityRecord) {
        JobStep jobStep = crowdDensityRecord.getJobStep();
        if ( jobStep == null ) {
            return null;
        }
        return jobStep.getJobStepId();
    }

    private UUID entityMapMapId1(HeatmapRecord heatmapRecord) {
        FacilityMap map = heatmapRecord.getMap();
        if ( map == null ) {
            return null;
        }
        return map.getMapId();
    }
}
