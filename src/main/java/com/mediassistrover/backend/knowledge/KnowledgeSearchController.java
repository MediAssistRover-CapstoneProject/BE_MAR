package com.mediassistrover.backend.knowledge;

import com.mediassistrover.backend.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/knowledge")
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name=OpenApiConfig.USER_BEARER_SCHEME)
@Tag(name="Knowledge documents")
@RequiredArgsConstructor
public class KnowledgeSearchController {
    private final KnowledgeSearchService service;

    @GetMapping("/documents/search")
    @Operation(summary="Search the extracted/OCR text of ready documents")
    public KnowledgeSearchService.SearchPage<KnowledgeSearchService.DocumentHit> documents(
            @RequestParam String q,@RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size) {
        return service.documents(q,page,size);
    }

    @GetMapping("/chunks/search")
    @Operation(summary="Search chunks in ready documents, including unpublished chunks")
    public KnowledgeSearchService.SearchPage<KnowledgeSearchService.ChunkHit> chunks(
            @RequestParam String q,@RequestParam(required=false) UUID documentId,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return service.chunks(q,documentId,page,size);
    }
}
