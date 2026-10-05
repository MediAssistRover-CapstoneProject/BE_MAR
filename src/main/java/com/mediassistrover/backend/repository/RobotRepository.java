package com.mediassistrover.backend.repository;

import com.mediassistrover.backend.entity.Robot;
import com.mediassistrover.backend.enums.RobotStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RobotRepository extends JpaRepository<Robot, UUID> {
    Optional<Robot> findByRobotCodeIgnoreCase(String robotCode);
    boolean existsByRobotCodeIgnoreCase(String robotCode);
    boolean existsBySerialNumber(String serialNumber);
    Page<Robot> findByStatus(RobotStatus status, Pageable pageable);
    List<Robot> findByStatusNotAndLastHeartbeatAtBefore(RobotStatus status, Instant threshold);
    List<Robot> findByStatusOrderByRobotCodeAsc(RobotStatus status);
    List<Robot> findByStatusAndCurrentMapMapIdOrderByRobotCodeAsc(RobotStatus status,UUID mapId);
}
