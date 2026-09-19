package com.ccb.datamigration.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

/**
 * V214 系统/组件清单关联人员迁移测试：全新库执行建表与角色字典种子、唯一键拒绝同一系统
 * 同一人员重复、一个系统可关联多名成员、重跑幂等。
 */
@Testcontainers
class ComponentPersonMigrationMySqlTest {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("component_person_migration")
            .withUsername("test")
            .withPassword("test");

    @Test
    void v214CreatesRelationTableAndSeedsRoleCategoryIdempotently() throws Exception {
        try (Connection connection = connection()) {
            execute(connection, """
                    CREATE TABLE sys_dict_type (
                        id BIGINT NOT NULL PRIMARY KEY,
                        tenant_id BIGINT NOT NULL DEFAULT 1,
                        dict_code VARCHAR(64) NOT NULL,
                        dict_name VARCHAR(128) NOT NULL,
                        status TINYINT NOT NULL DEFAULT 1,
                        deleted TINYINT NOT NULL DEFAULT 0,
                        UNIQUE KEY uk_sys_dict_code (tenant_id, dict_code, deleted)
                    )
                    """);
            execute(connection, """
                    CREATE TABLE sys_config (
                        id BIGINT NOT NULL PRIMARY KEY,
                        tenant_id BIGINT NOT NULL DEFAULT 1,
                        category_id BIGINT NOT NULL,
                        config_key VARCHAR(128) NOT NULL,
                        config_value TEXT NOT NULL,
                        config_type VARCHAR(32) NOT NULL DEFAULT 'string',
                        remark VARCHAR(255),
                        status TINYINT NOT NULL DEFAULT 1,
                        deleted TINYINT NOT NULL DEFAULT 0,
                        UNIQUE KEY uk_sys_config_key (tenant_id, config_key, deleted)
                    )
                    """);

            List<String> statements = readMigrationStatements("V214__data_migration_component_person.sql");
            for (String statement : statements) {
                execute(connection, statement);
            }

            // 关系表存在且主键约束生效：同一系统同一人员重复插入被拒绝，不同成员可共存。
            assertTrue(tableExists(connection, "dm_component_person"), "dm_component_person 表应被创建");
            execute(connection,
                    "INSERT INTO dm_component_person (tenant_id, project_id, system_code, user_id, person_role, created_by, updated_by) "
                            + "VALUES (1, 91, 'SYS-A', 7, 'OWNER', 7, 7)");
            assertThrows(SQLException.class, () -> execute(connection,
                    "INSERT INTO dm_component_person (tenant_id, project_id, system_code, user_id, person_role, created_by, updated_by) "
                            + "VALUES (1, 91, 'SYS-A', 7, 'LIAISON', 7, 7)"), "同系统同人员重复关系应被主键拒绝");
            execute(connection,
                    "INSERT INTO dm_component_person (tenant_id, project_id, system_code, user_id, person_role, created_by, updated_by) "
                            + "VALUES (1, 91, 'SYS-A', 8, 'LIAISON', 7, 7), (1, 91, 'SYS-B', 7, 'OWNER', 7, 7)");
            assertEquals(3, count(connection, "SELECT COUNT(*) FROM dm_component_person"));

            // 角色字典种子：1 个类别 + 4 个默认选项，config_key 前缀归属类别。
            assertEquals(1, count(connection,
                    "SELECT COUNT(*) FROM sys_dict_type WHERE tenant_id = 1 AND dict_code = 'DM_COMPONENT_PERSON_ROLE' AND deleted = 0"));
            assertEquals(4, count(connection,
                    "SELECT COUNT(*) FROM sys_config c JOIN sys_dict_type t ON t.id = c.category_id "
                            + "WHERE t.dict_code = 'DM_COMPONENT_PERSON_ROLE' AND c.deleted = 0"));
            assertEquals(4, count(connection,
                    "SELECT COUNT(*) FROM sys_config c JOIN sys_dict_type t ON t.id = c.category_id "
                            + "WHERE c.deleted = 0 AND c.config_key LIKE CONCAT(t.dict_code, '.%')"));

            // 幂等重跑：结构重建不覆盖数据，字典计数不变。
            for (String statement : statements) {
                execute(connection, statement);
            }
            assertEquals(3, count(connection, "SELECT COUNT(*) FROM dm_component_person"));
            assertEquals(1, count(connection,
                    "SELECT COUNT(*) FROM sys_dict_type WHERE tenant_id = 1 AND dict_code = 'DM_COMPONENT_PERSON_ROLE' AND deleted = 0"));
            assertEquals(4, count(connection,
                    "SELECT COUNT(*) FROM sys_config c JOIN sys_dict_type t ON t.id = c.category_id "
                            + "WHERE t.dict_code = 'DM_COMPONENT_PERSON_ROLE' AND c.deleted = 0"));
        }
    }

    private List<String> readMigrationStatements(String fileName) throws Exception {
        Path path = Path.of(migrationDirectory(), fileName);
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String raw : Files.readAllLines(path)) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("--")) continue;
            current.append(raw).append('\n');
            if (line.endsWith(";")) {
                String sql = current.toString().trim();
                if (!sql.isEmpty()) statements.add(sql.substring(0, sql.length() - 1));
                current.setLength(0);
            }
        }
        assertTrue(!statements.isEmpty(), "V214 未解析到可执行语句");
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

    private boolean tableExists(Connection connection, String table) throws SQLException {
        try (ResultSet result = connection.getMetaData().getTables(null, null, table, new String[]{"TABLE"})) {
            return result.next();
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
