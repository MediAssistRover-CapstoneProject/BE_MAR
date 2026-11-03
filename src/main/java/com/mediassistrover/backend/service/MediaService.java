package com.mediassistrover.backend.service;

import com.mediassistrover.backend.dto.media.response.MediaObjectResponse;
import com.mediassistrover.backend.dto.media.response.MediaListResponse;
import com.mediassistrover.backend.exception.*;
import com.mediassistrover.backend.repository.*;
import com.mediassistrover.backend.storage.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.InputStream;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class MediaService {
    private static final Pattern KEY=Pattern.compile("^(images/[0-9a-fA-F-]{36}\\.(jpg|png|webp)|videos/[0-9a-fA-F-]{36}\\.(mp4|webm|mov))$");
    private final S3ObjectStorageService storage;private final MediaFileValidator validator;private final StaffProfileRepository staff;private final HeatmapRecordRepository heatmaps;private final DetectionEventRepository detections;private final CrowdDensityRecordRepository crowds;private final AuditLogRepository audits;
    public MediaObjectResponse upload(MultipartFile file){var v=validator.imageOrVideo(file);String key=("IMAGE".equals(v.kind())?"images/":"videos/")+UUID.randomUUID()+"."+v.extension();try{var o=storage.put(key,file.getInputStream(),v.sizeBytes(),v.contentType(),sha256(file),true);return response(o,v.kind());}catch(RuntimeException e){throw e;}catch(Exception e){throw new BusinessRuleException("Uploaded file cannot be read");}}
    public MediaObjectResponse get(String objectKey){requireKey(objectKey);var o=storage.head(objectKey,true);return response(o,objectKey.startsWith("images/")?"IMAGE":"VIDEO");}
    public MediaListResponse list(String mediaType,String cursor,int size){
        List<String> roots=mediaType==null||mediaType.isBlank()?List.of("images","videos"):switch(mediaType.trim().toUpperCase(Locale.ROOT)){case "IMAGE"->List.of("images");case "VIDEO"->List.of("videos");default->throw new BusinessRuleException("mediaType must be IMAGE or VIDEO");};
        Cursor state=decodeCursor(cursor);int rootIndex=state==null?0:roots.indexOf(state.root());
        if(rootIndex<0)throw new BusinessRuleException("cursor does not belong to the requested media type");
        String storageCursor=state==null?null:state.storageCursor();List<MediaObjectResponse> content=new ArrayList<>(size);String nextCursor=null;
        while(rootIndex<roots.size()&&content.size()<size){String root=roots.get(rootIndex);var page=storage.list(root+"/",storageCursor,size-content.size(),true);content.addAll(page.content().stream().filter(value->KEY.matcher(value.objectKey()).matches()).map(value->response(value,"images".equals(root)?"IMAGE":"VIDEO")).toList());
            if(page.nextCursor()!=null){nextCursor=encodeCursor(root,page.nextCursor());break;}rootIndex++;storageCursor=null;}
        if(nextCursor==null&&rootIndex<roots.size())nextCursor=encodeCursor(roots.get(rootIndex),null);
        return new MediaListResponse(List.copyOf(content),nextCursor,nextCursor!=null);
    }
    @Transactional public void delete(String objectKey){requireKey(objectKey);String url=storage.publicUrl(objectKey);if(staff.existsByUrl(url)||heatmaps.existsByImageUri(url)||detections.existsByEvidenceRef(url)||crowds.existsByEvidenceRef(url)||audits.containsJsonString(url))throw new ConflictException("Media object is still referenced by business data");storage.delete(objectKey);}
    private MediaObjectResponse response(S3ObjectStorageService.ObjectInfo o,String type){return new MediaObjectResponse(o.objectKey(),o.url(),type,o.contentType(),o.sizeBytes(),o.sha256());}
    private record Cursor(String root,String storageCursor) { }
    private String encodeCursor(String root,String storageCursor){String value=root+":"+(storageCursor==null?"":storageCursor);return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));}
    private Cursor decodeCursor(String cursor){if(cursor==null||cursor.isBlank())return null;try{String decoded=new String(Base64.getUrlDecoder().decode(cursor),StandardCharsets.UTF_8);String[] parts=decoded.split(":",2);if(parts.length!=2||!Set.of("images","videos").contains(parts[0]))throw new IllegalArgumentException();return new Cursor(parts[0],parts[1].isBlank()?null:parts[1]);}catch(IllegalArgumentException e){throw new BusinessRuleException("cursor is invalid");}}
    private void requireKey(String key){if(key==null||!KEY.matcher(key).matches())throw new BusinessRuleException("objectKey must identify an uploaded image or video");try{UUID.fromString(key.substring(key.indexOf('/')+1,key.lastIndexOf('.')));}catch(IllegalArgumentException e){throw new BusinessRuleException("objectKey contains an invalid UUID");}}
    private String sha256(MultipartFile f){try(InputStream in=f.getInputStream()){MessageDigest d=MessageDigest.getInstance("SHA-256");byte[] b=new byte[8192];for(int n;(n=in.read(b))>=0;)if(n>0)d.update(b,0,n);return HexFormat.of().formatHex(d.digest());}catch(Exception e){throw new BusinessRuleException("Uploaded file cannot be hashed");}}
}
