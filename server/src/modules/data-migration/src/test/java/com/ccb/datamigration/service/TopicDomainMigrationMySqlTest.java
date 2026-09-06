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
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V180 专题材料域迁移测试：在 V178 形状的 dm_topic 上应用真实 V180 脚本，断言
 * 专题元数据列与历史行回填、dm_topic_system 关系唯一键、参数类别/23 项幂等初始化、
 * 查询索引与重复执行无副作用。自包含构造最小基线，直接执行迁移目录中的 V180 文件内容。
 */
@Testcontainers
class TopicDomainMigrationMySqlTest {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("topic_domain")
            .withUsername("test")
            .withPassword("test");

    @Test
    void v180AddsTopicDomainAndSeedsParametersIdempotently() throws Exception {
        try (Connection connection = connection()) {
            execute(connection, """
                    CREATE TABLE dm_topic (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        tenant_id BIGINT NOT NULL DEFAULT 1,
                        project_id BIGINT NOT NULL,
                        component_id BIGINT NULL,
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
                        UNIQUE KEY uk_dm_topic_code (tenant_id, project_id, doc_code),
                        KEY idx_dm_topic_query (tenant_id, project_id, deleted, updated_at),
                        KEY idx_dm_topic_owner (tenant_id, owner_id, deleted)
                    )
                    """);
            execute(connection, """
                    CREATE TABLE sys_dict_type (
                        id BIGINT NOT NULL PRIMARY KEY,
                        tenant_id BIGINT NOT NULL DEFAULT 1,
                        dict_code VARCHAR(64) NOT NULL,
                        dict_name VARCHAR(128) NOT NULL,
                        status TINYINT NOT NULL DEFAULT 1,
                        deleted TINYINT NOT NULL DEFAULT 0
                    )
                    """);
            execute(connection, """
                    CREATE TABLE sys_config (
                        id BIGINT NOT NULL PRIMARY KEY,
                        tenant_id BIGINT NOT NULL DEFAULT 1,
                        category_id BIGINT NOT NULL,
                        config_key VARCHAR(64) NOT NULL,
                        config_value VARCHAR(256) NOT NULL,
                        config_type VARCHAR(32) NULL,
                        remark VARCHAR(256) NULL,
                        status TINYINT NOT NULL DEFAULT 1,
                        deleted TINYINT NOT NULL DEFAULT 0
                    )
                    """);

            // 迁移前存量行（无新列），验证历史回填。
            execute(connection, "INSERT INTO dm_topic (id, tenant_id, project_id, doc_code, doc_name, owner_id) " +
                    "VALUES (1, 1, 100, 'TOPIC-LEGACY-1', '存量专题', 5)");

            List<String> statements = readMigrationStatements("V180__data_migration_topic_domain.sql");
            for (String statement : statements) {
                execute(connection, statement);
            }

            // 新列存在且历史行回填：PROJECT 颗粒度、LEGACY 兼容编码、空简述。
            assertEquals("YES", value(connection, existsColumn("granularity")));
            assertEquals("YES", value(connection, existsColumn("topic_type_code")));
            assertEquals("YES", value(connection, existsColumn("topic_summary")));
            assertEquals("PROJECT", value(connection, "SELECT granularity FROM dm_topic WHERE id = 1"));
            assertEquals("LEGACY", value(connection, "SELECT topic_type_code FROM dm_topic WHERE id = 1"));
            assertEquals("", value(connection, "SELECT topic_summary FROM dm_topic WHERE id = 1"));

            // 关系表与唯一键/索引。
            assertEquals("YES", value(connection,
                    "SELECT CASE WHEN COUNT(*) > 0 THEN 'YES' ELSE 'NO' END FROM information_schema.tables " +
                    "WHERE table_schema = DATABASE() AND table_name = 'dm_topic_system'"));
            assertEquals("YES", value(connection,
                    "SELECT CASE WHEN COUNT(*) > 0 THEN 'YES' ELSE 'NO' END FROM information_schema.statistics " +
                    "WHERE table_schema = DATABASE() AND table_name = 'dm_topic_system' AND index_name = 'uk_dm_topic_system'"));
            assertEquals("YES", value(connection,
                    "SELECT CASE WHEN COUNT(*) > 0 THEN 'YES' ELSE 'NO' END FROM information_schema.statistics " +
                    "WHERE table_schema = DATABASE() AND table_name = 'dm_topic_system' AND index_name = 'idx_dm_topic_system_project'"));
            assertEquals("YES", value(connection,
                    "SELECT CASE WHEN COUNT(*) > 0 THEN 'YES' ELSE 'NO' END FROM information_schema.statistics " +
                    "WHERE table_schema = DATABASE() AND table_name = 'dm_topic_system' AND index_name = 'idx_dm_topic_system_topic'"));

            // 查询索引。
            assertEquals("YES", value(connection,
                    "SELECT CASE WHEN COUNT(*) > 0 THEN 'YES' ELSE 'NO' END FROM information_schema.statistics " +
                    "WHERE table_schema = DATABASE() AND table_name = 'dm_topic' AND index_name = 'idx_dm_topic_granularity'"));
            assertEquals("YES", value(connection,
                    "SELECT CASE WHEN COUNT(*) > 0 THEN 'YES' ELSE 'NO' END FROM information_schema.statistics " +
                    "WHERE table_schema = DATABASE() AND table_name = 'dm_topic' AND index_name = 'idx_dm_topic_type'"));

            // 参数类别与 23 项初始化；19 个项目级 + 4 个系统级。
            assertEquals(2, count(connection, "SELECT COUNT(*) FROM sys_dict_type WHERE dict_code IN ('DM_TOPIC_PROJECT_TYPE', 'DM_TOPIC_SYSTEM_TYPE') AND deleted = 0"));
            assertEquals(23, count(connection,
                    "SELECT COUNT(*) FROM sys_config c JOIN sys_dict_type t ON t.tenant_id = c.tenant_id AND t.id = c.category_id " +
                    "WHERE t.dict_code IN ('DM_TOPIC_PROJECT_TYPE', 'DM_TOPIC_SYSTEM_TYPE') AND c.deleted = 0"));
            assertEquals(19, count(connection,
                    "SELECT COUNT(*) FROM sys_config c JOIN sys_dict_type t ON t.tenant_id = c.tenant_id AND t.id = c.category_id " +
                    "WHERE t.dict_code = 'DM_TOPIC_PROJECT_TYPE' AND c.deleted = 0"));
            assertEquals(4, count(connection,
                    "SELECT COUNT(*) FROM sys_config c JOIN sys_dict_type t ON t.tenant_id = c.tenant_id AND t.id = c.category_id " +
                    "WHERE t.dict_code = 'DM_TOPIC_SYSTEM_TYPE' AND c.deleted = 0"));

            // 幂等重跑：无重复列/表/键/参数。
            for (String statement : statements) {
                execute(connection, statement);
            }
            assertEquals(2, count(connection, "SELECT COUNT(*) FROM sys_dict_type WHERE dict_code IN ('DM_TOPIC_PROJECT_TYPE', 'DM_TOPIC_SYSTEM_TYPE') AND deleted = 0"));
            assertEquals(23, count(connection,
                    "SELECT COUNT(*) FROM sys_config c JOIN sys_dict_type t ON t.tenant_id = c.tenant_id AND t.id = c.category_id " +
                    "WHERE t.dict_code IN ('DM_TOPIC_PROJECT_TYPE', 'DM_TOPIC_SYSTEM_TYPE') AND c.deleted = 0"));
            assertEquals("YES", value(connection, existsColumn("granularity")));
            assertEquals("LEGACY", value(connection, "SELECT topic_type_code FROM dm_topic WHERE id = 1"));

            // 关系唯一键：同一 (tenant, topic, system) 仅一条；不同系统允许并存。
            execute(connection, "INSERT INTO dm_topic_system (id, tenant_id, topic_id, project_id, system_code, created_by) " +
                    "VALUES (1, 1, 1, 100, 'SYS_A', 5)");
            assertThrows(SQLIntegrityConstraintViolationException.class,
                    () -> execute(connection, "INSERT INTO dm_topic_system (id, tenant_id, topic_id, project_id, system_code, created_by) " +
                            "VALUES (2, 1, 1, 100, 'SYS_A', 5)"));
            execute(connection, "INSERT INTO dm_topic_system (id, tenant_id, topic_id, project_id, system_code, created_by) " +
                    "VALUES (3, 1, 1, 100, 'SYS_B', 5)");

            // 未授权表未被触碰：平台公共附件/审计表不得由 V180 创建。
            assertEquals(0, count(connection,
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() " +
                    "AND table_name IN ('dm_content_attachment', 'dm_operation_log')"));
        }
    }

    // ============ 辅助 ============

    private String existsColumn(String column) {
        return "SELECT CASE WHEN COUNT(*) > 0 THEN 'YES' ELSE 'NO' END FROM information_schema.columns " +
                "WHERE table_schema = DATABASE() AND table_name = 'dm_topic' AND column_name = '" + column + "'";
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
        assertTrue(!statements.isEmpty(), "V180 未解析到可执行语句");
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

    private String value(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getString(1);
        }
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
