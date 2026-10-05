package com.mediassistrover.backend.repository;

import com.mediassistrover.backend.entity.Room;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RoomRepository extends JpaRepository<Room, UUID> {
    boolean existsByRoomCodeIgnoreCase(String roomCode);
    Page<Room> findByActiveTrueAndPublicVisibleTrue(Pageable pageable);
}
