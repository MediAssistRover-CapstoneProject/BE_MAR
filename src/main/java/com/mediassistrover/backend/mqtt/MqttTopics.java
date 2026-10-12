package com.mediassistrover.backend.mqtt;

import java.util.*;
public final class MqttTopics {
    private MqttTopics() { }
    public record Topic(UUID robotId,String lane) { }
    public static String topic(UUID robotId,String lane) { return "mar/robots/"+robotId+"/"+lane; }
    public static Topic parse(String value) {
        String[] parts=value.split("/",-1);
        if (parts.length!=4 || !parts[0].equals("mar") || !parts[1].equals("robots")
                || !Set.of("events","heartbeat","presence","map-preview").contains(parts[3])) throw new IllegalArgumentException("Invalid inbound MQTT topic");
        UUID id=UUID.fromString(parts[2]);
        if (!id.toString().equals(parts[2])) throw new IllegalArgumentException("Topic robot ID must be a canonical lowercase UUID");
        return new Topic(id,parts[3]);
    }
}
