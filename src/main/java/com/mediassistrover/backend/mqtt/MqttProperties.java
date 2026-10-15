package com.mediassistrover.backend.mqtt;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.nio.file.Path;
import java.util.*;

@Component @ConfigurationProperties("app.mqtt") @Getter @Setter
public class MqttProperties {
    private boolean enabled;
    private boolean dispatchEnabled;
    private boolean allowSimulation;
    private boolean allowCommissioningUnlock;
    private boolean navigationProfileSyncEnabled=true;
    private int publishRatePerSecond=8;
    private int publishQueueCapacity=64;
    private String brokerUri="ssl://localhost:8883";
    private String clientId="mar-be-mqtt";
    private String username;
    private String password;
    private Path trustStore;
    private String trustStorePassword;
    private Path keyStore;
    private String keyStorePassword;
    private Set<UUID> robotIds=new HashSet<>();
    private Set<String> enabledStepTypes=new HashSet<>();
    private int commandTtlSec=30;
    private int retryIntervalSec=3;
    public boolean manages(UUID robotId) { return enabled && robotIds.contains(robotId); }
    public void validateConnection() {
        if (!enabled) return;
        var uri=java.net.URI.create(brokerUri);
        if (!"ssl".equals(uri.getScheme()) || uri.getHost()==null) throw new IllegalArgumentException("MQTT requires ssl://host:port with hostname verification");
        if (username==null || username.isBlank() || password==null || password.isBlank()) throw new IllegalArgumentException("MQTT broker credentials are required");
        if (clientId==null || clientId.isBlank() || commandTtlSec<1 || commandTtlSec>300 || retryIntervalSec<1 || retryIntervalSec>10)
            throw new IllegalArgumentException("Invalid MQTT client ID / timing configuration");
        if (publishRatePerSecond<1 || publishRatePerSecond>10 || publishQueueCapacity<8 || publishQueueCapacity>256)
            throw new IllegalArgumentException("MQTT publish rate must be 1..10/sec and queue capacity 8..256");
        if (!Set.of("WAIT","MOVE","CONTROL_HARDWARE","EXECUTE_SENSING").containsAll(enabledStepTypes)) throw new IllegalArgumentException("Unsupported MQTT enabled StepType");
    }
}
