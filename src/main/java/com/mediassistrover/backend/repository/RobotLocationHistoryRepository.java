package com.mediassistrover.backend.repository;

import com.mediassistrover.backend.entity.RobotLocationHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface RobotLocationHistoryRepository extends JpaRepository<RobotLocationHistory, UUID> {
    @Modifying(flushAutomatically=true)
    @Query(value="INSERT INTO RobotLocationHistory(robot_location_history_id,robot_id,map_id,x_m,y_m,yaw_rad,frame_id,source,is_fresh,captured_at,received_at) VALUES (:id,:robot,:map,:x,:y,:yaw,'map','MQTT_HEARTBEAT_MAP_POSE',TRUE,:captured,:received) ON DUPLICATE KEY UPDATE robot_location_history_id=robot_location_history_id",nativeQuery=true)
    void insertHeartbeat(@Param("id") UUID id,@Param("robot") UUID robot,@Param("map") UUID map,
        @Param("x") java.math.BigDecimal x,@Param("y") java.math.BigDecimal y,@Param("yaw") java.math.BigDecimal yaw,
        @Param("captured") Instant captured,@Param("received") Instant received);
    java.util.Optional<RobotLocationHistory> findFirstByRobotRobotIdOrderByCapturedAtDesc(UUID robotId);
    java.util.Optional<com.mediassistrover.backend.entity.RobotLocationHistory> findFirstByRobotRobotIdAndMapMapIdAndFreshTrueOrderByCapturedAtDesc(UUID robot,UUID map);
    Page<RobotLocationHistory> findByRobotRobotId(UUID robotId, Pageable pageable);
    @Query("select h from RobotLocationHistory h where h.robot.robotId=:robotId " +
            "and (:from is null or h.capturedAt>=:from) and (:to is null or h.capturedAt<:to)")
    Page<RobotLocationHistory> search(@Param("robotId") UUID robotId,@Param("from") Instant from,
                                      @Param("to") Instant to,Pageable pageable);
}
