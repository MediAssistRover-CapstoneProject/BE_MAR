package com.mediassistrover.backend;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediassistrover.backend.mqtt.*;
import com.mediassistrover.backend.validation.MqttContractValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.stream.*;
import static org.assertj.core.api.Assertions.*;

class MqttWireContractTest {
    private final MqttWire wire=new MqttWire();
    private final MqttContractValidator validator=new MqttContractValidator();
    record Case(String name,String schema,boolean valid,JsonNode instance) { }
    static Stream<Case> fixtures() throws Exception {
        JsonNode cases=new ObjectMapper().readTree(Path.of("docs/schemas/fixtures/contract-cases.json").toFile());
        return StreamSupport.stream(cases.spliterator(),false).filter(c->c.path("schema").asText().startsWith("mqtt-"))
                .map(c->new Case(c.path("name").asText(),c.path("schema").asText(),c.path("valid").asBoolean(),c.path("instance")));
    }
    @ParameterizedTest @MethodSource("fixtures") void agreesWithDocumentedWireFixtures(Case c) {
        org.assertj.core.api.ThrowableAssert.ThrowingCallable check=()->{
            switch(c.schema()) {
                case "mqtt-command.schema.json" -> validator.command(c.instance());
                case "mqtt-pi-message.schema.json" -> validator.inbound(c.instance());
                case "mqtt-be-response.schema.json" -> validator.response(c.instance());
                default -> throw new AssertionError("Missing test schema: "+c.schema());
            }
        };
        if(c.valid()) assertThatCode(check).as(c.name()).doesNotThrowAnyException();
        else assertThatThrownBy(check).as(c.name()).isInstanceOf(com.mediassistrover.backend.exception.BusinessRuleException.class);
    }
    @Test void rejectsDuplicateFieldsTrailingObjectsNonObjectsAndOversizedPayloads() {
        for(String input:new String[]{"{\"accepted\":true,\"accepted\":false}","{} {}","[]","null"})
            assertThatThrownBy(()->wire.parse(input.getBytes(StandardCharsets.UTF_8))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->wire.parse(new byte[1_000_001])).hasMessageContaining("exceeds");
    }
    @Test void receiptHashIgnoresOnlyTransportAndObjectOrder() {
        ObjectNode first=wire.object().put("transportSessionId","old").put("messageId","message");first.putObject("payload").put("a",1).put("b",2);
        ObjectNode replay=wire.object();replay.putObject("payload").put("b",2).put("a",1);replay.put("messageId","message").put("transportSessionId","new");
        assertThat(wire.canonicalHash(first)).isEqualTo(wire.canonicalHash(replay));
        ((ObjectNode)replay.path("payload")).put("a",2);assertThat(wire.canonicalHash(first)).isNotEqualTo(wire.canonicalHash(replay));
    }
}
