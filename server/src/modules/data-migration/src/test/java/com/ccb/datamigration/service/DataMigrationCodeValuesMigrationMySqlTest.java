package com.ccb.datamigration.service;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V183 数据迁移业务码值参数管理迁移测试：验证 11 个参数类别、44 条初始配置在全局唯一
 * config_key 约束下全部落库且重跑幂等。
 */
@Testcontainers
class DataMigrationCodeValuesMigrationMySqlTest {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("code_value_migration")
            .withUsername("test")
            .withPassword("test");

    @Test
    void v183SeedsAllCategoriesAndValuesIdempotently() throws Exception {
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

            List<String> statements = readMigrationStatements("V184__data_migration_code_values_parameter_management.sql");
            for (String statement : statements) {
                execute(connection, statement);
            }

            assertEquals(11, count(connection, "SELECT COUNT(*) FROM sys_dict_type WHERE tenant_id = 1 AND deleted = 0"));
            assertEquals(44, count(connection,
                    "SELECT COUNT(*) FROM sys_config c JOIN sys_dict_type t ON t.tenant_id = c.tenant_id AND t.id = c.category_id " +
                    "WHERE t.status = 1 AND t.deleted = 0 AND c.status = 1 AND c.deleted = 0"));

            // config_key 全局唯一且均带类别前缀，避免平台唯一索引冲突。
            assertEquals(44, count(connection,
                    "SELECT COUNT(DISTINCT config_key) FROM sys_config WHERE tenant_id = 1 AND deleted = 0"));
            assertEquals(44, count(connection,
                    "SELECT COUNT(*) FROM sys_config c JOIN sys_dict_type t ON t.id = c.category_id " +
                    "WHERE c.deleted = 0 AND c.config_key LIKE CONCAT(t.dict_code, '.%')"));

            // 幂等重跑。
            for (String statement : statements) {
                execute(connection, statement);
            }
            assertEquals(11, count(connection, "SELECT COUNT(*) FROM sys_dict_type WHERE tenant_id = 1 AND deleted = 0"));
            assertEquals(44, count(connection,
                    "SELECT COUNT(*) FROM sys_config c JOIN sys_dict_type t ON t.tenant_id = c.tenant_id AND t.id = c.category_id " +
                    "WHERE t.status = 1 AND t.deleted = 0 AND c.status = 1 AND c.deleted = 0"));
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
        assertTrue(!statements.isEmpty(), "V183 未解析到可执行语句");
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
