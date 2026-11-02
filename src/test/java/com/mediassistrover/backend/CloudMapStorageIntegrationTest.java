package com.mediassistrover.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediassistrover.backend.entity.*;
import com.mediassistrover.backend.enums.*;
import com.mediassistrover.backend.mapping.*;
import com.mediassistrover.backend.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CloudMapStorageIntegrationTest extends MySqlIntegrationTest {
    @Autowired MappingProperties properties;@Autowired MapBinaryStorage binary;@Autowired JdbcTemplate jdbc;
    @Autowired MapBundleService bundles;@Autowired ObjectMapper json;@Autowired MockMvc mvc;
    @Autowired FloorRepository floors;@Autowired AccountRepository accounts;@Autowired HospitalInformationRepository hospitals;
    @Autowired PlatformTransactionManager tm;
    @TempDir Path directory;
    MappingProperties.StorageBackend previous;String oldDirectory;
    @BeforeEach void setup(){previous=properties.getStorageBackend();oldDirectory=properties.getStorageDir();properties.setStorageDir(directory.resolve("uncreated").toString());}
    @AfterEach void restore(){properties.setStorageBackend(previous);properties.setStorageDir(oldDirectory);}
    UUID floor() {
        String code=UUID.randomUUID().toString();
        var a=accounts.save(Account.builder().accountCode(code).username(code).fullName("Cloud map").role(Role.ADMIN).authProvider(AuthProvider.LOCAL).status(AccountStatus.ACTIVE).passwordHash("unused").build());
        var h=hospitals.save(HospitalInformation.builder().updatedBy(a).infoCategory("GENERAL").title("Cloud map").content("Test").languageCode("vi").keywordsJson(json.createArrayNode()).status(HospitalInformationStatus.PUBLISHED).build());
        return floors.save(Floor.builder().hospitalInformation(h).floorCode(code).floorName("Cloud map").active(true).displayOrder(1).build()).getFloorId();
    }
    UUID imported(){return bundles.importMap(floor(),UUID.randomUUID().toString(),1,"Cloud",MappingTestArchive.YAML,MappingTestArchive.PGM).mapId();}
    @Test @Transactional @WithMockUser(roles="ADMIN")
    void newMapImageAndBundleComeFromDatabaseWithoutCreatingAnAssetDirectory() throws Exception {
        properties.setStorageBackend(MappingProperties.StorageBackend.DATABASE);UUID id=imported();
        assertThat(Files.exists(Path.of(properties.getStorageDir()))).isFalse();
        assertThat(new MapBinaryStorage(jdbc).read(MapBinaryStorage.mapKey(id,"map.pgm"),4_100_000)).isEqualTo(MappingTestArchive.PGM);
        mvc.perform(get("/api/v1/maps/{id}/image.png",id)).andExpect(status().isOk()).andExpect(content().contentType("image/png"));
        mvc.perform(get("/api/v1/pi/maps/{id}/bundle",id)).andExpect(status().isOk()).andExpect(content().contentType("application/zip"));
        mvc.perform(post("/api/v1/maps/{id}/storage/import-local",id)).andExpect(status().isOk()).andExpect(jsonPath("$.mapId").value(id.toString()));
    }
    @Test @Transactional @WithMockUser(roles="ADMIN")
    void explicitBackfillKeepsMapIdentityAndCanBeRetriedAfterLocalFilesDisappear() throws Exception {
        properties.setStorageBackend(MappingProperties.StorageBackend.FILESYSTEM);UUID id=imported();
        properties.setStorageBackend(MappingProperties.StorageBackend.DATABASE);
        mvc.perform(post("/api/v1/maps/{id}/storage/import-local",id)).andExpect(status().isOk()).andExpect(jsonPath("$.verified").value(true));
        Path local=Path.of(properties.getStorageDir()).resolve("imports").resolve(id.toString());
        Files.delete(local.resolve("map.pgm"));Files.delete(local.resolve("map.yaml"));
        mvc.perform(post("/api/v1/maps/{id}/storage/import-local",id)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/maps/{id}/image.png",id)).andExpect(status().isOk());
    }
    @Test void rollbackCannotLeaveAReadableCachedBlobOrOverwriteCommittedBytes() {
        String key=MapBinaryStorage.mapKey(UUID.randomUUID(),"map.pgm");var tx=new TransactionTemplate(tm);
        assertThatThrownBy(()->tx.executeWithoutResult(s->{binary.store(key,new byte[]{1,2});assertThat(binary.read(key,10)).containsExactly(1,2);throw new IllegalStateException("rollback");})).hasMessage("rollback");
        assertThat(binary.contains(key)).isFalse();assertThatThrownBy(()->binary.read(key,10)).hasMessageContaining("not stored");
        try {
            binary.store(key,new byte[]{3,4});
            assertThatThrownBy(()->binary.store(key,new byte[]{8,9})).hasMessageContaining("immutable");
            assertThat(new MapBinaryStorage(jdbc).read(key,10)).containsExactly(3,4);
        } finally {jdbc.update("DELETE FROM MapBinaryAsset WHERE asset_key=?",key);}
    }
    @Test @WithMockUser(roles="STAFF") void staffCannotBackfillServerFiles() throws Exception {
        mvc.perform(post("/api/v1/maps/{id}/storage/import-local",UUID.randomUUID())).andExpect(status().isForbidden());
    }
}
