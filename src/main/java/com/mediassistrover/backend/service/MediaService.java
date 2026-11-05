package com.mediassistrover.backend.service;

import com.mediassistrover.backend.dto.media.response.MediaObjectResponse;
import com.mediassistrover.backend.dto.media.response.MediaListResponse;
import com.mediassistrover.backend.entity.MediaAsset;
import com.mediassistrover.backend.exception.*;
import com.mediassistrover.backend.repository.*;
import com.mediassistrover.backend.storage.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.InputStream;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class MediaService {
    private static final Pattern KEY=Pattern.compile("^(images/[0-9a-fA-F-]{36}\\.(jpg|png|webp)|videos/[0-9a-fA-F-]{36}\\.(mp4|webm|mov))$");
    private final S3ObjectStorageService storage;private final MediaFileValidator validator;private final MediaAssetRepository assets;private final StaffProfileRepository staff;private final HeatmapRecordRepository heatmaps;private final DetectionEventRepository detections;private final CrowdDensityRecordRepository crowds;private final AuditLogRepository audits;
    @Transactional public MediaObjectResponse upload(MultipartFile file){var v=validator.imageOrVideo(file);String key=("IMAGE".equals(v.kind())?"images/":"videos/")+UUID.randomUUID()+"."+v.extension();try{String hash=sha256(file);var o=storage.put(key,file.getInputStream(),v.sizeBytes(),v.contentType(),hash,true);assets.save(MediaAsset.builder().objectKey(o.objectKey()).url(o.url()).mediaType(v.kind()).contentType(v.contentType()).sizeBytes(v.sizeBytes()).sha256(hash).active(true).createdAt(Instant.now()).build());return response(o,v.kind());}catch(RuntimeException e){throw e;}catch(Exception e){throw new BusinessRuleException("Uploaded file cannot be read");}}
    public MediaObjectResponse get(String objectKey){requireKey(objectKey);return assets.findByObjectKeyAndActiveTrue(objectKey).map(this::response).orElseThrow(()->new ResourceNotFoundException("Media asset not found: "+objectKey));}
    public MediaListResponse list(String mediaType,String cursor,int size){
        String normalized=mediaType==null||mediaType.isBlank()?null:mediaType.trim().toUpperCase(Locale.ROOT);if(normalized!=null&&!Set.of("IMAGE","VIDEO").contains(normalized))throw new BusinessRuleException("mediaType must be IMAGE or VIDEO");
        PageCursor pageCursor=decodePageCursor(cursor,size);PageRequest pageable=PageRequest.of(pageCursor.page(),size,Sort.by(Sort.Direction.DESC,"createdAt").and(Sort.by(Sort.Direction.DESC,"mediaAssetId")));
        Page<MediaAsset> catalog=normalized==null?assets.findByActiveTrue(pageable):assets.findByMediaTypeAndActiveTrue(normalized,pageable);
        String next=catalog.hasNext()?encodePageCursor(pageCursor.page()+1,size):null;
        return new MediaListResponse(catalog.getContent().stream().map(this::response).toList(),next,next!=null);
    }
    @Transactional public MediaObjectResponse delete(String objectKey,String reason){requireKey(objectKey);MediaAsset asset=asset(objectKey);asset.setActive(false);return response(asset);}
    @Transactional public MediaObjectResponse restore(String objectKey,String reason){MediaAsset asset=asset(objectKey);asset.setActive(true);return response(asset);}
    public com.mediassistrover.backend.dto.common.DeletionContracts.DeletionImpactResponse deletionImpact(String objectKey){MediaAsset asset=asset(objectKey);Map<String,Long> refs=references(asset.getUrl());List<String> blockers=refs.entrySet().stream().filter(e->e.getValue()>0).map(e->e.getKey()+" has "+e.getValue()+" reference(s)").toList();return new com.mediassistrover.backend.dto.common.DeletionContracts.DeletionImpactResponse(asset.getMediaAssetId(),"MediaAsset",blockers.isEmpty(),blockers,refs);}
    @Transactional public com.mediassistrover.backend.dto.common.DeletionContracts.HardDeleteResponse forceDelete(String objectKey,String reason){MediaAsset asset=asset(objectKey);var impact=deletionImpact(objectKey);if(!impact.canHardDelete())throw new ConflictException(String.join(", ",impact.blockers()));storage.delete(objectKey);assets.delete(asset);return new com.mediassistrover.backend.dto.common.DeletionContracts.HardDeleteResponse(asset.getMediaAssetId(),"MediaAsset",Map.of("MediaAsset",1L,"S3Object",1L));}
    private MediaObjectResponse response(S3ObjectStorageService.ObjectInfo o,String type){return new MediaObjectResponse(o.objectKey(),o.url(),type,o.contentType(),o.sizeBytes(),o.sha256());}
    private MediaObjectResponse response(MediaAsset value){return new MediaObjectResponse(value.getObjectKey(),value.getUrl(),value.getMediaType(),value.getContentType(),value.getSizeBytes(),value.getSha256());}
    private record PageCursor(int page,int size) { }
    private String encodePageCursor(int page,int size){return Base64.getUrlEncoder().withoutPadding().encodeToString(("db:"+page+":"+size).getBytes(StandardCharsets.UTF_8));}
    private PageCursor decodePageCursor(String cursor,int size){if(cursor==null||cursor.isBlank())return new PageCursor(0,size);try{String decoded=new String(Base64.getUrlDecoder().decode(cursor),StandardCharsets.UTF_8);if(!decoded.startsWith("db:"))return new PageCursor(0,size);String[] parts=decoded.split(":",3);int page=Integer.parseInt(parts[1]);int cursorSize=Integer.parseInt(parts[2]);if(page<0||cursorSize!=size)throw new IllegalArgumentException();return new PageCursor(page,size);}catch(IllegalArgumentException e){throw new BusinessRuleException("cursor is invalid");}}
    private void requireKey(String key){if(key==null||!KEY.matcher(key).matches())throw new BusinessRuleException("objectKey must identify an uploaded image or video");try{UUID.fromString(key.substring(key.indexOf('/')+1,key.lastIndexOf('.')));}catch(IllegalArgumentException e){throw new BusinessRuleException("objectKey contains an invalid UUID");}}
    private MediaAsset asset(String key){requireKey(key);return assets.findByObjectKey(key).orElseThrow(()->new ResourceNotFoundException("Media asset not found: "+key));}
    private Map<String,Long> references(String url){var out=new LinkedHashMap<String,Long>();out.put("StaffProfile.avatarUrl",staff.existsByUrl(url)?1L:0L);out.put("HeatmapRecord.imageUri",heatmaps.existsByImageUri(url)?1L:0L);out.put("DetectionEvent.evidenceRef",detections.existsByEvidenceRef(url)?1L:0L);out.put("CrowdDensityRecord.evidenceRef",crowds.existsByEvidenceRef(url)?1L:0L);out.put("AuditLog.json",audits.containsJsonString(url)?1L:0L);return Map.copyOf(out);}
    private String sha256(MultipartFile f){try(InputStream in=f.getInputStream()){MessageDigest d=MessageDigest.getInstance("SHA-256");byte[] b=new byte[8192];for(int n;(n=in.read(b))>=0;)if(n>0)d.update(b,0,n);return HexFormat.of().formatHex(d.digest());}catch(Exception e){throw new BusinessRuleException("Uploaded file cannot be hashed");}}
}
