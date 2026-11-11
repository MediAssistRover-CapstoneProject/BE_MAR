package com.mediassistrover.backend.mqtt;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediassistrover.backend.exception.BusinessRuleException;
import com.mediassistrover.backend.validation.MqttContractValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.*;

@Service @RequiredArgsConstructor @Slf4j
public class MqttInboundRouter {
    private final MqttProperties properties;
    private final MqttWire wire;
    private final MqttContractValidator contracts;
    private final MqttSessionRegistry sessions;
    private final MqttInboxStore store;
    private final MqttInboxProcessor processor;
    private final MqttDurablePublisher publisher;
    private final com.mediassistrover.backend.service.NavigationProfileService navigationProfiles;
    private final com.mediassistrover.backend.mapping.MappingControlService mappingControl;
    private final NavigationLivePoseService livePoses;
    private final com.mediassistrover.backend.ai.VisionDetectionIngestionService visionDetections;
    public void receive(String topic,byte[] bytes,int qos,boolean retained) {
        try {route(topic,bytes,qos,retained);}
        catch (BusinessRuleException | IllegalArgumentException ex) {log.warn("MQTT inbound rejected on {}: {}",topic,ex.getMessage());}
        catch (RuntimeException ex) {log.error("MQTT inbound failed on {}; no application ACK, durable work will retry",topic,ex);}
    }
    public void route(String topic,byte[] bytes,int qos,boolean retained) {
        MqttTopics.Topic lane=MqttTopics.parse(topic);
        if (!properties.manages(lane.robotId())) throw new BusinessRuleException("MQTT robot is not enrolled");
        ObjectNode raw=wire.parse(bytes);
        if("vision/detections".equals(lane.lane())) {
            if(retained||qos!=1)throw new BusinessRuleException("Vision detections require QoS 1 and retain=false");
            var result=visionDetections.ingest(lane.robotId(),raw);
            log.info("Vision detection accepted: robot={} detection={} incident={} duplicate={}",lane.robotId(),
                    result.detectionEventId(),result.incidentId(),result.duplicate());
            return;
        }
        boolean live=Set.of("mapping/control-acks","camera/preview","navigation/pose").contains(lane.lane());
        if(live)contracts.live(raw);else contracts.inbound(raw);
        if (!lane.robotId().equals(UUID.fromString(raw.path("robotId").asText()))) throw new BusinessRuleException("MQTT robotId differs from topic identity");
        String type=raw.path("messageType").asText();
        if(live) {
            if(retained || qos!=0)throw new BusinessRuleException("Live mapping lanes require QoS 0 and retain=false");
            if("mapping/control-acks".equals(lane.lane()) && "MAPPING_CONTROL_ACK".equals(type))mappingControl.acknowledge(lane.robotId(),raw);
            else if("camera/preview".equals(lane.lane()) && "CAMERA_PREVIEW".equals(type))sessions.camera(lane.robotId(),UUID.fromString(raw.path("transportSessionId").asText()),raw.path("payload"));
            else if("navigation/pose".equals(lane.lane()) && "NAVIGATION_POSE".equals(type))livePoses.accept(raw);
            else throw new BusinessRuleException("Wrong live mapping messageType");
            return;
        }
        if ("presence".equals(lane.lane())) {
            if(qos!=1)throw new BusinessRuleException("Presence requires QoS 1");
            if (raw.has("messageType")) throw new BusinessRuleException("Presence has no event envelope");
            if (!raw.path("online").asBoolean()) sessions.offline(lane.robotId(),UUID.fromString(raw.path("transportSessionId").asText()));return;
        }
        if ("profile-acks".equals(lane.lane())) {
            if (retained || qos!=1 || !"NAV_PROFILE_ACK".equals(type)) throw new BusinessRuleException("Invalid navigation profile ACK lane");
            navigationProfiles.confirmMqtt(lane.robotId(),raw);return;
        }
        if (retained || qos!=("events".equals(lane.lane())?1:0)) throw new BusinessRuleException("MQTT topic QoS/retain contract mismatch");
        if ("mapping/preview".equals(lane.lane())) {
            if (!"MAP_PREVIEW".equals(type)) throw new BusinessRuleException("Invalid preview messageType");
            sessions.preview(lane.robotId(),UUID.fromString(raw.path("transportSessionId").asText()),raw.path("payload"));return;
        }
        MqttWire.Envelope e=wire.envelope(raw);
        if ("heartbeat".equals(lane.lane())) {if (!"HEARTBEAT".equals(type)) throw new BusinessRuleException("Heartbeat topic requires HEARTBEAT");heartbeat(e);return;}
        if (Set.of("HEARTBEAT","MAP_PREVIEW").contains(type)) throw new BusinessRuleException("Transient message on durable events topic");
        if ("PROTOCOL_ERROR".equals(type)) {log.warn("Pi protocol error for robot {}: {}",e.robotId(),e.payload().path("errorCode").asText());return;}
        long timingStart=System.nanoTime();
        MqttInboxProcessor.CommittedReceipt committed=null;
        if(Set.of("COMMAND_ACK","STEP_STARTED").contains(type)) {
            try {
                for(int attempt=0;;attempt++) {
                    try {committed=processor.receiveAndProcess(raw,e);break;}
                    catch(org.springframework.dao.ConcurrencyFailureException ex) {if(attempt>=2)throw ex;}
                }
            }
            catch(BusinessRuleException ex) {
                // The combined transaction rolled back. Persist/reject through
                // the existing durable path, with the same identity and checks.
            }
        }
        var receipt=committed==null?store.receive(raw,e):committed.receipt();
        long storedAt=System.nanoTime();
        if (receipt.conflict()) {
            processor.protocolConflict(e.robotId());
            var ack="HELLO".equals(type)?wire.helloAck(e.robotId(),e.transportSessionId(),false):wire.eventAck(e.robotId(),e.transportSessionId(),e.messageId(),false);
            publisher.rejection(e.robotId(),ack);throw new BusinessRuleException("MQTT identity reused with conflicting business content");
        }
        MqttPublicationPlans.Publication acknowledgement;
        try {acknowledgement=committed==null?processor.process(receipt.messageId()):committed.acknowledgement();}
        catch (BusinessRuleException ex) {acknowledgement=processor.reject(receipt.messageId(),ex.getMessage());}
        long processedAt=System.nanoTime();
        // The processing proxy has committed; avoid another DB read of the same receipt/session.
        if(acknowledgement!=null)publisher.committedAcknowledgement(acknowledgement);
        else publisher.acknowledgement(receipt.messageId(),true);
        long acknowledgedAt=System.nanoTime();
        if(acknowledgedAt-timingStart>1_000_000_000L)
            log.warn("MQTT durable timing: type={} robot={} message={} storeMs={} processMs={} ackScheduleMs={}",
                type+(committed==null?"":"_COMMITTED"),e.robotId(),e.messageId(),(storedAt-timingStart)/1_000_000,
                (processedAt-storedAt)/1_000_000,(acknowledgedAt-processedAt)/1_000_000);
    }
    private void heartbeat(MqttWire.Envelope envelope) {
        for(int attempt=0;;attempt++) {
            try {sessions.heartbeat(envelope);return;}
            catch(org.springframework.dao.ConcurrencyFailureException ex) {
                if(attempt>=2)throw ex;
                // The proxied heartbeat transaction has already rolled back. Retry in a
                // new transaction, preserving the same message/session/sequence identity.
                // This never retries a physical command or a business-rule rejection.
                log.warn("MQTT heartbeat lock conflict for {}; retry {}/2",envelope.robotId(),attempt+1);
            }
        }
    }
}
