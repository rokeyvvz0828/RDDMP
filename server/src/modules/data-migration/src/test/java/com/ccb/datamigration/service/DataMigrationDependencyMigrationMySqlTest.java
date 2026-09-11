package com.ccb.datamigration.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** V199 迁移过程依赖文件专属域迁移测试：空表成功、非空失败关闭、列/索引/权限节点与重跑幂等（REQ-20260910-068）。 */
@Testcontainers
class DataMigrationDependencyMigrationMySqlTest {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("dependency_migration")
            .withUsername("test")
            .withPassword("test");

    @Test
    void v199RebuildsEmptyDependencyTableWithPermissionsAndIsIdempotent() throws Exception {
        try (Connection connection = connection()) {
            createLegacyDependencyTable(connection);
            createMenuPermissionTables(connection);
            List<String> statements = readMigrationStatements("V199__data_migration_dependency_domain.sql");

            for (String statement : statements) execute(connection, statement);

            assertV199Applied(connection);

            for (String statement : statements) execute(connection, statement);
            assertV199Applied(connection);
        }
    }

    @Test
    void v199FailsClosedWhenDependencyTableHasRows() throws Exception {
        try (Connection connection = connection()) {
            createLegacyDependencyTable(connection);
            createMenuPermissionTables(connection);
            cleanupProcedures(connection);
            execute(connection, """
                    INSERT INTO dm_dependency
                        (id, tenant_id, project_id, component_id, system_code, doc_code, doc_name, owner_id,
                         deleted, deleted_by, deleted_at, created_by, created_at, updated_by, updated_at)
                    VALUES (1, 1, 91, NULL, 'SYS-A', 'DEP-1', '历史依赖文件', 7, 0, NULL, NULL, 7,
                            CURRENT_TIMESTAMP, 7, CURRENT_TIMESTAMP)
                    """);

            List<String> statements = readMigrationStatements("V199__data_migration_dependency_domain.sql");
            SQLException failure = assertThrows(SQLException.class, () -> {
                for (String statement : statements) execute(connection, statement);
            });
            assertTrue(failure.getMessage().contains("存在存量行"), "失败信息应说明存量行原因：" + failure.getMessage());

            // 结构保持原状：旧列仍在、新列未加、旧数据未动
            assertTrue(hasColumn(connection, "dm_dependency", "doc_code"));
            assertTrue(hasColumn(connection, "dm_dependency", "doc_name"));
            assertTrue(hasColumn(connection, "dm_dependency", "system_code"));
            assertFalse(hasColumn(connection, "dm_dependency", "parameter_id"));
            assertTrue(hasIndex(connection, "dm_dependency", "uk_dm_dependency_code"));
            assertFalse(hasIndex(connection, "dm_dependency", "uk_dm_dependency_relation"));
            assertEquals(1L, count(connection, "SELECT COUNT(*) FROM dm_dependency"));
        }
    }

    private void assertV199Applied(Connection connection) throws Exception {
        assertTrue(hasColumn(connection, "dm_dependency", "parameter_id"));
        assertTrue(hasColumn(connection, "dm_dependency", "system_code"));
        assertFalse(hasColumn(connection, "dm_dependency", "doc_code"));
        assertFalse(hasColumn(connection, "dm_dependency", "doc_name"));
        assertFalse(hasIndex(connection, "dm_dependency", "uk_dm_dependency_code"));
        assertFalse(hasIndex(connection, "dm_dependency", "idx_dm_dependency_owner"));
        assertTrue(hasIndex(connection, "dm_dependency", "uk_dm_dependency_relation"));
        assertTrue(hasIndex(connection, "dm_dependency", "idx_dm_dependency_query"));
        assertTrue(hasIndex(connection, "dm_dependency", "idx_dm_dependency_system"));

        assertEquals(3, count(connection,
                "SELECT COUNT(*) FROM sys_menu_permission WHERE tenant_id = 1 AND menu_id = 750 "
                + "AND permission_code IN ('data-migration:content:dependencies:create',"
                + "'data-migration:content:dependencies:update','data-migration:content:dependencies:delete') AND status = 1"));
        assertEquals(1, count(connection,
                "SELECT COUNT(*) FROM sys_menu_permission WHERE tenant_id = 1 AND menu_id = 750 AND id = 7500 AND status = 1"));
        assertEquals(8, count(connection,
                "SELECT COUNT(*) FROM sys_role_permission WHERE permission_id IN (7500, 7501, 7502, 7503) AND role_id IN (1, 200)"));
        assertEquals(1, count(connection,
                "SELECT COUNT(*) FROM sys_role_permission WHERE role_id = 202 AND permission_id = 7500 AND tenant_id = 1"));
        assertEquals(0, count(connection,
                "SELECT COUNT(*) FROM sys_role_permission WHERE role_id = 202 AND permission_id IN (7501, 7502, 7503)"));
    }

    private void cleanupProcedures(Connection connection) throws SQLException {
        execute(connection, "DROP PROCEDURE IF EXISTS dm_v199_assert_dependency_empty");
    }

