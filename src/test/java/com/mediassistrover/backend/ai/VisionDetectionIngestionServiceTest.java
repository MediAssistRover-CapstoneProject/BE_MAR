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
        var created = (org.mockito.stubbing.Answer<EmergencyFlowService.Result>) invocation -> {
            var incident=EmergencyIncident.builder().incidentId(UUID.randomUUID()).build();
            return new EmergencyFlowService.Result(incident,new ServiceRequest(),new Alert(),null);
        };
        when(emergencies.create(any())).thenAnswer(created);
        when(emergencies.createForStaffReview(any())).thenAnswer(created);
        service=new VisionDetectionIngestionService(mqtt,robots,detections,incidents,receipts,emergencies,
                Clock.fixed(now,ZoneOffset.UTC));
    }

    @Test
    void mapsAllEmergencyVisionTypes() {
        assertMapping("SUSPECTED_FALL",DetectionEventType.SUSPECTED_FALL,"AI_SUSPECTED_FALL",
                EmergencyIncidentSeverity.WARNING);
        assertMapping("SUSPECTED_SEIZURE",DetectionEventType.SUSPECTED_SEIZURE,"AI_SUSPECTED_SEIZURE",
                EmergencyIncidentSeverity.CRITICAL);
        assertMapping("GESTURE_SOS",DetectionEventType.SOS_SIGNAL,"AI_GESTURE_SOS",
                EmergencyIncidentSeverity.CRITICAL);
    }

    @Test
    void simulatedVisionStoresOnlyDetectionAndReceipt() {
        ObjectNode payload = payload("SUSPECTED_FALL").put("simulation", true);
        var result = service.ingest(robotId, payload);
        assertNull(result.incidentId());
        assertFalse(result.duplicate());
        verify(detections).saveAndFlush(any());
        verify(receipts).recordVision(eq(result.detectionEventId()), any(), same(payload),
                argThat(provenance -> provenance.path("simulation").asBoolean()));
        verifyNoInteractions(emergencies);
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


    @Test
    void continuousTransitionDuringMoveReportsWithoutAJobOrReadyPose() {
        var f = new ContinuousFixture(now);
        // A recovery hold disables dispatch, while an authenticated camera
        // observation still reaches staff for review.
        f.robot.setStatus(RobotStatus.ERROR);
        var link = f.links.findById(f.robotId).orElseThrow();
        link.setDispatchReady(false);
        var result = f.service.record(f.request(PatrolFallEventService.DetectionKind.FALL_TRANSITION, null), f.device, null);
        assertFalse(result.duplicate());
        verify(f.emergencies).createForStaffReview(argThat(command -> command.xM() == null && command.yM() == null
                && command.data().path("location_basis").asText().equals("UNKNOWN")));
        verify(f.emergencies, never()).create(any());
        verifyNoInteractions(f.navigation);
        verify(f.registry).observationFresh(link);
        verify(f.registry, never()).fresh(any());
    }

    @Test
    void personAlreadyOnFloorRequiresActualSynchronizedDepth() {
        var f = new ContinuousFixture(now);
        assertThrows(com.mediassistrover.backend.exception.BusinessRuleException.class,
                () -> f.service.record(f.request(PatrolFallEventService.DetectionKind.PERSON_ON_FLOOR, null), f.device, null));
        verifyNoInteractions(f.emergencies, f.detections);
    }

    @Test
    void continuousSuspicionCoalescesAnActiveIncidentInsteadOfOpeningAnotherCall() {
        var f = new ContinuousFixture(now);
        var detectionId = UUID.randomUUID();
        var incidentId = UUID.randomUUID();
        when(f.incidents.findFirstByRobotRobotIdAndIncidentTypeAndArchivedAtIsNullOrderByOccurredAtDesc(any(), any()))
                .thenReturn(Optional.of(EmergencyIncident.builder().incidentId(incidentId).status(EmergencyIncidentStatus.OPEN).map(f.map)
                        .occurredAt(now.minusSeconds(10)).detectionEvent(DetectionEvent.builder().detectionEventId(detectionId).build()).build()));
        var request = f.request(PatrolFallEventService.DetectionKind.FALL_TRANSITION, null);
        var result = f.service.record(request, f.device, null);
        assertTrue(result.duplicate());assertEquals(incidentId, result.incidentId());
        assertEquals(detectionId, result.detectionEventId());
        verify(f.receipts).recordContinuousFall(eq(request.eventId()), same(f.robot), any(),
                argThat(provenance -> provenance.path("incident_id").asText().equals(incidentId.toString())));
        verifyNoInteractions(f.emergencies, f.detections);
    }

    @Test
    void anOldOpenReviewOrDifferentPatientLocationCannotSuppressANewAlert() {
        var f = new ContinuousFixture(now);
        var old = EmergencyIncident.builder().incidentId(UUID.randomUUID()).status(EmergencyIncidentStatus.OPEN).map(f.map)
                .occurredAt(now.minusSeconds(60)).detectionEvent(DetectionEvent.builder()
                        .detectionEventId(UUID.randomUUID()).locationBasis(DetectionLocationBasis.OBJECT)
                        .xM(java.math.BigDecimal.ONE).yM(java.math.BigDecimal.ONE).build()).build();
        when(f.incidents.findFirstByRobotRobotIdAndIncidentTypeAndArchivedAtIsNullOrderByOccurredAtDesc(any(), any()))
                .thenReturn(Optional.of(old));
        assertFalse(f.service.record(f.request(PatrolFallEventService.DetectionKind.FALL_TRANSITION, null), f.device, null).duplicate());
        verify(f.emergencies).createForStaffReview(any());
        old.setOccurredAt(now.minusSeconds(5));
        var distant = new PatrolFallEventService.PatientLocation(new java.math.BigDecimal("4"), java.math.BigDecimal.ONE,
                java.math.BigDecimal.ONE, now, UUID.randomUUID(), f.map.getMapId(), f.map.getAssetHash(), 1L,
                java.math.BigDecimal.ZERO, new java.math.BigDecimal(".2"));
        assertFalse(RobotFallEventService.coalesces(old, distant, f.map, now, 30, 1.));
        var near = new PatrolFallEventService.PatientLocation(new java.math.BigDecimal("1.2"), java.math.BigDecimal.ONE,
                java.math.BigDecimal.ONE, now, UUID.randomUUID(), f.map.getMapId(), f.map.getAssetHash(), 1L,
                java.math.BigDecimal.ZERO, new java.math.BigDecimal(".2"));
        assertTrue(RobotFallEventService.coalesces(old, near, f.map, now, 30, 1.));
    }

    @Test
    void exactContinuousRetryReplaysEvenAfterMqttDisconnects() {
        var f = new ContinuousFixture(now);
        UUID detectionId = UUID.randomUUID(), incidentId = UUID.randomUUID();
        when(f.receipts.duplicateContinuousFall(any(), any(), any())).thenReturn(Optional.of(
                json.createObjectNode().put("incident_id", incidentId.toString()).put("detection_event_id", detectionId.toString())));
        var result = f.service.record(f.request(PatrolFallEventService.DetectionKind.FALL_TRANSITION, null), f.device, null);
        assertTrue(result.duplicate());assertEquals(incidentId, result.incidentId());
        verifyNoInteractions(f.links, f.emergencies, f.detections);
    }

    @Test
    void sustainedFloorPersonUsesVerifiedDepthAndDoesNotConfirmAnEmergencyAutomatically() {
        var f = new ContinuousFixture(now);
        var session = new RobotNavigationSession();session.setRobotId(f.robotId);session.setSessionId(UUID.randomUUID());
        session.setMap(f.map);session.setMapAssetHash(f.map.getAssetHash());session.setInitializationRevision(1);
        session.setExpiresAt(now.plusSeconds(3600));
        when(f.sessions.findById(f.robotId)).thenReturn(Optional.of(session));
        when(f.locationEvidence.viewpoint(session, now)).thenReturn(
                new PatrolFallEventService.Viewpoint(new java.math.BigDecimal("1"), new java.math.BigDecimal("1"), now));
        var location = new PatrolFallEventService.PatientLocation(new java.math.BigDecimal("2"), new java.math.BigDecimal("2"),
                new java.math.BigDecimal("1"), now, session.getSessionId(), f.map.getMapId(), f.map.getAssetHash(), 1L,
                java.math.BigDecimal.ZERO, new java.math.BigDecimal("0.2"));
        assertFalse(f.service.record(f.request(PatrolFallEventService.DetectionKind.PERSON_ON_FLOOR, location), f.device, null).duplicate());
        verify(f.emergencies).createForStaffReview(argThat(command ->
                command.data().path("detection_kind").asText().equals("PERSON_ON_FLOOR")
                && command.data().path("patient_location").path("floor_height_m").asDouble() == .2
                && command.description().contains("nằm gần sàn")));
        verify(f.emergencies, never()).create(any());
        var tooHigh = new PatrolFallEventService.PatientLocation(location.xM(), location.yM(), location.rangeM(), now,
                session.getSessionId(), f.map.getMapId(), f.map.getAssetHash(), 1L, java.math.BigDecimal.ZERO,
                new java.math.BigDecimal("0.7"));
        assertThrows(com.mediassistrover.backend.exception.BusinessRuleException.class,
                () -> PatrolFallEventService.requireFloorEvidence(PatrolFallEventService.DetectionKind.PERSON_ON_FLOOR, tooHigh));
    }

    private static class ContinuousFixture {
        final UUID robotId = UUID.randomUUID();
        final FacilityMap map = FacilityMap.builder().mapId(UUID.randomUUID()).status(MapStatus.ACTIVE)
                .assetHash("a".repeat(64)).originXM(java.math.BigDecimal.ZERO).originYM(java.math.BigDecimal.ZERO)
                .originYawRad(java.math.BigDecimal.ZERO).widthM(new java.math.BigDecimal("10"))
                .heightM(new java.math.BigDecimal("10")).build();
        final Robot robot = Robot.builder().robotId(robotId).robotCode("mar-v1").status(RobotStatus.BUSY)
                .deviceSecretHash("hash").deviceTokenVersion(1).currentMap(map).build();
        final com.mediassistrover.backend.security.DevicePrincipal device;
        final com.mediassistrover.backend.validation.DeviceResourceScopePolicy scope = mock(com.mediassistrover.backend.validation.DeviceResourceScopePolicy.class);
        final RobotRepository robots = mock(RobotRepository.class);
        final RobotMqttSessionRepository links = mock(RobotMqttSessionRepository.class);
        final com.mediassistrover.backend.mqtt.MqttSessionRegistry registry = mock(com.mediassistrover.backend.mqtt.MqttSessionRegistry.class);
        final RobotNavigationSessionRepository sessions = mock(RobotNavigationSessionRepository.class);
        final com.mediassistrover.backend.service.NavigationSessionService navigation = mock(com.mediassistrover.backend.service.NavigationSessionService.class);
        final PatrolFallEventService locationEvidence = mock(PatrolFallEventService.class);
        final DetectionEventRepository detections = mock(DetectionEventRepository.class);
        final EmergencyIncidentRepository incidents = mock(EmergencyIncidentRepository.class);
        final MonitoringReceiptService receipts = mock(MonitoringReceiptService.class);
        final EmergencyFlowService emergencies = mock(EmergencyFlowService.class);
        final com.mediassistrover.backend.service.RuntimeSettingService settings = mock(com.mediassistrover.backend.service.RuntimeSettingService.class);
        final RobotFallEventService service;
        final Instant now;
        ContinuousFixture(Instant now) {
            this.now = now;
            device = new com.mediassistrover.backend.security.DevicePrincipal(robotId, "mar-v1", 1, "test", now.plusSeconds(3600));
            when(scope.requireCurrentDevice(device)).thenReturn(robot);
            when(robots.findByIdForUpdate(robotId)).thenReturn(Optional.of(robot));
            var link = new RobotMqttSession();
            link.setHelloJson(new ObjectMapper().createObjectNode().set("capabilities", new ObjectMapper().createArrayNode().add("continuous-fall-v1")));
            when(links.findById(robotId)).thenReturn(Optional.of(link));when(registry.observationFresh(link)).thenReturn(true);
            when(settings.getLong(anyString(), anyLong(), anyLong(), anyLong())).thenReturn(30L);
            when(detections.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
            when(emergencies.createForStaffReview(any())).thenReturn(new EmergencyFlowService.Result(
                    EmergencyIncident.builder().incidentId(UUID.randomUUID()).build(), new ServiceRequest(), new Alert(), null));
            service = new RobotFallEventService(scope, robots, links, registry, sessions, navigation, locationEvidence,
                    detections, incidents, receipts, emergencies, settings, new com.mediassistrover.backend.mqtt.MqttWire(),
                    Clock.fixed(now, ZoneOffset.UTC));
        }
        RobotFallEventService.Request request(PatrolFallEventService.DetectionKind kind, PatrolFallEventService.PatientLocation location) {
            return new RobotFallEventService.Request(UUID.randomUUID(), UUID.randomUUID(), now, new java.math.BigDecimal(".9"),
                    "test-model", kind, location);
        }
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
        if (storedType == DetectionEventType.SUSPECTED_FALL) {
            verify(emergencies).createForStaffReview(commandCaptor.capture());
            verify(emergencies, never()).create(any());
            assertTrue(commandCaptor.getValue().data().path("staff_review_required").asBoolean());
        } else {
            verify(emergencies).create(commandCaptor.capture());
        }
        assertEquals(incidentType,commandCaptor.getValue().incidentType());
        assertEquals(severity,commandCaptor.getValue().severity());
        assertFalse(commandCaptor.getValue().data().path("simulation").asBoolean());
    }

    private ObjectNode payload(String type) {
        ObjectNode payload=json.createObjectNode();
        payload.put("event_id",UUID.randomUUID().toString());payload.put("robot_id",robotId.toString());
        payload.put("event_type",type);payload.put("source","VISION_AI");payload.put("detected_at",now.toString());
        payload.put("algorithm","test-model");payload.put("confidence",0.91);payload.put("simulation",false);
        return payload;
    }
}
