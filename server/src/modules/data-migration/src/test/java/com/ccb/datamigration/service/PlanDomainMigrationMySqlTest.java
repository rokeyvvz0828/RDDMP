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
 * V168 迁移方案域化迁移测试：在 V162 形状的 dm_plan 上应用真实 V168 脚本，断言
 * 新增维度列、活动维度唯一键（{@code uk_dm_plan_active_dimension}）语义与项目级 system_id=0 哨兵。
 * 自包含构造最小基线，直接执行迁移目录中的 V168 文件内容（不依赖完整 Flyway 链）。
 */
@Testcontainers
class PlanDomainMigrationMySqlTest {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("plan_domain")
            .withUsername("test")
            .withPassword("test");

    @Test
    void v168AddsDimensionColumnsAndActiveDimensionUniqueness() throws Exception {
        try (Connection connection = connection()) {
            execute(connection, """
                    CREATE TABLE dm_plan (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        tenant_id BIGINT NOT NULL DEFAULT 1,
                        project_id BIGINT NOT NULL,
                        component_id BIGINT NULL,
                        doc_code VARCHAR(96) NOT NULL,
                        doc_name VARCHAR(255) NOT NULL,
                        checksum_md5 CHAR(32) NULL,
                        owner_id BIGINT NOT NULL,
                        deleted TINYINT NOT NULL DEFAULT 0,
                        deleted_by BIGINT NULL,
                        deleted_at TIMESTAMP NULL,
                        created_by BIGINT NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_by BIGINT NULL,
                        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        active_doc_code VARCHAR(96)
                            GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN doc_code ELSE NULL END) STORED,
                        UNIQUE KEY uk_dm_plan_active_code (tenant_id, project_id, active_doc_code)
                    )
                    """);

            for (String statement : readMigrationStatements("V168__data_migration_plan_domain.sql")) {
                execute(connection, statement);
            }

            // 新列存在且默认值生效（无回填：project-level system_id=0，granularity 默认 PROJECT，plan_type 默认 DATA）
            assertEquals("YES", value(connection, existsColumn("granularity")));
            assertEquals("YES", value(connection, existsColumn("plan_type")));
            assertEquals("YES", value(connection, existsColumn("system_id")));
            assertEquals("YES", value(connection, existsColumn("plan_summary")));
            assertEquals("STORED GENERATED", value(connection,
                    "SELECT EXTRA FROM information_schema.columns " +
                    "WHERE table_schema = DATABASE() AND table_name = 'dm_plan' AND column_name = 'active_dimension_key'"));

            // 一条项目级业务方案
            insertPlan(connection, 1, 100, "PROJECT", "BUSINESS", 0, "PLAN-B");
            // 同维度第二条活动记录必须被活动域唯一键拒绝
            assertThrows(SQLIntegrityConstraintViolationException.class,
                    () -> insertPlan(connection, 2, 100, "PROJECT", "BUSINESS", 0, "PLAN-B2"));
            // 不同方案类型 / 不同颗粒度 / 系统级(不同 system_id) 允许并存
            insertPlan(connection, 3, 100, "PROJECT", "DATA", 0, "PLAN-D");
            insertPlan(connection, 4, 100, "SYSTEM", "DATA", 5001, "PLAN-S1");
            insertPlan(connection, 5, 100, "SYSTEM", "DATA", 5002, "PLAN-S2");
            // 另一项目的同维度独立
            insertPlan(connection, 6, 200, "PROJECT", "BUSINESS", 0, "PLAN-OTHER");

            // 逻辑删除后同维度可重建（软删行 active_dimension_key 取 NULL 不参与唯一）
            execute(connection, "UPDATE dm_plan SET deleted = 1 WHERE id = 1");
            insertPlan(connection, 7, 100, "PROJECT", "BUSINESS", 0, "PLAN-REBUILT");

            // 活动行 5 条（id2 被拒；id1 软删）；含软删共 6 行
            assertEquals(5, count(connection, "SELECT COUNT(*) FROM dm_plan WHERE deleted = 0"));
            assertEquals(6, count(connection, "SELECT COUNT(*) FROM dm_plan"));
        }
    }

    // ============ 辅助 ============

    private String existsColumn(String column) {
        return "SELECT CASE WHEN COUNT(*) > 0 THEN 'YES' ELSE 'NO' END FROM information_schema.columns WHERE table_schema = DATABASE() " +
                "AND table_name = 'dm_plan' AND column_name = '" + column + "'";
    }

    private void insertPlan(Connection connection, long id, long projectId, String granularity,
                            String planType, long systemId, String docCode) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO dm_plan (id, tenant_id, project_id, doc_code, doc_name, owner_id, " +
                    "granularity, plan_type, system_id) VALUES (" + id + ", 1, " + projectId + ", '" + docCode + "', '" +
                    docCode + "', 1, '" + granularity + "', '" + planType + "', " + systemId + ")");
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
        assertTrue(!statements.isEmpty(), "V168 未解析到可执行语句");
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
