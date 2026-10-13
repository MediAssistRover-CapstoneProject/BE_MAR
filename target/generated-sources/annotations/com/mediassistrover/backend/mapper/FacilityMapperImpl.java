package com.mediassistrover.backend.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.mediassistrover.backend.dto.facility.request.FacilityRequests;
import com.mediassistrover.backend.dto.facility.response.FacilityResponses;
import com.mediassistrover.backend.entity.Account;
import com.mediassistrover.backend.entity.Endpoint;
import com.mediassistrover.backend.entity.EndpointGroup;
import com.mediassistrover.backend.entity.EndpointGroupMember;
import com.mediassistrover.backend.entity.FacilityMap;
import com.mediassistrover.backend.entity.Floor;
import com.mediassistrover.backend.entity.HospitalInformation;
import com.mediassistrover.backend.entity.HospitalInformationArticle;
import com.mediassistrover.backend.entity.Room;
import com.mediassistrover.backend.entity.Zone;
import com.mediassistrover.backend.enums.EndpointType;
import com.mediassistrover.backend.enums.HospitalInformationStatus;
import com.mediassistrover.backend.enums.MapStatus;
import com.mediassistrover.backend.enums.SelectionPolicy;
import com.mediassistrover.backend.enums.ZoneType;
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
public class FacilityMapperImpl implements FacilityMapper {

    @Override
    public FacilityResponses.HospitalInformationResponse toResponse(HospitalInformation entity) {
        if ( entity == null ) {
            return null;
        }

        UUID updatedByAccountId = null;
        UUID hospitalInformationId = null;
        String infoCategory = null;
        String title = null;
        String content = null;
        String languageCode = null;
        JsonNode keywordsJson = null;
        HospitalInformationStatus status = null;
        boolean publicVisible = false;
        Instant effectiveFrom = null;
        Instant effectiveUntil = null;
        Instant createdAt = null;
        Instant updatedAt = null;

        updatedByAccountId = entityUpdatedByAccountId( entity );
        hospitalInformationId = entity.getHospitalInformationId();
        infoCategory = entity.getInfoCategory();
        title = entity.getTitle();
        content = entity.getContent();
        languageCode = entity.getLanguageCode();
        keywordsJson = entity.getKeywordsJson();
        status = entity.getStatus();
        publicVisible = entity.isPublicVisible();
        effectiveFrom = entity.getEffectiveFrom();
        effectiveUntil = entity.getEffectiveUntil();
        createdAt = entity.getCreatedAt();
        updatedAt = entity.getUpdatedAt();

        FacilityResponses.HospitalInformationResponse hospitalInformationResponse = new FacilityResponses.HospitalInformationResponse( hospitalInformationId, updatedByAccountId, infoCategory, title, content, languageCode, keywordsJson, status, publicVisible, effectiveFrom, effectiveUntil, createdAt, updatedAt );

        return hospitalInformationResponse;
    }

    @Override
    public FacilityResponses.HospitalInformationArticleResponse toResponse(HospitalInformationArticle entity) {
        if ( entity == null ) {
            return null;
        }

        UUID hospitalInformationId = null;
        UUID updatedByAccountId = null;
        UUID articleId = null;
        String articleCode = null;
        String category = null;
        String canonicalQuestion = null;
        String title = null;
        String answer = null;
        String languageCode = null;
        JsonNode keywordsJson = null;
        HospitalInformationStatus status = null;
        boolean publicVisible = false;
        Instant effectiveFrom = null;
        Instant effectiveUntil = null;
        long rowVersion = 0L;
        Instant createdAt = null;
        Instant updatedAt = null;

        hospitalInformationId = entityHospitalInformationHospitalInformationId( entity );
        updatedByAccountId = entityUpdatedByAccountId1( entity );
        articleId = entity.getArticleId();
        articleCode = entity.getArticleCode();
        category = entity.getCategory();
        canonicalQuestion = entity.getCanonicalQuestion();
        title = entity.getTitle();
        answer = entity.getAnswer();
        languageCode = entity.getLanguageCode();
        keywordsJson = entity.getKeywordsJson();
        status = entity.getStatus();
        publicVisible = entity.isPublicVisible();
        effectiveFrom = entity.getEffectiveFrom();
        effectiveUntil = entity.getEffectiveUntil();
        rowVersion = entity.getRowVersion();
        createdAt = entity.getCreatedAt();
        updatedAt = entity.getUpdatedAt();

        FacilityResponses.HospitalInformationArticleResponse hospitalInformationArticleResponse = new FacilityResponses.HospitalInformationArticleResponse( articleId, hospitalInformationId, updatedByAccountId, articleCode, category, canonicalQuestion, title, answer, languageCode, keywordsJson, status, publicVisible, effectiveFrom, effectiveUntil, rowVersion, createdAt, updatedAt );

        return hospitalInformationArticleResponse;
    }

