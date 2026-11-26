package com.mediassistrover.backend.validation;
import com.fasterxml.jackson.databind.JsonNode;
import com.mediassistrover.backend.exception.BusinessRuleException;
import org.springframework.stereotype.Component;

@Component
public class MqttContractValidator {
    private final FixedContractSchemaValidator schemas=new FixedContractSchemaValidator();
    public void live(JsonNode node) { schemas.validateRoot("mapping-live.schema.json",node); }
    public void inbound(JsonNode node) {
        JsonNode baseline=node;
        JsonNode started="COMMAND_ACK".equals(node.path("messageType").asText())
                ? node.path("payload").path("start") : "STEP_STARTED".equals(node.path("messageType").asText())
                    ? node.path("payload") : null;
        if(started!=null && "MOVE".equals(started.path("stepType").asText()) && started.path("resolvedInputs").has("path_search")) {
            GroupPathSearchPolicy.validate(started.path("resolvedInputs").get("path_search"));
            baseline=node.deepCopy();
            JsonNode copied="COMMAND_ACK".equals(node.path("messageType").asText())
                    ? baseline.path("payload").path("start") : baseline.path("payload");
            ((com.fasterxml.jackson.databind.node.ObjectNode)copied.path("resolvedInputs")).remove("path_search");
        }
        schemas.validateRoot("mqtt-pi-message.schema.json",baseline);
    }
    public void command(JsonNode node) {
        JsonNode baseline=node;
        if("EXECUTE_STEP".equals(node.path("commandType").asText()) && "MOVE".equals(node.path("step").path("stepType").asText())
                && node.path("step").path("params").has("path_search")) {
            GroupPathSearchPolicy.validate(node.path("step").path("params").get("path_search"));
            baseline=node.deepCopy();
            ((com.fasterxml.jackson.databind.node.ObjectNode)baseline.path("step").path("params")).remove("path_search");
        }
        schemas.validateRoot("mqtt-command.schema.json",baseline);
        if (node.path("commissioningUnlock").asBoolean()) {
            JsonNode step=node.path("step"), params=step.path("params");
            if (!"CONTROL_HARDWARE".equals(step.path("stepType").asText())
                    || !"UNLOCK_COMPARTMENT".equals(params.path("action").asText())
                    || params.has("authorization")) {
                throw new BusinessRuleException("Commissioning marker requires a grantless CONTROL_HARDWARE UNLOCK");
            }
        }
        rejectUnresolvedReferences(node,"$");
    }
    public void response(JsonNode node) { schemas.validateRoot("mqtt-be-response.schema.json",node); }

    private void rejectUnresolvedReferences(JsonNode node,String path) {
        if (node.isObject()) {
            if (node.has("$ref")) {
                throw new BusinessRuleException("Invalid MQTT command: unresolved reference at " + path + ".$ref");
            }
            node.fields().forEachRemaining(field -> rejectUnresolvedReferences(field.getValue(),path+"."+field.getKey()));
        } else if (node.isArray()) {
            for (int i=0;i<node.size();i++) rejectUnresolvedReferences(node.get(i),path+"["+i+"]");
        }
    }
}
