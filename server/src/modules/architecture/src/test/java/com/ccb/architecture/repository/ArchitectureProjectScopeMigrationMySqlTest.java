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
class ArchitectureProjectScopeMigrationMySqlTest {
    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("architecture_project_scope")
            .withUsername("test")
            .withPassword("test");

    @BeforeEach
    void cleanDatabase() throws Exception {
        flyway("155").clean();
        try (Connection connection = connection()) {
            execute(connection, "ALTER DATABASE `architecture_project_scope` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
    }

    @Test
    void migratesEmptyDatabaseWithoutInventingDefaultProject() throws Exception {
        assertTrue(flyway("156").migrate().success);

        try (Connection connection = connection()) {
            assertColumnNullable(connection, "arch_physical_subsystem", "project_id", "NO");
            assertColumnNullable(connection, "arch_environment", "project_id", "NO");
            assertColumnNullable(connection, "arch_resource_request", "project_id", "NO");
            assertColumnNullable(connection, "arch_environment_instance", "project_id", "NO");
            assertColumnNullable(connection, "arch_setup_plan", "project_id", "NO");
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name IN ('arch_plan_template','arch_plan_template_version') AND column_name = 'project_id'"));
        }
    }

    @Test
    void assignsLegacyRowsAndScopesPermanentUniquenessToProject() throws Exception {
        assertTrue(flyway("155").migrate().success);
        try (Connection connection = connection()) {
            insertProject(connection, 101, "RDDMP-PLATFORM", "平台项目");
            insertProject(connection, 102, "PROJECT-B", "项目B");
            execute(connection, "INSERT INTO arch_physical_subsystem (id,tenant_id,code,short_name,name,logical_subsystem_name,responsible_team_org_id,responsible_team_name_snapshot,status,row_version,created_by,updated_by) VALUES (501,1,'PAYMENT_AP','支付','支付系统','支付域',1,'支付团队','ACTIVE',0,1,1)");
        }

        assertTrue(flyway("156").migrate().success);
        try (Connection connection = connection()) {
            assertEquals(101, count(connection, "SELECT project_id FROM arch_physical_subsystem WHERE id = 501"));
            execute(connection, "INSERT INTO arch_physical_subsystem (id,tenant_id,project_id,code,short_name,name,logical_subsystem_name,responsible_team_org_id,responsible_team_name_snapshot,status,row_version,created_by,updated_by) VALUES (502,1,102,'PAYMENT_AP','支付B','支付系统','支付域',1,'支付团队','ACTIVE',0,1,1)");
            assertThrows(Exception.class, () -> execute(connection, "INSERT INTO arch_physical_subsystem (id,tenant_id,project_id,code,short_name,name,logical_subsystem_name,responsible_team_org_id,responsible_team_name_snapshot,status,row_version,created_by,updated_by) VALUES (503,1,101,'PAYMENT_AP','重复','另一名称','支付域',1,'支付团队','ACTIVE',0,1,1)"));
            assertThrows(Exception.class, () -> execute(connection, "INSERT INTO arch_physical_subsystem (id,tenant_id,project_id,code,short_name,name,logical_subsystem_name,responsible_team_org_id,responsible_team_name_snapshot,status,deleted,row_version,created_by,updated_by) VALUES (504,1,101,'PAYMENT_AP','删除后重复','删除记录仍占号','支付域',1,'支付团队','ACTIVE',1,0,1,1)"));
            assertEquals("tenant_id,project_id,code", indexColumns(connection, "arch_physical_subsystem", "uk_arch_physical_code"));
        }
    }

    @Test
    void failsBeforeSchemaChangesWhenLegacyTenantHasNoDefaultProject() throws Exception {
        assertTrue(flyway("155").migrate().success);
        try (Connection connection = connection()) {
            execute(connection, "INSERT INTO arch_physical_subsystem (id,tenant_id,code,short_name,name,logical_subsystem_name,responsible_team_org_id,responsible_team_name_snapshot,status,row_version,created_by,updated_by) VALUES (601,1,'LEGACY','存量','存量系统','存量域',1,'存量团队','ACTIVE',0,1,1)");
        }

        assertThrows(FlywayException.class, () -> flyway("156").migrate());
        try (Connection connection = connection()) {
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'arch_physical_subsystem' AND column_name = 'project_id'"));
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

    private void insertProject(Connection connection, long id, String code, String name) throws Exception {
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

    private long count(Connection connection, String sql) throws Exception {
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
