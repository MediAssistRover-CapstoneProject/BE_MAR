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
import java.util.Comparator;
import java.util.List;
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
    public record ObjectPage(List<ObjectInfo> content,String nextCursor) { }

    public ObjectInfo put(String key,InputStream input,long size,String contentType,String sha256,boolean publicObject) {
        requireKey(key);
        if(!properties.isEnabled())try(input){byte[] bytes=input.readAllBytes();if(bytes.length!=size)throw new IllegalArgumentException("Object length differs from request");ObjectInfo info=new ObjectInfo(key,publicObject?publicUrl(key):s3Uri(key),contentType,size,sha256);testObjects.put(key,bytes);testMetadata.put(key,info);return info;}catch(Exception e){throw new StorageUnavailableException("Unable to store test object",e);}
        try(input){PutObjectRequest.Builder request=PutObjectRequest.builder().bucket(properties.getBucket()).key(key).contentLength(size).contentType(contentType).metadata(sha256==null?Map.of():Map.of("sha256",sha256));if(publicObject)request.cacheControl("public, max-age=31536000, immutable");client.putObject(request.build(),RequestBody.fromInputStream(input,size));return new ObjectInfo(key,publicObject?publicUrl(key):s3Uri(key),contentType,size,sha256);}catch(S3Exception e){throw unavailable(e);}catch(Exception e){throw new StorageUnavailableException("Unable to upload object to S3",e);}
    }
    public ObjectInfo head(String key,boolean publicObject){requireKey(key);if(!properties.isEnabled()){ObjectInfo i=testMetadata.get(key);if(i==null)throw new ResourceNotFoundException("Media object not found");return new ObjectInfo(key,publicObject?publicUrl(key):s3Uri(key),i.contentType(),i.sizeBytes(),i.sha256());}try{HeadObjectResponse r=client.headObject(HeadObjectRequest.builder().bucket(properties.getBucket()).key(key).build());return new ObjectInfo(key,publicObject?publicUrl(key):s3Uri(key),r.contentType(),r.contentLength(),r.metadata().get("sha256"));}catch(S3Exception e){if(e.statusCode()==404)throw new ResourceNotFoundException("Media object not found");throw unavailable(e);}}
    public ObjectPage list(String prefix,String cursor,int size,boolean publicObjects){
        if(prefix==null||prefix.isBlank()||!prefix.endsWith("/"))throw new IllegalArgumentException("Object prefix is required");
        requireKey(prefix+"placeholder");
        if(size<1||size>100)throw new IllegalArgumentException("Object page size must be between 1 and 100");
        if(!properties.isEnabled()){
            List<String> keys=testMetadata.keySet().stream().filter(key->key.startsWith(prefix))
                    .filter(key->cursor==null||cursor.isBlank()||key.compareTo(cursor)>0).sorted(Comparator.naturalOrder()).limit(size+1L).toList();
            boolean more=keys.size()>size;List<String> page=more?keys.subList(0,size):keys;
            List<ObjectInfo> content=page.stream().map(key->{ObjectInfo value=testMetadata.get(key);return new ObjectInfo(key,publicObjects?publicUrl(key):s3Uri(key),value.contentType(),value.sizeBytes(),value.sha256());}).toList();
            return new ObjectPage(content,more?page.getLast():null);
        }
        try{
            ListObjectsV2Request.Builder request=ListObjectsV2Request.builder().bucket(properties.getBucket()).prefix(prefix).maxKeys(size);
            if(cursor!=null&&!cursor.isBlank())request.continuationToken(cursor);
            ListObjectsV2Response response=client.listObjectsV2(request.build());
            List<ObjectInfo> content=response.contents().stream().map(item->new ObjectInfo(item.key(),publicObjects?publicUrl(item.key()):s3Uri(item.key()),contentType(item.key()),item.size(),null)).toList();
            return new ObjectPage(content,response.isTruncated()?response.nextContinuationToken():null);
        }catch(S3Exception e){throw unavailable(e);}
    }
    public byte[] get(String key,long maxBytes){if(!properties.isEnabled()){head(key,isPublicKey(key));byte[] v=testObjects.get(key);if(v.length>maxBytes)throw new StorageUnavailableException("Stored object exceeds the allowed size",null);return v.clone();}ObjectInfo info=head(key,isPublicKey(key));if(info.sizeBytes()>maxBytes)throw new StorageUnavailableException("Stored object exceeds the allowed size",null);try{ResponseBytes<GetObjectResponse> b=client.getObjectAsBytes(GetObjectRequest.builder().bucket(properties.getBucket()).key(key).build());byte[] v=b.asByteArray();if(v.length>maxBytes)throw new StorageUnavailableException("Stored object exceeds the allowed size",null);return v;}catch(S3Exception e){if(e.statusCode()==404)throw new ResourceNotFoundException("Media object not found");throw unavailable(e);}}
    public void delete(String key){if(!properties.isEnabled()){head(key,isPublicKey(key));testObjects.remove(key);testMetadata.remove(key);return;}head(key,isPublicKey(key));try{client.deleteObject(DeleteObjectRequest.builder().bucket(properties.getBucket()).key(key).build());}catch(S3Exception e){throw unavailable(e);}}
    public String publicUrl(String key){requireKey(key);String encoded=java.util.Arrays.stream(key.split("/",-1)).map(p->URLEncoder.encode(p,StandardCharsets.UTF_8).replace("+","%20")).reduce((a,b)->a+"/"+b).orElseThrow();return "https://"+properties.getBucket()+".s3."+properties.getRegion()+".amazonaws.com/"+encoded;}
    public String s3Uri(String key){requireKey(key);return "s3://"+properties.getBucket()+"/"+key;}
    public String keyFromPublicUrl(String value,Set<String> allowedRoots){try{URI uri=URI.create(value);String host=properties.getBucket()+".s3."+properties.getRegion()+".amazonaws.com";if(!"https".equalsIgnoreCase(uri.getScheme())||!host.equalsIgnoreCase(uri.getHost())||uri.getRawQuery()!=null||uri.getRawFragment()!=null||uri.getUserInfo()!=null)throw new IllegalArgumentException("URL must be a fixed HTTPS URL from the configured S3 bucket");String key=uri.getPath().replaceFirst("^/","");requireKey(key);if(!allowedRoots.contains(root(key)))throw new IllegalArgumentException("S3 URL uses an unsupported prefix");return key;}catch(IllegalArgumentException e){throw e;}catch(Exception e){throw new IllegalArgumentException("Invalid S3 URL",e);}}
    public String keyFromStoredReference(String value){if(value==null||value.isBlank())throw new IllegalArgumentException("Stored S3 reference is required");String prefix="s3://"+properties.getBucket()+"/";if(value.startsWith(prefix)){String key=value.substring(prefix.length());requireKey(key);return key;}return keyFromPublicUrl(value,ROOTS);}
    public boolean isPublicKey(String key){return PUBLIC_ROOTS.contains(root(key));}
    private String contentType(String key){String value=key.toLowerCase(java.util.Locale.ROOT);if(value.endsWith(".jpg")||value.endsWith(".jpeg"))return "image/jpeg";if(value.endsWith(".png"))return "image/png";if(value.endsWith(".webp"))return "image/webp";if(value.endsWith(".mp4"))return "video/mp4";if(value.endsWith(".webm"))return "video/webm";if(value.endsWith(".mov"))return "video/quicktime";return "application/octet-stream";}
    private void requireKey(String key){if(key==null||key.isBlank()||key.startsWith("/")||key.contains("..")||key.contains("\\")||key.chars().anyMatch(Character::isISOControl)||!ROOTS.contains(root(key)))throw new IllegalArgumentException("Invalid S3 object key");}
    private String root(String key){int slash=key.indexOf('/');return slash<1?"":key.substring(0,slash);}
    private StorageUnavailableException unavailable(S3Exception e){return new StorageUnavailableException("Amazon S3 is unavailable",e);}
}
