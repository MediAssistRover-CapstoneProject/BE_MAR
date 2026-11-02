package com.mediassistrover.backend.mapping;
import com.mediassistrover.backend.exception.BusinessRuleException;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import java.io.*;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;
import java.util.*;

/** Immutable content-addressed blobs. A rolled-back DB transaction may leave a safe orphan, never a receipt. */
@Component
public class MappingArtifactStorage {
    private final MappingProperties properties;
    private final MappingArchiveReader reader;
    private final com.mediassistrover.backend.storage.S3ObjectStorageService s3;
    @Autowired
    public MappingArtifactStorage(MappingProperties properties,MappingArchiveReader reader,com.mediassistrover.backend.storage.S3ObjectStorageService s3){this.properties=properties;this.reader=reader;this.s3=s3;}
    public MappingArtifactStorage(MappingProperties properties,MappingArchiveReader reader){this.properties=properties;this.reader=reader;this.s3=null;}
    private Path path(UUID artifact,String archiveHash) throws IOException {
        if(!archiveHash.matches("[a-f0-9]{64}"))throw new BusinessRuleException("Invalid stored hash");
        Path root=Path.of(properties.getStorageDir()).toAbsolutePath().normalize();Files.createDirectories(root);
        if(!root.toRealPath().equals(root))throw new BusinessRuleException("Artifact root must not contain symbolic links");
        Path dir=root.resolve(artifact.toString());Files.createDirectories(dir);
        if(!dir.toRealPath().equals(dir))throw new BusinessRuleException("Artifact directory must not be a symbolic link");
        return dir.resolve(archiveHash+".tar.gz");
    }
    public void store(UUID artifact,MappingArchiveReader.Verified verified) {
        if(s3==null){storeLocal(artifact,verified);return;}
        String key=key(artifact);
        try {var existing=s3.head(key,true);if(existing.sha256()!=null&&!existing.sha256().equals(verified.archiveHash()))throw new BusinessRuleException("Artifact ID reused with different content");return;}
        catch(com.mediassistrover.backend.exception.ResourceNotFoundException ignored) { }
        s3.put(key,new java.io.ByteArrayInputStream(verified.archive()),verified.archive().length,
                "application/gzip",verified.archiveHash(),true);
    }
    public byte[] read(UUID artifact,String hash) {
        if(s3!=null)try {byte[] archive=s3.get(key(artifact),properties.getMaxArchiveBytes());if(!MappingArchiveReader.hash(archive).equals(hash))throw new BusinessRuleException("Stored archive integrity failed");return archive;} catch(com.mediassistrover.backend.exception.ResourceNotFoundException missing) { /* local fallback */ }
        try {
            Path path=path(artifact,hash);if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))throw new BusinessRuleException("Artifact file missing");
            byte[] archive;try(var input=Files.newInputStream(path)){archive=reader.readBounded(input,properties.getMaxArchiveBytes());}
            if(!MappingArchiveReader.hash(archive).equals(hash))throw new BusinessRuleException("Stored archive integrity failed");return archive;
        } catch(IOException ex){throw new BusinessRuleException("Stored archive cannot be read");}
    }
    public String publicUrl(UUID artifact){return s3.publicUrl(key(artifact));}
    private String key(UUID artifact){return "maps/"+artifact+".tar.gz";}
    private void storeLocal(UUID artifact,MappingArchiveReader.Verified verified){Path temporary=null;try{Path target=path(artifact,verified.archiveHash());if(Files.exists(target,LinkOption.NOFOLLOW_LINKS)){read(artifact,verified.archiveHash());return;}temporary=Files.createTempFile(target.getParent(),"upload-",".tmp");try(var file=FileChannel.open(temporary,StandardOpenOption.WRITE)){ByteBuffer data=ByteBuffer.wrap(verified.archive());while(data.hasRemaining())file.write(data);file.force(true);}Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE);temporary=null;}catch(IOException ex){throw new BusinessRuleException("Artifact durable storage failed");}finally{if(temporary!=null)try{Files.deleteIfExists(temporary);}catch(IOException ignored){}}}
}
