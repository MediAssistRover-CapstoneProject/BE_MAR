package com.mediassistrover.backend.service;

import com.mediassistrover.backend.dto.robot.response.RobotResponses.BatteryResponse;
import com.mediassistrover.backend.entity.RobotMqttSession;
import com.mediassistrover.backend.enums.BatteryUnit;
import com.mediassistrover.backend.exception.ResourceNotFoundException;
import com.mediassistrover.backend.mqtt.BatteryTelemetry;
import com.mediassistrover.backend.mqtt.MqttSessionRegistry;
import com.mediassistrover.backend.repository.RobotMqttSessionRepository;
import com.mediassistrover.backend.repository.RobotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Duration;
import java.time.Instant;
import java.time.Clock;
import java.util.UUID;

/** Reads only accepted MQTT telemetry. Calling this API never polls hardware or starts a worker. */
@Service @RequiredArgsConstructor
public class RobotBatteryService {
    private final RobotRepository robots;
    private final RobotMqttSessionRepository sessions;
    private final MqttSessionRegistry registry;
    private final Clock clock;

    @Transactional(readOnly=true)
    public BatteryResponse current(UUID robotId) {
        var robot=robots.findById(robotId).orElseThrow(()->new ResourceNotFoundException("Robot not found: "+robotId));
        Instant now=Instant.now(clock);RobotMqttSession session=sessions.findById(robotId).orElse(null);
        BatteryTelemetry.Reading reading=null;
        if(session!=null && session.getHeartbeatJson()!=null) {
            try {
                var heartbeat=session.getHeartbeatJson();
                reading=BatteryTelemetry.read(heartbeat.path("telemetry").path("battery"),
                        Instant.parse(heartbeat.path("occurredAt").asText()));
            } catch(java.time.DateTimeException ignored) { }
        }
        boolean hasCurrentSession=session!=null && registry.owned(session) && session.getLastHeartbeatAt()!=null
                && !session.getLastHeartbeatAt().isAfter(now.plusSeconds(5));
        if(reading==null) {
            Instant captured=robot.getBatteryCapturedAt();
            Double age=captured==null?null:Math.max(0.,Duration.between(captured,now).toMillis()/1000.);
            return new BatteryResponse(robotId,robot.getBatteryRaw(),robot.getBatteryUnit()==null?BatteryUnit.UNKNOWN:robot.getBatteryUnit(),
                    null,null,"LAST_KNOWN","UNAVAILABLE",null,captured==null?"UNAVAILABLE":"STALE",null,
                    null,null,null,null,captured,age,false,session==null?null:session.getLastHeartbeatAt());
        }
        boolean fresh=hasCurrentSession && reading.fresh(now),estimated=fresh && reading.estimated(now);
        String status=hasCurrentSession && !reading.capturedAt().isAfter(now.plusSeconds(5))
                && reading.ageSec(now)<=BatteryTelemetry.FRESH_SECONDS?reading.estimateStatus():"STALE";
        return new BatteryResponse(robotId,reading.raw(),reading.unit(),estimated?reading.voltageV():null,
                estimated?reading.percent():null,"MQTT_HEARTBEAT",reading.estimateMethod(),reading.profileId(),status,
                reading.curveCalibrated(),reading.seriesCells(),reading.parallelCells(),reading.nominalCapacityMah(),
                reading.nominalVoltageV(),reading.capturedAt(),reading.ageSec(now),fresh,session.getLastHeartbeatAt());
    }
}
