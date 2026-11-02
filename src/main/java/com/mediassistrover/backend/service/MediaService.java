package com.mediassistrover.backend.service;

import com.mediassistrover.backend.dto.media.response.MediaObjectResponse;
import com.mediassistrover.backend.exception.BusinessRuleException;
import com.mediassistrover.backend.exception.ConflictException;
import com.mediassistrover.backend.repository.*;
import com.mediassistrover.backend.storage.MediaFileValidator;
import com.mediassistrover.backend.storage.S3ObjectStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MediaService {
    private static final Pattern PUBLIC_MEDIA_KEY = Pattern.compile(
            "^(images/[0-9a-fA-F-]{36}\\.(jpg|png|webp)|videos/[0-9a-fA-F-]{36}\\.(mp4|webm|mov))$");
    private final S3ObjectStorageService storage;
    private final MediaFileValidator validator;
    private final StaffProfileRepository staffProfiles;
    private final HeatmapRecordRepository heatmaps;
    private final DetectionEventRepository detections;
    private final CrowdDensityRecordRepository crowdDensity;
    private final AuditLogRepository auditLogs;

    public MediaObjectResponse upload(MultipartFile file) {
        MediaFileValidator.Validated valid = validator.imageOrVideo(file);
        String prefix = "IMAGE".equals(valid.kind()) ? "images/" : "videos/";
        String key = prefix + UUID.randomUUID() + "." + valid.extension();
        String sha256 = sha256(file);
        try {
            var stored = storage.put(key, file.getInputStream(), valid.sizeBytes(), valid.contentType(), sha256, true);
            return response(stored, valid.kind());
        } catch (BusinessRuleException e) {
            throw e;
        } catch (Exception e) {
            if (e instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("Uploaded file cannot be read", e);
        }
    }

    public MediaObjectResponse get(String objectKey) {
        requirePublicMediaKey(objectKey);
        var info = storage.head(objectKey, true);
        return response(info, objectKey.startsWith("images/") ? "IMAGE" : "VIDEO");
    }

    @Transactional
    public void delete(String objectKey) {
        requirePublicMediaKey(objectKey);
        String url = storage.publicUrl(objectKey);
        if (staffProfiles.existsByUrl(url) || heatmaps.existsByImageUri(url)
                || detections.existsByEvidenceRef(url) || crowdDensity.existsByEvidenceRef(url)
                || auditLogs.containsJsonString(url))
            throw new ConflictException("Media object is still referenced by business data");
        storage.delete(objectKey);
    }

    private MediaObjectResponse response(S3ObjectStorageService.ObjectInfo info, String mediaType) {
        return new MediaObjectResponse(info.objectKey(), info.url(), mediaType, info.contentType(),
                info.sizeBytes(), info.sha256());
    }

    private void requirePublicMediaKey(String key) {
        if (key == null || !PUBLIC_MEDIA_KEY.matcher(key).matches())
            throw new BusinessRuleException("objectKey must identify an uploaded image or video");
        try { UUID.fromString(key.substring(key.indexOf('/') + 1, key.lastIndexOf('.'))); }
        catch (IllegalArgumentException e) { throw new BusinessRuleException("objectKey contains an invalid UUID"); }
    }

    private String sha256(MultipartFile file) {
        try (InputStream input = file.getInputStream()) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            for (int read; (read = input.read(buffer)) >= 0;) if (read > 0) digest.update(buffer, 0, read);
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) {
            throw new BusinessRuleException("Uploaded file cannot be hashed");
        }
    }
}
