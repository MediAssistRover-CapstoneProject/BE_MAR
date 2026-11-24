package com.mediassistrover.backend.knowledge;

import com.mediassistrover.backend.config.OpenApiConfig;
import com.mediassistrover.backend.security.AccountPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/knowledge/documents")
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = OpenApiConfig.USER_BEARER_SCHEME)
@Tag(name = "Knowledge documents", description = "ADMIN uploads for the configured OpenAI voice vector store")
@RequiredArgsConstructor
public class KnowledgeDocumentController {
    private final KnowledgeDocumentService service;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Upload PDF, DOCX, DOC, UTF-8 TXT or MD (20 MB maximum)",
        description = "Returns a durable document ID. Poll GET /{id} until COMPLETED before asking voice questions. "
            + "A new upload is a new publication attempt, even when its bytes match an older upload.")
    public KnowledgeDocumentService.Document upload(
            @Parameter(required = true, schema = @Schema(type = "string", format = "binary"))
            @RequestPart("file") MultipartFile file, @AuthenticationPrincipal AccountPrincipal actor) {
        return service.upload(file, actor.accountId());
    }

    @GetMapping
    @Operation(summary = "List documents and indexing states")
    public KnowledgeDocumentService.DocumentPage list(@RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        return service.list(page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Read indexing status and sanitized error code")
    public KnowledgeDocumentService.Document get(@PathVariable UUID id) { return service.get(id); }

    @GetMapping("/{id}/chunks")
    @Operation(summary="Read extracted chunks (no additional approval required)")
    public java.util.List<KnowledgeDocumentService.ChunkView> chunks(@PathVariable UUID id) { return service.chunks(id); }

    @GetMapping(value="/{id}/extracted-text",produces=MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary="Read the editable text extracted from the file, including OCR text")
    public String extractedText(@PathVariable UUID id) { return service.extractedText(id); }

    @PostMapping("/{id}/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary="Retry a failed v2/v3 extraction/indexing job after fixing the cause (not unknown upload outcomes)")
    public KnowledgeDocumentService.Document retry(@PathVariable UUID id) { return service.retry(id); }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary="Delete document and chunks from the database; detach remote files asynchronously")
    public void delete(@PathVariable UUID id) { service.delete(id); }

    @DeleteMapping("/{id}/chunks/{chunkId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary="Delete one indexed chunk from the database; detach its remote file asynchronously")
    public void deleteChunk(@PathVariable UUID id, @PathVariable UUID chunkId) {
        service.deleteChunk(id,chunkId);
    }

    @PostMapping(value="/{id}/replace",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary="Replace a ready document; old content remains available until new content is fully indexed")
    public KnowledgeDocumentService.Document replace(@PathVariable UUID id, @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal AccountPrincipal actor) { return service.replace(id,file,actor.accountId()); }

    @PutMapping(value="/{id}",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary="Update a document file and reindex all of its chunks as a new revision")
    public KnowledgeDocumentService.Document update(@PathVariable UUID id, @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal AccountPrincipal actor) { return service.replace(id,file,actor.accountId()); }

    public record ChunkUpdate(String content) { }

    @PutMapping(value="/{id}/chunks/{chunkId}",consumes=MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary="Edit one extracted/OCR chunk and publish a new document revision")
    public KnowledgeDocumentService.Document updateChunk(@PathVariable UUID id,@PathVariable UUID chunkId,
            @RequestBody ChunkUpdate body,@AuthenticationPrincipal AccountPrincipal actor) {
        return service.updateChunk(id,chunkId,body==null?null:body.content(),actor.accountId());
    }
}