    @Override
    public FacilityResponses.FloorResponse toResponse(Floor entity) {
        if ( entity == null ) {
            return null;
        }

        UUID hospitalInformationId = null;
        UUID floorId = null;
        String floorCode = null;
        String floorName = null;
        Integer levelNo = null;
        int displayOrder = 0;
        String description = null;
        boolean active = false;
        Instant createdAt = null;
        Instant updatedAt = null;

        hospitalInformationId = entityHospitalInformationHospitalInformationId1( entity );
        floorId = entity.getFloorId();
        floorCode = entity.getFloorCode();
        floorName = entity.getFloorName();
        levelNo = entity.getLevelNo();
        displayOrder = entity.getDisplayOrder();
        description = entity.getDescription();
        active = entity.isActive();
        createdAt = entity.getCreatedAt();
        updatedAt = entity.getUpdatedAt();

        FacilityResponses.FloorResponse floorResponse = new FacilityResponses.FloorResponse( floorId, hospitalInformationId, floorCode, floorName, levelNo, displayOrder, description, active, createdAt, updatedAt );

        return floorResponse;
    }

    @Override
    public FacilityResponses.MapResponse toResponse(FacilityMap entity) {
        if ( entity == null ) {
            return null;
        }

        UUID floorId = null;
        UUID mapId = null;
        String mapCode = null;
        int versionNo = 0;
        String mapName = null;
        BigDecimal widthM = null;
        BigDecimal heightM = null;
        BigDecimal resolutionMPerPixel = null;
        BigDecimal originXM = null;
        BigDecimal originYM = null;
        BigDecimal originYawRad = null;
        String frameId = null;
        String mapFileUri = null;
        String assetHash = null;
        MapStatus status = null;
        Instant commissionedAt = null;
        Instant createdAt = null;
        Instant updatedAt = null;

        floorId = entityFloorFloorId( entity );
        mapId = entity.getMapId();
        mapCode = entity.getMapCode();
        versionNo = entity.getVersionNo();
        mapName = entity.getMapName();
        widthM = entity.getWidthM();
        heightM = entity.getHeightM();
        resolutionMPerPixel = entity.getResolutionMPerPixel();
        originXM = entity.getOriginXM();
        originYM = entity.getOriginYM();
        originYawRad = entity.getOriginYawRad();
        frameId = entity.getFrameId();
        mapFileUri = entity.getMapFileUri();
        assetHash = entity.getAssetHash();
        status = entity.getStatus();
        commissionedAt = entity.getCommissionedAt();
        createdAt = entity.getCreatedAt();
        updatedAt = entity.getUpdatedAt();

        FacilityResponses.MapResponse mapResponse = new FacilityResponses.MapResponse( mapId, floorId, mapCode, versionNo, mapName, widthM, heightM, resolutionMPerPixel, originXM, originYM, originYawRad, frameId, mapFileUri, assetHash, status, commissionedAt, createdAt, updatedAt );

        return mapResponse;
    }

