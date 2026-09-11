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

/** V200 迁移参数字段子表迁移测试：建表、活动生成列唯一键、码值与重跑幂等（REQ-20260910-069）。 */
@Testcontainers
class DataMigrationParameterFieldMigrationMySqlTest {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("parameter_field_migration")
            .withUsername("test")
            .withPassword("test");

    @Test
    void v200CreatesFieldTableWithCodeValuesAndIsIdempotent() throws Exception {
        try (Connection connection = connection()) {
            createContextTables(connection);
            List<String> statements = readMigrationStatements("V200__data_migration_parameter_field.sql");

            for (String statement : statements) execute(connection, statement);
            assertV200Applied(connection);

            for (String statement : statements) execute(connection, statement);
            assertV200Applied(connection);
        }
    }

    @Test
    void v200UniqueKeysRejectCaseInsensitiveDuplicateFieldNames() throws Exception {
        try (Connection connection = connection()) {
            createContextTables(connection);
            for (String statement : readMigrationStatements("V200__data_migration_parameter_field.sql")) {
                execute(connection, statement);
            }
            execute(connection, "INSERT INTO dm_parameter (id, tenant_id, project_id) VALUES (1, 1, 91)");
            execute(connection, """
                    INSERT INTO dm_parameter_field (id, tenant_id, parameter_id, field_name_en, field_name_cn,
                        field_type, field_length, field_description, sort_no, owner_id, created_by, updated_by)
                    VALUES (1, 1, 1, 'amount', '金额', 'NUMBER', 15, NULL, 1, 7, 7, 7)
                    """);
            // 英文名仅大小写不同视为重复
            SQLException englishFailure = assertThrows(SQLException.class, () -> execute(connection, """
                    INSERT INTO dm_parameter_field (id, tenant_id, parameter_id, field_name_en, field_name_cn,
                        field_type, field_length, field_description, sort_no, owner_id, created_by, updated_by)
                    VALUES (2, 1, 1, 'AMOUNT', '金额大写', 'NUMBER', 15, NULL, 2, 7, 7, 7)
                    """));
            assertTrue(englishFailure.getMessage().toLowerCase().contains("duplicate"),
                    "英文名重复应触发唯一键：" + englishFailure.getMessage());

            // 中文名仅大小写不同视为重复
            SQLException chineseFailure = assertThrows(SQLException.class, () -> execute(connection, """
                    INSERT INTO dm_parameter_field (id, tenant_id, parameter_id, field_name_en, field_name_cn,
                        field_type, field_length, field_description, sort_no, owner_id, created_by, updated_by)
                    VALUES (3, 1, 1, 'client_no', '金额', 'VARCHAR', 32, NULL, 3, 7, 7, 7)
                    """));
            assertTrue(chineseFailure.getMessage().toLowerCase().contains("duplicate"),
                    "中文名重复应触发唯一键：" + chineseFailure.getMessage());
        }
    }

    private void assertV200Applied(Connection connection) throws Exception {
        assertTrue(hasColumn(connection, "dm_parameter_field", "id"));
        assertTrue(hasColumn(connection, "dm_parameter_field", "parameter_id"));
        assertTrue(hasColumn(connection, "dm_parameter_field", "field_name_en"));
        assertTrue(hasColumn(connection, "dm_parameter_field", "field_name_cn"));
        assertTrue(hasColumn(connection, "dm_parameter_field", "field_type"));
        assertTrue(hasColumn(connection, "dm_parameter_field", "field_length"));
        assertTrue(hasColumn(connection, "dm_parameter_field", "active_field_name_en"));
        assertTrue(hasColumn(connection, "dm_parameter_field", "active_field_name_cn"));
        assertTrue(hasColumn(connection, "dm_parameter_field", "deleted_by"));
        assertTrue(hasColumn(connection, "dm_parameter_field", "deleted_at"));
        assertTrue(hasIndex(connection, "dm_parameter_field", "uk_dm_parameter_field_active_en"));
        assertTrue(hasIndex(connection, "dm_parameter_field", "uk_dm_parameter_field_active_cn"));
        assertTrue(hasIndex(connection, "dm_parameter_field", "idx_dm_parameter_field_parameter"));
        assertEquals(1, count(connection,
                "SELECT COUNT(*) FROM sys_dict_type WHERE tenant_id = 1 AND dict_code = 'DM_PARAMETER_FIELD_TYPE' AND deleted = 0"));
        assertEquals(7, count(connection,
                "SELECT COUNT(*) FROM sys_config WHERE tenant_id = 1 AND config_key LIKE 'DM_PARAMETER_FIELD_TYPE.%' AND status = 1"));
    }

    private void createContextTables(Connection connection) throws SQLException {
        execute(connection, "DROP TABLE IF EXISTS dm_parameter_field, dm_parameter, sys_config, sys_dict_type");
        execute(connection, """
                CREATE TABLE dm_parameter (
                    id BIGINT PRIMARY KEY,
                    tenant_id BIGINT NOT NULL DEFAULT 1,
                    project_id BIGINT NOT NULL DEFAULT 91
                )
                """);
        execute(connection, """
                CREATE TABLE sys_dict_type (
                    id BIGINT PRIMARY KEY,
                    tenant_id BIGINT NOT NULL DEFAULT 1,
                    dict_code VARCHAR(64) NOT NULL,
                    dict_name VARCHAR(64) NOT NULL,
                    status TINYINT NOT NULL DEFAULT 1,
                    deleted TINYINT NOT NULL DEFAULT 0
                )
                """);
        execute(connection, """
                CREATE TABLE sys_config (
                    id BIGINT PRIMARY KEY,
                    tenant_id BIGINT NOT NULL DEFAULT 1,
                    category_id BIGINT NOT NULL DEFAULT 0,
                    config_key VARCHAR(160) NOT NULL,
                    config_value VARCHAR(160) NOT NULL,
                    config_type VARCHAR(32) NOT NULL DEFAULT 'string',
                    remark VARCHAR(500) NULL,
                    status TINYINT NOT NULL DEFAULT 1,
                    deleted TINYINT NOT NULL DEFAULT 0,
                    UNIQUE KEY uk_sys_config_key (tenant_id, config_key)
                )
                """);
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
        assertTrue(!statements.isEmpty(), "V200 未解析到可执行语句");
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
            result.next();
            return result.getLong(1);
        }
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
