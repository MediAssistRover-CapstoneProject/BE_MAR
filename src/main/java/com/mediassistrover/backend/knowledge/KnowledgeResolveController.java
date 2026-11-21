package com.mediassistrover.backend.knowledge;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/** Separate service credential; never accept the robot token or a client target document. */
@RestController
public class KnowledgeResolveController {
    private final KnowledgeDocumentService service;
    private final String token;
    public KnowledgeResolveController(KnowledgeDocumentService service,
            @Value("${app.knowledge.service-token:}") String token) { this.service=service; this.token=token; }
    public record Resolve(String vectorStoreId, List<String> fileIds) { }
    @PostMapping("/api/v1/internal/knowledge/resolve")
    public List<KnowledgeDocumentService.ResolvedFile> resolve(
            @RequestHeader(value="X-Knowledge-Token",required=false) String supplied, @RequestBody Resolve request) {
        if (token.length()<32) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"KNOWLEDGE_SERVICE_NOT_CONFIGURED");
        if (supplied==null || !MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8)))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"KNOWLEDGE_SERVICE_UNAUTHORIZED");
        return service.resolve(request.vectorStoreId(),request.fileIds());
    }
}
