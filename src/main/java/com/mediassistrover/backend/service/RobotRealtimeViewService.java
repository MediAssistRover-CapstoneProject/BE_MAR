package com.mediassistrover.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Short, shared display cache only. Dispatch and physical authorization never use this read model. */
@Service @RequiredArgsConstructor
public class RobotRealtimeViewService {
    private final RobotRealtimeSnapshotService snapshots;
    private static final long TTL_NANOS=1_000_000_000L;
    private static final class Slot {
        final AtomicLong revision=new AtomicLong();
        volatile long loadedRevision=-1,loadedAt;
        volatile RobotRealtimeSnapshotService.Snapshot value;
    }
    private final ConcurrentHashMap<UUID,Slot> slots=new ConcurrentHashMap<>();
    public void invalidate(UUID robotId) {
        var slot=slots.get(robotId);if(slot!=null)slot.revision.incrementAndGet();
    }
    public RobotRealtimeSnapshotService.Snapshot snapshot(UUID robotId) {
        if(slots.size()>=256&&!slots.containsKey(robotId))return snapshots.snapshot(robotId);
        var slot=slots.computeIfAbsent(robotId,id->new Slot());
        synchronized(slot) {
            long revision=slot.revision.get();
            if(slot.value!=null&&slot.loadedRevision==revision&&System.nanoTime()-slot.loadedAt<TTL_NANOS)return slot.value;
            var value=snapshots.snapshot(robotId);
            slot.value=value;slot.loadedRevision=revision;slot.loadedAt=System.nanoTime();
            return value;
        }
    }
}