    @Override
    public FacilityResponses.ZoneResponse toResponse(Zone entity) {
        if ( entity == null ) {
            return null;
        }

        UUID mapId = null;
        UUID zoneId = null;
        String zoneCode = null;
        String zoneName = null;
        ZoneType zoneType = null;
        JsonNode polygonJson = null;
        BigDecimal speedLimitMps = null;
        Integer capacityLimit = null;
        BigDecimal calibratedAreaM2 = null;
        String cameraSourceRef = null;
        JsonNode cameraRoiJson = null;
        boolean active = false;
        boolean publicVisible = false;
        String description = null;
        Instant createdAt = null;
        Instant updatedAt = null;

        mapId = entityMapMapId( entity );
        zoneId = entity.getZoneId();
        zoneCode = entity.getZoneCode();
        zoneName = entity.getZoneName();
        zoneType = entity.getZoneType();
        polygonJson = entity.getPolygonJson();
        speedLimitMps = entity.getSpeedLimitMps();
        capacityLimit = entity.getCapacityLimit();
        calibratedAreaM2 = entity.getCalibratedAreaM2();
        cameraSourceRef = entity.getCameraSourceRef();
        cameraRoiJson = entity.getCameraRoiJson();
        active = entity.isActive();
        publicVisible = entity.isPublicVisible();
        description = entity.getDescription();
        createdAt = entity.getCreatedAt();
        updatedAt = entity.getUpdatedAt();

        FacilityResponses.ZoneResponse zoneResponse = new FacilityResponses.ZoneResponse( zoneId, mapId, zoneCode, zoneName, zoneType, polygonJson, speedLimitMps, capacityLimit, calibratedAreaM2, cameraSourceRef, cameraRoiJson, active, publicVisible, description, createdAt, updatedAt );

        return zoneResponse;
    }

    @Override
    public FacilityResponses.RoomResponse toResponse(Room entity) {
        if ( entity == null ) {
            return null;
        }

        UUID floorId = null;
        UUID defaultEndpointGroupId = null;
        UUID roomId = null;
        String roomCode = null;
        String roomName = null;
        String roomType = null;
        String departmentCode = null;
        String description = null;
        boolean active = false;
        boolean publicVisible = false;
        Instant createdAt = null;
        Instant updatedAt = null;

        floorId = entityFloorFloorId1( entity );
        defaultEndpointGroupId = entityDefaultEndpointGroupEndpointGroupId( entity );
        roomId = entity.getRoomId();
        roomCode = entity.getRoomCode();
        roomName = entity.getRoomName();
        roomType = entity.getRoomType();
        departmentCode = entity.getDepartmentCode();
        description = entity.getDescription();
        active = entity.isActive();
        publicVisible = entity.isPublicVisible();
        createdAt = entity.getCreatedAt();
        updatedAt = entity.getUpdatedAt();

        FacilityResponses.RoomResponse roomResponse = new FacilityResponses.RoomResponse( roomId, floorId, defaultEndpointGroupId, roomCode, roomName, roomType, departmentCode, description, active, publicVisible, createdAt, updatedAt );

        return roomResponse;
    }

    @Override
    public FacilityResponses.EndpointResponse toResponse(Endpoint entity) {
        if ( entity == null ) {
            return null;
        }

        UUID mapId = null;
        UUID zoneId = null;
        UUID roomId = null;
        UUID endpointId = null;
        String endpointCode = null;
        String endpointName = null;
        EndpointType endpointType = null;
        BigDecimal yawRad = null;
        boolean active = false;
        boolean accessible = false;
        boolean publicVisible = false;
        String description = null;
        Instant createdAt = null;
        Instant updatedAt = null;

        mapId = entityMapMapId1( entity );
        zoneId = entityZoneZoneId( entity );
        roomId = entityRoomRoomId( entity );
        endpointId = entity.getEndpointId();
        endpointCode = entity.getEndpointCode();
        endpointName = entity.getEndpointName();
        endpointType = entity.getEndpointType();
        yawRad = entity.getYawRad();
        active = entity.isActive();
        accessible = entity.isAccessible();
        publicVisible = entity.isPublicVisible();
        description = entity.getDescription();
        createdAt = entity.getCreatedAt();
        updatedAt = entity.getUpdatedAt();

        BigDecimal xM = null;
        BigDecimal yM = null;

        FacilityResponses.EndpointResponse endpointResponse = new FacilityResponses.EndpointResponse( endpointId, mapId, zoneId, roomId, endpointCode, endpointName, endpointType, xM, yM, yawRad, active, accessible, publicVisible, description, createdAt, updatedAt );

        return endpointResponse;
    }

