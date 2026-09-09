package com.ccb.architecture.repository;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class ArchitectureRemainingProjectScopeMigrationMySqlTest {
    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("architecture_remaining_project_scope")
            .withUsername("test")
            .withPassword("test");

    @BeforeEach
    void cleanDatabase() throws Exception {
        flyway("157").clean();
        try (Connection connection = connection()) {
            execute(connection, "ALTER DATABASE `architecture_remaining_project_scope` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
    }

    @Test
    void migratesRemainingArchitectureTablesAndKeepsSharedTablesUnscoped() throws Exception {
        assertTrue(flyway("157").migrate().success);
        try (Connection connection = connection()) {
            ensureProject(connection, 101, "RDDMP-PLATFORM", "平台项目");
            execute(connection, "INSERT INTO arch_network_work_order (id,tenant_id,kind,action_type,subject,applicant_id,status,business_payload,created_by,updated_by) VALUES (501,1,'DNS','ADD','uat.example.test',1,'DRAFT',JSON_OBJECT('domainName','uat.example.test'),1,1)");
            execute(connection, "INSERT INTO arch_decision_matter (id,tenant_id,matter_no,title,problem,status,received_at,first_handling_deadline,proposer_id,proposer_name,submitter_id,submitter_name,created_by,updated_by) VALUES (601,1,'AD-2026-0001','项目隔离决策','验证项目隔离','SUBMITTED',NOW(),CURRENT_DATE,1,'管理员',1,'管理员',1,1)");
        }

        assertTrue(flyway("158").migrate().success);
        try (Connection connection = connection()) {
            assertEquals(101, scalar(connection, "SELECT project_id FROM arch_network_work_order WHERE id = 501"));
            assertEquals(101, scalar(connection, "SELECT project_id FROM arch_decision_matter WHERE id = 601"));
            assertColumnNullable(connection, "arch_network_work_order", "project_id", "NO");
            assertColumnNullable(connection, "arch_network_access_application", "project_id", "NO");
            assertColumnNullable(connection, "arch_decision_matter", "project_id", "NO");
            assertEquals("tenant_id,project_id,matter_no",
                    indexColumns(connection, "arch_decision_matter", "uk_arch_decision_matter_no"));
            assertEquals("tenant_id,project_id,address_type,address_value",
                    indexColumns(connection, "arch_external_network_address", "uk_arch_external_network_address_value"));
            assertEquals(0, scalar(connection, "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name IN ('arch_standard_document','arch_plan_template','arch_environment_type') AND column_name = 'project_id'"));
        }
    }

    @Test
    void failsBeforeSchemaChangesWhenTargetTenantHasNoDefaultProject() throws Exception {
        assertTrue(flyway("157").migrate().success);
        try (Connection connection = connection()) {
            execute(connection, "DELETE FROM pm_project WHERE tenant_id = 1");
            execute(connection, "INSERT INTO arch_network_work_order (id,tenant_id,kind,action_type,subject,applicant_id,status,business_payload,created_by,updated_by) VALUES (701,1,'CLB','OPEN','legacy-clb',1,'DRAFT',JSON_OBJECT('clbName','legacy-clb'),1,1)");
        }

        assertThrows(FlywayException.class, () -> flyway("158").migrate());
        try (Connection connection = connection()) {
            assertEquals(0, scalar(connection, "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'arch_network_work_order' AND column_name = 'project_id'"));
        }
    }

    private Flyway flyway(String target) {
        return Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("filesystem:" + migrationDirectory())
                .placeholders(java.util.Map.of("bootstrap_admin_password_hash", "test-hash"))
                .target(MigrationVersion.fromVersion(target))
                .cleanDisabled(false)
                .load();
    }

    private Connection connection() throws Exception {
        return DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
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

    private void ensureProject(Connection connection, long id, String code, String name) throws Exception {
        execute(connection, "DELETE FROM pm_project WHERE tenant_id = 1 AND project_code = '" + code + "'");
        execute(connection, "INSERT INTO pm_project (id,tenant_id,project_code,project_name,status,owner_id,created_by,deleted) VALUES (" + id + ",1,'" + code + "','" + name + "','RUNNING',1,1,0)");
    }

    private void assertColumnNullable(Connection connection, String table, String column, String nullable) throws Exception {
        try (var statement = connection.prepareStatement("SELECT IS_NULLABLE FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?")) {
            statement.setString(1, table);
            statement.setString(2, column);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals(nullable, result.getString(1));
            }
        }
    }

    private String indexColumns(Connection connection, String table, String index) throws Exception {
        try (var statement = connection.prepareStatement("SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ?")) {
            statement.setString(1, table);
            statement.setString(2, index);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getString(1);
            }
        }
    }

    private long scalar(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getLong(1);
        }
    }

    private void execute(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
