package com.mediassistrover.backend.service.impl;

import com.mediassistrover.backend.dto.facility.request.FacilityRequests.*;
import com.mediassistrover.backend.dto.facility.response.FacilityResponses.*;
import com.mediassistrover.backend.dto.common.DeletionContracts.*;
import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.HospitalInformationStatus;
import com.mediassistrover.backend.enums.MapStatus;
import com.mediassistrover.backend.exception.BusinessRuleException;
import com.mediassistrover.backend.exception.ConflictException;
import com.mediassistrover.backend.exception.ResourceNotFoundException;
import com.mediassistrover.backend.mapper.FacilityMapper;
import com.mediassistrover.backend.repository.*;
import com.mediassistrover.backend.service.AuditService;
import com.mediassistrover.backend.service.FacilityService;
import com.mediassistrover.backend.service.FacilityDeletionResource;
import com.mediassistrover.backend.service.RequestMetadata;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FacilityServiceImpl implements FacilityService {
    private final HospitalInformationRepository hospitalRepository;
    private final FloorRepository floorRepository;
    private final FacilityMapRepository mapRepository;
    private final ZoneRepository zoneRepository;
    private final RoomRepository roomRepository;
    private final EndpointRepository endpointRepository;
    private final EndpointGroupRepository groupRepository;
    private final EndpointGroupMemberRepository memberRepository;
    private final AccountRepository accountRepository;
    private final FacilityMapper mapper;
    private final com.mediassistrover.backend.storage.S3ObjectStorageService s3;
    private final AuditService auditService;
    private final Clock clock;
    private final HospitalHardDeleteService hospitalDelete;
    private final FloorHardDeleteService floorDelete;
    private final FacilityCatalogHardDeleteService catalogDelete;

    @Override @Transactional
    public HospitalInformationResponse createHospitalInformation(HospitalInformationRequest r, UUID actorId, RequestMetadata md) {
        validateEffectiveWindow(r.effectiveFrom(), r.effectiveUntil());
        Account actor = account(actorId);
        HospitalInformation entity = mapper.toEntity(r);
        entity.setUpdatedBy(actor);
        entity = hospitalRepository.save(entity);
        audit(actor, "HOSPITAL_INFORMATION_CREATED", "HospitalInformation", entity.getHospitalInformationId(), md);
        return mapper.toResponse(entity);
    }

    @Override @Transactional
    public HospitalInformationResponse updateHospitalInformation(UUID id, HospitalInformationRequest r, UUID actorId, RequestMetadata md) {
        validateEffectiveWindow(r.effectiveFrom(), r.effectiveUntil());
        HospitalInformation entity = hospital(id);
        if (entity.getStatus() == HospitalInformationStatus.RETIRED) throw new BusinessRuleException("Retired hospital information cannot be updated");
        Account actor = account(actorId);
        mapper.update(r, entity); entity.setUpdatedBy(actor);
        audit(actor, "HOSPITAL_INFORMATION_UPDATED", "HospitalInformation", id, md);
        return mapper.toResponse(entity);
    }

    @Override @Transactional
    public HospitalInformationResponse publishHospitalInformation(UUID id, UUID actorId, RequestMetadata md) {
        HospitalInformation entity = hospital(id);
        if (entity.getStatus() != HospitalInformationStatus.DRAFT) throw new BusinessRuleException("Only DRAFT hospital information can be published");
        entity.setStatus(HospitalInformationStatus.PUBLISHED);
        Account actor = account(actorId); entity.setUpdatedBy(actor);
        audit(actor, "HOSPITAL_INFORMATION_PUBLISHED", "HospitalInformation", id, md);
        return mapper.toResponse(entity);
    }

    @Override @Transactional
    public HospitalInformationResponse retireHospitalInformation(UUID id,String reason,UUID actorId,RequestMetadata md) {
        HospitalInformation entity = hospital(id); HospitalInformationStatus before=entity.getStatus();entity.setStatus(HospitalInformationStatus.RETIRED);
        Account actor = account(actorId); entity.setUpdatedBy(actor);
        auditService.record(actor,"HOSPITAL_INFORMATION_RETIRED","HospitalInformation",id,Map.of("status",before.name()),Map.of("status","RETIRED","reason",reason),md);
        return mapper.toResponse(entity);
    }
    @Override @Transactional public HospitalInformationResponse restoreHospitalInformation(UUID id,String reason,UUID actorId,RequestMetadata md){HospitalInformation entity=hospital(id);if(entity.getStatus()!=HospitalInformationStatus.RETIRED)throw new BusinessRuleException("Only RETIRED hospital information can be restored");entity.setStatus(HospitalInformationStatus.DRAFT);Account actor=account(actorId);entity.setUpdatedBy(actor);auditService.record(actor,"HOSPITAL_INFORMATION_RESTORED","HospitalInformation",id,Map.of("status","RETIRED"),Map.of("status","DRAFT","reason",reason),md);return mapper.toResponse(entity);}
    @Override public Page<HospitalInformationResponse> listHospitalInformation(boolean includeDeleted,Pageable p) { return(includeDeleted?hospitalRepository.findAll(p):hospitalRepository.findByStatusNot(HospitalInformationStatus.RETIRED,p)).map(mapper::toResponse); }
    @Override public HospitalInformationResponse getHospitalInformation(UUID id,boolean includeDeleted){HospitalInformation entity=hospital(id);if(!includeDeleted&&entity.getStatus()==HospitalInformationStatus.RETIRED)throw notFound("HospitalInformation",id);return mapper.toResponse(entity);}
    @Override public DeletionImpactResponse hospitalDeletionImpact(UUID id){hospital(id);return hospitalDelete.impact(id);}
    @Override @Transactional public HardDeleteResponse forceDeleteHospital(UUID id,String reason,UUID actorId,RequestMetadata md){Account actor=account(actorId);HardDeleteResponse result=hospitalDelete.delete(id);auditService.record(actor,"HOSPITAL_INFORMATION_FORCE_DELETED","HospitalInformation",id,null,Map.of("reason",reason,"deleted",result.deleted()),md);return result;}

    @Override @Transactional
    public FloorResponse createFloor(FloorRequest r, UUID actorId, RequestMetadata md) {
        HospitalInformation hospital = hospitalProfile(r.hospitalInformationId());
        if (floorRepository.existsByHospitalInformationHospitalInformationIdAndFloorCodeIgnoreCase(r.hospitalInformationId(), r.floorCode())) throw new ConflictException("Floor code already exists in this hospital");
        Floor entity = mapper.toEntity(r); entity.setHospitalInformation(hospital); entity = floorRepository.save(entity);
        audit(account(actorId), "FLOOR_CREATED", "Floor", entity.getFloorId(), md); return mapper.toResponse(entity);
    }
    @Override @Transactional
    public FloorResponse updateFloor(UUID id, FloorRequest r, UUID actorId, RequestMetadata md) {
        Floor entity = floor(id); HospitalInformation hospital = hospitalProfile(r.hospitalInformationId());
        if ((!entity.getHospitalInformation().getHospitalInformationId().equals(r.hospitalInformationId()) || !entity.getFloorCode().equalsIgnoreCase(r.floorCode()))
                && floorRepository.existsByHospitalInformationHospitalInformationIdAndFloorCodeIgnoreCase(r.hospitalInformationId(), r.floorCode())) throw new ConflictException("Floor code already exists in this hospital");
        mapper.update(r, entity); entity.setHospitalInformation(hospital);
        audit(account(actorId), "FLOOR_UPDATED", "Floor", id, md); return mapper.toResponse(entity);
    }
    @Override @Transactional public FloorResponse setFloorActive(UUID id,boolean active,String reason,UUID actorId,RequestMetadata md) {
        Floor e=floor(id);boolean before=e.isActive();e.setActive(active);Account actor=account(actorId);auditService.record(actor,active?"FLOOR_ACTIVATED":"FLOOR_DEACTIVATED","Floor",id,Map.of("active",before),Map.of("active",active,"reason",reason),md);return mapper.toResponse(e);
    }
    @Override public Page<FloorResponse> listFloors(boolean includeDeleted,Pageable p) { return(includeDeleted?floorRepository.findAll(p):floorRepository.findByActiveTrue(p)).map(mapper::toResponse); }
    @Override public FloorResponse getFloor(UUID id,boolean includeDeleted){Floor e=floor(id);if(!includeDeleted&&!e.isActive())throw notFound("Floor",id);return mapper.toResponse(e);}
    @Override public DeletionImpactResponse floorDeletionImpact(UUID id){floor(id);return floorDelete.impact(id);}
    @Override @Transactional public HardDeleteResponse forceDeleteFloor(UUID id,String reason,UUID actorId,RequestMetadata md){Account actor=account(actorId);HardDeleteResponse result=floorDelete.delete(id);auditService.record(actor,"FLOOR_FORCE_DELETED","Floor",id,null,Map.of("reason",reason,"deleted",result.deleted()),md);return result;}

    @Override @Transactional
    public MapResponse createMap(MapRequest r, UUID actorId, RequestMetadata md) {
        validateMapUrl(r.mapFileUri());
        if (mapRepository.existsByMapCodeIgnoreCaseAndVersionNo(r.mapCode(), r.versionNo())) throw new ConflictException("Map code and version already exist");
        FacilityMap entity=mapper.toEntity(r);entity.setFloor(floor(r.floorId()));entity=mapRepository.save(entity);
        audit(account(actorId),"MAP_CREATED","Map",entity.getMapId(),md);return mapper.toResponse(entity);
    }
    @Override @Transactional
    public MapResponse updateMap(UUID id, MapRequest r, UUID actorId, RequestMetadata md) {
        validateMapUrl(r.mapFileUri());
        FacilityMap entity=facilityMap(id);
        if(entity.getStatus()!=MapStatus.DRAFT) throw new BusinessRuleException("Only DRAFT map metadata can be updated");
        if((!entity.getMapCode().equalsIgnoreCase(r.mapCode())||entity.getVersionNo()!=r.versionNo())&&mapRepository.existsByMapCodeIgnoreCaseAndVersionNo(r.mapCode(),r.versionNo())) throw new ConflictException("Map code and version already exist");
        mapper.update(r,entity);entity.setFloor(floor(r.floorId()));audit(account(actorId),"MAP_UPDATED","Map",id,md);return mapper.toResponse(entity);
    }
    @Override @Transactional
    public MapResponse activateMap(UUID id, UUID actorId, RequestMetadata md) {
        FacilityMap target=facilityMap(id);
        if(target.getStatus()==MapStatus.ARCHIVED) throw new BusinessRuleException("Archived map cannot be activated");
        floorRepository.findByIdForUpdate(target.getFloor().getFloorId()).orElseThrow(() -> notFound("Floor", target.getFloor().getFloorId()));
        mapRepository.findAllByFloorAndStatusForUpdate(target.getFloor().getFloorId(),MapStatus.ACTIVE).stream()
                .filter(current -> !current.getMapId().equals(id)).forEach(current -> current.setStatus(MapStatus.INACTIVE));
        target.setStatus(MapStatus.ACTIVE);target.setCommissionedAt(Instant.now(clock));
        audit(account(actorId),"MAP_ACTIVATED","Map",id,md);return mapper.toResponse(target);
    }
    @Override @Transactional public MapResponse archiveMap(UUID id,String reason,UUID actorId,RequestMetadata md) {FacilityMap e=facilityMap(id);MapStatus before=e.getStatus();e.setStatus(MapStatus.ARCHIVED);Account actor=account(actorId);auditService.record(actor,"MAP_ARCHIVED","Map",id,Map.of("status",before.name()),Map.of("status","ARCHIVED","reason",reason),md);return mapper.toResponse(e);}
    @Override @Transactional public MapResponse restoreMap(UUID id,String reason,UUID actorId,RequestMetadata md) {FacilityMap e=facilityMap(id);if(e.getStatus()!=MapStatus.ARCHIVED)throw new BusinessRuleException("Only ARCHIVED map can be restored");e.setStatus(MapStatus.INACTIVE);Account actor=account(actorId);auditService.record(actor,"MAP_RESTORED","Map",id,Map.of("status","ARCHIVED"),Map.of("status","INACTIVE","reason",reason),md);return mapper.toResponse(e);}
    @Override public MapArtifactResponse getMapArtifact(UUID id) { FacilityMap e=facilityMap(id);return new MapArtifactResponse(e.getMapId(),e.getMapCode(),e.getVersionNo(),e.getMapFileUri(),e.getAssetHash(),e.getFrameId(),e.getStatus()); }
    @Override public Page<MapResponse> listMaps(boolean includeDeleted,Pageable p) { return(includeDeleted?mapRepository.findAll(p):mapRepository.findByStatusNot(MapStatus.ARCHIVED,p)).map(mapper::toResponse); }
    @Override public MapResponse getMap(UUID id,boolean includeDeleted){FacilityMap e=facilityMap(id);if(!includeDeleted&&e.getStatus()==MapStatus.ARCHIVED)throw notFound("Map",id);return mapper.toResponse(e);}
    @Override public DeletionImpactResponse mapDeletionImpact(UUID id){return catalogDelete.impact(FacilityDeletionResource.MAP,id);}
    @Override @Transactional public HardDeleteResponse forceDeleteMap(UUID id,String reason,UUID actorId,RequestMetadata md){return forceCatalog(FacilityDeletionResource.MAP,id,reason,actorId,md);}

    @Override @Transactional public ZoneResponse createZone(ZoneRequest r,UUID a,RequestMetadata md){
        com.mediassistrover.backend.service.ZonePolicy.validate(r.zoneType(),r.polygonJson(),r.speedLimitMps());
        if(zoneRepository.existsByMapMapIdAndZoneCodeIgnoreCase(r.mapId(),r.zoneCode()))throw new ConflictException("Zone code already exists in this map");
        Zone e=mapper.toEntity(r);e.setMap(facilityMap(r.mapId()));e=zoneRepository.save(e);audit(account(a),"ZONE_CREATED","Zone",e.getZoneId(),md);return mapper.toResponse(e);}
    @Override @Transactional public ZoneResponse updateZone(UUID id,ZoneRequest r,UUID a,RequestMetadata md){Zone e=zone(id);
        com.mediassistrover.backend.service.ZonePolicy.validate(r.zoneType(),r.polygonJson(),r.speedLimitMps());
        if((!e.getMap().getMapId().equals(r.mapId())||!e.getZoneCode().equalsIgnoreCase(r.zoneCode()))&&zoneRepository.existsByMapMapIdAndZoneCodeIgnoreCase(r.mapId(),r.zoneCode()))throw new ConflictException("Zone code already exists in this map");
        mapper.update(r,e);e.setMap(facilityMap(r.mapId()));audit(account(a),"ZONE_UPDATED","Zone",id,md);return mapper.toResponse(e);}
    @Override @Transactional public ZoneResponse setZoneActive(UUID id,boolean active,String reason,UUID a,RequestMetadata md){Zone e=zone(id);if(active)com.mediassistrover.backend.service.ZonePolicy.validate(e.getZoneType(),e.getPolygonJson(),e.getSpeedLimitMps());boolean before=e.isActive();e.setActive(active);auditService.record(account(a),active?"ZONE_ACTIVATED":"ZONE_DEACTIVATED","Zone",id,Map.of("active",before),Map.of("active",active,"reason",reason),md);return mapper.toResponse(e);}
    @Override public Page<ZoneResponse> listZones(boolean includeDeleted,Pageable p){return(includeDeleted?zoneRepository.findAll(p):zoneRepository.findByActiveTrue(p)).map(mapper::toResponse);}
    @Override public ZoneResponse getZone(UUID id,boolean includeDeleted){Zone e=zone(id);if(!includeDeleted&&!e.isActive())throw notFound("Zone",id);return mapper.toResponse(e);}
    @Override public DeletionImpactResponse zoneDeletionImpact(UUID id){return catalogDelete.impact(FacilityDeletionResource.ZONE,id);}
    @Override @Transactional public HardDeleteResponse forceDeleteZone(UUID id,String reason,UUID actorId,RequestMetadata md){return forceCatalog(FacilityDeletionResource.ZONE,id,reason,actorId,md);}

    @Override @Transactional public RoomResponse createRoom(RoomRequest r,UUID a,RequestMetadata md){
        if(roomRepository.existsByRoomCodeIgnoreCase(r.roomCode()))throw new ConflictException("Room code already exists");
        Room e=mapper.toEntity(r);bindRoom(e,r);e=roomRepository.save(e);audit(account(a),"ROOM_CREATED","Room",e.getRoomId(),md);return mapper.toResponse(e);}
    @Override @Transactional public RoomResponse updateRoom(UUID id,RoomRequest r,UUID a,RequestMetadata md){Room e=room(id);
        if(!e.getRoomCode().equalsIgnoreCase(r.roomCode())&&roomRepository.existsByRoomCodeIgnoreCase(r.roomCode()))throw new ConflictException("Room code already exists");
        mapper.update(r,e);bindRoom(e,r);audit(account(a),"ROOM_UPDATED","Room",id,md);return mapper.toResponse(e);}
    @Override @Transactional public RoomResponse setRoomActive(UUID id,boolean active,String reason,UUID a,RequestMetadata md){Room e=room(id);boolean before=e.isActive();e.setActive(active);auditService.record(account(a),active?"ROOM_ACTIVATED":"ROOM_DEACTIVATED","Room",id,Map.of("active",before),Map.of("active",active,"reason",reason),md);return mapper.toResponse(e);}
    @Override public Page<RoomResponse> listRooms(boolean includeDeleted,Pageable p){return(includeDeleted?roomRepository.findAll(p):roomRepository.findByActiveTrue(p)).map(mapper::toResponse);}
    @Override public RoomResponse getRoom(UUID id,boolean includeDeleted){Room e=room(id);if(!includeDeleted&&!e.isActive())throw notFound("Room",id);return mapper.toResponse(e);}
    @Override public DeletionImpactResponse roomDeletionImpact(UUID id){return catalogDelete.impact(FacilityDeletionResource.ROOM,id);}
    @Override @Transactional public HardDeleteResponse forceDeleteRoom(UUID id,String reason,UUID actorId,RequestMetadata md){return forceCatalog(FacilityDeletionResource.ROOM,id,reason,actorId,md);}

    @Override @Transactional public EndpointResponse createEndpoint(EndpointRequest r,UUID a,RequestMetadata md){
        if(endpointRepository.existsByMapMapIdAndEndpointCodeIgnoreCase(r.mapId(),r.endpointCode()))throw new ConflictException("Endpoint code already exists in this map");
        Endpoint e=mapper.toEntity(r);bindEndpoint(e,r);e=endpointRepository.save(e);audit(account(a),"ENDPOINT_CREATED","Endpoint",e.getEndpointId(),md);return mapper.toResponse(e);}
    @Override @Transactional public EndpointResponse updateEndpoint(UUID id,EndpointRequest r,UUID a,RequestMetadata md){Endpoint e=endpoint(id);
        if((!e.getMap().getMapId().equals(r.mapId())||!e.getEndpointCode().equalsIgnoreCase(r.endpointCode()))&&endpointRepository.existsByMapMapIdAndEndpointCodeIgnoreCase(r.mapId(),r.endpointCode()))throw new ConflictException("Endpoint code already exists in this map");
        mapper.update(r,e);bindEndpoint(e,r);audit(account(a),"ENDPOINT_UPDATED","Endpoint",id,md);return mapper.toResponse(e);}
    @Override @Transactional public EndpointResponse setEndpointActive(UUID id,boolean active,String reason,UUID a,RequestMetadata md){Endpoint e=endpoint(id);boolean before=e.isActive();e.setActive(active);auditService.record(account(a),active?"ENDPOINT_ACTIVATED":"ENDPOINT_DEACTIVATED","Endpoint",id,Map.of("active",before),Map.of("active",active,"reason",reason),md);return mapper.toResponse(e);}
    @Override public Page<EndpointResponse> listEndpoints(boolean includeDeleted,Pageable p){return(includeDeleted?endpointRepository.findAll(p):endpointRepository.findByActiveTrue(p)).map(mapper::toResponse);}
    @Override public EndpointResponse getEndpoint(UUID id,boolean includeDeleted){Endpoint e=endpoint(id);if(!includeDeleted&&!e.isActive())throw notFound("Endpoint",id);return mapper.toResponse(e);}
    @Override public DeletionImpactResponse endpointDeletionImpact(UUID id){return catalogDelete.impact(FacilityDeletionResource.ENDPOINT,id);}
    @Override @Transactional public HardDeleteResponse forceDeleteEndpoint(UUID id,String reason,UUID actorId,RequestMetadata md){return forceCatalog(FacilityDeletionResource.ENDPOINT,id,reason,actorId,md);}

    @Override @Transactional public EndpointGroupResponse createEndpointGroup(EndpointGroupRequest r,UUID a,RequestMetadata md){
        if(groupRepository.existsByMapMapIdAndGroupCodeIgnoreCase(r.mapId(),r.groupCode()))throw new ConflictException("Endpoint group code already exists in this map");
        EndpointGroup e=mapper.toEntity(r);e.setMap(facilityMap(r.mapId()));e=groupRepository.save(e);audit(account(a),"ENDPOINT_GROUP_CREATED","EndpointGroup",e.getEndpointGroupId(),md);return mapper.toResponse(e);}
    @Override @Transactional public EndpointGroupResponse updateEndpointGroup(UUID id,EndpointGroupRequest r,UUID a,RequestMetadata md){EndpointGroup e=group(id);
        if((!e.getMap().getMapId().equals(r.mapId())||!e.getGroupCode().equalsIgnoreCase(r.groupCode()))&&groupRepository.existsByMapMapIdAndGroupCodeIgnoreCase(r.mapId(),r.groupCode()))throw new ConflictException("Endpoint group code already exists in this map");
        mapper.update(r,e);e.setMap(facilityMap(r.mapId()));audit(account(a),"ENDPOINT_GROUP_UPDATED","EndpointGroup",id,md);return mapper.toResponse(e);}
    @Override @Transactional public EndpointGroupResponse setEndpointGroupActive(UUID id,boolean active,String reason,UUID a,RequestMetadata md){EndpointGroup e=group(id);boolean before=e.isActive();e.setActive(active);auditService.record(account(a),active?"ENDPOINT_GROUP_ACTIVATED":"ENDPOINT_GROUP_DEACTIVATED","EndpointGroup",id,Map.of("active",before),Map.of("active",active,"reason",reason),md);return mapper.toResponse(e);}
    @Override public Page<EndpointGroupResponse> listEndpointGroups(boolean includeDeleted,Pageable p){return(includeDeleted?groupRepository.findAll(p):groupRepository.findByActiveTrue(p)).map(mapper::toResponse);}
    @Override public EndpointGroupResponse getEndpointGroup(UUID id,boolean includeDeleted){EndpointGroup e=group(id);if(!includeDeleted&&!e.isActive())throw notFound("EndpointGroup",id);return mapper.toResponse(e);}
    @Override public DeletionImpactResponse endpointGroupDeletionImpact(UUID id){return catalogDelete.impact(FacilityDeletionResource.ENDPOINT_GROUP,id);}
    @Override @Transactional public HardDeleteResponse forceDeleteEndpointGroup(UUID id,String reason,UUID actorId,RequestMetadata md){return forceCatalog(FacilityDeletionResource.ENDPOINT_GROUP,id,reason,actorId,md);}

    @Override @Transactional public EndpointGroupMemberResponse createEndpointGroupMember(EndpointGroupMemberRequest r,UUID a,RequestMetadata md){
        EndpointGroupMemberId id=new EndpointGroupMemberId(r.endpointGroupId(),r.endpointId());if(memberRepository.existsById(id))throw new ConflictException("Endpoint is already in this group");
        if(memberRepository.existsByEndpointGroupEndpointGroupIdAndSequenceNo(r.endpointGroupId(),r.sequenceNo()))throw new ConflictException("Sequence number already exists in this group");
        EndpointGroup g=group(r.endpointGroupId());Endpoint e=endpoint(r.endpointId());ensureSameMap(g,e);
        EndpointGroupMember m=EndpointGroupMember.builder().id(id).endpointGroup(g).endpoint(e).map(g.getMap()).sequenceNo(r.sequenceNo()).active(r.active()).build();m=memberRepository.save(m);
        audit(account(a),"ENDPOINT_GROUP_MEMBER_CREATED","EndpointGroup",g.getEndpointGroupId(),md);return mapper.toResponse(m);}
    @Override @Transactional public EndpointGroupMemberResponse updateEndpointGroupMember(UUID groupId,UUID endpointId,EndpointGroupMemberRequest r,UUID a,RequestMetadata md){
        if(!groupId.equals(r.endpointGroupId())||!endpointId.equals(r.endpointId()))throw new BusinessRuleException("Path IDs must match request IDs");
        EndpointGroupMember m=memberRepository.findById(new EndpointGroupMemberId(groupId,endpointId)).orElseThrow(()->new ResourceNotFoundException("Endpoint group member not found"));
        if(m.getSequenceNo()!=r.sequenceNo()&&memberRepository.existsByEndpointGroupEndpointGroupIdAndSequenceNo(groupId,r.sequenceNo()))throw new ConflictException("Sequence number already exists in this group");
        m.setSequenceNo(r.sequenceNo());m.setActive(r.active());audit(account(a),"ENDPOINT_GROUP_MEMBER_UPDATED","EndpointGroup",groupId,md);return mapper.toResponse(m);}
    @Override @Transactional public void removeEndpointGroupMember(UUID groupId,UUID endpointId,UUID a,RequestMetadata md){EndpointGroupMember m=memberRepository.findById(new EndpointGroupMemberId(groupId,endpointId)).orElseThrow(()->new ResourceNotFoundException("Endpoint group member not found"));m.setActive(false);audit(account(a),"ENDPOINT_GROUP_MEMBER_DEACTIVATED","EndpointGroup",groupId,md);}
    @Override public Page<EndpointGroupMemberResponse> listEndpointGroupMembers(Pageable p){return memberRepository.findAll(p).map(mapper::toResponse);}

    @Override public Page<HospitalInformationResponse> listPublicHospitalInformation(Pageable p){return hospitalRepository.findPublicEffective(HospitalInformationStatus.PUBLISHED,Instant.now(clock),p).map(mapper::toResponse);}
    @Override public Page<FloorResponse> listPublicFloors(Pageable p){return floorRepository.findByActiveTrue(p).map(mapper::toResponse);}
    @Override public Page<ZoneResponse> listPublicZones(Pageable p){return zoneRepository.findByActiveTrueAndPublicVisibleTrue(p).map(mapper::toResponse);}
    @Override public Page<RoomResponse> listPublicRooms(Pageable p){return roomRepository.findByActiveTrueAndPublicVisibleTrue(p).map(mapper::toResponse);}
    @Override public Page<EndpointResponse> listPublicEndpoints(Pageable p){return endpointRepository.findByActiveTrueAndPublicVisibleTrue(p).map(mapper::toResponse);}

    private void bindRoom(Room e,RoomRequest r){Floor f=floor(r.floorId());e.setFloor(f);if(r.defaultEndpointGroupId()==null){e.setDefaultEndpointGroup(null);return;}EndpointGroup g=group(r.defaultEndpointGroupId());if(!g.getMap().getFloor().getFloorId().equals(f.getFloorId()))throw new BusinessRuleException("Default endpoint group must belong to the room floor");e.setDefaultEndpointGroup(g);}
    private void bindEndpoint(Endpoint e,EndpointRequest r){FacilityMap map=facilityMap(r.mapId());e.setMap(map);Zone z=r.zoneId()==null?null:zone(r.zoneId());Room room=r.roomId()==null?null:room(r.roomId());if(z!=null&&!z.getMap().getMapId().equals(map.getMapId()))throw new BusinessRuleException("Zone and endpoint must use the same map");if(room!=null&&!room.getFloor().getFloorId().equals(map.getFloor().getFloorId()))throw new BusinessRuleException("Room and endpoint map must belong to the same floor");e.setZone(z);e.setRoom(room);}
    private void ensureSameMap(EndpointGroup g,Endpoint e){if(!g.getMap().getMapId().equals(e.getMap().getMapId()))throw new BusinessRuleException("Endpoint group and endpoint must use the same map");}
    private void validateEffectiveWindow(Instant from,Instant until){if(from!=null&&until!=null&&!until.isAfter(from))throw new BusinessRuleException("effectiveUntil must be after effectiveFrom");}
    private HospitalInformation hospitalProfile(UUID id){HospitalInformation h=hospital(id);if(!"HOSPITAL_PROFILE".equalsIgnoreCase(h.getInfoCategory()))throw new BusinessRuleException("Floor must reference HospitalInformation category HOSPITAL_PROFILE");return h;}
    private Account account(UUID id){return accountRepository.findById(id).orElseThrow(()->notFound("Account",id));}
    private HospitalInformation hospital(UUID id){return hospitalRepository.findById(id).orElseThrow(()->notFound("HospitalInformation",id));}
    private Floor floor(UUID id){return floorRepository.findById(id).orElseThrow(()->notFound("Floor",id));}
    private FacilityMap facilityMap(UUID id){return mapRepository.findById(id).orElseThrow(()->notFound("Map",id));}
    private void validateMapUrl(String value){try{String key=s3.keyFromPublicUrl(value,java.util.Set.of("maps"));if(!key.endsWith(".zip")&&!key.endsWith(".tar.gz"))throw new IllegalArgumentException();}catch(IllegalArgumentException e){throw new BusinessRuleException("mapFileUri must be a fixed S3 URL for a .zip or .tar.gz map archive");}}
    private Zone zone(UUID id){return zoneRepository.findById(id).orElseThrow(()->notFound("Zone",id));}
    private Room room(UUID id){return roomRepository.findById(id).orElseThrow(()->notFound("Room",id));}
    private Endpoint endpoint(UUID id){return endpointRepository.findById(id).orElseThrow(()->notFound("Endpoint",id));}
    private EndpointGroup group(UUID id){return groupRepository.findById(id).orElseThrow(()->notFound("EndpointGroup",id));}
    private ResourceNotFoundException notFound(String type,UUID id){return new ResourceNotFoundException(type+" not found: "+id);}
    private HardDeleteResponse forceCatalog(FacilityDeletionResource resource,UUID id,String reason,UUID actorId,RequestMetadata md){Account actor=account(actorId);HardDeleteResponse result=catalogDelete.delete(resource,id);auditService.record(actor,resource.name()+"_FORCE_DELETED",resource.name(),id,null,Map.of("reason",reason,"deleted",result.deleted()),md);return result;}
    private void audit(Account actor,String action,String type,UUID id,RequestMetadata md){auditService.record(actor,action,type,id,null,Map.of("action",action),md);}
}