    @Override
    public FacilityResponses.EndpointGroupResponse toResponse(EndpointGroup entity) {
        if ( entity == null ) {
            return null;
        }

        UUID mapId = null;
        UUID endpointGroupId = null;
        String groupCode = null;
        String groupName = null;
        SelectionPolicy selectionPolicy = null;
        boolean allowFallback = false;
        String description = null;
        boolean active = false;
        Instant createdAt = null;
        Instant updatedAt = null;

        mapId = entityMapMapId2( entity );
        endpointGroupId = entity.getEndpointGroupId();
        groupCode = entity.getGroupCode();
        groupName = entity.getGroupName();
        selectionPolicy = entity.getSelectionPolicy();
        allowFallback = entity.isAllowFallback();
        description = entity.getDescription();
        active = entity.isActive();
        createdAt = entity.getCreatedAt();
        updatedAt = entity.getUpdatedAt();

        FacilityResponses.EndpointGroupResponse endpointGroupResponse = new FacilityResponses.EndpointGroupResponse( endpointGroupId, mapId, groupCode, groupName, selectionPolicy, allowFallback, description, active, createdAt, updatedAt );

        return endpointGroupResponse;
    }

    @Override
    public FacilityResponses.EndpointGroupMemberResponse toResponse(EndpointGroupMember entity) {
        if ( entity == null ) {
            return null;
        }

        UUID endpointGroupId = null;
        UUID endpointId = null;
        UUID mapId = null;
        int sequenceNo = 0;
        boolean active = false;
        Instant createdAt = null;

        endpointGroupId = entityEndpointGroupEndpointGroupId( entity );
        endpointId = entityEndpointEndpointId( entity );
        mapId = entityMapMapId3( entity );
        sequenceNo = entity.getSequenceNo();
        active = entity.isActive();
        createdAt = entity.getCreatedAt();

        FacilityResponses.EndpointGroupMemberResponse endpointGroupMemberResponse = new FacilityResponses.EndpointGroupMemberResponse( endpointGroupId, endpointId, mapId, sequenceNo, active, createdAt );

        return endpointGroupMemberResponse;
    }

    @Override
    public HospitalInformation toEntity(FacilityRequests.HospitalInformationRequest request) {
        if ( request == null ) {
            return null;
        }

        HospitalInformation.HospitalInformationBuilder hospitalInformation = HospitalInformation.builder();

        hospitalInformation.infoCategory( request.infoCategory() );
        hospitalInformation.title( request.title() );
        hospitalInformation.content( request.content() );
        hospitalInformation.languageCode( request.languageCode() );
        hospitalInformation.keywordsJson( request.keywordsJson() );
        hospitalInformation.publicVisible( request.publicVisible() );
        hospitalInformation.effectiveFrom( request.effectiveFrom() );
        hospitalInformation.effectiveUntil( request.effectiveUntil() );

        hospitalInformation.status( HospitalInformationStatus.DRAFT );

        return hospitalInformation.build();
    }

    @Override
    public HospitalInformationArticle toEntity(FacilityRequests.HospitalInformationArticleRequest request) {
        if ( request == null ) {
            return null;
        }

        HospitalInformationArticle.HospitalInformationArticleBuilder hospitalInformationArticle = HospitalInformationArticle.builder();

        hospitalInformationArticle.articleCode( request.articleCode() );
        hospitalInformationArticle.category( request.category() );
        hospitalInformationArticle.canonicalQuestion( request.canonicalQuestion() );
        hospitalInformationArticle.title( request.title() );
        hospitalInformationArticle.answer( request.answer() );
        hospitalInformationArticle.languageCode( request.languageCode() );
        hospitalInformationArticle.keywordsJson( request.keywordsJson() );
        hospitalInformationArticle.publicVisible( request.publicVisible() );
        hospitalInformationArticle.effectiveFrom( request.effectiveFrom() );
        hospitalInformationArticle.effectiveUntil( request.effectiveUntil() );

        hospitalInformationArticle.status( HospitalInformationStatus.DRAFT );

        return hospitalInformationArticle.build();
    }

