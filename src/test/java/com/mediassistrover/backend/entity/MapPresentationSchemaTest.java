package com.mediassistrover.backend.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.nio.ByteBuffer;
import java.sql.*;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Validates the actual immutable Flyway DDL against Hibernate on an isolated MySQL 8 database. */
@EnabledIfEnvironmentVariable(named="MAP_SCHEMA_TEST_JDBC_URL", matches=".+")
class MapPresentationSchemaTest {
    private final String url=System.getenv("MAP_SCHEMA_TEST_JDBC_URL");
    private final String user=System.getenv().getOrDefault("MAP_SCHEMA_TEST_DB_USER","root");
    private final String password=System.getenv().getOrDefault("MAP_SCHEMA_TEST_DB_PASSWORD","");

    private Connection connection() throws SQLException {
        Connection connection=DriverManager.getConnection(url,user,password);
        // This test creates/drops tables; never run against an application or production database.
        if(!"mar_map_schema_test".equals(connection.getCatalog())) {
            connection.close();
            throw new IllegalStateException("Schema test requires the isolated mar_map_schema_test database");
        }
        return connection;
    }
    @BeforeEach void migrate() throws SQLException {
        try(Connection connection=connection(); Statement sql=connection.createStatement()) {
            sql.execute("CREATE TABLE `Map` (`map_id` BINARY(16) NOT NULL PRIMARY KEY) ENGINE=InnoDB");
            ScriptUtils.executeSqlScript(connection,new ClassPathResource("db/migration/V53__map_presentation.sql"));
        }
    }
    @AfterEach void cleanup() throws SQLException {
        try(Connection connection=connection(); Statement sql=connection.createStatement()) {
            sql.execute("DROP TABLE IF EXISTS `MapPresentation`");
            sql.execute("DROP TABLE IF EXISTS `Map`");
        }
    }
    @Test void deployedMigrationMatchesEntityAndPersistsJsonAndUuid() throws Exception {
        var registry=new StandardServiceRegistryBuilder()
            .applySetting("hibernate.connection.url",url)
            .applySetting("hibernate.connection.username",user)
            .applySetting("hibernate.connection.password",password)
            .applySetting("hibernate.hbm2ddl.auto","validate")
            .applySetting("hibernate.type.preferred_uuid_jdbc_type","BINARY")
            .applySetting("hibernate.jdbc.time_zone","UTC")
            .build();
        try(var factory=new MetadataSources(registry).addAnnotatedClass(MapPresentation.class).buildMetadata().buildSessionFactory()) {
            UUID id=UUID.randomUUID();
            try(Connection connection=connection(); var statement=connection.prepareStatement("INSERT INTO `Map` (`map_id`) VALUES (?)")) {
                statement.setBytes(1,ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array());
                statement.executeUpdate();
            }
            var json=new ObjectMapper();
            var row=new MapPresentation(); row.setMapId(id); row.setRevision(1); row.setSourceAssetHash("a".repeat(64));
            row.setSourceJson(json.createObjectNode().put("mapId",id.toString()));
            row.setDraftJson(json.createObjectNode().put("schemaVersion",1).set("features",json.createArrayNode()));
            row.setUpdatedAt(Instant.parse("2026-11-06T09:00:00Z"));
            try(var session=factory.openSession()) {
                var transaction=session.beginTransaction(); session.persist(row); transaction.commit();
            }
            try(var session=factory.openSession()) {
                var saved=session.find(MapPresentation.class,id);
                assertNotNull(saved); assertEquals(row.getSourceAssetHash(),saved.getSourceAssetHash());
                assertEquals(row.getDraftJson(),saved.getDraftJson()); assertEquals(row.getSourceJson(),saved.getSourceJson());
                assertEquals(row.getUpdatedAt(),saved.getUpdatedAt()); assertNull(saved.getPublishedJson());
            }
        } finally { StandardServiceRegistryBuilder.destroy(registry); }
    }
}
