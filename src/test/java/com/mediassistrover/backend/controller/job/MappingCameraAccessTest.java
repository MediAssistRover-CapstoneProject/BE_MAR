package com.mediassistrover.backend.controller.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.Role;
import com.mediassistrover.backend.mapping.*;
import com.mediassistrover.backend.repository.*;
import com.mediassistrover.backend.security.AccountPrincipal;
import com.mediassistrover.backend.service.JobUiSnapshotService;
import com.mediassistrover.backend.validation.StaffOperationalScopePolicy;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MappingCameraAccessTest {
    final JobUiSnapshotService snapshots = mock(JobUiSnapshotService.class);
    final StaffOperationalScopePolicy scope = mock(StaffOperationalScopePolicy.class);
    final MappingSessionRepository sessions = mock(MappingSessionRepository.class);
    final MappingPreviewService previews = mock(MappingPreviewService.class);
    final MappingSessionController controller = new MappingSessionController(
            snapshots, scope, sessions, previews, mock(MappingControlService.class),
            new ObjectMapper(), mock(RobotMqttSessionRepository.class));

    @Test void adminAndStaffCanReadCameraWithoutMappingJobAssignment() {
        for (Role role : List.of(Role.ADMIN, Role.STAFF)) {
            var actor = AccountPrincipal.fromAccount(
                    Account.builder().accountId(UUID.randomUUID()).role(role).build());
            var jobId = UUID.randomUUID();
            var mapping = new MappingSession();
            mapping.setSessionId(UUID.randomUUID());
            when(sessions.findByJobId(jobId)).thenReturn(Optional.of(mapping));
            assertEquals(204, controller.camera(jobId, actor).getStatusCode().value());
            verify(scope).requireRobotRead(actor);
            verify(previews).cameraImage(mapping.getSessionId());
        }
        verifyNoInteractions(snapshots);
    }

    @Test void invalidSessionCannotReadCamera() {
        doThrow(new AccessDeniedException("Session unavailable")).when(scope).requireRobotRead(null);
        assertThrows(AccessDeniedException.class, () -> controller.camera(UUID.randomUUID(), null));
        verifyNoInteractions(sessions, previews);
    }
}