    @Override
    public Floor toEntity(FacilityRequests.FloorRequest request) {
        if ( request == null ) {
            return null;
        }

        Floor.FloorBuilder floor = Floor.builder();

        floor.floorCode( request.floorCode() );
        floor.floorName( request.floorName() );
        floor.levelNo( request.levelNo() );
        floor.displayOrder( request.displayOrder() );
        floor.description( request.description() );
        floor.active( request.active() );

        return floor.build();
    }

    @Override
    public FacilityMap toEntity(FacilityRequests.MapRequest request) {
        if ( request == null ) {
            return null;
        }

        FacilityMap.FacilityMapBuilder facilityMap = FacilityMap.builder();

        facilityMap.mapCode( request.mapCode() );
        facilityMap.versionNo( request.versionNo() );
        facilityMap.mapName( request.mapName() );
        facilityMap.widthM( request.widthM() );
        facilityMap.heightM( request.heightM() );
        facilityMap.resolutionMPerPixel( request.resolutionMPerPixel() );
        facilityMap.originXM( request.originXM() );
        facilityMap.originYM( request.originYM() );
        facilityMap.originYawRad( request.originYawRad() );
        facilityMap.frameId( request.frameId() );
        facilityMap.mapFileUri( request.mapFileUri() );
        facilityMap.assetHash( request.assetHash() );

        facilityMap.status( MapStatus.DRAFT );

        return facilityMap.build();
    }

    @Override
    public Zone toEntity(FacilityRequests.ZoneRequest request) {
        if ( request == null ) {
            return null;
        }

        Zone.ZoneBuilder zone = Zone.builder();

        zone.zoneCode( request.zoneCode() );
        zone.zoneName( request.zoneName() );
        zone.zoneType( request.zoneType() );
        zone.polygonJson( request.polygonJson() );
        zone.speedLimitMps( request.speedLimitMps() );
        zone.capacityLimit( request.capacityLimit() );
        zone.calibratedAreaM2( request.calibratedAreaM2() );
        zone.cameraSourceRef( request.cameraSourceRef() );
        zone.cameraRoiJson( request.cameraRoiJson() );
        zone.active( request.active() );
        zone.publicVisible( request.publicVisible() );
        zone.description( request.description() );

        return zone.build();
    }

    @Override
    public Room toEntity(FacilityRequests.RoomRequest request) {
        if ( request == null ) {
            return null;
        }

        Room.RoomBuilder room = Room.builder();

        room.roomCode( request.roomCode() );
        room.roomName( request.roomName() );
        room.roomType( request.roomType() );
        room.departmentCode( request.departmentCode() );
        room.description( request.description() );
        room.active( request.active() );
        room.publicVisible( request.publicVisible() );

        return room.build();
    }

    @Override
    public Endpoint toEntity(FacilityRequests.EndpointRequest request) {
        if ( request == null ) {
            return null;
        }

        Endpoint.EndpointBuilder endpoint = Endpoint.builder();

        endpoint.endpointCode( request.endpointCode() );
        endpoint.endpointName( request.endpointName() );
        endpoint.endpointType( request.endpointType() );
        endpoint.xM( request.xM() );
        endpoint.yM( request.yM() );
        endpoint.yawRad( request.yawRad() );
        endpoint.active( request.active() );
        endpoint.accessible( request.accessible() );
        endpoint.publicVisible( request.publicVisible() );
        endpoint.description( request.description() );

        return endpoint.build();
    }

    @Override
    public EndpointGroup toEntity(FacilityRequests.EndpointGroupRequest request) {
        if ( request == null ) {
            return null;
        }

        EndpointGroup.EndpointGroupBuilder endpointGroup = EndpointGroup.builder();

        endpointGroup.groupCode( request.groupCode() );
        endpointGroup.groupName( request.groupName() );
        endpointGroup.selectionPolicy( request.selectionPolicy() );
        endpointGroup.allowFallback( request.allowFallback() );
        endpointGroup.description( request.description() );
        endpointGroup.active( request.active() );

        return endpointGroup.build();
    }

