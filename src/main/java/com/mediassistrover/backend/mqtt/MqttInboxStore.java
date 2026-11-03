package com.mediassistrover.backend.mqtt;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediassistrover.backend.entity.RobotMqttInbox;
import com.mediassistrover.backend.exception.BusinessRuleException;
import com.mediassistrover.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class MqttInboxStore {
    private final RobotMqttInboxRepository inbox;
    private final RobotRepository robots;
    private final RobotMqttSessionRepository sessions;
    private final MqttProperties properties;
    private final MqttSessionRegistry registry;
    private final MqttWire wire;
    private final Clock clock;
    public record Receipt(UUID messageId,boolean duplicate,boolean conflict) { }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Receipt receive(ObjectNode raw,MqttWire.Envelope e) {
        if (!properties.manages(e.robotId())) throw new BusinessRuleException("Robot is not enrolled for MQTT");
        // INSERT checks the Robot FK. Acquire it before Session to match processing/heartbeat lock order.
        robots.findByIdForUpdate(e.robotId()).orElseThrow(()->new BusinessRuleException("Robot is not enrolled for MQTT"));
        var session="HELLO".equals(e.messageType())?sessions.lock(e.robotId()).orElse(null):registry.requireCurrent(e.robotId(),e.transportSessionId());
        return receiveLocked(raw,e,session).receipt();
    }
    public record Stored(Receipt receipt,RobotMqttInbox row) { }
    public record Staged(Stored stored,boolean freshProcessed) { }
    /** Stage the first acceptance/start receipt in its FINAL shape. This method
     * joins the caller's transaction; no accepted receipt commits unless the
     * scoped runtime transition below also succeeds. Replays use the existing
     * locked hash/producer/sequence validation, including collision recovery. */
    public Staged stageStart(ObjectNode raw,MqttWire.Envelope e,com.mediassistrover.backend.entity.RobotMqttSession session) {
        if(!java.util.Set.of("COMMAND_ACK","STEP_STARTED").contains(e.messageType()))throw new IllegalArgumentException("Not a start receipt");
        if(!properties.manages(e.robotId()))throw new BusinessRuleException("Robot is not enrolled for MQTT");
        if(!registry.owned(session)||!e.transportSessionId().equals(session.getTransportSessionId()))throw new MqttSessionUnavailableException("Stale MQTT session");
        Instant now=Instant.now(clock);
        if(e.time().isAfter(now.plusSeconds(5)))throw new BusinessRuleException("Future MQTT event");
        String hash=wire.canonicalHash(raw);var response=wire.eventAck(e.robotId(),e.transportSessionId(),e.messageId(),true);
        int inserted=inbox.insertCommittedStart(e.messageId(),e.robotId(),e.producerSessionId(),e.sequenceNo(),e.transportSessionId(),e.messageType(),hash,wire.json(raw),wire.json(response),now);
        if(inserted==0)return new Staged(receiveLocked(raw,e,session),false);
        // Previously committed journal receipts may replay after a process
        // restart, but only the HELLO owner can request a new transition.
        if(!e.producerSessionId().equals(session.getProducerSessionId()))
            throw new BusinessRuleException("Runtime producer does not match HELLO");
        var row=new RobotMqttInbox();row.setMessageId(e.messageId());row.setRobotId(e.robotId());row.setTransportSessionId(e.transportSessionId());
        row.setMessageType(e.messageType());row.setResponseJson(response);row.setAccepted(true);row.setState("PROCESSED");
        return new Staged(new Stored(new Receipt(e.messageId(),false,false),row),true);
    }
    /** Caller holds Job -> Robot -> Session in the same transaction. */
    public Stored receiveLocked(ObjectNode raw,MqttWire.Envelope e,com.mediassistrover.backend.entity.RobotMqttSession session) {
        if (!properties.manages(e.robotId())) throw new BusinessRuleException("Robot is not enrolled for MQTT");
        if (!"HELLO".equals(e.messageType()) && (!registry.owned(session) || !e.transportSessionId().equals(session.getTransportSessionId())))
            throw new MqttSessionUnavailableException("Stale MQTT session");
        if (e.time().isAfter(Instant.now(clock).plusSeconds(5))) throw new BusinessRuleException("Future MQTT event");
        String hash=wire.canonicalHash(raw);
        inbox.insert(e.messageId(),e.robotId(),e.producerSessionId(),e.sequenceNo(),e.transportSessionId(),e.messageType(),hash,wire.json(raw),Instant.now(clock));
        RobotMqttInbox row=inbox.lock(e.messageId()).orElseGet(()->inbox.findByProducerSessionIdAndSequenceNo(e.producerSessionId(),e.sequenceNo()).orElseThrow());
        if (!row.getMessageId().equals(e.messageId()) || !row.getCanonicalHash().equals(hash)) return new Stored(new Receipt(e.messageId(),false,true),row);
        boolean duplicate=!"PENDING".equals(row.getState());
        // A committed HELLO receipt alone cannot revive a session after BE/broker reconnect.
        // Re-run journal reconciliation when its replay carries a new transport/connection.
        if ("HELLO".equals(e.messageType()) && Boolean.TRUE.equals(row.getAccepted())
                && (!registry.owned(session) || !session.getTransportSessionId().equals(e.transportSessionId()))) {
            row.setState("PENDING");row.setAccepted(null);row.setResponseJson(null);row.setProcessedAt(null);duplicate=false;
        }
        row.setTransportSessionId(e.transportSessionId());row.setRawEnvelope(raw.deepCopy());row.setAckPublishedAt(null);
        if (row.getResponseJson() instanceof ObjectNode response) response.put("transportSessionId",e.transportSessionId().toString());
        return new Stored(new Receipt(e.messageId(),duplicate,false),row);
    }
}
