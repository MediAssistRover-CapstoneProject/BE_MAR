package com.mediassistrover.backend.validation;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

@Component
public class MqttContractValidator {
    private final FixedContractSchemaValidator schemas=new FixedContractSchemaValidator();
    public void inbound(JsonNode node) { schemas.validateRoot("mqtt-pi-message.schema.json",node); }
    public void command(JsonNode node) { schemas.validateRoot("mqtt-command.schema.json",node); }
    public void response(JsonNode node) { schemas.validateRoot("mqtt-be-response.schema.json",node); }
}
