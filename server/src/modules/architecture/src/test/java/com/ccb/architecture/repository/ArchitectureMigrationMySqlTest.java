package com.ccb.architecture.repository;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class ArchitectureMigrationMySqlTest {
    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("architecture_empty")
            .withUsername("test")
            .withPassword("test");

    @BeforeEach
    void cleanDatabase() throws Exception {
        flyway("77").clean();
        try (Connection connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
            execute(connection, "ALTER DATABASE `architecture_empty` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
    }

    @Test
    void migratesEmptyDatabaseToV77WithTenantSafeConstraints() throws Exception {
        var migration = flyway("77").migrate();
        assertTrue(migration.success);

        try (Connection connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
            assertEquals(2, count(connection, "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name IN ('arch_logical_subsystem','arch_physical_subsystem')"));
            assertColumn(connection, "arch_logical_subsystem", "tenant_id", "NO", null);
            assertColumn(connection, "arch_physical_subsystem", "tenant_id", "NO", null);
            assertColumn(connection, "arch_physical_subsystem", "business_group_name", "YES", null);
            assertColumn(connection, "arch_physical_subsystem", "responsible_team_org_id", "NO", null);
            assertColumn(connection, "arch_physical_subsystem", "responsible_team_name_snapshot", "NO", null);
            assertColumn(connection, "arch_physical_subsystem", "owner_user_id", "YES", null);
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'arch_physical_subsystem' AND column_name = 'contact_user_id'"));
            assertEquals("tenant_id,code", indexColumns(connection, "arch_logical_subsystem", "uk_arch_logical_code"));
            assertEquals("tenant_id,name", indexColumns(connection, "arch_logical_subsystem", "uk_arch_logical_name"));
            assertEquals("tenant_id,code", indexColumns(connection, "arch_physical_subsystem", "uk_arch_physical_code"));
            assertEquals("tenant_id,name", indexColumns(connection, "arch_physical_subsystem", "uk_arch_physical_name"));
            assertEquals("tenant_id,logical_subsystem_id", foreignKeyColumns(connection));
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM FLW_EV_DATABASECHANGELOGLOCK WHERE id = 1 AND locked = 0"));
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM FLW_EV_DATABASECHANGELOG"));
            assertEquals(71, count(connection, "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1"));
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM flyway_schema_history WHERE script LIKE '%ensure_flowable_event_registry_metadata%'"));

            execute(connection, "INSERT INTO arch_logical_subsystem (id,tenant_id,code,short_name,name,business_org_id,contact_user_id,created_by,updated_by) VALUES (1,1,'LOGICAL_1','逻辑一','逻辑系统一',1,1,1,1)");
            assertThrows(Exception.class, () -> execute(connection, "INSERT INTO arch_logical_subsystem (id,tenant_id,code,short_name,name,business_org_id,contact_user_id,deleted,created_by,updated_by) VALUES (2,1,'LOGICAL_1','已删逻辑','另一个名称',1,1,1,1,1)"));
            assertThrows(Exception.class, () -> execute(connection, "INSERT INTO arch_physical_subsystem (id,tenant_id,code,short_name,name,logical_subsystem_id,responsible_team_org_id,responsible_team_name_snapshot,created_by,updated_by) VALUES (1,2,'PHYSICAL_1','物理一','物理系统一',1,1,'团队',1,1)"));
        }

        var repeated = flyway("77").migrate();
        assertTrue(repeated.success);
        assertEquals(0, repeated.migrationsExecuted);
        try (Connection connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM FLW_EV_DATABASECHANGELOGLOCK WHERE id = 1"));
            assertEquals(71, count(connection, "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1"));
        }
    }

    @Test
    void migratesIncrementallyFromV76ToV77() throws Exception {
        assertTrue(flyway("76").migrate().success);
        var result = flyway("77").migrate();
        assertTrue(result.success);
        assertEquals(1, result.migrationsExecuted);
        try (Connection connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
            assertEquals(2, count(connection, "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name LIKE 'arch_%_subsystem'"));
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM FLW_EV_DATABASECHANGELOGLOCK WHERE id = 1"));
            assertEquals(71, count(connection, "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1"));
        }
    }

    @Test
    void migratesLegacyMqDeploymentUnitsFromV147ToV148() throws Exception {
        assertTrue(flyway("147").migrate().success);
        try (Connection connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
            execute(connection, "INSERT INTO arch_physical_subsystem "
                    + "(id,tenant_id,code,short_name,name,logical_subsystem_name,responsible_team_org_id,responsible_team_name_snapshot,status,row_version,created_by,updated_by) "
                    + "VALUES (501,1,'W0001A','渠道接入','渠道接入系统','渠道域逻辑子系统',1,'渠道团队','ACTIVE',0,1,1)");
            execute(connection, "INSERT INTO arch_deployment_unit "
                    + "(id,tenant_id,code,physical_subsystem_id,short_name,name,kind,deployment_unit_type,status,current_version,created_by,updated_by) "
                    + "VALUES (601,1,'DW0001A001',501,'消息服务','消息服务','MQ','AP','ACTIVE',1,1,1)");
            execute(connection, "INSERT INTO arch_deployment_unit_version "
                    + "(id,tenant_id,unit_id,version_no,short_name,name,kind,deployment_unit_type,published_by) "
                    + "VALUES (701,1,601,1,'消息服务','消息服务','MQ','AP',1)");
        }

        var result = flyway("148").migrate();
        assertTrue(result.success);
        assertEquals(1, result.migrationsExecuted);
        try (Connection connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM arch_deployment_unit WHERE id = 601 AND kind = 'WEB' AND name = 'DW0001A001_WB'"));
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM arch_deployment_unit_version WHERE id = 701 AND kind = 'WEB' AND name = 'DW0001A001_WB'"));
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'arch_deployment_unit' AND column_name = 'short_name'"));
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

    private String migrationDirectory() {
        Path cursor = Path.of("").toAbsolutePath();
        while (cursor != null) {
            Path candidate = cursor.resolve("server/src/platform/infrastructure/src/main/resources/db/migration");
            if (Files.isDirectory(candidate)) {
                return candidate.toString();
            }
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("找不到 Flyway 迁移目录");
    }

    private void assertColumn(Connection connection, String table, String column, String nullable, String defaultValue) throws Exception {
        try (var statement = connection.prepareStatement("SELECT IS_NULLABLE, COLUMN_DEFAULT FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?")) {
            statement.setString(1, table);
            statement.setString(2, column);
            try (ResultSet rs = statement.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(nullable, rs.getString("IS_NULLABLE"));
                assertEquals(defaultValue, rs.getString("COLUMN_DEFAULT"));
                assertFalse(rs.next());
            }
        }
    }

    private String indexColumns(Connection connection, String table, String index) throws Exception {
        try (var statement = connection.prepareStatement("SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) columns_list FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ?")) {
            statement.setString(1, table);
            statement.setString(2, index);
            try (ResultSet rs = statement.executeQuery()) {
                assertTrue(rs.next());
                return rs.getString("columns_list");
            }
        }
    }

    private String foreignKeyColumns(Connection connection) throws Exception {
        String sql = "SELECT GROUP_CONCAT(column_name ORDER BY ordinal_position) columns_list FROM information_schema.key_column_usage WHERE table_schema = DATABASE() AND table_name = 'arch_physical_subsystem' AND referenced_table_name = 'arch_logical_subsystem'";
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(sql)) {
            assertTrue(rs.next());
            return rs.getString("columns_list");
        }
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
}
