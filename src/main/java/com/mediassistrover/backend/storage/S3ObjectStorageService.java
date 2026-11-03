package com.mediassistrover.backend.storage;

import com.mediassistrover.backend.config.S3Properties;
import com.mediassistrover.backend.exception.ResourceNotFoundException;
import com.mediassistrover.backend.exception.StorageUnavailableException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class S3ObjectStorageService {
    private static final Set<String> ROOTS=Set.of("images","videos","maps","audio","documents");
    private static final Set<String> PUBLIC_ROOTS=Set.of("images","videos","maps","audio");
    private final S3Client client;
    private final S3Properties properties;
    private final Map<String,byte[]> testObjects=new ConcurrentHashMap<>();
    private final Map<String,ObjectInfo> testMetadata=new ConcurrentHashMap<>();

    public record ObjectInfo(String objectKey,String url,String contentType,long sizeBytes,String sha256) { }

    public ObjectInfo put(String key,InputStream input,long size,String contentType,String sha256,boolean publicObject) {
        requireKey(key);
        if(!properties.isEnabled())try(input){byte[] bytes=input.readAllBytes();if(bytes.length!=size)throw new IllegalArgumentException("Object length differs from request");ObjectInfo info=new ObjectInfo(key,publicObject?publicUrl(key):s3Uri(key),contentType,size,sha256);testObjects.put(key,bytes);testMetadata.put(key,info);return info;}catch(Exception e){throw new StorageUnavailableException("Unable to store test object",e);}
        try(input){PutObjectRequest.Builder request=PutObjectRequest.builder().bucket(properties.getBucket()).key(key).contentLength(size).contentType(contentType).metadata(sha256==null?Map.of():Map.of("sha256",sha256));if(publicObject)request.cacheControl("public, max-age=31536000, immutable");client.putObject(request.build(),RequestBody.fromInputStream(input,size));return new ObjectInfo(key,publicObject?publicUrl(key):s3Uri(key),contentType,size,sha256);}catch(S3Exception e){throw unavailable(e);}catch(Exception e){throw new StorageUnavailableException("Unable to upload object to S3",e);}
    }
    public ObjectInfo head(String key,boolean publicObject){requireKey(key);if(!properties.isEnabled()){ObjectInfo i=testMetadata.get(key);if(i==null)throw new ResourceNotFoundException("Media object not found");return new ObjectInfo(key,publicObject?publicUrl(key):s3Uri(key),i.contentType(),i.sizeBytes(),i.sha256());}try{HeadObjectResponse r=client.headObject(HeadObjectRequest.builder().bucket(properties.getBucket()).key(key).build());return new ObjectInfo(key,publicObject?publicUrl(key):s3Uri(key),r.contentType(),r.contentLength(),r.metadata().get("sha256"));}catch(S3Exception e){if(e.statusCode()==404)throw new ResourceNotFoundException("Media object not found");throw unavailable(e);}}
    public byte[] get(String key,long maxBytes){if(!properties.isEnabled()){head(key,isPublicKey(key));byte[] v=testObjects.get(key);if(v.length>maxBytes)throw new StorageUnavailableException("Stored object exceeds the allowed size",null);return v.clone();}ObjectInfo info=head(key,isPublicKey(key));if(info.sizeBytes()>maxBytes)throw new StorageUnavailableException("Stored object exceeds the allowed size",null);try{ResponseBytes<GetObjectResponse> b=client.getObjectAsBytes(GetObjectRequest.builder().bucket(properties.getBucket()).key(key).build());byte[] v=b.asByteArray();if(v.length>maxBytes)throw new StorageUnavailableException("Stored object exceeds the allowed size",null);return v;}catch(S3Exception e){if(e.statusCode()==404)throw new ResourceNotFoundException("Media object not found");throw unavailable(e);}}
    public void delete(String key){if(!properties.isEnabled()){head(key,isPublicKey(key));testObjects.remove(key);testMetadata.remove(key);return;}head(key,isPublicKey(key));try{client.deleteObject(DeleteObjectRequest.builder().bucket(properties.getBucket()).key(key).build());}catch(S3Exception e){throw unavailable(e);}}
    public String publicUrl(String key){requireKey(key);String encoded=java.util.Arrays.stream(key.split("/",-1)).map(p->URLEncoder.encode(p,StandardCharsets.UTF_8).replace("+","%20")).reduce((a,b)->a+"/"+b).orElseThrow();return "https://"+properties.getBucket()+".s3."+properties.getRegion()+".amazonaws.com/"+encoded;}
    public String s3Uri(String key){requireKey(key);return "s3://"+properties.getBucket()+"/"+key;}
    public String keyFromPublicUrl(String value,Set<String> allowedRoots){try{URI uri=URI.create(value);String host=properties.getBucket()+".s3."+properties.getRegion()+".amazonaws.com";if(!"https".equalsIgnoreCase(uri.getScheme())||!host.equalsIgnoreCase(uri.getHost())||uri.getRawQuery()!=null||uri.getRawFragment()!=null||uri.getUserInfo()!=null)throw new IllegalArgumentException("URL must be a fixed HTTPS URL from the configured S3 bucket");String key=uri.getPath().replaceFirst("^/","");requireKey(key);if(!allowedRoots.contains(root(key)))throw new IllegalArgumentException("S3 URL uses an unsupported prefix");return key;}catch(IllegalArgumentException e){throw e;}catch(Exception e){throw new IllegalArgumentException("Invalid S3 URL",e);}}
    public String keyFromStoredReference(String value){if(value==null||value.isBlank())throw new IllegalArgumentException("Stored S3 reference is required");String prefix="s3://"+properties.getBucket()+"/";if(value.startsWith(prefix)){String key=value.substring(prefix.length());requireKey(key);return key;}return keyFromPublicUrl(value,ROOTS);}
    public boolean isPublicKey(String key){return PUBLIC_ROOTS.contains(root(key));}
    private void requireKey(String key){if(key==null||key.isBlank()||key.startsWith("/")||key.contains("..")||key.contains("\\")||key.chars().anyMatch(Character::isISOControl)||!ROOTS.contains(root(key)))throw new IllegalArgumentException("Invalid S3 object key");}
    private String root(String key){int slash=key.indexOf('/');return slash<1?"":key.substring(0,slash);}
    private StorageUnavailableException unavailable(S3Exception e){return new StorageUnavailableException("Amazon S3 is unavailable",e);}
}
