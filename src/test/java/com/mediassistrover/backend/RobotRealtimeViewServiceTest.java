package com.mediassistrover.backend;

import com.mediassistrover.backend.service.*;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RobotRealtimeViewServiceTest {
    @Test void viewersShareOneSnapshotUntilACommittedStateInvalidatesIt() {
        var source=mock(RobotRealtimeSnapshotService.class);
        var view=new RobotRealtimeViewService(source);var id=UUID.randomUUID();
        var first=mock(RobotRealtimeSnapshotService.Snapshot.class);
        var second=mock(RobotRealtimeSnapshotService.Snapshot.class);
        when(source.snapshot(id)).thenReturn(first,second);
        assertThat(view.snapshot(id)).isSameAs(first);
        assertThat(view.snapshot(id)).isSameAs(first);
        verify(source).snapshot(id);
        view.invalidate(id);
        assertThat(view.snapshot(id)).isSameAs(second);
        verify(source,times(2)).snapshot(id);
    }
    @Test void invalidationDuringAnInFlightReadCannotBeLost() {
        var source=mock(RobotRealtimeSnapshotService.class);
        var view=new RobotRealtimeViewService(source);var id=UUID.randomUUID();
        var value=mock(RobotRealtimeSnapshotService.Snapshot.class);
        when(source.snapshot(id)).thenAnswer(call->{view.invalidate(id);return value;});
        view.snapshot(id);view.snapshot(id);
        verify(source,times(2)).snapshot(id);
    }
    @Test void aReadFailureNeverReturnsAnOldSnapshotAsNewlyObserved() {
        var source=mock(RobotRealtimeSnapshotService.class);
        var view=new RobotRealtimeViewService(source);var id=UUID.randomUUID();
        when(source.snapshot(id)).thenReturn(mock(RobotRealtimeSnapshotService.Snapshot.class))
                .thenThrow(new IllegalStateException("DB unavailable"));
        view.snapshot(id);view.invalidate(id);
        assertThatThrownBy(()->view.snapshot(id)).hasMessage("DB unavailable");
    }
}
