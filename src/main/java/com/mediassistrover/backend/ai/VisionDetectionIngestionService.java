package com.mediassistrover.backend.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.exception.*;
import com.mediassistrover.backend.mqtt.MqttProperties;
import com.mediassistrover.backend.repository.*;
import com.mediassistrover.backend.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
@RequiredArgsConstructor
public class VisionDetectionIngestionService {
    public record Result(UUID detectionEventId, UUID incidentId, boolean duplicate) { }

    private final MqttProperties mqttProperties;
    private final RobotRepository robotRepository;
    private final DetectionEventRepository detectionRepository;
    private final EmergencyIncidentRepository incidentRepository;
    private final MonitoringReceiptService receipts;
    private final EmergencyFlowService emergencies;
    private final Clock clock;

    @Transactional
    public Result ingest(UUID topicRobotId, ObjectNode payload) {
        if (!mqttProperties.manages(topicRobotId))
            throw new BusinessRuleException("Vision robot is not enrolled for MQTT");
        UUID eventId=uuid(payload,"event_id");
        UUID payloadRobotId=uuid(payload,"robot_id");
        if(!topicRobotId.equals(payloadRobotId))
            throw new BusinessRuleException("Vision robotId differs from topic identity");
        if(!"VISION_AI".equals(text(payload,"source",true)))
            throw new BusinessRuleException("Vision source must be VISION_AI");

        Robot robot=robotRepository.findByIdForUpdate(topicRobotId)
                .orElseThrow(()->new ResourceNotFoundException("Robot not found: "+topicRobotId));
        if(robot.getStatus()==RobotStatus.RETIRED)
            throw new BusinessRuleException("Retired Robot cannot publish vision detections");
        if(robot.getCurrentMap()==null)
            throw new BusinessRuleException("Robot has no assigned map");

        if(receipts.duplicateVision(eventId,robot,payload)) {
            var incident=incidentRepository.findByDetectionEventDetectionEventId(eventId).orElse(null);
            return new Result(eventId,incident==null?null:incident.getIncidentId(),true);
        }
        if(detectionRepository.existsById(eventId))
            throw new ConflictException("Vision event ID is already used by another detection");

        String sourceType=text(payload,"event_type",true);
        DetectionEventType detectionType=detectionType(sourceType);
        Instant detectedAt=instant(payload,"detected_at");
        Instant now=Instant.now(clock);
        if(detectedAt.isAfter(now.plusSeconds(5)))
            throw new BusinessRuleException("Vision detected_at is too far in the future");
        BigDecimal confidence=decimal(payload,"confidence");
        if(confidence!=null&&(confidence.compareTo(BigDecimal.ZERO)<0||confidence.compareTo(BigDecimal.ONE)>0))
            throw new BusinessRuleException("Vision confidence must be between 0 and 1");
        String algorithm=text(payload,"algorithm",false);
        if(algorithm==null)algorithm="unspecified";
        if(algorithm.length()>100)throw new BusinessRuleException("Vision algorithm exceeds 100 characters");
        if(payload.has("simulation")&&!payload.path("simulation").isBoolean())
            throw new BusinessRuleException("Vision simulation must be boolean");

        DetectionEvent detection=DetectionEvent.builder().detectionEventId(eventId).robot(robot)
                .map(robot.getCurrentMap()).eventType(detectionType).confidenceScore(confidence)
                .locationBasis(DetectionLocationBasis.UNKNOWN).modelVersion(algorithm)
                .eventPayloadJson(payload.deepCopy()).detectedAt(detectedAt).receivedAt(now).build();
        detectionRepository.saveAndFlush(detection);

        ObjectNode provenance=payload.objectNode().put("transport","MQTT")
                .put("topic","mar/robots/"+topicRobotId+"/vision/detections")
                .put("simulation",payload.path("simulation").asBoolean(false));
        receipts.recordVision(eventId,robot,payload,provenance);

        EmergencyIncidentSeverity severity=detectionType==DetectionEventType.SUSPECTED_FALL
                ?EmergencyIncidentSeverity.WARNING:EmergencyIncidentSeverity.CRITICAL;
        String description=description(payload,sourceType);
        ObjectNode data=payload.objectNode().put("source","VISION_AI")
                .put("source_event_type",sourceType)
                .put("simulation",payload.path("simulation").asBoolean(false));
        if(confidence!=null)data.put("confidence",confidence);
        data.put("algorithm",algorithm);
        data.set("vision_event",payload.deepCopy());

        String incidentType=switch(detectionType) {
            case SUSPECTED_FALL -> "AI_SUSPECTED_FALL";
            case SUSPECTED_SEIZURE -> "AI_SUSPECTED_SEIZURE";
            case SOS_SIGNAL -> "AI_GESTURE_SOS";
            default -> throw new BusinessRuleException("Unsupported emergency vision event");
        };
        var created=emergencies.create(new EmergencyFlowService.Command("VISION_AI:"+eventId,robot,
                robot.getCurrentMap(),null,detection,incidentType,severity,description,null,null,detectedAt,
                RequestSourceType.DETECTOR,data,"AI_EMERGENCY_CREATED",
                new RequestMetadata(null,"MQTT VISION_AI",eventId.toString())));
        return new Result(eventId,created.incident().getIncidentId(),false);
    }

    private DetectionEventType detectionType(String value) {
        return switch(value) {
            case "SUSPECTED_FALL" -> DetectionEventType.SUSPECTED_FALL;
            case "SUSPECTED_SEIZURE" -> DetectionEventType.SUSPECTED_SEIZURE;
            case "GESTURE_SOS" -> DetectionEventType.SOS_SIGNAL;
            default -> throw new BusinessRuleException("Unsupported vision event_type: "+value);
        };
    }

    private String description(ObjectNode payload,String type) {
        String supplied=text(payload,"event_text_vi",false);
        if(supplied!=null) {
            if(supplied.length()>4000)throw new BusinessRuleException("Vision event_text_vi exceeds 4000 characters");
            return supplied;
        }
        return switch(type) {
            case "SUSPECTED_FALL" -> "AI phát hiện người nghi bị té ngã";
            case "SUSPECTED_SEIZURE" -> "AI phát hiện người nghi có chuyển động co giật";
            case "GESTURE_SOS" -> "AI phát hiện cử chỉ cầu cứu SOS";
            default -> throw new BusinessRuleException("Unsupported vision event_type: "+type);
        };
    }

    private UUID uuid(ObjectNode payload,String field) {
        String value=text(payload,field,true);
        try{return UUID.fromString(value);}
        catch(IllegalArgumentException error){throw new BusinessRuleException("Vision "+field+" must be a UUID");}
    }
    private Instant instant(ObjectNode payload,String field) {
        String value=text(payload,field,true);
        try{return Instant.parse(value);}
        catch(DateTimeParseException error){throw new BusinessRuleException("Vision "+field+" must be ISO-8601 UTC time");}
    }
    private BigDecimal decimal(ObjectNode payload,String field) {
        JsonNode value=payload.get(field);if(value==null||value.isNull())return null;
        if(!value.isNumber())throw new BusinessRuleException("Vision "+field+" must be numeric");
        return value.decimalValue();
    }
    private String text(ObjectNode payload,String field,boolean required) {
        JsonNode value=payload.get(field);
        if(value==null||value.isNull()||!value.isTextual()||value.asText().isBlank()) {
            if(required)throw new BusinessRuleException("Vision "+field+" is required");
            return null;
        }
        return value.asText().trim();
    }
}
