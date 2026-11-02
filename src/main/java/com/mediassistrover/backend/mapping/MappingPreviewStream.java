package com.mediassistrover.backend.mapping;

import com.mediassistrover.backend.repository.MappingSessionRepository;
import com.mediassistrover.backend.security.AccountPrincipal;
import com.mediassistrover.backend.service.JobUiSnapshotService;
import com.mediassistrover.backend.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** Bounded live display stream. Does not authorize or issue physical robot commands. */
@Service @RequiredArgsConstructor
public class MappingPreviewStream {
    private final MappingPreviewService previews;
    private final MappingSessionRepository sessions;
    private final JobUiSnapshotService snapshots;
    private final Clock clock;
    private static final class Subscriber {
        final UUID job,robot,session;
        final AccountPrincipal actor;final SseEmitter emitter;
        long revision=-1;Instant validatedAt;
        Subscriber(UUID j,UUID r,UUID s,AccountPrincipal a,SseEmitter e,Instant at){job=j;robot=r;session=s;actor=a;emitter=e;validatedAt=at;}
    }
    private final ConcurrentMap<UUID,CopyOnWriteArrayList<Subscriber>> viewers=new ConcurrentHashMap<>();
    private final Set<UUID> queued=ConcurrentHashMap.newKeySet();
    private final ExecutorService executor=new ThreadPoolExecutor(2,2,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(128),
            task->{var t=new Thread(task,"mar-map-ui");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());

    public SseEmitter subscribe(UUID job,AccountPrincipal actor) {
        snapshots.snapshot(job,actor);
        var session=sessions.findByJobId(job).orElseThrow(()->new ResourceNotFoundException("Job has no mapping session"));
        if(!session.isPotentiallyOpen())throw new BusinessRuleException("Mapping session is closed; use the saved map API");
        Instant now=Instant.now(clock);long ttl=actor.tokenExpiresAt()==null?60_000:Math.min(60_000,Duration.between(now,actor.tokenExpiresAt()).toMillis());
        var emitter=new SseEmitter(Math.max(1,ttl));var sub=new Subscriber(job,session.getRobotId(),session.getSessionId(),actor,emitter,now);
        viewers.compute(job,(id,list)->{if(list==null)list=new CopyOnWriteArrayList<>();if(list.size()>=16)throw new BusinessRuleException("Too many mapping viewers");list.add(sub);return list;});
        Runnable remove=()->remove(sub);emitter.onCompletion(remove);emitter.onTimeout(remove);emitter.onError(error->remove.run());enqueue(job);return emitter;
    }
    @EventListener public void changed(MappingPreviewService.Changed event){enqueue(event.jobId());}
    @Scheduled(fixedDelay=3000) public void keepalive(){viewers.keySet().forEach(this::enqueue);}
    private void enqueue(UUID job) {
        if(!viewers.containsKey(job)||!queued.add(job))return;
        try {executor.execute(()->{try{for(var sub:viewers.getOrDefault(job,new CopyOnWriteArrayList<>()))send(sub);}finally{queued.remove(job);}});}
        catch(RejectedExecutionException ignored){queued.remove(job);}
    }
    private void send(Subscriber sub){synchronized(sub){try{
        Instant now=Instant.now(clock);
        if(sub.actor.tokenExpiresAt()!=null&&!sub.actor.tokenExpiresAt().isAfter(now))throw new IllegalStateException("Expired viewer");
        // Recheck account/ownership periodically, not once per PNG frame.
        if(!sub.validatedAt.isAfter(now.minusSeconds(15))){snapshots.snapshot(sub.job,sub.actor);sub.validatedAt=now;}
        var frame=previews.streamImage(sub.robot,sub.session);
        if(frame==null){sub.emitter.send(SseEmitter.event().name("mapping-wait").data(Map.of("mapping_session_id",sub.session)));return;}
        if(frame.revision()==sub.revision)return;
        sub.emitter.send(SseEmitter.event().name("mapping-frame").id(Long.toString(frame.revision()))
                .data(Map.of("preview",frame.metadata(),"png_base64",Base64.getEncoder().encodeToString(frame.bytes()))));
        sub.revision=frame.revision();
    }catch(Exception error){remove(sub);sub.emitter.complete();}}}
    private void remove(Subscriber sub){viewers.computeIfPresent(sub.job,(id,list)->{list.remove(sub);return list.isEmpty()?null:list;});}
    @jakarta.annotation.PreDestroy public void destroy(){executor.shutdownNow();viewers.values().forEach(list->list.forEach(sub->sub.emitter.complete()));viewers.clear();}
}
