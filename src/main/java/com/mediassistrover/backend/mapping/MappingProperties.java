package com.mediassistrover.backend.mapping;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component @ConfigurationProperties("app.mapping") @Getter @Setter
public class MappingProperties {
    public enum StorageBackend { FILESYSTEM, DATABASE }
    private boolean enabled;
    private StorageBackend storageBackend=StorageBackend.FILESYSTEM;
    private String storageDir="./data/map-artifacts";
    private int maxArchiveBytes=16*1024*1024;
    private int maxUncompressedBytes=12*1024*1024;
    private int startTimeoutSec=45;
    public boolean databaseStorage(){return storageBackend==StorageBackend.DATABASE;}
}
