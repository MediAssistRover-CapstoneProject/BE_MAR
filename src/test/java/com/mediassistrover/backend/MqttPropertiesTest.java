package com.mediassistrover.backend;

import com.mediassistrover.backend.enums.StepType;
import com.mediassistrover.backend.mqtt.MqttProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class MqttPropertiesTest {

    private static final String ALL_TYPES =
            Arrays.stream(StepType.values())
                    .map(Enum::name)
                    .collect(Collectors.joining(","));

    private final ApplicationContextRunner context =
            new ApplicationContextRunner()
                    .withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void bindsAllElevenTypesWithMqttEnabled() {
        context.withPropertyValues(
                        "app.mqtt.enabled=true",
                        "app.mqtt.enabled-step-types=" + ALL_TYPES
                )
                .run(result -> {
                    assertThat(result).hasNotFailed();

                    assertThat(result.getBean(MqttProperties.class)
                                    .getEnabledStepTypes())
                            .containsExactlyInAnyOrderElementsOf(
                                    Arrays.stream(StepType.values())
                                            .map(Enum::name)
                                            .toList()
                            );
                });
    }

    @Test
    void rejectsUnknownStepTypeDuringContextStartup() {
        context.withPropertyValues(
                        "app.mqtt.enabled=true",
                        "app.mqtt.enabled-step-types=MOVE,NOT_A_STEP"
                )
                .run(result -> {
                    assertThat(result).hasFailed();

                    assertThat(result.getStartupFailure())
                            .hasRootCauseMessage(
                                    "Unsupported MQTT enabled StepType: NOT_A_STEP"
                            );
                });
    }

    @Test
    void allowedValuesAreExactlyTheStepTypeEnum() {
        Set<String> expected = Set.of(
                "AUTHENTICATE",
                "CONTROL_HARDWARE",
                "MOVE",
                "WAIT",
                "INTERACT",
                "HANDLE_PAYLOAD",
                "DATA_SERVICE",
                "SET_CONTEXT",
                "CHECK_CONDITION",
                "EXECUTE_SENSING",
                "NOTIFY"
        );

        Set<String> actual = Arrays.stream(StepType.values())
                .map(Enum::name)
                .collect(Collectors.toSet());

        assertThat(actual)
                .hasSize(11)
                .containsExactlyInAnyOrderElementsOf(expected);
    }

    @EnableConfigurationProperties(MqttProperties.class)
    static class PropertiesConfiguration {
    }
}