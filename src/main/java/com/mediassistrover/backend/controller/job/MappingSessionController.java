package com.mediassistrover.backend.controller.job;
import com.mediassistrover.backend.mapping.MappingPreviewService;
import com.mediassistrover.backend.repository.MappingSessionRepository;
import com.mediassistrover.backend.service.JobUiSnapshotService;
import com.mediassistrover.backend.security.AccountPrincipal;
import com.mediassistrover.backend.exception.ResourceNotFoundException;
import com.fasterxml.jackson.databind.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController @RequestMapping("/api/v1/jobs") @RequiredArgsConstructor @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
public class MappingSessionController {
    private final JobUiSnapshotService snapshots;
    private final MappingSessionRepository sessions;
    private final MappingPreviewService previews;
    private final ObjectMapper json;
    @GetMapping("/{jobId}/mapping-session") public JsonNode detail(@PathVariable UUID jobId,@AuthenticationPrincipal AccountPrincipal actor) {
        snapshots.snapshot(jobId,actor);var session=sessions.findByJobId(jobId).orElseThrow(()->new ResourceNotFoundException("Job has no mapping session"));
        var result=json.createObjectNode().put("mapping_session_id",session.getSessionId().toString()).put("artifact_id",session.getArtifactId().toString())
                .put("state",session.getState()).put("potentially_open",session.isPotentiallyOpen());result.set("preview",previews.latest(session.getSessionId()));return result;
    }
}
