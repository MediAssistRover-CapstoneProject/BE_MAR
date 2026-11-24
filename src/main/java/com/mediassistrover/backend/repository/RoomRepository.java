package com.mediassistrover.backend.repository;

import com.mediassistrover.backend.entity.Room;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.UUID;
import java.util.Optional;

public interface RoomRepository extends JpaRepository<Room, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Room r where r.roomId = :id")
    Optional<Room> lockById(@Param("id") UUID id);
    boolean existsByRoomCodeIgnoreCase(String roomCode);
    Optional<Room> findByRoomCodeIgnoreCase(String roomCode);
    java.util.List<Room> findByDefaultEndpointGroupEndpointGroupId(UUID groupId);
    @Query("""
            select r from Room r where r.active = true and r.floor.active = true and r.publicVisible = true
              and exists (select e.endpointId from Endpoint e where e.room = r and e.active = true
                and e.publicVisible = true and e.map.status = com.mediassistrover.backend.enums.MapStatus.ACTIVE
                and e.map.floor = r.floor)
            """)
    Page<Room> findByActiveTrueAndPublicVisibleTrue(Pageable pageable);
    @Query("""
            select r from Room r where r.active = true and r.floor.active = true
              and exists (select e.endpointId from Endpoint e where e.room = r and e.active = true
                and e.map.status = com.mediassistrover.backend.enums.MapStatus.ACTIVE and e.map.floor = r.floor)
            """)
    Page<Room> findByActiveTrue(Pageable pageable);
    @Query("""
            select r from Room r where r.active = true and r.floor.active = true and r.publicVisible = true
              and exists (
                    select e.endpointId from Endpoint e
                    where e.room = r and (:mapId is null or e.map.mapId = :mapId)
                      and e.active = true and e.publicVisible = true
                      and e.map.status = com.mediassistrover.backend.enums.MapStatus.ACTIVE and e.map.floor = r.floor
                  )
              and (:floorId is null or r.floor.floorId = :floorId)
              and (:roomType is null or lower(r.roomType) = lower(:roomType))
              and (:departmentCode is null or lower(r.departmentCode) = lower(:departmentCode))
              and (:q is null or lower(r.roomName) like lower(concat('%', :q, '%'))
                   or lower(r.roomCode) like lower(concat('%', :q, '%')))
            """)
    Page<Room> searchPublic(@Param("mapId") UUID mapId, @Param("floorId") UUID floorId, @Param("roomType") String roomType,
            @Param("departmentCode") String departmentCode, @Param("q") String q, Pageable pageable);
    @Query("""
            select r from Room r where r.roomId = :id and r.active = true and r.floor.active = true and r.publicVisible = true
              and exists (select e.endpointId from Endpoint e where e.room = r and e.active = true
                and e.publicVisible = true and e.map.status = com.mediassistrover.backend.enums.MapStatus.ACTIVE
                and e.map.floor = r.floor)
            """)
    java.util.Optional<Room> findByRoomIdAndActiveTrueAndPublicVisibleTrue(@Param("id") UUID id);
}
