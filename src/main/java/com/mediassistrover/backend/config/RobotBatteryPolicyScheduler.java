package com.mediassistrover.backend.config;

import com.mediassistrover.backend.repository.RobotBatteryPolicyRepository;
import com.mediassistrover.backend.service.RobotBatteryPolicyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Accepted telemetry is polled locally; never polls robot hardware or fabricates telemetry. */
@Component @RequiredArgsConstructor @Slf4j
@ConditionalOnProperty(name="app.operations.scheduler-enabled",havingValue="true",matchIfMissing=true)
public class RobotBatteryPolicyScheduler {
    private final RobotBatteryPolicyRepository rows;
    private final RobotBatteryPolicyService policy;
    @Scheduled(fixedDelayString="${app.battery.policy-check-ms:2000}")
    public void reconcile() {
        // Include durable intents even if a disconnected robot has no current MQTT session after restart.
        var ids=new java.util.LinkedHashSet<java.util.UUID>(rows.findObservedRobotIds());
        rows.findAll().forEach(row->ids.add(row.getRobotId()));
        for(var id:ids)try {policy.observe(id);policy.advance(id);}
        catch(RuntimeException error){log.warn("Battery policy retry robot={} code={}",id,error.getClass().getSimpleName());}
    }
}
