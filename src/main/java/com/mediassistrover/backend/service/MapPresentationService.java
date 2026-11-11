package com.mediassistrover.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediassistrover.backend.dto.facility.MapPresentationContracts.*;
import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.exception.*;
import com.mediassistrover.backend.mapping.SavedMapImageService;
import com.mediassistrover.backend.repository.*;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class MapPresentationService {
    private final MapPresentationRepository presentations;
    private final FacilityMapRepository maps;
    private final EndpointRepository endpoints;
    private final RoomRepository rooms;
    private final SavedMapImageService images;
    private final ObjectMapper json;
    private final EntityManager entityManager;
    private final Clock clock;
    private final SosMonitoringRoomService sosMonitoringRooms;

    @Transactional(readOnly=true)
    public Editor editor(UUID id) {
        FacilityMap map=map(id);
        Source source=source(map);
        MapPresentation row=presentations.findById(id).orElse(null);
        return editor(source,row);
    }

    @Transactional
    public Editor save(UUID id, Save request) {
        FacilityMap map=lock(id);
        Source source=source(map);
        if(!source.assetHash().equals(request.sourceAssetHash()))
            throw new ConflictException("Bản đồ gốc đã thay đổi. Tải lại trước khi chỉnh sửa.");
        MapPresentation row=presentations.findById(id).orElse(null);
        checkRevision(row,request.expectedRevision());
        if(row!=null && !row.getSourceAssetHash().equals(source.assetHash()))
            throw new ConflictException("Bản vẽ thuộc map cũ. Tạo bản vẽ cho phiên bản map mới.");
        validate(request.document(),source);
        if(row==null) { row=new MapPresentation(); row.setMapId(id); }
        row.setRevision(request.expectedRevision()+1);
        row.setSourceAssetHash(source.assetHash());
        row.setSourceJson(json.valueToTree(source));
        row.setDraftJson(json.valueToTree(request.document()));
        row.setUpdatedAt(Instant.now(clock));
        presentations.save(row);
        return editor(source,row);
    }

    @Transactional
    public Editor publish(UUID id, Change request) {
        FacilityMap map=lock(id);
        MapPresentation row=requireRow(id);
        checkRevision(row,request.expectedRevision());
        Source source=source(map);
        requireSource(row,map);
        Document draft=document(row.getDraftJson());
        if(draft.features().isEmpty() && draft.rotationDegrees()==0. && row.getPublishedJson()==null)
            throw new BusinessRuleException("Thêm ít nhất một đối tượng hoặc căn góc bản đồ trước khi xuất bản.");
        validate(draft,source);
        Document synchronizedDraft=syncRooms(draft,map);
        row.setDraftJson(json.valueToTree(synchronizedDraft));
        row.setPublishedJson(row.getDraftJson().deepCopy());
        row.setRevision(row.getRevision()+1);
        row.setPublishedRevision(row.getRevision());
        row.setPublishedAt(Instant.now(clock));
        row.setUpdatedAt(Instant.now(clock));
        return editor(source,row);
    }

    @Transactional
    public Editor unpublish(UUID id, Change request) {
        FacilityMap map=lock(id);
        MapPresentation row=requireRow(id);
        checkRevision(row,request.expectedRevision());
        row.setPublishedJson(null); row.setPublishedRevision(null); row.setPublishedAt(null);
        row.setRevision(row.getRevision()+1); row.setUpdatedAt(Instant.now(clock));
        return editor(source(map),row);
    }

    @Transactional(readOnly=true)
    public Published published(UUID id) {
        FacilityMap map=map(id);
        MapPresentation row=requireRow(id);
        requireSource(row,map);
        if(row.getPublishedJson()==null) throw new ResourceNotFoundException("Bản đồ chưa có giao diện đã xuất bản.");
        return new Published(json.convertValue(row.getSourceJson(),Source.class),row.getPublishedRevision(),
            document(row.getPublishedJson()),row.getPublishedAt());
    }

    void validate(Document document,Source source) {
        if(!Double.isFinite(document.rotationDegrees()) || document.rotationDegrees() < -180 || document.rotationDegrees() > 180)
            throw new BusinessRuleException("Góc xoay giao diện phải từ -180 đến 180 độ.");
        long sosRoomCount=document.features().stream().filter(feature->feature.type()==FeatureType.ROOM
                && sosMonitoringRooms.isSosType(feature.roomType())).count();
        if(sosRoomCount>1) throw new BusinessRuleException("Mỗi bản đồ chỉ được khai báo một phòng SOS.");
        Set<String> ids=new HashSet<>();
        Set<UUID> linked=new HashSet<>();
        for(Feature feature:document.features()) {
            if(!ids.add(feature.id())) throw new BusinessRuleException("Trùng mã đối tượng trong bản vẽ.");
            int count=feature.points().size();
            switch(feature.type()) {
                case ROOM, CORRIDOR -> {
                    if(count<3) throw new BusinessRuleException("Phòng/hành lang cần ít nhất 3 đỉnh.");
                    double area=0;
                    for(int i=0;i<count;i++) { Point a=feature.points().get(i),b=feature.points().get((i+1)%count); area+=a.x()*b.y()-b.x()*a.y(); }
                    if(Math.abs(area)<0.000001) throw new BusinessRuleException("Vùng vẽ phải có diện tích.");
                }
                case WALL, DOOR -> { if(count<2) throw new BusinessRuleException("Tường/cửa cần ít nhất 2 điểm."); }
                case LABEL, LANDMARK -> { if(count!=1) throw new BusinessRuleException("Nhãn/ký hiệu cần đúng 1 điểm."); }
            }
            for(Point point:feature.points()) {
                if(!Double.isFinite(point.x()) || !Double.isFinite(point.y()) || point.x()<0 || point.y()<0
                    || point.x()>source.widthM() || point.y()>source.heightM())
                    throw new BusinessRuleException("Đối tượng nằm ngoài phạm vi map gốc.");
            }
            if(feature.endpointId()!=null) linked.add(feature.endpointId());
        }
        // Batch lookup, and reject links into other maps. No endpoint is created by drawing.
        var found=endpoints.findAllById(linked);
        if(found.size()!=linked.size() || found.stream().anyMatch(e->!e.getMap().getMapId().equals(source.mapId()) || !e.isActive() || !e.isAccessible()))
            throw new BusinessRuleException("Điểm liên kết phải đang hoạt động, có thể tiếp cận và thuộc map này.");
    }
    private Document syncRooms(Document document,FacilityMap map) {
        List<Feature> result=new ArrayList<>(document.features().size());
        Set<UUID> endpointIds=new HashSet<>();
        Set<UUID> roomIds=new HashSet<>();
        Set<String> roomCodes=new HashSet<>();
        for(Feature feature:document.features()) {
            if(feature.type()!=FeatureType.ROOM || feature.endpointId()==null) { result.add(feature); continue; }
            if(!endpointIds.add(feature.endpointId()))
                throw new BusinessRuleException("Mỗi điểm đích chỉ được gắn với một phòng trên bản đồ.");
            Endpoint endpoint=endpoints.findById(feature.endpointId()).orElseThrow(()->new ResourceNotFoundException("Điểm đích không tồn tại: "+feature.endpointId()));
            if(!endpoint.getMap().getMapId().equals(map.getMapId()) || !endpoint.isActive() || !endpoint.isAccessible())
                throw new BusinessRuleException("Điểm liên kết phải đang hoạt động, có thể tiếp cận và thuộc map này.");
            String endpointName=clean(feature.endpointName());
            if(endpointName==null) throw new BusinessRuleException("Phòng gắn điểm đích phải có tên endpoint.");
            if(endpointName.length()>160) throw new BusinessRuleException("Tên endpoint không được vượt quá 160 ký tự.");
            String code=roomCode(feature,map), normalized=code.toLowerCase(Locale.ROOT);
            if(!roomCodes.add(normalized)) throw new BusinessRuleException("Mã phòng bị trùng trong bản đồ: "+code);
            String name=text(feature.roomName(),feature.label());
            if(name==null) throw new BusinessRuleException("Phòng gắn điểm đích phải có tên phòng.");
            String type=text(feature.roomType(),"GENERAL");
            Room room=resolveRoom(feature,code,map);
            boolean created=room.getRoomId()==null;
            String previousName=created?name:room.getRoomName();
            String previousType=created?"GENERAL":room.getRoomType();
            if(room.getRoomId()!=null && !roomIds.add(room.getRoomId()))
                throw new BusinessRuleException("Một phòng trong database chỉ được gắn với một hình phòng trên bản đồ.");
            Optional<Room> codeOwner=rooms.findByRoomCodeIgnoreCase(code);
            if(codeOwner.isPresent() && !codeOwner.get().getRoomId().equals(room.getRoomId()))
                throw new ConflictException("Mã phòng đã được sử dụng: "+code);
            if(endpoint.getRoom()!=null && !endpoint.getRoom().getRoomId().equals(room.getRoomId()))
                throw new ConflictException("Điểm đích đã liên kết với phòng khác: "+endpoint.getEndpointName());
            boolean visible=feature.roomPublicVisible()==null || feature.roomPublicVisible();
            room.setFloor(map.getFloor()); room.setRoomCode(code); room.setRoomName(name); room.setRoomType(type);
            room.setDepartmentCode(clean(feature.departmentCode())); room.setDescription(clean(feature.roomDescription()));
            room.setActive(true); room.setPublicVisible(visible); room=rooms.save(room);
            endpoint.setEndpointName(endpointName);
            endpoint.setRoom(room);
            if(visible) endpoint.setPublicVisible(true);
            endpoints.save(endpoint);
            sosMonitoringRooms.reconcile(room,previousName,previousType,null);
            result.add(feature.withRoom(room.getRoomId(),room.getRoomCode(),room.getRoomName(),room.getRoomType(),
                    room.getDepartmentCode(),room.getDescription(),room.isPublicVisible(),endpointName));
        }
        return new Document(document.schemaVersion(),document.backgroundColor(),result,document.rotationDegrees());
    }
    private Room resolveRoom(Feature feature,String code,FacilityMap map) {
        Room room;
        if(feature.roomId()!=null) room=rooms.findById(feature.roomId()).orElseThrow(()->new ResourceNotFoundException("Phòng không tồn tại: "+feature.roomId()));
        else room=rooms.findByRoomCodeIgnoreCase(code).orElseGet(Room::new);
        if(room.getFloor()!=null && !room.getFloor().getFloorId().equals(map.getFloor().getFloorId()))
            throw new BusinessRuleException("Phòng và bản đồ phải thuộc cùng một tầng.");
        return room;
    }
    private String roomCode(Feature feature,FacilityMap map) {
        String supplied=clean(feature.roomCode());
        if(supplied!=null) return supplied;
        String generated=(map.getMapCode()+"_"+feature.id()).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9._-]","_");
        return generated.substring(0,Math.min(64,generated.length()));
    }
    private String text(String value,String fallback) { String cleaned=clean(value); return cleaned==null?clean(fallback):cleaned; }
    private String clean(String value) { return value==null||value.isBlank()?null:value.trim(); }
    private Source source(FacilityMap map) {
        var m=images.metadata(map.getMapId());
        return new Source(map.getMapId(),m.path("asset_hash").asText(),m.path("width_m").asDouble(),m.path("height_m").asDouble(),
            m.path("resolution_m_per_pixel").asDouble(),m.path("origin_x_m").asDouble(),m.path("origin_y_m").asDouble(),
            m.path("origin_yaw_rad").asDouble(),m.path("frame_id").asText(),m.path("width_px").asInt(),m.path("height_px").asInt());
    }
    private FacilityMap map(UUID id) { return maps.findById(id).orElseThrow(()->new ResourceNotFoundException("Map not found")); }
    private FacilityMap lock(UUID id) {
        FacilityMap value=entityManager.find(FacilityMap.class,id,LockModeType.PESSIMISTIC_WRITE);
        if(value==null) throw new ResourceNotFoundException("Map not found");
        return value;
    }
    private MapPresentation requireRow(UUID id) { return presentations.findById(id).orElseThrow(()->new ResourceNotFoundException("Bản đồ chưa có giao diện đã lưu.")); }
    private void checkRevision(MapPresentation row,long expected) {
        if((row==null?0:row.getRevision())!=expected) throw new ConflictException("Bản vẽ đã được cập nhật ở phiên khác. Tải lại trước khi lưu.");
    }
    private void requireSource(MapPresentation row,FacilityMap map) {
        if(!row.getSourceAssetHash().equals(map.getAssetHash())) throw new ConflictException("Giao diện đã cũ so với map gốc.");
    }
    private Document document(com.fasterxml.jackson.databind.JsonNode node) { return json.convertValue(node,Document.class); }
    private Editor editor(Source source,MapPresentation row) {
        return row==null?new Editor(source,0,new Document(1,"#f8fafc",List.of()),null,null,null,false):
            new Editor(source,row.getRevision(),document(row.getDraftJson()),row.getPublishedJson()==null?null:document(row.getPublishedJson()),
                row.getPublishedRevision(),row.getPublishedAt(),!row.getSourceAssetHash().equals(source.assetHash()));
    }
}