    @Override
    public void update(FacilityRequests.HospitalInformationRequest request, HospitalInformation entity) {
        if ( request == null ) {
            return;
        }

        entity.setInfoCategory( request.infoCategory() );
        entity.setTitle( request.title() );
        entity.setContent( request.content() );
        entity.setLanguageCode( request.languageCode() );
        entity.setKeywordsJson( request.keywordsJson() );
        entity.setPublicVisible( request.publicVisible() );
        entity.setEffectiveFrom( request.effectiveFrom() );
        entity.setEffectiveUntil( request.effectiveUntil() );
    }

    @Override
    public void update(FacilityRequests.HospitalInformationArticleRequest request, HospitalInformationArticle entity) {
        if ( request == null ) {
            return;
        }

        entity.setArticleCode( request.articleCode() );
        entity.setCategory( request.category() );
        entity.setCanonicalQuestion( request.canonicalQuestion() );
        entity.setTitle( request.title() );
        entity.setAnswer( request.answer() );
        entity.setLanguageCode( request.languageCode() );
        entity.setKeywordsJson( request.keywordsJson() );
        entity.setPublicVisible( request.publicVisible() );
        entity.setEffectiveFrom( request.effectiveFrom() );
        entity.setEffectiveUntil( request.effectiveUntil() );
    }

    @Override
    public void update(FacilityRequests.FloorRequest request, Floor entity) {
        if ( request == null ) {
            return;
        }

        entity.setFloorCode( request.floorCode() );
        entity.setFloorName( request.floorName() );
        entity.setLevelNo( request.levelNo() );
        entity.setDisplayOrder( request.displayOrder() );
        entity.setDescription( request.description() );
        entity.setActive( request.active() );
    }

    @Override
    public void update(FacilityRequests.MapRequest request, FacilityMap entity) {
        if ( request == null ) {
            return;
        }

        entity.setMapCode( request.mapCode() );
        entity.setVersionNo( request.versionNo() );
        entity.setMapName( request.mapName() );
        entity.setWidthM( request.widthM() );
        entity.setHeightM( request.heightM() );
        entity.setResolutionMPerPixel( request.resolutionMPerPixel() );
        entity.setOriginXM( request.originXM() );
        entity.setOriginYM( request.originYM() );
        entity.setOriginYawRad( request.originYawRad() );
        entity.setFrameId( request.frameId() );
        entity.setMapFileUri( request.mapFileUri() );
        entity.setAssetHash( request.assetHash() );
    }

    @Override
    public void update(FacilityRequests.ZoneRequest request, Zone entity) {
        if ( request == null ) {
            return;
        }

        entity.setZoneCode( request.zoneCode() );
        entity.setZoneName( request.zoneName() );
        entity.setZoneType( request.zoneType() );
        entity.setPolygonJson( request.polygonJson() );
        entity.setSpeedLimitMps( request.speedLimitMps() );
        entity.setCapacityLimit( request.capacityLimit() );
        entity.setCalibratedAreaM2( request.calibratedAreaM2() );
        entity.setCameraSourceRef( request.cameraSourceRef() );
        entity.setCameraRoiJson( request.cameraRoiJson() );
        entity.setActive( request.active() );
        entity.setPublicVisible( request.publicVisible() );
        entity.setDescription( request.description() );
    }

    @Override
    public void update(FacilityRequests.RoomRequest request, Room entity) {
        if ( request == null ) {
            return;
        }

        entity.setRoomCode( request.roomCode() );
        entity.setRoomName( request.roomName() );
        entity.setRoomType( request.roomType() );
        entity.setDepartmentCode( request.departmentCode() );
        entity.setDescription( request.description() );
        entity.setActive( request.active() );
        entity.setPublicVisible( request.publicVisible() );
    }

    @Override
    public void update(FacilityRequests.EndpointRequest request, Endpoint entity) {
        if ( request == null ) {
            return;
        }

        entity.setEndpointCode( request.endpointCode() );
        entity.setEndpointName( request.endpointName() );
        entity.setEndpointType( request.endpointType() );
        entity.setYawRad( request.yawRad() );
        entity.setActive( request.active() );
        entity.setAccessible( request.accessible() );
        entity.setPublicVisible( request.publicVisible() );
        entity.setDescription( request.description() );
    }

