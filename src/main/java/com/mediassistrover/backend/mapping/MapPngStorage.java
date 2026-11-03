package com.mediassistrover.backend.mapping;

import com.mediassistrover.backend.storage.S3ObjectStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;

/** One immutable display image per saved map; navigation still uses verified YAML/PGM. */
@Service @RequiredArgsConstructor
public class MapPngStorage {
    private final S3ObjectStorageService s3;
    private final Map<String,String> verified=Collections.synchronizedMap(new LinkedHashMap<>(16,.75f,true){
        @Override protected boolean removeEldestEntry(Map.Entry<String,String> entry){return size()>256;}
    });
    public record Stored(UUID mapId,String objectKey,String url,String sha256,int sizeBytes) { }
    public String key(UUID mapId){return "images/maps/"+mapId+".png";}
    public String url(UUID mapId){return s3.publicUrl(key(mapId));}
    public Stored store(UUID mapId,byte[] pgm){
        byte[] png=PgmPngRenderer.render(pgm);String hash=MappingArchiveReader.hash(png),key=key(mapId);
        if(!hash.equals(verified.get(key))){
            s3.putIfAbsent(key,png,"image/png",hash,true);
            verified.put(key,hash);
        }
        return new Stored(mapId,key,url(mapId),hash,png.length);
    }
}
