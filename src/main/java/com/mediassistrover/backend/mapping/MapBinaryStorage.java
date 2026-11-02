package com.mediassistrover.backend.mapping;

import com.mediassistrover.backend.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.*;

/** Immutable DB blobs; bounded RAM cache only. No filesystem fallback in DATABASE mode. */
@Service @RequiredArgsConstructor
public class MapBinaryStorage {
    private final JdbcTemplate jdbc;
    private static final int MAX_BYTES=16*1024*1024, CACHE_BYTES=32*1024*1024;
    private final LinkedHashMap<String,byte[]> cache=new LinkedHashMap<>(16,.75f,true);
    private int cachedBytes;

    public static String mapKey(UUID id,String file) {
        if(!Set.of("map.yaml","map.pgm").contains(file))throw new IllegalArgumentException("Invalid map file");
        return "maps/"+id+"/"+file;
    }
    private void key(String key) {
        if(key==null||!key.matches("[a-z0-9/._-]{1,128}"))throw new IllegalArgumentException("Invalid binary asset key");
    }
    @Transactional
    public void store(String key,byte[] bytes) {
        key(key);
        if(bytes==null||bytes.length==0||bytes.length>MAX_BYTES)throw new BusinessRuleException("Map binary byte limit exceeded");
        String hash=MappingArchiveReader.hash(bytes);
        // Existing keys are never overwritten, including a concurrent retry.
        jdbc.update("INSERT INTO MapBinaryAsset(asset_key,content_hash,byte_size,payload) VALUES (?,?,?,?) "
            +"ON DUPLICATE KEY UPDATE asset_key=asset_key",key,hash,bytes.length,bytes);
        String stored=jdbc.queryForObject("SELECT content_hash FROM MapBinaryAsset WHERE asset_key=?",String.class,key);
        if(!hash.equals(stored))throw new BusinessRuleException("Cloud map asset is immutable; content differs");
        // A later receipt/catalog failure must not expose rolled-back bytes from RAM.
        cacheAfterCommit(key,bytes);
    }
    public boolean contains(String key) {
        key(key);return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM MapBinaryAsset WHERE asset_key=?)",Boolean.class,key));
    }
    public byte[] read(String key,int max) {
        key(key);
        synchronized(cache) {
            byte[] hit=cache.get(key);
            if(hit!=null){if(hit.length>max)throw new BusinessRuleException("Map binary byte limit exceeded");return hit.clone();}
        }
        var values=jdbc.query("SELECT content_hash,byte_size,payload FROM MapBinaryAsset WHERE asset_key=?",(rs,row)->{
            int size=rs.getInt("byte_size");
            if(size<1||size>max||size>MAX_BYTES)throw new BusinessRuleException("Map binary byte limit exceeded");
            byte[] raw=rs.getBytes("payload");
            if(raw.length!=size||!MappingArchiveReader.hash(raw).equals(rs.getString("content_hash")))
                throw new BusinessRuleException("Cloud map asset integrity failed");
            return raw;
        },key);
        if(values.isEmpty())throw new BusinessRuleException("Map asset is not stored in the database; import this map's local assets first");
        byte[] value=values.getFirst();cacheAfterCommit(key,value);return value.clone();
    }
    private void cacheAfterCommit(String key,byte[] bytes) {
        byte[] immutable=bytes.clone();
        if(TransactionSynchronizationManager.isSynchronizationActive()&&TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
                @Override public void afterCommit(){remember(key,immutable);}
            });
        } else remember(key,immutable);
    }
    private void remember(String key,byte[] bytes) {
        synchronized(cache) {
            byte[] old=cache.put(key,bytes);if(old!=null)cachedBytes-=old.length;cachedBytes+=bytes.length;
            var it=cache.entrySet().iterator();
            while(cachedBytes>CACHE_BYTES&&it.hasNext()){cachedBytes-=it.next().getValue().length;it.remove();}
        }
    }
}
