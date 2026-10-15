package com.mediassistrover.backend.repository;

import com.mediassistrover.backend.entity.RobotLocationHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RobotLocationHistoryRepository extends JpaRepository<RobotLocationHistory, UUID> {
    java.util.Optional<com.mediassistrover.backend.entity.RobotLocationHistory> findFirstByRobotRobotIdAndMapMapIdAndFreshTrueOrderByCapturedAtDesc(UUID robot,UUID map);
    Page<RobotLocationHistory> findByRobotRobotId(UUID robotId, Pageable pageable);
}
