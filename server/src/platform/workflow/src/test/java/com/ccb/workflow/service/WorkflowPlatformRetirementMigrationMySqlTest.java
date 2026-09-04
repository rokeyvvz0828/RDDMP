package com.ccb.workflow.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class WorkflowPlatformRetirementMigrationMySqlTest {
    private static final long TARGET_PROJECT_ID = 910000000003001L;
    private static final long GENERATED_ID_MIN = 913600000000000L;
    private static final long GENERATED_ID_MAX = 913699999999999L;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("workflow_platform_retirement")
            .withUsername("test")
            .withPassword("test")
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci");

    @BeforeEach
    void cleanDatabase() {
        flyway("135").clean();
    }

    @Test
    void migratesPlatformDefinitionsToTemplatesAndOnlyTargetProjectDrafts() throws Exception {
        assertTrue(flyway("135").migrate().success);

        try (Connection connection = connection()) {
            seedTargetProject(connection);
            seedRuntimePlatformDefinition(connection);
            assertEquals(11, count(connection, "SELECT COUNT(*) FROM wf_definition WHERE scope_type = 'PLATFORM' AND deleted = 0"));
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM pm_project WHERE id = 910000000003001 AND project_code = 'RDDMP-PLATFORM' AND deleted = 0"));

            Map<Long, String> definitionsBefore = rows(connection,
                    "SELECT id, CONCAT_WS('|', code, name, scope_type, COALESCE(project_id, 0), status, current_version, model_schema_version, deleted) snapshot "
                            + "FROM wf_definition WHERE scope_type = 'PLATFORM' AND deleted = 0 ORDER BY id");
            Map<Long, String> versionsBefore = rows(connection,
                    "SELECT v.id, CONCAT_WS('|', v.definition_id, v.version_no, v.status, v.model_schema_version, CAST(v.definition_json AS CHAR)) snapshot "
                            + "FROM wf_version v JOIN wf_definition d ON d.id = v.definition_id AND d.tenant_id = v.tenant_id "
                            + "WHERE d.scope_type = 'PLATFORM' AND d.deleted = 0 ORDER BY v.id");
            Map<Long, String> instancesBefore = rows(connection,
                    "SELECT id, CONCAT_WS('|', definition_id, version_no, status, COALESCE(project_id, 0)) snapshot FROM wf_instance ORDER BY id");

            assertTrue(flyway("136").migrate().success);

            assertEquals(11, count(connection, generatedDefinitions("TEMPLATE", null)));
            assertEquals(11, count(connection, generatedDefinitions("PROJECT", TARGET_PROJECT_ID)));
            assertEquals(0, count(connection,
                    "SELECT COUNT(*) FROM wf_definition WHERE id BETWEEN " + GENERATED_ID_MIN + " AND " + GENERATED_ID_MAX
                            + " AND scope_type = 'PROJECT' AND project_id <> " + TARGET_PROJECT_ID));
            assertEquals(22, count(connection,
                    "SELECT COUNT(*) FROM wf_version WHERE id BETWEEN " + GENERATED_ID_MIN + " AND " + GENERATED_ID_MAX
                            + " AND status = 'DRAFT' AND version_no = 1"));

            assertSanitizedDrafts(connection, "TEMPLATE");
            assertSanitizedDrafts(connection, "PROJECT");

            assertEquals(definitionsBefore, rows(connection,
                    "SELECT id, CONCAT_WS('|', code, name, scope_type, COALESCE(project_id, 0), status, current_version, model_schema_version, deleted) snapshot "
                            + "FROM wf_definition WHERE scope_type = 'PLATFORM' AND deleted = 0 ORDER BY id"));
            assertEquals(versionsBefore, rows(connection,
                    "SELECT v.id, CONCAT_WS('|', v.definition_id, v.version_no, v.status, v.model_schema_version, CAST(v.definition_json AS CHAR)) snapshot "
                            + "FROM wf_version v JOIN wf_definition d ON d.id = v.definition_id AND d.tenant_id = v.tenant_id "
                            + "WHERE d.scope_type = 'PLATFORM' AND d.deleted = 0 ORDER BY v.id"));
            assertEquals(instancesBefore, rows(connection,
                    "SELECT id, CONCAT_WS('|', definition_id, version_no, status, COALESCE(project_id, 0)) snapshot FROM wf_instance ORDER BY id"));

            assertEquals(0, flyway("136").migrate().migrationsExecuted);
            assertEquals(22, count(connection,
                    "SELECT COUNT(*) FROM wf_definition WHERE id BETWEEN " + GENERATED_ID_MIN + " AND " + GENERATED_ID_MAX));
        }
    }

    private void assertSanitizedDrafts(Connection connection, String scope) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(
                "SELECT CAST(v.definition_json AS CHAR) definition_json FROM wf_definition d "
                        + "JOIN wf_version v ON v.definition_id = d.id AND v.tenant_id = d.tenant_id "
                        + "WHERE d.id BETWEEN " + GENERATED_ID_MIN + " AND " + GENERATED_ID_MAX
                        + " AND d.scope_type = '" + scope + "' ORDER BY d.id")) {
            int definitions = 0;
            while (rs.next()) {
                definitions++;
                JsonNode graph = MAPPER.readTree(rs.getString("definition_json"));
                assertTrue(graph.path("nodes").isArray());
                for (JsonNode node : graph.path("nodes")) {
                    JsonNode config = node.path("config");
                    if ("APPROVAL".equals(node.path("type").asText())) {
                        assertTrue(config.path("assigneeIds").isArray());
                        assertEquals(0, config.path("assigneeIds").size());
                        assertTrue("STARTER".equals(config.path("assigneeType").asText())
                                || "TEMPLATE_PLACEHOLDER".equals(config.path("assigneeType").asText()));
                        assertFalse(config.has("roleIds"));
                        assertFalse(config.has("assigneeVariable"));
                        assertFalse(config.has("fieldName"));
                        assertFalse(config.has("expression"));
                        assertFalse(config.has("organizationId"));
                    }
                    if ("CC".equals(node.path("type").asText())) {
                        assertTrue(config.path("templatePlaceholder").asBoolean());
                        assertTrue(config.path("userIds").isArray());
                        assertEquals(0, config.path("userIds").size());
                    }
                }
            }
            assertEquals(11, definitions);
        }
    }

    private String generatedDefinitions(String scope, Long projectId) {
        return "SELECT COUNT(*) FROM wf_definition WHERE id BETWEEN " + GENERATED_ID_MIN + " AND " + GENERATED_ID_MAX
                + " AND scope_type = '" + scope + "' AND status = 'DRAFT' AND current_version = 0"
                + (projectId == null ? " AND project_id IS NULL" : " AND project_id = " + projectId);
    }

    private void seedRuntimePlatformDefinition(Connection connection) throws Exception {
        execute(connection, "INSERT INTO wf_definition "
                + "(id, tenant_id, code, name, scope_type, project_id, status, current_version, model_schema_version, deleted) VALUES "
                + "(913599000000001, 1, 'runtime.custom.review', '运行期自定义审批', 'PLATFORM', NULL, 'DRAFT', 0, 2, 0)");
        execute(connection, "INSERT INTO wf_version "
                + "(id, tenant_id, definition_id, version_no, definition_json, model_schema_version, status) VALUES "
                + "(913599000000002, 1, 913599000000001, 1, "
                + "'{\"schemaVersion\":2,\"variables\":[],\"formBindings\":[],\"nodes\":["
                + "{\"id\":\"start\",\"type\":\"START\",\"label\":\"发起\",\"position\":{\"x\":80,\"y\":160},\"config\":{}},"
                + "{\"id\":\"approve\",\"type\":\"APPROVAL\",\"label\":\"审批\",\"position\":{\"x\":360,\"y\":160},\"config\":{\"assigneeType\":\"VARIABLE\",\"assigneeVariable\":\"approverIds\",\"assigneeIds\":[101],\"roleIds\":[110],\"mode\":\"ANY\"}},"
                + "{\"id\":\"cc\",\"type\":\"CC\",\"label\":\"抄送\",\"position\":{\"x\":640,\"y\":160},\"config\":{\"userIds\":[1]}},"
                + "{\"id\":\"end\",\"type\":\"END\",\"label\":\"结束\",\"position\":{\"x\":920,\"y\":160},\"config\":{}}],"
                + "\"edges\":[{\"id\":\"e1\",\"source\":\"start\",\"target\":\"approve\"},{\"id\":\"e2\",\"source\":\"approve\",\"target\":\"cc\"},{\"id\":\"e3\",\"source\":\"cc\",\"target\":\"end\"}]}', 2, 'DRAFT')");
    }

    private void seedTargetProject(Connection connection) throws Exception {
        execute(connection, "INSERT INTO pm_project "
                + "(id, tenant_id, project_code, project_name, status, owner_id, created_by, deleted) VALUES "
                + "(910000000003001, 1, 'RDDMP-PLATFORM', '平台能力升级项目', 'RUNNING', 1, 1, 0)");
    }

    private Flyway flyway(String target) {
        return Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("filesystem:" + migrationDirectory())
                .placeholders(Map.of("bootstrap_admin_password_hash", "test-hash"))
                .target(MigrationVersion.fromVersion(target))
                .cleanDisabled(false)
                .load();
    }

    private String migrationDirectory() {
        Path cursor = Path.of("").toAbsolutePath();
        while (cursor != null) {
            Path candidate = cursor.resolve("server/src/platform/infrastructure/src/main/resources/db/migration");
            if (Files.isDirectory(candidate)) return candidate.toString();
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("找不到 Flyway 迁移目录");
    }

    private Connection connection() throws Exception {
        return DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    }

    private long count(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(sql)) {
            assertTrue(rs.next());
            return rs.getLong(1);
        }
    }

    private void execute(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private Map<Long, String> rows(Connection connection, String sql) throws Exception {
        Map<Long, String> result = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) result.put(rs.getLong("id"), rs.getString("snapshot"));
        }
        return result;
    }
}
