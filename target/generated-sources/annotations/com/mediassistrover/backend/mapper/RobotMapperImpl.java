package com.mediassistrover.backend.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.mediassistrover.backend.dto.robot.request.RobotRequests;
import com.mediassistrover.backend.dto.robot.response.RobotResponses;
import com.mediassistrover.backend.entity.FacilityMap;
import com.mediassistrover.backend.entity.Job;
import com.mediassistrover.backend.entity.Robot;
import com.mediassistrover.backend.entity.RobotLocationHistory;
import com.mediassistrover.backend.entity.RobotStatusHistory;
import com.mediassistrover.backend.enums.BatteryUnit;
import com.mediassistrover.backend.enums.RobotStatus;
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
public class RobotMapperImpl implements RobotMapper {

    @Override
    public RobotResponses.RobotResponse toResponse(Robot robot) {
        if ( robot == null ) {
            return null;
        }

        UUID currentMapId = null;
        UUID robotId = null;
        String robotCode = null;
        String robotName = null;
        String model = null;
        String serialNumber = null;
        String runtimeVersion = null;
        String firmwareVersion = null;
        int protocolVersion = 0;
        JsonNode capabilitiesJson = null;
        String deployedCatalogHash = null;
        String mapAssetHash = null;
        RobotStatus status = null;
        Integer batteryRaw = null;
        BatteryUnit batteryUnit = null;
        BigDecimal batteryPercent = null;
        Instant batteryCapturedAt = null;
        Instant lastHeartbeatAt = null;
        Instant commissionedAt = null;
        long rowVersion = 0L;
        Instant createdAt = null;
        Instant updatedAt = null;

        currentMapId = robotCurrentMapMapId( robot );
        robotId = robot.getRobotId();
        robotCode = robot.getRobotCode();
        robotName = robot.getRobotName();
        model = robot.getModel();
        serialNumber = robot.getSerialNumber();
        runtimeVersion = robot.getRuntimeVersion();
        firmwareVersion = robot.getFirmwareVersion();
        protocolVersion = robot.getProtocolVersion();
        capabilitiesJson = robot.getCapabilitiesJson();
        deployedCatalogHash = robot.getDeployedCatalogHash();
        mapAssetHash = robot.getMapAssetHash();
        status = robot.getStatus();
        batteryRaw = robot.getBatteryRaw();
        batteryUnit = robot.getBatteryUnit();
        batteryPercent = robot.getBatteryPercent();
        batteryCapturedAt = robot.getBatteryCapturedAt();
        lastHeartbeatAt = robot.getLastHeartbeatAt();
        commissionedAt = robot.getCommissionedAt();
        rowVersion = robot.getRowVersion();
        createdAt = robot.getCreatedAt();
        updatedAt = robot.getUpdatedAt();

        RobotResponses.RobotResponse robotResponse = new RobotResponses.RobotResponse( robotId, currentMapId, robotCode, robotName, model, serialNumber, runtimeVersion, firmwareVersion, protocolVersion, capabilitiesJson, deployedCatalogHash, mapAssetHash, status, batteryRaw, batteryUnit, batteryPercent, batteryCapturedAt, lastHeartbeatAt, commissionedAt, rowVersion, createdAt, updatedAt );

        return robotResponse;
    }

    @Override
    public Robot toEntity(RobotRequests.RobotCreateRequest request) {
        if ( request == null ) {
            return null;
        }

        Robot.RobotBuilder robot = Robot.builder();

        robot.robotCode( request.robotCode() );
        robot.robotName( request.robotName() );
        robot.model( request.model() );
        robot.serialNumber( request.serialNumber() );
        robot.protocolVersion( request.protocolVersion() );
        robot.capabilitiesJson( request.capabilitiesJson() );

        robot.status( RobotStatus.OFFLINE );

        return robot.build();
    }

