package com.mediassistrover.backend.mqtt;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import com.mediassistrover.backend.enums.StepType;
import jakarta.annotation.PostConstruct;
import java.nio.file.Path;
import java.util.*;

@Component @ConfigurationProperties("app.mqtt") @Getter @Setter
public class MqttProperties {
    /** StepTypes that schema 3 may route to a robot. The allowlist may be a safe subset. */
    public static final Set<StepType> ROBOT_STEP_TYPES =
            Collections.unmodifiableSet(EnumSet.of(
                    StepType.MOVE,
                    StepType.CONTROL_HARDWARE,
                    StepType.EXECUTE_SENSING,
                    StepType.WAIT
            ));
    private boolean enabled;
    private boolean dispatchEnabled;
    private boolean allowSimulation;
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
    public boolean dispatches(StepType stepType) {
        return enabled && dispatchEnabled && stepType!=null && enabledStepTypes.contains(stepType.name());
    }
    @PostConstruct
    private void validateStepTypes() {
        for (String value : enabledStepTypes) {
            final StepType type;
            try {
                type = StepType.valueOf(value);
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException(
                        "Unsupported MQTT enabled StepType: " + value
                );
            }
            if (!ROBOT_STEP_TYPES.contains(type)) {
                throw new IllegalArgumentException(
                        "MQTT enabled StepType must be one of MOVE, CONTROL_HARDWARE, "
                                + "EXECUTE_SENSING, WAIT; backend-owned StepType is not dispatchable: " + value
                );
            }
        }
    }
    public void validateConnection() {
        validateStepTypes();
        if (!enabled) return;
        var uri=java.net.URI.create(brokerUri);
        if (!"ssl".equals(uri.getScheme()) || uri.getHost()==null) throw new IllegalArgumentException("MQTT requires ssl://host:port with hostname verification");
        if (username==null || username.isBlank() || password==null || password.isBlank()) throw new IllegalArgumentException("MQTT broker credentials are required");
        if (clientId==null || clientId.isBlank() || commandTtlSec<1 || commandTtlSec>300 || retryIntervalSec<1 || retryIntervalSec>10)
            throw new IllegalArgumentException("Invalid MQTT client ID / timing configuration");
    }
}
