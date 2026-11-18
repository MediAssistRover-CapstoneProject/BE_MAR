package com.mediassistrover.backend.mqtt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Live display and time-aligned observation evidence; never authorizes motion or renews a heartbeat. */
@Service @RequiredArgsConstructor
public class NavigationLivePoseService {
    private static final int HISTORY_LIMIT=64;
    private static final Duration HISTORY_WINDOW=Duration.ofSeconds(15);
    public record Authorization(UUID robotId,UUID transport,UUID producer,UUID connection,UUID session,UUID map,
                                String hash,long revision,Instant until) { }
    public record Closed(UUID robotId) { }
    public record Pose(UUID robotId,ObjectNode value) { }
    private record Sample(Instant capturedAt,ObjectNode value) { }
    private static final class Slot {
        final Authorization authorization;long sequence=-1;ObjectNode latest;
        final Deque<Sample> history=new ArrayDeque<>();
        Slot(Authorization authorization){this.authorization=authorization;}
    }
    private final Clock clock;
    private final MqttConnectionState connection;
    private final ApplicationEventPublisher events;
    private final ConcurrentHashMap<UUID,Slot> slots=new ConcurrentHashMap<>();
    @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
    public void authorized(Authorization value){
        if(slots.size()>=256&&!slots.containsKey(value.robotId()))return;
        slots.compute(value.robotId(),(id,old)->{
            if(old!=null&&sameIdentity(old.authorization,value)){
                var next=new Slot(value);
                synchronized(old){next.sequence=old.sequence;next.latest=old.latest;next.history.addAll(old.history);}
                prune(next,Instant.now(clock));return next;
            }
            return new Slot(value);
        });
    }
    private boolean sameIdentity(Authorization a,Authorization b){return a.transport().equals(b.transport())
        &&a.producer().equals(b.producer())&&a.connection().equals(b.connection())&&a.session().equals(b.session())
        &&a.map().equals(b.map())&&a.hash().equals(b.hash())&&a.revision()==b.revision();}
    @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
    public void closed(Closed value){slots.remove(value.robotId());}
    public ObjectNode latest(UUID robot){
        var slot=slots.get(robot);if(slot==null)return null;
        synchronized(slot){
            if(!slot.authorization.until().isAfter(Instant.now(clock))
                    ||!slot.authorization.connection().equals(connection.connectionId())||slot.latest==null)return null;
            return slot.latest.deepCopy();
        }
    }
    public ObjectNode nearest(UUID robot,Instant capturedAt,Duration maxSkew){
        if(capturedAt==null||maxSkew==null||maxSkew.isNegative())
            throw new IllegalArgumentException("Observation time and non-negative maximum skew are required");
        var slot=slots.get(robot);if(slot==null)return null;
        synchronized(slot){
            Instant now=Instant.now(clock);
            if(slots.get(robot)!=slot||!slot.authorization.until().isAfter(now)
                    ||!slot.authorization.connection().equals(connection.connectionId()))return null;
            prune(slot,now);
            Sample nearest=null;Duration nearestSkew=null;
            for(var sample:slot.history){
                Duration skew=Duration.between(capturedAt,sample.capturedAt()).abs();
                if(skew.compareTo(maxSkew)<=0&&(nearestSkew==null||skew.compareTo(nearestSkew)<0)){
                    nearest=sample;nearestSkew=skew;
                }
            }
            return nearest==null?null:nearest.value().deepCopy();
        }
    }
    private void prune(Slot slot,Instant now){
        Instant oldest=now.minus(HISTORY_WINDOW);
        slot.history.removeIf(sample->sample.capturedAt().isBefore(oldest));
        while(slot.history.size()>HISTORY_LIMIT)slot.history.removeFirst();
    }
    public void accept(ObjectNode value){
        UUID robot=UUID.fromString(value.path("robotId").asText());var slot=slots.get(robot);if(slot==null)return;
        synchronized(slot){
            var a=slot.authorization;Instant now=Instant.now(clock),captured=Instant.parse(value.path("capturedAt").asText());
            if(slots.get(robot)!=slot||!a.until().isAfter(now)||!a.connection().equals(connection.connectionId())
                ||!a.transport().toString().equals(value.path("transportSessionId").asText())
                ||!a.producer().toString().equals(value.path("producerSessionId").asText())
                ||!a.session().toString().equals(value.path("navigationSessionId").asText())
                ||!a.map().toString().equals(value.path("mapId").asText())
                ||!a.hash().equals(value.path("mapAssetHash").asText())
                ||a.revision()!=value.path("initializationRevision").asLong()
                ||captured.isBefore(now.minusSeconds(2))||captured.isAfter(now.plusSeconds(1))
                ||value.path("sequenceNo").asLong()<=slot.sequence)return;
            slot.sequence=value.path("sequenceNo").asLong();
            ObjectNode sample=value.deepCopy();sample.put("receivedAt",now.toString());
            slot.latest=sample;
            slot.history.addLast(new Sample(captured,sample));prune(slot,now);
            events.publishEvent(new Pose(robot,sample));
        }
    }
}
