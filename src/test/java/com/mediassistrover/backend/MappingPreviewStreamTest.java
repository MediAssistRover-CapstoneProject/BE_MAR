package com.mediassistrover.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediassistrover.backend.entity.MappingSession;
import com.mediassistrover.backend.mapping.*;
import com.mediassistrover.backend.repository.MappingSessionRepository;
import com.mediassistrover.backend.security.AccountPrincipal;
import com.mediassistrover.backend.service.JobUiSnapshotService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MappingPreviewStreamTest {
    final MappingPreviewService previews=mock(MappingPreviewService.class);
    final MappingSessionRepository sessions=mock(MappingSessionRepository.class);
    final JobUiSnapshotService snapshots=mock(JobUiSnapshotService.class);
    final Instant now=Instant.parse("2026-11-02T04:00:00Z");
    final MappingPreviewStream stream=new MappingPreviewStream(previews,sessions,snapshots,Clock.fixed(now,ZoneOffset.UTC));
    final UUID job=UUID.randomUUID(),robot=UUID.randomUUID(),session=UUID.randomUUID();
    final AccountPrincipal actor=mock(AccountPrincipal.class);
    @Test void streamUsesExactlyTheAuthorizedRobotSessionWithoutQueryingDbForEachFrame() {
        when(sessions.findByJobId(job)).thenReturn(Optional.of(MappingSession.builder().jobId(job).robotId(robot).sessionId(session).potentiallyOpen(true).build()));
        var meta=new ObjectMapper().createObjectNode().put("revision",1).put("mapping_session_id",session.toString());
        when(previews.streamImage(robot,session)).thenReturn(new MappingPreviewService.Picture(session,now,1,new byte[]{1},meta));
        try {
            stream.subscribe(job,actor);
            verify(previews,timeout(1500).atLeastOnce()).streamImage(robot,session);
            stream.changed(new MappingPreviewService.Changed(job,meta));
            verify(snapshots,times(1)).snapshot(job,actor);
            verify(sessions,times(1)).findByJobId(job);
        } finally {stream.destroy();}
    }
    @Test void failedAuthorizationCannotReadAnyFrame() {
        doThrow(new AccessDeniedException("revoked")).when(snapshots).snapshot(job,actor);
        try {
            assertThatThrownBy(()->stream.subscribe(job,actor)).isInstanceOf(AccessDeniedException.class);
            verifyNoInteractions(previews,sessions);
        } finally {stream.destroy();}
    }
    @Test void closedJobDoesNotExposeRetainedPreviewAsLive() {
        when(sessions.findByJobId(job)).thenReturn(Optional.of(MappingSession.builder().jobId(job).robotId(robot).sessionId(session).potentiallyOpen(false).build()));
        try {assertThatThrownBy(()->stream.subscribe(job,actor)).hasMessageContaining("closed");verifyNoInteractions(previews);}
        finally {stream.destroy();}
    }
}
