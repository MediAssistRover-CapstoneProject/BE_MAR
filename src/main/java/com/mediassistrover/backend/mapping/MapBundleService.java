package com.mediassistrover.backend.mapping;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediassistrover.backend.entity.FacilityMap;
import com.mediassistrover.backend.enums.MapStatus;
import com.mediassistrover.backend.exception.*;
import com.mediassistrover.backend.repository.*;
import com.mediassistrover.backend.security.DevicePrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

@Service @RequiredArgsConstructor
public class MapBundleService {
    private final FacilityMapRepository maps;
    private final FloorRepository floors;
    private final RobotRepository robots;
    private final MappingArchiveReader reader;
    private final MappingArtifactService artifacts;
    private final MapArtifactRepository receipts;
    private final MappingArtifactStorage storage;
    private final MappingProperties properties;
    private final ObjectMapper json;
    public record Imported(UUID mapId,String assetHash,String status) { }
    private Path imported(UUID mapId) throws IOException {
        Path p=Path.of(properties.getStorageDir()).toAbsolutePath().normalize().resolve("imports").resolve(mapId.toString());
        Files.createDirectories(p);
        if(!p.toRealPath().equals(p))throw new BusinessRuleException("Imported map directory contains a symbolic link");
        return p;
    }
    @Transactional
    public Imported importMap(UUID floorId,String code,int version,String name,byte[] yaml,byte[] pgm) {
        if(maps.existsByMapCodeIgnoreCaseAndVersionNo(code,version))throw new ConflictException("Map code/version already exists");
        var floor=floors.findById(floorId).orElseThrow(()->new ResourceNotFoundException("Floor not found"));
        if(!floor.isActive())throw new BusinessRuleException("Map import requires an active floor");
        var meta=reader.inspectMap(yaml,pgm);String hash=fingerprint(yaml,pgm);
        var map=maps.saveAndFlush(FacilityMap.builder().floor(floor).mapCode(code).versionNo(version).mapName(name).status(MapStatus.DRAFT)
            .widthM(meta.path("width_m").decimalValue()).heightM(meta.path("height_m").decimalValue())
            .resolutionMPerPixel(meta.path("resolution_m_per_pixel").decimalValue()).originXM(meta.path("origin_x_m").decimalValue())
            .originYM(meta.path("origin_y_m").decimalValue()).originYawRad(meta.path("origin_yaw_rad").decimalValue())
            .frameId("map").mapFileUri("pending").assetHash(hash).build());
        try {
            Path root=imported(map.getMapId());Files.write(root.resolve("map.yaml"),yaml,StandardOpenOption.CREATE_NEW);
            Files.write(root.resolve("map.pgm"),pgm,StandardOpenOption.CREATE_NEW);
        } catch(IOException e){throw new BusinessRuleException("Cannot persist imported map");}
        map.setMapFileUri("/api/v1/pi/maps/"+map.getMapId()+"/bundle");
        return new Imported(map.getMapId(),hash,"DRAFT");
    }
    @Transactional(readOnly=true)
    public byte[] bundle(UUID id,DevicePrincipal device) {
        var map=maps.findById(id).orElseThrow(()->new ResourceNotFoundException("Map not found"));
        if(device!=null) {
            var robot=robots.findById(device.robotId()).orElseThrow();
            if(robot.getCurrentMap()==null||!robot.getCurrentMap().getMapId().equals(id))throw new AccessDeniedException("Map is not assigned to this robot");
        }
        try {
            Map<String,byte[]> files;
            String uri=map.getMapFileUri();
            if(uri.equals("/api/v1/pi/maps/"+id+"/bundle")) {
                Path root=imported(id);files=Map.of("map.yaml",read(root.resolve("map.yaml"),65536),"map.pgm",read(root.resolve("map.pgm"),4_100_000));
            } else if(uri.matches("/map-assets/[A-Za-z0-9_-]+/v[0-9]+/map.yaml")) {
                var yaml=new ClassPathResource("static"+uri);var pgm=new ClassPathResource("static"+uri.replace("map.yaml","map.pgm"));
                try(var a=yaml.getInputStream();var b=pgm.getInputStream()) {files=Map.of("map.yaml",reader.readBounded(a,65536),"map.pgm",reader.readBounded(b,4_100_000));}
            } else if(uri.startsWith("/api/v1/pi/mapping-artifacts/")) {
                UUID artifact=UUID.fromString(uri.substring(uri.lastIndexOf('/')+1));
                var receipt=receipts.findById(artifact).orElseThrow(()->new ResourceNotFoundException("Map artifact not found"));
                if(!id.equals(receipt.getMapId()))throw new BusinessRuleException("Map artifact is not linked to this map");
                files=reader.mapFiles(device==null?storage.read(artifact,receipt.getArchiveHash()):artifacts.download(artifact,device));
            } else throw new BusinessRuleException("Map URI is not a supported local asset/import; use the map import API");
            String hash=fingerprint(files.get("map.yaml"),files.get("map.pgm"));
            if(!hash.equalsIgnoreCase(map.getAssetHash()))throw new BusinessRuleException("Map asset hash differs from catalog");
            var meta=reader.inspectMap(files.get("map.yaml"),files.get("map.pgm"));
            if(Math.abs(meta.path("resolution_m_per_pixel").asDouble()-map.getResolutionMPerPixel().doubleValue())>1e-6
                    ||Math.abs(meta.path("width_m").asDouble()-map.getWidthM().doubleValue())>1e-5
                    ||Math.abs(meta.path("height_m").asDouble()-map.getHeightM().doubleValue())>1e-5
                    ||Math.abs(meta.path("origin_x_m").asDouble()-map.getOriginXM().doubleValue())>1e-6
                    ||Math.abs(meta.path("origin_y_m").asDouble()-map.getOriginYM().doubleValue())>1e-6
                    ||Math.abs(meta.path("origin_yaw_rad").asDouble()-map.getOriginYawRad().doubleValue())>1e-6)
                throw new BusinessRuleException("Map dimensions differ from catalog");
            meta.put("map_id",id.toString()).put("asset_hash",hash).put("frame_id",map.getFrameId());
            var output=new ByteArrayOutputStream();
            try(var zip=new ZipOutputStream(output)) {
                for(var e:files.entrySet()){zip.putNextEntry(new ZipEntry(e.getKey()));zip.write(e.getValue());zip.closeEntry();}
                zip.putNextEntry(new ZipEntry("metadata.json"));zip.write(json.writeValueAsBytes(meta));zip.closeEntry();
            }
            return output.toByteArray();
        } catch(IOException e){throw new BusinessRuleException("Map files are not available");}
    }
    private byte[] read(Path p,int max) throws IOException {
        if(!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS))throw new BusinessRuleException("Map file is missing");
        try(var input=Files.newInputStream(p)){return reader.readBounded(input,max);}
    }
    public static String fingerprint(byte[] yaml,byte[] pgm) {
        try{var d=MessageDigest.getInstance("SHA-256");d.update(yaml);d.update((byte)0);d.update(pgm);return HexFormat.of().formatHex(d.digest());}
        catch(Exception e){throw new IllegalStateException(e);}
    }
}
