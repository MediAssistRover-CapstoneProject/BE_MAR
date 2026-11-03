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

class JobUiEventStreamTest {
    @Test void aSlowJobReadNeverBlocksMqttCommitAndChangesDuringTheReadAreCoalesced() throws Exception {
        var snapshots=mock(JobUiSnapshotService.class);var job=UUID.randomUUID();
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        var refreshed=new CountDownLatch(1);var calls=new AtomicInteger();
        when(snapshots.snapshot(eq(job),any())).thenAnswer(call->{
            int n=calls.incrementAndGet();
            if(n==2){entered.countDown();release.await(5,TimeUnit.SECONDS);}
            if(n>=3)refreshed.countDown();
            return null;
        });
        var stream=new JobUiEventStream(snapshots,Clock.systemUTC());
        var producer=Executors.newSingleThreadExecutor();
        try {
            stream.subscribe(job,mock(AccountPrincipal.class));
            assertThat(entered.await(2,TimeUnit.SECONDS)).isTrue();
            producer.submit(()->{for(int i=0;i<100;i++)stream.changed(new RuntimeChanged(job));}).get(1,TimeUnit.SECONDS);
            assertThat(calls.get()).isEqualTo(2);
            release.countDown();assertThat(refreshed.await(2,TimeUnit.SECONDS)).isTrue();
        } finally {release.countDown();stream.destroy();producer.shutdownNow();}
    }
}