    private void createLegacyDependencyTable(Connection connection) throws SQLException {
        cleanupProcedures(connection);
        execute(connection, "DROP TABLE IF EXISTS dm_dependency");
        // 还原 V162（建表）+ V174（去 checksum_md5）+ V178（active 唯一键改 uk_dm_dependency_code）
        // + V182（补 system_code）后的真实升级前结构
        execute(connection, """
                CREATE TABLE dm_dependency (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    tenant_id BIGINT NOT NULL DEFAULT 1,
                    project_id BIGINT NOT NULL,
                    component_id BIGINT NULL,
                    system_code VARCHAR(64) NOT NULL DEFAULT '',
                    doc_code VARCHAR(96) NOT NULL,
                    doc_name VARCHAR(255) NOT NULL,
                    owner_id BIGINT NOT NULL,
                    deleted TINYINT NOT NULL DEFAULT 0,
                    deleted_by BIGINT NULL,
                    deleted_at TIMESTAMP NULL,
                    created_by BIGINT NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_by BIGINT NULL,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    UNIQUE KEY uk_dm_dependency_code (tenant_id, project_id, doc_code),
                    KEY idx_dm_dependency_query (tenant_id, project_id, deleted, updated_at),
                    KEY idx_dm_dependency_owner (tenant_id, owner_id, deleted)
                )
                """);
    }

    private void createMenuPermissionTables(Connection connection) throws SQLException {
        execute(connection, "DROP TABLE IF EXISTS sys_role_permission, sys_menu_permission");
        execute(connection, """
                CREATE TABLE sys_menu_permission (
                    id BIGINT PRIMARY KEY,
                    tenant_id BIGINT NOT NULL DEFAULT 1,
                    menu_id BIGINT NOT NULL,
                    action_code VARCHAR(32) NOT NULL,
                    permission_code VARCHAR(160) NOT NULL,
                    permission_name VARCHAR(64) NOT NULL,
                    status TINYINT NOT NULL DEFAULT 1,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    UNIQUE KEY uk_sys_menu_permission_action (tenant_id, menu_id, action_code),
                    UNIQUE KEY uk_sys_menu_permission_code (tenant_id, permission_code),
                    KEY idx_sys_menu_permission_menu (tenant_id, menu_id)
                )
                """);
        execute(connection, "INSERT INTO sys_menu_permission (id, tenant_id, menu_id, action_code, permission_code, permission_name) "
                + "VALUES (7500, 1, 750, 'read', 'data-migration:content:dependencies', '查看')");
        execute(connection, """
                CREATE TABLE sys_role_permission (
                    role_id BIGINT NOT NULL,
                    permission_id BIGINT NOT NULL,
                    tenant_id BIGINT NOT NULL DEFAULT 1,
                    PRIMARY KEY (role_id, permission_id),
                    KEY idx_sys_role_permission_permission (tenant_id, permission_id)
                )
                """);
        execute(connection, "INSERT INTO sys_role_permission (role_id, permission_id, tenant_id) VALUES (1, 7500, 1), (200, 7500, 1), (202, 7500, 1)");
    }

    /** 解析迁移脚本语句，支持 {@code DELIMITER $$} 过程块与 {@code ;} 结尾的普通语句。 */
    private List<String> readMigrationStatements(String fileName) throws Exception {
        Path path = Path.of(migrationDirectory(), fileName);
        List<String> statements = new ArrayList<>();
        String delimiter = ";";
        StringBuilder current = new StringBuilder();
        for (String raw : Files.readAllLines(path)) {
            String trimmed = raw.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("--")) continue;
            if (trimmed.startsWith("DELIMITER ")) {
                delimiter = trimmed.substring("DELIMITER ".length()).trim();
                continue;
            }
            current.append(raw).append('\n');
            if (trimmed.endsWith(delimiter)) {
                String sql = current.toString().trim();
                if (!sql.isEmpty()) {
                    statements.add(sql.substring(0, sql.length() - delimiter.length()).trim());
                }
                current.setLength(0);
            }
        }
        assertTrue(!statements.isEmpty(), "V199 未解析到可执行语句");
        return statements;
    }

    private String migrationDirectory() {
        Path cursor = Path.of("").toAbsolutePath();
        while (cursor != null) {
            Path candidate = cursor.resolve("server/src/platform/infrastructure/src/main/resources/db/migration");
            if (Files.isDirectory(candidate)) return candidate.toString();
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("Flyway migration directory not found");
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    }

    private boolean hasColumn(Connection connection, String table, String column) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = '" + table
                + "' AND column_name = '" + column + "'")) {
            result.next();
            return result.getLong(1) == 1L;
        }
    }

    private boolean hasIndex(Connection connection, String table, String index) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(
                "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = '" + table
                + "' AND index_name = '" + index + "'")) {
            result.next();
            return result.getLong(1) >= 1L;
        }
    }

    private long count(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getLong(1);
        }
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
