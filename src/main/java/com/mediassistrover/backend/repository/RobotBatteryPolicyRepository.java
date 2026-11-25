package com.mediassistrover.backend.repository;

import com.mediassistrover.backend.entity.RobotBatteryPolicy;
import org.springframework.data.jpa.repository.*;
import java.util.*;

public interface RobotBatteryPolicyRepository extends JpaRepository<RobotBatteryPolicy,UUID> {
    @Query("select distinct s.robotId from RobotMqttSession s")
    List<UUID> findObservedRobotIds();
}
