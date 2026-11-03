package com.mediassistrover.backend.controller.media;

import com.mediassistrover.backend.config.OpenApiConfig;
import com.mediassistrover.backend.dto.media.response.MediaObjectResponse;
import com.mediassistrover.backend.service.MediaService;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController @RequestMapping("/api/v1/media") @RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','STAFF')") @SecurityRequirement(name=OpenApiConfig.USER_BEARER_SCHEME)
@Tag(name="Media",description="Upload and manage public image/video objects in Amazon S3")
public class MediaController {
    private final MediaService service;
    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary="Upload an image or video to Amazon S3")
    public MediaObjectResponse upload(@Parameter(required=true,schema=@Schema(type="string",format="binary")) @RequestPart("file") MultipartFile file){return service.upload(file);}
    @GetMapping("/url") @Operation(summary="Resolve a fixed public URL for an existing media object")
    public MediaObjectResponse url(@RequestParam String objectKey){return service.get(objectKey);}
    @DeleteMapping @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasRole('ADMIN')") @Operation(summary="Delete an unreferenced image or video")
    public void delete(@RequestParam String objectKey){service.delete(objectKey);}
}
