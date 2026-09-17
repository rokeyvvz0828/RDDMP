package com.ccb.workflow.service;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class WorkflowDefinitionSummaryMigrationMySqlTest {
    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("workflow_summary").withUsername("test").withPassword("test")
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci");

    @Test
    void backfillsLatestVersionAndConfigurationSummary() throws Exception {
        Flyway before = flyway("200");
        before.clean();
        assertTrue(before.migrate().success);
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO wf_definition (id, tenant_id, code, name, scope_type, project_id, status, current_version, deleted) VALUES (99001, 1, 'summary.test', 'Summary test', 'PROJECT', 80001, 'DRAFT', 0, 0)");
            statement.execute("INSERT INTO wf_version (id, tenant_id, definition_id, version_no, definition_json, status) VALUES (99011, 1, 99001, 1, '{\"nodes\":[]}', 'DRAFT')");
            statement.execute("INSERT INTO wf_version (id, tenant_id, definition_id, version_no, definition_json, status) VALUES (99012, 1, 99001, 2, '{\"nodes\":[{\"type\":\"APPROVAL\",\"config\":{\"assigneeType\":\"TEMPLATE_PLACEHOLDER\"}}]}', 'DRAFT')");
            statement.execute("INSERT INTO wf_definition (id, tenant_id, code, name, scope_type, project_id, status, current_version, deleted) VALUES (99002, 1, 'summary.cc', 'Summary CC', 'PROJECT', 80001, 'DRAFT', 0, 0)");
            statement.execute("INSERT INTO wf_version (id, tenant_id, definition_id, version_no, definition_json, status) VALUES (99021, 1, 99002, 1, '{\"nodes\":[{\"type\":\"CC\",\"config\":{\"templatePlaceholder\":true}}]}', 'DRAFT')");
            statement.execute("INSERT INTO wf_definition (id, tenant_id, code, name, scope_type, project_id, status, current_version, deleted) VALUES (99003, 1, 'summary.template', 'Summary template', 'TEMPLATE', NULL, 'DRAFT', 0, 0)");
            statement.execute("INSERT INTO wf_version (id, tenant_id, definition_id, version_no, definition_json, status) VALUES (99031, 1, 99003, 1, '{\"nodes\":[{\"type\":\"APPROVAL\",\"config\":{\"assigneeType\":\"TEMPLATE_PLACEHOLDER\"}}]}', 'DRAFT')");
        }
        assertTrue(flyway("203").migrate().success);
        try (Connection connection = connection(); Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery("SELECT id, latest_version_no, requires_configuration FROM wf_definition WHERE id IN (99001, 99002, 99003) AND tenant_id = 1 ORDER BY id")) {
            assertTrue(rows.next());
            assertEquals(99001, rows.getLong("id"));
            assertEquals(2, rows.getInt("latest_version_no"));
            assertEquals(1, rows.getInt("requires_configuration"));
            assertTrue(rows.next());
            assertEquals(99002, rows.getLong("id"));
            assertEquals(1, rows.getInt("latest_version_no"));
            assertEquals(1, rows.getInt("requires_configuration"));
            assertTrue(rows.next());
            assertEquals(99003, rows.getLong("id"));
            assertEquals(1, rows.getInt("latest_version_no"));
            assertEquals(0, rows.getInt("requires_configuration"));
        }
    }

    private Flyway flyway(String target) {
        return Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("filesystem:" + migrationDirectory()).target(target).cleanDisabled(false)
                .placeholders(Map.of("bootstrap_admin_password_hash", "$2a$10$test"))
                .load();
    }

    private String migrationDirectory() {
        Path cursor = Path.of("").toAbsolutePath();
        while (cursor != null) {
            Path candidate = cursor.resolve("server/src/platform/infrastructure/src/main/resources/db/migration");
            if (java.nio.file.Files.isDirectory(candidate)) return candidate.toString();
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("Migration directory not found");
    }

    private Connection connection() throws Exception { return DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()); }
}
