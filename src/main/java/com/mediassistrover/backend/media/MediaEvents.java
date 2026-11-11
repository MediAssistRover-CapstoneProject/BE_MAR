package com.mediassistrover.backend.media;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.UUID;
import java.util.concurrent.*;

/** Notifications carry descriptors, never room credentials or media bytes. */
@Component
public class MediaEvents {
    private final ConcurrentMap<UUID,CopyOnWriteArrayList<SseEmitter>> robots=new ConcurrentHashMap<>();
    public SseEmitter subscribe(UUID robot,long timeout,Object initial) {
        var emitter=new SseEmitter(Math.max(1000,Math.min(55000,timeout)));
        robots.compute(robot,(id,list)->{
            var subscriptions=list==null?new CopyOnWriteArrayList<SseEmitter>():list;
            subscriptions.add(emitter);
            return subscriptions;
        });
        Runnable remove=()->remove(robot,emitter);
        emitter.onCompletion(remove);emitter.onTimeout(()->{remove.run();emitter.complete();});
        emitter.onError(e->remove.run());
        send(robot,emitter,initial);return emitter;
    }
    public boolean isListening(UUID robot) {
        var list=robots.get(robot);
        return list!=null&&!list.isEmpty();
    }
    void unsubscribe(UUID robot,SseEmitter emitter) {
        remove(robot,emitter);
        emitter.complete();
    }
    public void publish(UUID robot,Object event) {
        var list=robots.get(robot);if(list!=null)for(var emitter:list)send(robot,emitter,event);
    }
    public void heartbeat() {
        robots.forEach((id,list)->list.forEach(emitter->{
            try {emitter.send(SseEmitter.event().comment("keepalive"));}
            catch(Exception e){remove(id,emitter);emitter.complete();}
        }));
    }
    private void send(UUID robot,SseEmitter emitter,Object event) {
        try {emitter.send(SseEmitter.event().name("media").data(event));}
        catch(Exception e){remove(robot,emitter);emitter.complete();}
    }
    private void remove(UUID robot,SseEmitter emitter) {
        robots.computeIfPresent(robot,(id,list)->{
            list.remove(emitter);
            return list.isEmpty()?null:list;
        });
    }
}