    @Override
    public void update(FacilityRequests.EndpointGroupRequest request, EndpointGroup entity) {
        if ( request == null ) {
            return;
        }

        entity.setGroupCode( request.groupCode() );
        entity.setGroupName( request.groupName() );
        entity.setSelectionPolicy( request.selectionPolicy() );
        entity.setAllowFallback( request.allowFallback() );
        entity.setDescription( request.description() );
        entity.setActive( request.active() );
    }

    private UUID entityUpdatedByAccountId(HospitalInformation hospitalInformation) {
        Account updatedBy = hospitalInformation.getUpdatedBy();
        if ( updatedBy == null ) {
            return null;
        }
        return updatedBy.getAccountId();
    }

    private UUID entityHospitalInformationHospitalInformationId(HospitalInformationArticle hospitalInformationArticle) {
        HospitalInformation hospitalInformation = hospitalInformationArticle.getHospitalInformation();
        if ( hospitalInformation == null ) {
            return null;
        }
        return hospitalInformation.getHospitalInformationId();
    }

    private UUID entityUpdatedByAccountId1(HospitalInformationArticle hospitalInformationArticle) {
        Account updatedBy = hospitalInformationArticle.getUpdatedBy();
        if ( updatedBy == null ) {
            return null;
        }
        return updatedBy.getAccountId();
    }

    private UUID entityHospitalInformationHospitalInformationId1(Floor floor) {
        HospitalInformation hospitalInformation = floor.getHospitalInformation();
        if ( hospitalInformation == null ) {
            return null;
        }
        return hospitalInformation.getHospitalInformationId();
    }

    private UUID entityFloorFloorId(FacilityMap facilityMap) {
        Floor floor = facilityMap.getFloor();
        if ( floor == null ) {
            return null;
        }
        return floor.getFloorId();
    }

    private UUID entityMapMapId(Zone zone) {
        FacilityMap map = zone.getMap();
        if ( map == null ) {
            return null;
        }
        return map.getMapId();
    }

    private UUID entityFloorFloorId1(Room room) {
        Floor floor = room.getFloor();
        if ( floor == null ) {
            return null;
        }
        return floor.getFloorId();
    }

    private UUID entityDefaultEndpointGroupEndpointGroupId(Room room) {
        EndpointGroup defaultEndpointGroup = room.getDefaultEndpointGroup();
        if ( defaultEndpointGroup == null ) {
            return null;
        }
        return defaultEndpointGroup.getEndpointGroupId();
    }

    private UUID entityMapMapId1(Endpoint endpoint) {
        FacilityMap map = endpoint.getMap();
        if ( map == null ) {
            return null;
        }
        return map.getMapId();
    }

    private UUID entityZoneZoneId(Endpoint endpoint) {
        Zone zone = endpoint.getZone();
        if ( zone == null ) {
            return null;
        }
        return zone.getZoneId();
    }

    private UUID entityRoomRoomId(Endpoint endpoint) {
        Room room = endpoint.getRoom();
        if ( room == null ) {
            return null;
        }
        return room.getRoomId();
    }

    private UUID entityMapMapId2(EndpointGroup endpointGroup) {
        FacilityMap map = endpointGroup.getMap();
        if ( map == null ) {
            return null;
        }
        return map.getMapId();
    }

    private UUID entityEndpointGroupEndpointGroupId(EndpointGroupMember endpointGroupMember) {
        EndpointGroup endpointGroup = endpointGroupMember.getEndpointGroup();
        if ( endpointGroup == null ) {
            return null;
        }
        return endpointGroup.getEndpointGroupId();
    }

    private UUID entityEndpointEndpointId(EndpointGroupMember endpointGroupMember) {
        Endpoint endpoint = endpointGroupMember.getEndpoint();
        if ( endpoint == null ) {
            return null;
        }
        return endpoint.getEndpointId();
    }

    private UUID entityMapMapId3(EndpointGroupMember endpointGroupMember) {
        FacilityMap map = endpointGroupMember.getMap();
        if ( map == null ) {
            return null;
        }
        return map.getMapId();
    }
}
