package com.mediassistrover.backend.service;

import com.mediassistrover.backend.dto.facility.request.FacilityRequests.*;
import com.mediassistrover.backend.dto.facility.response.FacilityResponses.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface FacilityService {
    HospitalInformationResponse createHospitalInformation(HospitalInformationRequest request, UUID actorId, RequestMetadata metadata);
    HospitalInformationResponse updateHospitalInformation(UUID id, HospitalInformationRequest request, UUID actorId, RequestMetadata metadata);
    HospitalInformationResponse publishHospitalInformation(UUID id, UUID actorId, RequestMetadata metadata);
    HospitalInformationResponse retireHospitalInformation(UUID id,String reason,UUID actorId,RequestMetadata metadata);
    HospitalInformationResponse restoreHospitalInformation(UUID id,String reason,UUID actorId,RequestMetadata metadata);
    Page<HospitalInformationResponse> listHospitalInformation(boolean includeDeleted,Pageable pageable);
    HospitalInformationResponse getHospitalInformation(UUID id,boolean includeDeleted);
    com.mediassistrover.backend.dto.common.DeletionContracts.DeletionImpactResponse hospitalDeletionImpact(UUID id);
    com.mediassistrover.backend.dto.common.DeletionContracts.HardDeleteResponse forceDeleteHospital(UUID id,String reason,UUID actorId,RequestMetadata metadata);

    FloorResponse createFloor(FloorRequest request, UUID actorId, RequestMetadata metadata);
    FloorResponse updateFloor(UUID id, FloorRequest request, UUID actorId, RequestMetadata metadata);
    FloorResponse setFloorActive(UUID id,boolean active,String reason,UUID actorId,RequestMetadata metadata);
    Page<FloorResponse> listFloors(boolean includeDeleted,Pageable pageable);
    FloorResponse getFloor(UUID id,boolean includeDeleted);
    com.mediassistrover.backend.dto.common.DeletionContracts.DeletionImpactResponse floorDeletionImpact(UUID id);
    com.mediassistrover.backend.dto.common.DeletionContracts.HardDeleteResponse forceDeleteFloor(UUID id,String reason,UUID actorId,RequestMetadata metadata);

    MapResponse createMap(MapRequest request, UUID actorId, RequestMetadata metadata);
    MapResponse updateMap(UUID id, MapRequest request, UUID actorId, RequestMetadata metadata);
    MapResponse activateMap(UUID id, UUID actorId, RequestMetadata metadata);
    MapResponse archiveMap(UUID id, String reason, UUID actorId, RequestMetadata metadata);
    MapResponse restoreMap(UUID id, String reason, UUID actorId, RequestMetadata metadata);
    MapArtifactResponse getMapArtifact(UUID id);
    Page<MapResponse> listMaps(boolean includeDeleted, Pageable pageable);
    MapResponse getMap(UUID id, boolean includeDeleted);
    com.mediassistrover.backend.dto.common.DeletionContracts.DeletionImpactResponse mapDeletionImpact(UUID id);
    com.mediassistrover.backend.dto.common.DeletionContracts.HardDeleteResponse forceDeleteMap(UUID id, String reason, UUID actorId, RequestMetadata metadata);

    ZoneResponse createZone(ZoneRequest request, UUID actorId, RequestMetadata metadata);
    ZoneResponse updateZone(UUID id, ZoneRequest request, UUID actorId, RequestMetadata metadata);
    ZoneResponse setZoneActive(UUID id, boolean active, String reason, UUID actorId, RequestMetadata metadata);
    Page<ZoneResponse> listZones(boolean includeDeleted, Pageable pageable);
    ZoneResponse getZone(UUID id, boolean includeDeleted);
    com.mediassistrover.backend.dto.common.DeletionContracts.DeletionImpactResponse zoneDeletionImpact(UUID id);
    com.mediassistrover.backend.dto.common.DeletionContracts.HardDeleteResponse forceDeleteZone(UUID id, String reason, UUID actorId, RequestMetadata metadata);

    RoomResponse createRoom(RoomRequest request, UUID actorId, RequestMetadata metadata);
    RoomResponse updateRoom(UUID id, RoomRequest request, UUID actorId, RequestMetadata metadata);
    RoomResponse setRoomActive(UUID id, boolean active, String reason, UUID actorId, RequestMetadata metadata);
    Page<RoomResponse> listRooms(boolean includeDeleted, Pageable pageable);
    RoomResponse getRoom(UUID id, boolean includeDeleted);
    com.mediassistrover.backend.dto.common.DeletionContracts.DeletionImpactResponse roomDeletionImpact(UUID id);
    com.mediassistrover.backend.dto.common.DeletionContracts.HardDeleteResponse forceDeleteRoom(UUID id, String reason, UUID actorId, RequestMetadata metadata);

    EndpointResponse createEndpoint(EndpointRequest request, UUID actorId, RequestMetadata metadata);
    EndpointResponse updateEndpoint(UUID id, EndpointRequest request, UUID actorId, RequestMetadata metadata);
    EndpointResponse setEndpointActive(UUID id, boolean active, String reason, UUID actorId, RequestMetadata metadata);
    Page<EndpointResponse> listEndpoints(boolean includeDeleted, Pageable pageable);
    EndpointResponse getEndpoint(UUID id, boolean includeDeleted);
    com.mediassistrover.backend.dto.common.DeletionContracts.DeletionImpactResponse endpointDeletionImpact(UUID id);
    com.mediassistrover.backend.dto.common.DeletionContracts.HardDeleteResponse forceDeleteEndpoint(UUID id, String reason, UUID actorId, RequestMetadata metadata);

    EndpointGroupResponse createEndpointGroup(EndpointGroupRequest request, UUID actorId, RequestMetadata metadata);
    EndpointGroupResponse updateEndpointGroup(UUID id, EndpointGroupRequest request, UUID actorId, RequestMetadata metadata);
    EndpointGroupResponse setEndpointGroupActive(UUID id, boolean active, String reason, UUID actorId, RequestMetadata metadata);
    Page<EndpointGroupResponse> listEndpointGroups(boolean includeDeleted, Pageable pageable);
    EndpointGroupResponse getEndpointGroup(UUID id, boolean includeDeleted);
    com.mediassistrover.backend.dto.common.DeletionContracts.DeletionImpactResponse endpointGroupDeletionImpact(UUID id);
    com.mediassistrover.backend.dto.common.DeletionContracts.HardDeleteResponse forceDeleteEndpointGroup(UUID id, String reason, UUID actorId, RequestMetadata metadata);

    EndpointGroupMemberResponse createEndpointGroupMember(EndpointGroupMemberRequest request, UUID actorId, RequestMetadata metadata);
    EndpointGroupMemberResponse updateEndpointGroupMember(UUID groupId, UUID endpointId, EndpointGroupMemberRequest request, UUID actorId, RequestMetadata metadata);
    EndpointGroupMemberResponse removeEndpointGroupMember(UUID groupId, UUID endpointId,String reason, UUID actorId, RequestMetadata metadata);
    EndpointGroupMemberResponse restoreEndpointGroupMember(UUID groupId,UUID endpointId,String reason,UUID actorId,RequestMetadata metadata);
    com.mediassistrover.backend.dto.common.DeletionContracts.DeletionImpactResponse endpointGroupMemberDeletionImpact(UUID groupId,UUID endpointId);
    com.mediassistrover.backend.dto.common.DeletionContracts.HardDeleteResponse forceDeleteEndpointGroupMember(UUID groupId,UUID endpointId,String reason,UUID actorId,RequestMetadata metadata);
    Page<EndpointGroupMemberResponse> listEndpointGroupMembers(Pageable pageable);

    Page<HospitalInformationResponse> listPublicHospitalInformation(Pageable pageable);
    Page<FloorResponse> listPublicFloors(Pageable pageable);
    Page<ZoneResponse> listPublicZones(Pageable pageable);
    Page<RoomResponse> listPublicRooms(Pageable pageable);
    Page<EndpointResponse> listPublicEndpoints(Pageable pageable);
}
