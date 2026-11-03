package com.mediassistrover.backend.service;

import com.mediassistrover.backend.dto.media.response.MediaObjectResponse;
import com.mediassistrover.backend.exception.*;
import com.mediassistrover.backend.repository.*;
import com.mediassistrover.backend.storage.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Pattern;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class MediaService {
    private static final Pattern KEY=Pattern.compile("^(images/[0-9a-fA-F-]{36}\\.(jpg|png|webp)|videos/[0-9a-fA-F-]{36}\\.(mp4|webm|mov))$");
    private final S3ObjectStorageService storage;private final MediaFileValidator validator;private final StaffProfileRepository staff;private final HeatmapRecordRepository heatmaps;private final DetectionEventRepository detections;private final CrowdDensityRecordRepository crowds;private final AuditLogRepository audits;
    public MediaObjectResponse upload(MultipartFile file){var v=validator.imageOrVideo(file);String key=("IMAGE".equals(v.kind())?"images/":"videos/")+UUID.randomUUID()+"."+v.extension();try{var o=storage.put(key,file.getInputStream(),v.sizeBytes(),v.contentType(),sha256(file),true);return response(o,v.kind());}catch(RuntimeException e){throw e;}catch(Exception e){throw new BusinessRuleException("Uploaded file cannot be read");}}
    public MediaObjectResponse get(String objectKey){requireKey(objectKey);var o=storage.head(objectKey,true);return response(o,objectKey.startsWith("images/")?"IMAGE":"VIDEO");}
    @Transactional public void delete(String objectKey){requireKey(objectKey);String url=storage.publicUrl(objectKey);if(staff.existsByUrl(url)||heatmaps.existsByImageUri(url)||detections.existsByEvidenceRef(url)||crowds.existsByEvidenceRef(url)||audits.containsJsonString(url))throw new ConflictException("Media object is still referenced by business data");storage.delete(objectKey);}
    private MediaObjectResponse response(S3ObjectStorageService.ObjectInfo o,String type){return new MediaObjectResponse(o.objectKey(),o.url(),type,o.contentType(),o.sizeBytes(),o.sha256());}
    private void requireKey(String key){if(key==null||!KEY.matcher(key).matches())throw new BusinessRuleException("objectKey must identify an uploaded image or video");try{UUID.fromString(key.substring(key.indexOf('/')+1,key.lastIndexOf('.')));}catch(IllegalArgumentException e){throw new BusinessRuleException("objectKey contains an invalid UUID");}}
    private String sha256(MultipartFile f){try(InputStream in=f.getInputStream()){MessageDigest d=MessageDigest.getInstance("SHA-256");byte[] b=new byte[8192];for(int n;(n=in.read(b))>=0;)if(n>0)d.update(b,0,n);return HexFormat.of().formatHex(d.digest());}catch(Exception e){throw new BusinessRuleException("Uploaded file cannot be hashed");}}
}
