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
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class ArchitectureCatalogMigrationMySqlTest {
    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("architecture_catalog")
            .withUsername("test")
            .withPassword("test");

    @BeforeEach
    void cleanDatabase() {
        flyway("37").clean();
    }

    @Test
    void migratesFromV36WithExactCatalogAndOnlySuperAdminGrants() throws Exception {
        assertTrue(flyway("36").migrate().success);
        try (Connection connection = connection()) {
            execute(connection, "INSERT INTO sys_role (id, tenant_id, role_code, role_name) VALUES (2, 1, 'ARCH_READER', '架构查看者')");
        }

        var result = flyway("37").migrate();
        assertTrue(result.success);
        assertEquals(1, result.migrationsExecuted);

        try (Connection connection = connection()) {
            assertEquals(3, count(connection, "SELECT COUNT(*) FROM sys_menu WHERE tenant_id = 1 AND id IN (600,601,602) AND deleted = 0"));
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM sys_menu WHERE id = 600 AND parent_id = 0 AND menu_type = 'directory' AND route_name = 'ArchitectureRoot' AND route_path = '/architecture' AND component_path = 'LAYOUT' AND permission_code IS NULL"));
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM sys_menu WHERE id = 601 AND parent_id = 600 AND route_name = 'ArchitectureLogicalSubsystems' AND route_path = '/architecture/logical-subsystems' AND component_path = 'architecture/logical-subsystems/index' AND permission_code = 'architecture:logical:list'"));
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM sys_menu WHERE id = 602 AND parent_id = 600 AND route_name = 'ArchitecturePhysicalSubsystems' AND route_path = '/architecture/physical-subsystems' AND component_path = 'architecture/physical-subsystems/index' AND permission_code = 'architecture:physical:list'"));

            assertEquals(3, count(connection, "SELECT COUNT(*) FROM sys_role_menu WHERE tenant_id = 1 AND role_id = 1 AND menu_id IN (600,601,602)"));
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM sys_role_menu WHERE tenant_id = 1 AND role_id = 2 AND menu_id IN (600,601,602)"));
            assertEquals(8, count(connection, "SELECT COUNT(*) FROM sys_menu_permission WHERE tenant_id = 1 AND id IN (6011,6012,6013,6014,6021,6022,6023,6024)"));
            assertEquals(8, count(connection, "SELECT COUNT(*) FROM sys_menu_permission WHERE tenant_id = 1 AND id IN (6011,6012,6013,6014,6021,6022,6023,6024) AND action_code IN ('read','create','update','delete')"));
            assertEquals("architecture:logical:list,architecture:logical:create,architecture:logical:update,architecture:logical:delete,architecture:physical:list,architecture:physical:create,architecture:physical:update,architecture:physical:delete",
                    value(connection, "SELECT GROUP_CONCAT(permission_code ORDER BY id) FROM sys_menu_permission WHERE tenant_id = 1 AND id IN (6011,6012,6013,6014,6021,6022,6023,6024)"));
            assertEquals(8, count(connection, "SELECT COUNT(*) FROM sys_role_permission WHERE tenant_id = 1 AND role_id = 1 AND permission_id IN (6011,6012,6013,6014,6021,6022,6023,6024)"));
            assertEquals(8, count(connection, "SELECT COUNT(*) FROM sys_role_permission WHERE tenant_id = 1 AND role_id = 1 AND permission_id BETWEEN 6011 AND 6024"));
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM sys_role_permission WHERE tenant_id = 1 AND role_id = 2 AND permission_id IN (6011,6012,6013,6014,6021,6022,6023,6024)"));

            assertEquals(6, count(connection, "SELECT COUNT(*) FROM sys_dict_type WHERE tenant_id = 1 AND id BETWEEN 360001 AND 360006 AND dict_code LIKE 'ARCH_%' AND deleted = 0"));
            assertEquals(6, count(connection, "SELECT COUNT(*) FROM sys_config WHERE tenant_id = 1 AND id BETWEEN 360101 AND 360106 AND category_id BETWEEN 360001 AND 360006 AND deleted = 0"));
            assertEquals("ARCH_DEPLOYMENT_PLATFORM,ARCH_SYSTEM_TYPE,ARCH_SYSTEM_OWNERSHIP,ARCH_RUNTIME,ARCH_SYSTEM_LEVEL,ARCH_DEVELOPMENT_FRAMEWORK",
                    value(connection, "SELECT GROUP_CONCAT(dict_code ORDER BY id) FROM sys_dict_type WHERE tenant_id = 1 AND id BETWEEN 360001 AND 360006"));
            assertEquals("architecture.deployment-platform.employee-channel-p2,architecture.system-type.application-platform,architecture.system-ownership.channel-integration,architecture.runtime.7x24,architecture.system-level.a-plus,architecture.development-framework.employee-channel-p2",
                    value(connection, "SELECT GROUP_CONCAT(config_key ORDER BY id) FROM sys_config WHERE tenant_id = 1 AND id BETWEEN 360101 AND 360106"));
            assertEquals("员工渠道平台（P2）", value(connection, "SELECT config_value FROM sys_config WHERE id = 360101"));
            assertEquals("应用平台类", value(connection, "SELECT config_value FROM sys_config WHERE id = 360102"));
            assertEquals("渠道整合层", value(connection, "SELECT config_value FROM sys_config WHERE id = 360103"));
            assertEquals("7*24", value(connection, "SELECT config_value FROM sys_config WHERE id = 360104"));
            assertEquals("A+", value(connection, "SELECT config_value FROM sys_config WHERE id = 360105"));
            assertEquals("员工渠道平台（P2）", value(connection, "SELECT config_value FROM sys_config WHERE id = 360106"));
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM biz_form_scope"));
            assertEquals(37, count(connection, "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1"));
        }

        var repeated = flyway("37").migrate();
        assertTrue(repeated.success);
        assertEquals(0, repeated.migrationsExecuted);
        try (Connection connection = connection()) {
            assertEquals(3, count(connection, "SELECT COUNT(*) FROM sys_role_menu WHERE tenant_id = 1 AND role_id = 1 AND menu_id IN (600,601,602)"));
            assertEquals(8, count(connection, "SELECT COUNT(*) FROM sys_role_permission WHERE tenant_id = 1 AND role_id = 1 AND permission_id IN (6011,6012,6013,6014,6021,6022,6023,6024)"));
            assertEquals(6, count(connection, "SELECT COUNT(*) FROM sys_config WHERE tenant_id = 1 AND id BETWEEN 360101 AND 360106"));
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
            if (Files.isDirectory(candidate)) {
                return candidate.toString();
            }
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("找不到 Flyway 迁移目录");
    }

    private long count(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(sql)) {
            assertTrue(rs.next());
            return rs.getLong(1);
        }
    }

    private String value(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(sql)) {
            assertTrue(rs.next());
            return rs.getString(1);
        }
    }

    private void execute(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
