package com.mediassistrover.backend.mapping;
import com.mediassistrover.backend.exception.BusinessRuleException;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import java.io.*;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;
import java.util.*;

/** Immutable archives. DATABASE bytes join the receipt transaction; legacy FILESYSTEM remains available. */
@Component
public class MappingArtifactStorage {
    private final MappingProperties properties;
    private final MappingArchiveReader reader;
    private final MapBinaryStorage binary;
    private final com.mediassistrover.backend.storage.S3ObjectStorageService s3;
    @Autowired public MappingArtifactStorage(MappingProperties properties,MappingArchiveReader reader,MapBinaryStorage binary,com.mediassistrover.backend.storage.S3ObjectStorageService s3){this.properties=properties;this.reader=reader;this.binary=binary;this.s3=s3;}
    public MappingArtifactStorage(MappingProperties properties,MappingArchiveReader reader,MapBinaryStorage binary){this.properties=properties;this.reader=reader;this.binary=binary;this.s3=null;}
    private String archiveKey(UUID artifact,String hash) {
        if(!hash.matches("[a-f0-9]{64}"))throw new BusinessRuleException("Invalid stored hash");
        return "archives/"+artifact+"/"+hash;
    }
    private Path path(UUID artifact,String archiveHash) throws IOException {
        if(!archiveHash.matches("[a-f0-9]{64}"))throw new BusinessRuleException("Invalid stored hash");
        Path root=Path.of(properties.getStorageDir()).toAbsolutePath().normalize();Files.createDirectories(root);
        if(!root.toRealPath().equals(root))throw new BusinessRuleException("Artifact root must not contain symbolic links");
        Path dir=root.resolve(artifact.toString());Files.createDirectories(dir);
        if(!dir.toRealPath().equals(dir))throw new BusinessRuleException("Artifact directory must not be a symbolic link");
        return dir.resolve(archiveHash+".tar.gz");
    }
    public void store(UUID artifact,MappingArchiveReader.Verified verified) {
        if(s3!=null)s3.put(s3Key(artifact),new ByteArrayInputStream(verified.archive()),verified.archive().length,"application/gzip",verified.archiveHash(),true);
        if(properties.databaseStorage()){binary.store(archiveKey(artifact,verified.archiveHash()),verified.archive());return;}
        Path temporary=null;
        try {
            Path target=path(artifact,verified.archiveHash());
            if(Files.exists(target,LinkOption.NOFOLLOW_LINKS)) {read(artifact,verified.archiveHash());return;}
            temporary=Files.createTempFile(target.getParent(),"upload-",".tmp");
            try(var file=FileChannel.open(temporary,StandardOpenOption.WRITE)) {ByteBuffer data=ByteBuffer.wrap(verified.archive());while(data.hasRemaining())file.write(data);file.force(true);}
            Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE);temporary=null;
            // Directory fsync is supported on Unix. Windows persists the file plus atomic rename.
            if(!System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win"))try(var dir=FileChannel.open(target.getParent(),StandardOpenOption.READ)){dir.force(true);}
        } catch(IOException ex){throw new BusinessRuleException("Artifact durable storage failed");}
        finally {if(temporary!=null)try{Files.deleteIfExists(temporary);}catch(IOException ignored){}}
    }
    public byte[] read(UUID artifact,String hash) {
        if(s3!=null)try{byte[] raw=s3.get(s3Key(artifact),properties.getMaxArchiveBytes());if(!MappingArchiveReader.hash(raw).equals(hash))throw new BusinessRuleException("Stored archive integrity failed");return raw;}catch(com.mediassistrover.backend.exception.ResourceNotFoundException ignored){}
        if(properties.databaseStorage()) {
            byte[] raw=binary.read(archiveKey(artifact,hash),properties.getMaxArchiveBytes());
            if(!MappingArchiveReader.hash(raw).equals(hash))throw new BusinessRuleException("Stored archive integrity failed");
            return raw;
        }
        return readLocal(artifact,hash);
    }
    public String publicUrl(UUID artifact){if(s3==null)throw new BusinessRuleException("S3 storage is unavailable");return s3.publicUrl(s3Key(artifact));}
    private String s3Key(UUID artifact){return "maps/"+artifact+".tar.gz";}
    public void importLocal(UUID artifact,String assetHash,String archiveHash) {
        if(!properties.databaseStorage())throw new BusinessRuleException("DATABASE map storage is required");
        String key=archiveKey(artifact,archiveHash);
        byte[] raw=binary.contains(key)?binary.read(key,properties.getMaxArchiveBytes()):readLocal(artifact,archiveHash);
        var verified=reader.verify(new ByteArrayInputStream(raw),artifact,assetHash,archiveHash);
        store(artifact,verified);
    }
    private byte[] readLocal(UUID artifact,String hash) {
        try {
            Path path=path(artifact,hash);if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))throw new BusinessRuleException("Artifact file missing");
            byte[] archive;try(var input=Files.newInputStream(path)){archive=reader.readBounded(input,properties.getMaxArchiveBytes());}
            if(!MappingArchiveReader.hash(archive).equals(hash))throw new BusinessRuleException("Stored archive integrity failed");return archive;
        } catch(IOException ex){throw new BusinessRuleException("Stored archive cannot be read");}
    }
}
