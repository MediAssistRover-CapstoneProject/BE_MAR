package com.mediassistrover.backend.service;

import com.mediassistrover.backend.security.AccountPrincipal;
import com.mediassistrover.backend.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/** SSE is a live hint; persisted REST Job detail is the reconnect source of truth. */
@Service @RequiredArgsConstructor
public class JobUiEventStream {
    private final JobUiSnapshotService snapshots;
    private final Clock clock;
    private final ConcurrentMap<UUID,CopyOnWriteArrayList<Subscription>> subscribers=new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID,Pending> pending=new ConcurrentHashMap<>();
    private static final class Pending {
        final AtomicLong revision=new AtomicLong();
        volatile boolean full;
        volatile com.mediassistrover.backend.mapping.MappingPreviewService.Changed preview;
    }
    private final ExecutorService updates=new ThreadPoolExecutor(2,2,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(256),
            task->{var thread=new Thread(task,"mar-job-ui");thread.setDaemon(true);return thread;},new ThreadPoolExecutor.AbortPolicy());
    private record Subscription(AccountPrincipal actor,SseEmitter emitter) { }

    public SseEmitter subscribe(UUID jobId,AccountPrincipal actor) {
        snapshots.snapshot(jobId,actor);
        long ttl=actor.tokenExpiresAt()==null?300_000:Math.min(300_000,Duration.between(Instant.now(clock),actor.tokenExpiresAt()).toMillis());
        SseEmitter emitter=new SseEmitter(Math.max(1,ttl));
        Subscription subscription=new Subscription(actor,emitter);
        subscribers.compute(jobId,(id,current)->{
            var list=current==null?new CopyOnWriteArrayList<Subscription>():current;
            if (list.size()>=64) throw new BusinessRuleException("Too many Job event subscriptions");
            list.add(subscription); return list;
        });
        Runnable remove=()->subscribers.computeIfPresent(jobId,(id,list)->{list.remove(subscription);return list.isEmpty()?null:list;});
        emitter.onCompletion(remove); emitter.onTimeout(remove); emitter.onError(error->remove.run());
        enqueue(jobId,null);
        return emitter;
    }
    @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
    public void changed(RuntimeChanged event) {
        enqueue(event.jobId(),null);
    }
    @org.springframework.context.event.EventListener
    public void preview(com.mediassistrover.backend.mapping.MappingPreviewService.Changed event) {
        enqueue(event.jobId(),event);
    }
    public void heartbeat() {
        subscribers.keySet().forEach(id->enqueue(id,null));
    }
    /** Database reads and browser writes must never delay a committed MQTT business ACK. */
    private void enqueue(UUID jobId,com.mediassistrover.backend.mapping.MappingPreviewService.Changed preview) {
        if(!subscribers.containsKey(jobId))return;
        var created=new Pending();created.preview=preview;created.full=preview==null;
        var state=pending.putIfAbsent(jobId,created);
        if(state!=null){if(preview!=null)state.preview=preview;else state.full=true;state.revision.incrementAndGet();return;}
        try {updates.execute(()->{
            long seen=created.revision.get();
            try {
                var frame=created.preview;
                for(var subscription:subscribers.getOrDefault(jobId,new CopyOnWriteArrayList<>()))send(jobId,subscription,created.full,frame);
            } finally {
                pending.remove(jobId,created);
                // A newer change may arrive while a slow cloud read is in progress.
                if(created.revision.get()!=seen)enqueue(jobId,created.full?null:created.preview);
            }
        });}catch(RejectedExecutionException ignored){pending.remove(jobId,created);}
    }
    private void send(UUID jobId,Subscription subscription,boolean full,com.mediassistrover.backend.mapping.MappingPreviewService.Changed preview) {
        synchronized (subscription) {
            try {
                var detail=snapshots.snapshot(jobId,subscription.actor());
                if(full)subscription.emitter().send(SseEmitter.event().name("job-state").data(detail));
                if(preview!=null)subscription.emitter().send(SseEmitter.event().name("mapping-preview").data(preview.preview()));
            } catch (Exception ex) {
                subscribers.computeIfPresent(jobId,(id,list)->{list.remove(subscription);return list.isEmpty()?null:list;});
                subscription.emitter().complete();
            }
        }
    }
    @jakarta.annotation.PreDestroy public void destroy(){updates.shutdownNow();subscribers.values().forEach(list->list.forEach(s->s.emitter().complete()));subscribers.clear();}
}
