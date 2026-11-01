package com.mediassistrover.backend;

import com.mediassistrover.backend.security.AccountPrincipal;
import com.mediassistrover.backend.service.*;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RobotRealtimeEventStreamTest {
    @Test void aSlowViewerReadCannotBlockHeartbeatTransactionCompletionOrQueueUnlimitedCopies() throws Exception {
        var views=mock(RobotRealtimeViewService.class);var robot=UUID.randomUUID();
        var value=mock(RobotRealtimeSnapshotService.Snapshot.class);
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var calls=new AtomicInteger();
        when(views.snapshot(robot)).thenAnswer(call->{
            if(calls.incrementAndGet()>1){entered.countDown();release.await(5,TimeUnit.SECONDS);}
            return value;
        });
        var stream=new RobotRealtimeEventStream(views,Clock.systemUTC());
        var worker=Executors.newSingleThreadExecutor();
        try {
            stream.subscribe(robot,mock(AccountPrincipal.class));
            assertThat(entered.await(2,TimeUnit.SECONDS)).isTrue();
            worker.submit(()->{for(int i=0;i<100;i++)stream.changed(new RobotRealtimeEventStream.Changed(robot));})
                    .get(1,TimeUnit.SECONDS);
            assertThat(calls.get()).isEqualTo(2); // initial authorization + ONE coalesced async read
            verify(views,times(100)).invalidate(robot);
        } finally {release.countDown();stream.destroy();worker.shutdownNow();}
    }
}
