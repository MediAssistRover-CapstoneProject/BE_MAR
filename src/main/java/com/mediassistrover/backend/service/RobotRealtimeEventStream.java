package com.mediassistrover.backend.service;

import com.mediassistrover.backend.exception.BusinessRuleException;
import com.mediassistrover.backend.security.AccountPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;

@Service @RequiredArgsConstructor
public class RobotRealtimeEventStream {
    public record Changed(UUID robotId) { }
    private record Subscription(AccountPrincipal actor,SseEmitter emitter) { }
    private final RobotRealtimeViewService snapshots;
    private final Clock clock;
    private final ConcurrentMap<UUID,CopyOnWriteArrayList<Subscription>> subscribers=new ConcurrentHashMap<>();
    private final java.util.Set<UUID> queued=ConcurrentHashMap.newKeySet();
    private final ExecutorService updates=new ThreadPoolExecutor(2,2,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(256),
            task->{var thread=new Thread(task,"mar-robot-ui");thread.setDaemon(true);return thread;},new ThreadPoolExecutor.AbortPolicy());

    public SseEmitter subscribe(UUID robotId,AccountPrincipal actor) {
        snapshots.snapshot(robotId);
        long ttl=actor.tokenExpiresAt()==null?300_000:Math.min(300_000,Duration.between(Instant.now(clock),actor.tokenExpiresAt()).toMillis());
        SseEmitter emitter=new SseEmitter(Math.max(1,ttl));Subscription subscription=new Subscription(actor,emitter);
        subscribers.compute(robotId,(id,current)->{var list=current==null?new CopyOnWriteArrayList<Subscription>():current;
            if(list.size()>=64)throw new BusinessRuleException("Too many robot realtime subscriptions");list.add(subscription);return list;});
        Runnable remove=()->subscribers.computeIfPresent(robotId,(id,list)->{list.remove(subscription);return list.isEmpty()?null:list;});
        emitter.onCompletion(remove);emitter.onTimeout(remove);emitter.onError(error->remove.run());enqueue(robotId);return emitter;
    }
    @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
    public void changed(Changed event){snapshots.invalidate(event.robotId());enqueue(event.robotId());}
    /** Periodic state event doubles as SSE keepalive and exposes heartbeat/pose freshness transitions. */
    public void heartbeat(){subscribers.keySet().forEach(this::enqueue);}
    private void enqueue(UUID robotId) {
        if(!subscribers.containsKey(robotId)||!queued.add(robotId))return;
        try {updates.execute(()->{
            try {
                // One snapshot per robot, independent of viewer count; never query DB or
                // write to a browser on the MQTT/transaction completion thread.
                var value=snapshots.snapshot(robotId);
                for(var subscription:subscribers.getOrDefault(robotId,new CopyOnWriteArrayList<>()))send(robotId,subscription,value);
            } catch(RuntimeException ignored) {
                // Next committed state/keepalive retries. No robot command is affected.
            } finally {queued.remove(robotId);}
        });} catch(RejectedExecutionException ignored) {queued.remove(robotId);}
    }
    private void send(UUID robotId,Subscription subscription,RobotRealtimeSnapshotService.Snapshot value){synchronized(subscription){try{
        if(subscription.actor().tokenExpiresAt()!=null&&!subscription.actor().tokenExpiresAt().isAfter(Instant.now(clock)))throw new IllegalStateException("Expired subscription");
        subscription.emitter().send(SseEmitter.event().name("robot-state").data(value));
    }catch(Exception ex){subscribers.computeIfPresent(robotId,(id,list)->{list.remove(subscription);return list.isEmpty()?null:list;});subscription.emitter().complete();}}}
    @jakarta.annotation.PreDestroy public void destroy(){updates.shutdownNow();subscribers.values().forEach(list->list.forEach(s->s.emitter().complete()));subscribers.clear();}
}
