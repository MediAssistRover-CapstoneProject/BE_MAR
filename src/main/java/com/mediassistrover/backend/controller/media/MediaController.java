package com.mediassistrover.backend.controller.media;

import com.mediassistrover.backend.config.OpenApiConfig;
import com.mediassistrover.backend.dto.media.response.MediaObjectResponse;
import com.mediassistrover.backend.dto.media.response.MediaListResponse;
import com.mediassistrover.backend.service.MediaService;
import com.mediassistrover.backend.dto.common.DeletionContracts.*;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController @RequestMapping("/api/v1/media") @RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','STAFF')") @SecurityRequirement(name=OpenApiConfig.USER_BEARER_SCHEME)
@Tag(name="Media",description="Upload and manage public image/video objects in Amazon S3")
public class MediaController {
    private final MediaService service;
    private final com.mediassistrover.backend.service.DeletionPolicy deletionPolicy;
    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary="Upload an image or video to Amazon S3")
    public MediaObjectResponse upload(@Parameter(required=true,schema=@Schema(type="string",format="binary")) @RequestPart("file") MultipartFile file){return service.upload(file);}
    @GetMapping @Operation(summary="List uploaded images and videos for the frontend media library")
    public MediaListResponse list(@RequestParam(required=false) String mediaType,@RequestParam(required=false) @jakarta.validation.constraints.Size(max=2048) String cursor,@RequestParam(defaultValue="20") @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(100) int size){return service.list(mediaType,cursor,size);}
    @GetMapping("/url") @Operation(summary="Resolve a fixed public URL for an existing media object")
    public MediaObjectResponse url(@RequestParam String objectKey){return service.get(objectKey);}
    @DeleteMapping @PreAuthorize("hasRole('ADMIN')") @Operation(summary="Soft-delete an image or video from the media catalog")
    public MediaObjectResponse delete(@RequestParam String objectKey,@Valid @RequestBody LifecycleActionRequest body){return service.delete(objectKey,body.reason());}
    @PostMapping("/restore") @PreAuthorize("hasRole('ADMIN')") public MediaObjectResponse restore(@RequestParam String objectKey,@Valid @RequestBody LifecycleActionRequest body){return service.restore(objectKey,body.reason());}
    @GetMapping("/deletion-impact") @PreAuthorize("hasRole('ADMIN')") public DeletionImpactResponse impact(@RequestParam String objectKey){return service.deletionImpact(objectKey);}
    @DeleteMapping("/force") @PreAuthorize("hasRole('ADMIN')") public HardDeleteResponse force(@RequestParam String objectKey,@RequestHeader("X-Confirm-Hard-Delete")Boolean confirmed,@Valid @RequestBody LifecycleActionRequest body){deletionPolicy.requireHardDeleteConfirmation(confirmed);return service.forceDelete(objectKey,body.reason());}
}
