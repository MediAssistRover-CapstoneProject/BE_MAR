package fu.se181738.backend_robot_v1;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.persistence.*;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.boot.spi.MetadataImplementor;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Table;
import org.hibernate.tool.schema.internal.SchemaCreatorImpl;
import org.hibernate.type.SqlTypes;
import org.junit.jupiter.api.*;

/** Independently compares actual Hibernate metadata against the source Markdown. */
class EntitySchemaMappingTest {
    private static final String ENTITY_PACKAGE = "fu.se181738.backend_robot_v1.entity.";
    private static final Path SOURCE = Path.of("docs/logicalDiagram.md");
    private static StandardServiceRegistry registry;
    private static MetadataImplementor metadata;
    private static Map<String, SourceTable> schema;
    private static String source;

    record SourceField(String name, String type, String key, String nullable,
                       String reference, String unique, String defaultValue, String description) {}
    record SourceTable(List<SourceField> fields, String body) {}

    @BeforeAll
    static void bootstrap() throws Exception {
        source = Files.readString(SOURCE);
        schema = readSchema(source);
        registry = new StandardServiceRegistryBuilder()
                .disableAutoClose()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.MySQLDialect")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", false)
                .applySetting("hibernate.hbm2ddl.auto", "none")
                .applySetting("hibernate.physical_naming_strategy",
                        "org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl")
                .build();
        MetadataSources sources = new MetadataSources(registry);
        for (String name : schema.keySet()) {
            sources.addAnnotatedClass(Class.forName(ENTITY_PACKAGE + name));
        }
        metadata = (MetadataImplementor) sources.buildMetadata();
        metadata.validate();
    }

    @AfterAll
    static void close() {
        if (registry != null) StandardServiceRegistryBuilder.destroy(registry);
    }

    @Test
    void exactTablesColumnsPrimaryKeysNullabilityAndTypes() throws Exception {
        assertEquals(40, schema.size());
        assertEquals(40, metadata.getEntityBindings().size());
        try (var files = Files.list(Path.of("src/main/java/fu/se181738/backend_robot_v1/entity"))) {
            assertEquals(schema.keySet(), files.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.getFileName().toString().equals("EndpointGroupMemberId.java"))
                    .map(p -> p.getFileName().toString().replace(".java", "")).collect(Collectors.toSet()));
        }
        int total = 0;
        for (var entry : schema.entrySet()) {
            String name = entry.getKey();
            Table table = binding(name).getTable();
            assertEquals(name, table.getName(), name);
            assertEquals(entry.getValue().fields().stream().map(SourceField::name).collect(Collectors.toSet()),
                    columnNames(table.getColumns()), name + " columns");
            assertEquals(entry.getValue().fields().stream().filter(f -> f.key().contains("PK"))
                            .map(SourceField::name).collect(Collectors.toSet()),
                    columnNames(table.getPrimaryKey().getColumns()), name + " PK");
            for (SourceField f : entry.getValue().fields()) {
                String label = name + "." + f.name();
                Column column = table.getColumn(new Column(f.name()));
                assertEquals(f.nullable().equals("Nullable"), column.isNullable(), label);
                Field field = Class.forName(ENTITY_PACKAGE + name).getDeclaredField(f.name());
                assertNull(field.getAnnotation(GeneratedValue.class), label);
                assertNull(column.getDefaultValue(), label + " must not infer SQL default");
                if (f.key().contains("FK") && field.getType() != UUID.class) {
                    assertEquals(f.reference().split("\\.")[0], field.getType().getSimpleName(), label);
                } else {
                    Class<?> expected = switch (f.type()) {
                        case "UUID" -> UUID.class;
                        case "int" -> Integer.class;
                        case "bigint" -> Long.class;
                        case "bool" -> Boolean.class;
                        case "datetime UTC" -> Instant.class;
                        case "enum" -> field.getType();
                        default -> f.type().startsWith("decimal") ? BigDecimal.class : String.class;
                    };
                    assertEquals(expected, field.getType(), label);
                }
                if (f.type().startsWith("varchar(") || f.type().startsWith("char(")) {
                    long length = Long.parseLong(f.type().replaceAll("\\D", ""));
                    assertEquals(length, column.getLength(), label);
                }
                if (f.type().startsWith("char(")) assertEquals(SqlTypes.CHAR, column.getSqlTypeCode(metadata), label);
                if (f.type().equals("text")) assertEquals("text", column.getSqlType(metadata), label);
                if (f.type().equals("json")) assertEquals(SqlTypes.JSON, column.getSqlTypeCode(metadata), label);
                if (f.type().startsWith("decimal")) {
                    var digits = Pattern.compile("decimal\\((\\d+),(\\d+)\\)").matcher(f.type());
                    assertTrue(digits.matches());
                    assertEquals(Integer.valueOf(digits.group(1)), column.getPrecision(), label);
                    assertEquals(Integer.valueOf(digits.group(2)), column.getScale(), label);
                }
                total++;
            }
        }
        assertEquals(550, total);
    }

    @Test
    void allExplicitForeignKeysAndOnlyThreeFullySpecifiedCompositeForeignKeys() {
        Set<String> expected = new HashSet<>();
        String summary = source.split("# Foreign Key Summary")[1].split("## Composite FK notes")[0];
        for (String line : summary.lines().toList()) {
            if (!line.startsWith("| `")) continue;
            String[] cells = cells(line);
            String[] target = cells[2].split("\\.");
            expected.add(cells[0]+"("+cells[1]+") -> "+target[0]+"("+target[1]+")");
        }
        assertEquals(110, expected.size());
        // Annotation-only mapping represents these three relationships by their
        // stronger source-defined composite FK, rather than a redundant single FK.
        assertTrue(expected.remove("Endpoint(zone_id) -> Zone(zone_id)"));
        assertTrue(expected.remove("Step(task_id) -> Task(task_id)"));
        assertTrue(expected.remove("EmergencyIncident(endpoint_id) -> Endpoint(endpoint_id)"));
        expected.add("Endpoint(map_id,zone_id) -> Zone(map_id,zone_id)");
        expected.add("Step(workflow_id,task_id) -> Task(workflow_id,task_id)");
        expected.add("EmergencyIncident(map_id,endpoint_id) -> Endpoint(map_id,endpoint_id)");
        Set<String> actual = new HashSet<>();
        for (PersistentClass entity : metadata.getEntityBindings()) {
            for (var fk : entity.getTable().getForeignKeys().values()) {
                List<Column> target = fk.isReferenceToPrimaryKey()
                        ? fk.getReferencedTable().getPrimaryKey().getColumns() : fk.getReferencedColumns();
                actual.add(entity.getTable().getName()+"("+joined(fk.getColumns())+") -> "
                        +fk.getReferencedTable().getName()+"("+joined(target)+")");
            }
        }
        // Hibernate may reorder both sides of a composite FK when building a
        // synthetic referenced property. Compare paired columns, never each side independently.
        assertEquals(expected.stream().map(EntitySchemaMappingTest::canonicalForeignKey).collect(Collectors.toSet()),
                actual.stream().map(EntitySchemaMappingTest::canonicalForeignKey).collect(Collectors.toSet()));
    }

    @Test
    void uniqueConstraintsMatchSourceAndConditionalUniqueIsNotInvented() {
        int singleUk = 0;
        for (var entry : schema.entrySet()) {
            Set<Set<String>> expected = new HashSet<>();
            for (SourceField f : entry.getValue().fields()) {
                if (f.key().contains("UK")) {
                    expected.add(Set.of(f.name()));
                    singleUk++;
                }
            }
            {
                var matches = Pattern.compile("UQ\\(([^)]+)\\)")
                        .matcher(entry.getValue().body().split("### Constraints and source notes")[1]);
                while (matches.find()) {
                    expected.add(Arrays.stream(matches.group(1).split(",")).map(String::trim).collect(Collectors.toSet()));
                }
            }
            Table table = binding(entry.getKey()).getTable();
            Set<Set<String>> actual = new HashSet<>();
            for (var uk : table.getUniqueKeys().values()) actual.add(columnNames(uk.getColumns()));
            for (Column col : table.getColumns()) if (col.isUnique()) actual.add(Set.of(col.getName()));
            // PK uniqueness is intrinsic, not an additional UK declaration.
            actual.remove(columnNames(table.getPrimaryKey().getColumns()));
            assertEquals(expected, actual, entry.getKey());
        }
        assertEquals(19, singleUk);
    }

    @Test
    void relationshipCardinalityOptionalityAndNoInverseOrCascadeMappings() throws Exception {
        Map<String, Boolean> oneToOne = new HashMap<>();
        String summary = source.split("# Relationship Summary")[1].split("## Many-to-many")[0];
        for (String line : summary.lines().toList()) {
            if (line.startsWith("| `")) {
                String[] cells = cells(line);
                oneToOne.put(cells[3], cells[1].startsWith("1:1"));
            }
        }
        Set<String> actual = new HashSet<>();
        int oneCount = 0;
        for (String name : schema.keySet()) {
            for (Field field : Class.forName(ENTITY_PACKAGE+name).getDeclaredFields()) {
                assertNull(field.getAnnotation(OneToMany.class));
                assertNull(field.getAnnotation(ManyToMany.class));
                ManyToOne many = field.getAnnotation(ManyToOne.class);
                OneToOne one = field.getAnnotation(OneToOne.class);
                if (many == null && one == null) continue;
                JoinColumn join = field.getAnnotation(JoinColumn.class);
                if (join == null) {
                    JoinColumns composite = field.getAnnotation(JoinColumns.class);
                    assertNotNull(composite);
                    assertEquals(2, composite.value().length);
                    for (JoinColumn part : composite.value()) {
                        assertFalse(part.insertable());
                        assertFalse(part.updatable());
                    }
                    join = composite.value()[1];
                }
                assertNotNull(join);
                String key = name+"."+join.name();
                assertTrue(actual.add(key));
                assertEquals(oneToOne.get(key), one != null, key);
                String joinName = join.name();
                SourceField f = schema.get(name).fields().stream().filter(s -> s.name().equals(joinName)).findFirst().orElseThrow();
                assertEquals(f.nullable().equals("Nullable"), many == null ? one.optional() : many.optional(), key);
                assertEquals(0, many == null ? one.cascade().length : many.cascade().length, key);
                if (one != null) { assertEquals("", one.mappedBy()); oneCount++; }
            }
        }
        assertEquals(oneToOne.keySet(), actual);
        assertEquals(6, oneCount);
    }

    @Test
    void enumsUseOnlyDatabaseFieldVocabulary() throws Exception {
        Pattern vocabulary = Pattern.compile("^([A-Z][A-Z_]*(?:(?:, | hoặc )[A-Z][A-Z_]*)*)");
        for (var entry : schema.entrySet()) {
            for (SourceField f : entry.getValue().fields()) {
                if (!f.type().equals("enum")) continue;
                Field field = Class.forName(ENTITY_PACKAGE+entry.getKey()).getDeclaredField(f.name());
                assertTrue(field.getType().isEnum());
                assertEquals(EnumType.STRING, field.getAnnotation(Enumerated.class).value());
                String description = f.description();
                if (entry.getKey().equals("RobotStatusHistory")) {
                    description = schema.get("Robot").fields().stream().filter(r -> r.name().equals("status"))
                            .findFirst().orElseThrow().description();
                }
                var match = vocabulary.matcher(description);
                assertTrue(match.find());
                assertEquals(Arrays.asList(match.group(1).split(", | hoặc ")),
                        Arrays.stream(field.getType().getEnumConstants()).map(Object::toString).toList());
            }
        }
        // MOVE contract has PATROL; NavigationRequest.navigation_mode does not.
        assertEquals(List.of("NORMAL", "ESCORT"), Arrays.stream(
                Class.forName(ENTITY_PACKAGE+"NavigationRequest").getDeclaredField("navigation_mode")
                        .getType().getEnumConstants()).map(Object::toString).toList());
    }

    @Test
    void sessionFactoryAndGenericJsonSupportAllJsonShapesWithoutDatabase() {
        try (var factory = metadata.buildSessionFactory(); var session = factory.openSession()) {
            var internal = session.unwrap(org.hibernate.engine.spi.SharedSessionContractImplementor.class);
            var mapper = internal.getJsonFormatMapper();
            var type = org.hibernate.type.descriptor.java.StringJavaType.INSTANCE;
            for (String raw : List.of("{\"key\":null}", "[1,true,null]", "\"text\"", "42", "false", "null")) {
                assertEquals(raw, mapper.toString(raw, type, internal));
                assertEquals(raw, mapper.fromString(raw, type, internal));
            }
        }
    }

    @Test
    void mysqlSchemaGenerationIncludesBothSingleAndCompositeForeignKeys() throws Exception {
        List<String> commands = new SchemaCreatorImpl(registry).generateCreationCommands(metadata, false);
        String ddl = String.join(";\n", commands);
        assertTrue(ddl.contains("fk_endpoint_map_zone"));
        assertTrue(ddl.contains("fk_step_workflow_task"));
        assertTrue(ddl.contains("fk_incident_map_endpoint"));
        assertEquals(110, commands.stream().filter(c -> c.contains(" foreign key ")).count());
        // Generated only to a review artifact; never executed against a database.
        Files.writeString(Path.of("target/entity-schema-review.sql"), ddl);
    }

    private static PersistentClass binding(String name) { return metadata.getEntityBinding(ENTITY_PACKAGE+name); }
    private static String canonicalForeignKey(String signature) {
        var match = Pattern.compile("(\\w+)\\(([^)]+)\\) -> (\\w+)\\(([^)]+)\\)").matcher(signature);
        assertTrue(match.matches(), signature);
        String[] child = match.group(2).split(",");
        String[] parent = match.group(4).split(",");
        assertEquals(child.length, parent.length);
        SortedSet<String> pairs = new TreeSet<>();
        for (int i = 0; i < child.length; i++) pairs.add(child[i]+"="+parent[i]);
        return match.group(1)+" -> "+match.group(3)+pairs;
    }
    private static Set<String> columnNames(Collection<Column> columns) {
        return columns.stream().map(Column::getName).collect(Collectors.toSet());
    }
    private static String joined(List<Column> columns) {
        return columns.stream().map(Column::getName).collect(Collectors.joining(","));
    }
    private static String[] cells(String line) {
        return Arrays.stream(line.substring(1, line.length()-1).split("\\|", -1))
                .map(String::trim).map(s -> s.replace("`", "")).toArray(String[]::new);
    }
    private static Map<String, SourceTable> readSchema(String markdown) {
        String[] chunks = markdown.split("# Relationship Summary")[0].split("(?m)^## (\\w+)\\s*$");
        var headings = Pattern.compile("(?m)^## (\\w+)\\s*$")
                .matcher(markdown.split("# Relationship Summary")[0]);
        Map<String, SourceTable> result = new LinkedHashMap<>();
        int i = 1;
        while (headings.find()) {
            String body = chunks[i++];
            List<SourceField> fields = body.lines().filter(l -> l.startsWith("| `")).map(l -> {
                String[] c = cells(l);
                assertEquals(8, c.length);
                return new SourceField(c[0],c[1],c[2],c[3],c[4],c[5],c[6],c[7]);
            }).toList();
            result.put(headings.group(1), new SourceTable(fields, body));
        }
        return result;
    }
}
