package com.ccb.datamigration.service;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** V186 迁移映射菜单优化迁移测试：dm_mapping_doc 补映射类型、参数种子与重跑幂等。 */
@Testcontainers
class DataMigrationMappingMigrationMySqlTest {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("mapping_migration")
            .withUsername("test")
            .withPassword("test");

    @Test
    void v186AddsMappingTypeAndCodeValuesIdempotently() throws Exception {
        try (Connection connection = connection()) {
            createDictType(connection);
            createConfig(connection);
            createMappingTable(connection);

            List<String> statements = readMigrationStatements("V187__data_migration_mapping_domain.sql");
            for (String statement : statements) execute(connection, statement);

            assertEquals(1, count(connection,
                    "SELECT COUNT(*) FROM sys_dict_type WHERE tenant_id = 1 AND dict_code = 'DM_MAPPING_TYPE' AND deleted = 0"));
            assertEquals(2, count(connection,
                    "SELECT COUNT(*) FROM sys_config c JOIN sys_dict_type t ON t.id = c.category_id AND t.tenant_id = c.tenant_id "
                    + "WHERE c.tenant_id = 1 AND t.dict_code = 'DM_MAPPING_TYPE' AND c.deleted = 0 AND c.status = 1"));
            assertTrue(hasColumn(connection, "dm_mapping_doc", "mapping_type"));
            assertMappingTypeRequiresExplicitValue(connection);
            assertMigrationDoesNotBackfillMappingType(statements);
            assertEquals(1, count(connection,
                    "SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics "
                    + "WHERE table_schema = DATABASE() AND table_name = 'dm_mapping_doc' AND index_name = 'idx_dm_mapping_doc_mapping_type'"));

            for (String statement : statements) execute(connection, statement);
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM sys_dict_type WHERE tenant_id = 1 AND dict_code = 'DM_MAPPING_TYPE' AND deleted = 0"));
            assertEquals(2, count(connection,
                    "SELECT COUNT(*) FROM sys_config c JOIN sys_dict_type t ON t.id = c.category_id AND t.tenant_id = c.tenant_id "
                    + "WHERE c.tenant_id = 1 AND t.dict_code = 'DM_MAPPING_TYPE' AND c.deleted = 0 AND c.status = 1"));
        }
    }

    private void createDictType(Connection connection) throws SQLException {
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
    }

    private void createConfig(Connection connection) throws SQLException {
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
    }

    private void createMappingTable(Connection connection) throws SQLException {
        execute(connection, """
                CREATE TABLE dm_mapping_doc (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    tenant_id BIGINT NOT NULL DEFAULT 1,
                    project_id BIGINT NOT NULL,
                    system_code VARCHAR(64) NOT NULL DEFAULT '',
                    doc_name VARCHAR(255) NOT NULL,
                    deleted TINYINT NOT NULL DEFAULT 0
                )
                """);
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
        assertTrue(!statements.isEmpty(), "V186 未解析到可执行语句");
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

    private void assertMappingTypeRequiresExplicitValue(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(
                "SELECT is_nullable, column_default FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name = 'dm_mapping_doc' AND column_name = 'mapping_type'")) {
            assertTrue(result.next());
            assertEquals("NO", result.getString("is_nullable"));
            assertNull(result.getString("column_default"));
        }
    }

    private void assertMigrationDoesNotBackfillMappingType(List<String> statements) {
        assertFalse(statements.stream()
                .map(String::toUpperCase)
                .anyMatch(statement -> statement.contains("UPDATE DM_MAPPING_DOC SET MAPPING_TYPE")));
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
