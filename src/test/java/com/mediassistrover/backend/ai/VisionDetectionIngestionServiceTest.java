package com.mediassistrover.backend.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.mqtt.MqttProperties;
import com.mediassistrover.backend.repository.*;
import com.mediassistrover.backend.service.EmergencyFlowService;
import org.junit.jupiter.api.*;

import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VisionDetectionIngestionServiceTest {
    private final RobotRepository robots=mock(RobotRepository.class);
    private final DetectionEventRepository detections=mock(DetectionEventRepository.class);
    private final EmergencyIncidentRepository incidents=mock(EmergencyIncidentRepository.class);
    private final MonitoringReceiptService receipts=mock(MonitoringReceiptService.class);
    private final EmergencyFlowService emergencies=mock(EmergencyFlowService.class);
    private final MqttProperties mqtt=new MqttProperties();
    private final ObjectMapper json=new ObjectMapper();
    private final Instant now=Instant.parse("2026-11-12T03:00:00Z");
    private final UUID robotId=UUID.randomUUID();
    private VisionDetectionIngestionService service;

    @BeforeEach
    void setup() {
        mqtt.setEnabled(true);mqtt.setRobotIds(Set.of(robotId));
        var robot=Robot.builder().robotId(robotId).status(RobotStatus.IDLE)
                .currentMap(FacilityMap.builder().mapId(UUID.randomUUID()).build()).build();
        when(robots.findByIdForUpdate(robotId)).thenReturn(Optional.of(robot));
        when(emergencies.create(any())).thenAnswer(invocation->{
            var incident=EmergencyIncident.builder().incidentId(UUID.randomUUID()).build();
            return new EmergencyFlowService.Result(incident,new ServiceRequest(),new Alert(),null);
        });
        service=new VisionDetectionIngestionService(mqtt,robots,detections,incidents,receipts,emergencies,
                Clock.fixed(now,ZoneOffset.UTC));
    }

    @Test
    void mapsAllEmergencyVisionTypesAndKeepsSimulationCallsEnabled() {
        assertMapping("SUSPECTED_FALL",DetectionEventType.SUSPECTED_FALL,"AI_SUSPECTED_FALL",
                EmergencyIncidentSeverity.WARNING);
        assertMapping("SUSPECTED_SEIZURE",DetectionEventType.SUSPECTED_SEIZURE,"AI_SUSPECTED_SEIZURE",
                EmergencyIncidentSeverity.CRITICAL);
        assertMapping("GESTURE_SOS",DetectionEventType.SOS_SIGNAL,"AI_GESTURE_SOS",
                EmergencyIncidentSeverity.CRITICAL);
    }

    @Test
    void duplicateEventDoesNotCreateAnotherDetectionOrCall() {
        ObjectNode payload=payload("SUSPECTED_FALL");UUID id=UUID.fromString(payload.path("event_id").asText());
        UUID incidentId=UUID.randomUUID();
        when(receipts.duplicateVision(eq(id),any(),same(payload))).thenReturn(true);
        when(incidents.findByDetectionEventDetectionEventId(id))
                .thenReturn(Optional.of(EmergencyIncident.builder().incidentId(incidentId).build()));
        var result=service.ingest(robotId,payload);
        assertTrue(result.duplicate());assertEquals(incidentId,result.incidentId());
        verify(detections,never()).saveAndFlush(any());verifyNoInteractions(emergencies);
    }

    private void assertMapping(String sourceType,DetectionEventType storedType,String incidentType,
                               EmergencyIncidentSeverity severity) {
        clearInvocations(detections,receipts,emergencies);
        ObjectNode payload=payload(sourceType);
        var result=service.ingest(robotId,payload);
        assertFalse(result.duplicate());
        var detectionCaptor=org.mockito.ArgumentCaptor.forClass(DetectionEvent.class);
        verify(detections).saveAndFlush(detectionCaptor.capture());
        assertEquals(storedType,detectionCaptor.getValue().getEventType());
        assertEquals(DetectionLocationBasis.UNKNOWN,detectionCaptor.getValue().getLocationBasis());
        var commandCaptor=org.mockito.ArgumentCaptor.forClass(EmergencyFlowService.Command.class);
        verify(emergencies).create(commandCaptor.capture());
        assertEquals(incidentType,commandCaptor.getValue().incidentType());
        assertEquals(severity,commandCaptor.getValue().severity());
        assertTrue(commandCaptor.getValue().data().path("simulation").asBoolean());
    }

    private ObjectNode payload(String type) {
        ObjectNode payload=json.createObjectNode();
        payload.put("event_id",UUID.randomUUID().toString());payload.put("robot_id",robotId.toString());
        payload.put("event_type",type);payload.put("source","VISION_AI");payload.put("detected_at",now.toString());
        payload.put("algorithm","test-model");payload.put("confidence",0.91);payload.put("simulation",true);
        return payload;
    }
}
