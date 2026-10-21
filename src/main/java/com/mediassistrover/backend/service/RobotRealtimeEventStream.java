package com.mediassistrover.backend.service;

import com.mediassistrover.backend.exception.BusinessRuleException;
import com.mediassistrover.backend.security.AccountPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;

@Service @RequiredArgsConstructor @Slf4j
public class RobotRealtimeEventStream {
    public record Changed(UUID robotId) { }
    private record Subscription(UUID subscriptionId,AccountPrincipal actor,SseEmitter emitter,
                                java.util.concurrent.atomic.AtomicLong sequence) { }
    private final RobotRealtimeSnapshotService snapshots;
    private final Clock clock;
    private final ConcurrentMap<UUID,CopyOnWriteArrayList<Subscription>> subscribers=new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID robotId,AccountPrincipal actor) {
        snapshots.snapshot(robotId);
        long ttl=actor.tokenExpiresAt()==null?300_000:Math.min(300_000,Duration.between(Instant.now(clock),actor.tokenExpiresAt()).toMillis());
        SseEmitter emitter=new SseEmitter(Math.max(1,ttl));Subscription subscription=new Subscription(
                UUID.randomUUID(),actor,emitter,new java.util.concurrent.atomic.AtomicLong());
        subscribers.compute(robotId,(id,current)->{var list=current==null?new CopyOnWriteArrayList<Subscription>():current;
            if(list.size()>=64)throw new BusinessRuleException("Too many robot realtime subscriptions");list.add(subscription);return list;});
        Runnable remove=()->remove(robotId,subscription,"complete",null);
        emitter.onCompletion(remove);emitter.onTimeout(()->remove(robotId,subscription,"timeout",null));
        emitter.onError(error->remove(robotId,subscription,"error",error));
        log.info("robot_sse subscribed robotId={} subscriptionId={} ttlMs={}",robotId,subscription.subscriptionId(),ttl);
        send(robotId,subscription);return emitter;
    }
    @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
    public void changed(Changed event){for(Subscription subscription:subscribers.getOrDefault(event.robotId(),new CopyOnWriteArrayList<>()))send(event.robotId(),subscription);}
    /** Periodic state event doubles as SSE keepalive and exposes heartbeat/pose freshness transitions. */
    public void heartbeat(){subscribers.forEach((robotId,list)->list.forEach(subscription->send(robotId,subscription)));}
    private void send(UUID robotId,Subscription subscription){synchronized(subscription){try{
        long sequence=subscription.sequence().incrementAndGet();
        subscription.emitter().send(SseEmitter.event().id(subscription.subscriptionId()+":"+sequence)
                .name("robot-state").reconnectTime(1_000).data(snapshots.snapshot(robotId)));
        log.debug("robot_sse event robotId={} subscriptionId={} sequence={}",robotId,subscription.subscriptionId(),sequence);
    }catch(Exception ex){remove(robotId,subscription,"send_failed",ex);try{subscription.emitter().complete();}catch(Exception ignored){/* response is already streaming */}}}}
    private void remove(UUID robotId,Subscription subscription,String reason,Throwable error){
        subscribers.computeIfPresent(robotId,(id,list)->{list.remove(subscription);return list.isEmpty()?null:list;});
        if(error==null)log.info("robot_sse removed robotId={} subscriptionId={} reason={} events={}",robotId,subscription.subscriptionId(),reason,subscription.sequence().get());
        else log.info("robot_sse removed robotId={} subscriptionId={} reason={} events={} cause={}",robotId,subscription.subscriptionId(),reason,subscription.sequence().get(),error.getClass().getSimpleName());
    }
}