    @Override
    public void update(RobotRequests.RobotUpdateRequest request, Robot robot) {
        if ( request == null ) {
            return;
        }

        robot.setRobotName( request.robotName() );
        robot.setModel( request.model() );
        robot.setSerialNumber( request.serialNumber() );
        robot.setProtocolVersion( request.protocolVersion() );
        robot.setCapabilitiesJson( request.capabilitiesJson() );
    }

    @Override
    public RobotResponses.RobotStatusHistoryResponse toResponse(RobotStatusHistory history) {
        if ( history == null ) {
            return null;
        }

        UUID robotId = null;
        UUID jobId = null;
        UUID robotStatusHistoryId = null;
        RobotStatus previousStatus = null;
        RobotStatus newStatus = null;
        String reason = null;
        String errorCode = null;
        Instant recordedAt = null;

        robotId = historyRobotRobotId( history );
        jobId = historyJobJobId( history );
        robotStatusHistoryId = history.getRobotStatusHistoryId();
        previousStatus = history.getPreviousStatus();
        newStatus = history.getNewStatus();
        reason = history.getReason();
        errorCode = history.getErrorCode();
        recordedAt = history.getRecordedAt();

        RobotResponses.RobotStatusHistoryResponse robotStatusHistoryResponse = new RobotResponses.RobotStatusHistoryResponse( robotStatusHistoryId, robotId, jobId, previousStatus, newStatus, reason, errorCode, recordedAt );

        return robotStatusHistoryResponse;
    }

    @Override
    public RobotResponses.RobotLocationResponse toResponse(RobotLocationHistory history) {
        if ( history == null ) {
            return null;
        }

        UUID robotId = null;
        UUID mapId = null;
        UUID jobId = null;
        UUID robotLocationHistoryId = null;
        BigDecimal yawRad = null;
        BigDecimal localizationConfidence = null;
        boolean fresh = false;
        Instant capturedAt = null;
        Instant receivedAt = null;

        robotId = historyRobotRobotId1( history );
        mapId = historyMapMapId( history );
        jobId = historyJobJobId1( history );
        robotLocationHistoryId = history.getRobotLocationHistoryId();
        yawRad = history.getYawRad();
        localizationConfidence = history.getLocalizationConfidence();
        fresh = history.isFresh();
        capturedAt = history.getCapturedAt();
        receivedAt = history.getReceivedAt();

        BigDecimal xM = null;
        BigDecimal yM = null;

        RobotResponses.RobotLocationResponse robotLocationResponse = new RobotResponses.RobotLocationResponse( robotLocationHistoryId, robotId, mapId, jobId, xM, yM, yawRad, localizationConfidence, fresh, capturedAt, receivedAt );

        return robotLocationResponse;
    }

    private UUID robotCurrentMapMapId(Robot robot) {
        FacilityMap currentMap = robot.getCurrentMap();
        if ( currentMap == null ) {
            return null;
        }
        return currentMap.getMapId();
    }

    private UUID historyRobotRobotId(RobotStatusHistory robotStatusHistory) {
        Robot robot = robotStatusHistory.getRobot();
        if ( robot == null ) {
            return null;
        }
        return robot.getRobotId();
    }

    private UUID historyJobJobId(RobotStatusHistory robotStatusHistory) {
        Job job = robotStatusHistory.getJob();
        if ( job == null ) {
            return null;
        }
        return job.getJobId();
    }

    private UUID historyRobotRobotId1(RobotLocationHistory robotLocationHistory) {
        Robot robot = robotLocationHistory.getRobot();
        if ( robot == null ) {
            return null;
        }
        return robot.getRobotId();
    }

    private UUID historyMapMapId(RobotLocationHistory robotLocationHistory) {
        FacilityMap map = robotLocationHistory.getMap();
        if ( map == null ) {
            return null;
        }
        return map.getMapId();
    }

    private UUID historyJobJobId1(RobotLocationHistory robotLocationHistory) {
        Job job = robotLocationHistory.getJob();
        if ( job == null ) {
            return null;
        }
        return job.getJobId();
    }
}
