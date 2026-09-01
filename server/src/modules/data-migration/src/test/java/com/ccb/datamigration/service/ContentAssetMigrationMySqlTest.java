package com.ccb.datamigration.service;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V99/V100 迁移测试：内容表拆分建表 + 存量复制搬迁。
 * 基线为 V98 之后的库结构（手工构造最小源表集合），逐版本迁移并断言：
 * id 保留、字段映射、软删/排序保留、关系归一与去重、断言保护触发。
 */
@Testcontainers
class ContentAssetMigrationMySqlTest {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("data_migration_content")
            .withUsername("test")
            .withPassword("test");

    @Test
    void v100SplitsAssetsIntoContentTablesPreservingIdsAndRelations() throws Exception {
        prepareV98Baseline();
        seedSourceData();

        assertTrue(flyway("129").migrate().success);
        try (Connection connection = connection()) {
            assertEquals("STORED GENERATED", value(connection, """
                    SELECT EXTRA FROM information_schema.columns
                    WHERE table_schema = DATABASE() AND table_name = 'dm_plan' AND column_name = 'active_doc_code'
                    """));
            assertEquals("STORED GENERATED", value(connection, """
                    SELECT EXTRA FROM information_schema.columns
                    WHERE table_schema = DATABASE() AND table_name = 'dm_content_attachment' AND column_name = 'active_attachment_key'
                    """));
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM dm_plan"));
        }

        assertTrue(flyway("130").migrate().success);
        try (Connection connection = connection()) {
            // 十张内容表行数与 id 保留
            assertEquals(2, count(connection, "SELECT COUNT(*) FROM dm_plan"));
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM dm_plan WHERE id = 10001 AND doc_code = 'PLAN-001' AND doc_name = '迁移方案A' AND checksum_md5 = 'aaaa0000aaaa0000aaaa0000aaaa0001'"));
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM dm_plan WHERE id = 10011 AND deleted = 1 AND deleted_by = 7 AND deleted_at = '2026-03-03 09:00:00'"));
            assertEquals("2026-01-05 10:00:00", value(connection, "SELECT DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s') FROM dm_plan WHERE id = 10001"));
            assertEquals("2026-02-06 11:30:00", value(connection, "SELECT DATE_FORMAT(updated_at, '%Y-%m-%d %H:%i:%s') FROM dm_plan WHERE id = 10001"));
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM dm_mapping_doc WHERE id = 10003 AND deleted = 1 AND active_doc_code IS NULL"));
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM dm_dependency WHERE id = 10004"));
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM dm_script WHERE id = 10005"));
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM dm_topic WHERE id = 10006"));
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM dm_release_drill WHERE id = 10007"));

            // 汇报材料专属维度
            assertEquals(1, count(connection, """
                    SELECT COUNT(*) FROM dm_report WHERE id = 10002 AND doc_code = 'RPT-001'
                    AND report_period = 'WEEKLY' AND report_date = '2026-08-01' AND keywords = 'migration,test'
                    """));

            // 结构化表主体保留
            assertEquals("x", value(connection, "SELECT JSON_UNQUOTE(JSON_EXTRACT(structured_data, '$.rule')) FROM dm_rule WHERE id = 10008"));
            assertEquals("v", value(connection, "SELECT JSON_UNQUOTE(JSON_EXTRACT(structured_data, '$.p')) FROM dm_parameter WHERE id = 10009"));
            assertEquals("t", value(connection, "SELECT JSON_UNQUOTE(JSON_EXTRACT(structured_data, '$.table')) FROM dm_intermediate_table WHERE id = 10010"));

            // 公共附件关系表：会议附件原样保留 + 文件型资产主文件
            assertEquals(6, count(connection, "SELECT COUNT(*) FROM dm_content_attachment"));
            assertEquals(3, count(connection, "SELECT COUNT(*) FROM dm_content_attachment WHERE business_type = 'MEETING' AND id IN (90001, 90002, 90003)"));
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM dm_content_attachment WHERE id = 90003 AND deleted = 1 AND deleted_at IS NOT NULL"));
            assertEquals(1, count(connection, """
                    SELECT COUNT(*) FROM dm_content_attachment
                    WHERE business_type = 'PLAN' AND business_id = 10001 AND attachment_id = 5001
                    AND file_name = '方案文件.pdf' AND sort_order = 0 AND deleted = 0
                    """));
            assertEquals(1, count(connection, """
                    SELECT COUNT(*) FROM dm_content_attachment
                    WHERE business_type = 'REPORT' AND business_id = 10002 AND attachment_id = 5005 AND file_name = '汇报材料.docx'
                    """));
            // att_file 缺失时回退资产名，软删状态保留
            assertEquals(1, count(connection, """
                    SELECT COUNT(*) FROM dm_content_attachment
                    WHERE business_type = 'PLAN' AND business_id = 10011 AND attachment_id = 5004
                    AND file_name = '已删除方案' AND sort_order = 0 AND deleted = 1
                    """));

            // 公共问题关系表：双向归一去重后 4 行
            assertEquals(4, count(connection, "SELECT COUNT(*) FROM dm_issue_relation"));
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM dm_issue_relation WHERE issue_id = 6001 AND related_type = 'MEETING' AND related_id = 7001"));
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM dm_issue_relation WHERE issue_id = 6001 AND related_type = 'TABLE' AND related_id = 4001"));
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM dm_issue_relation WHERE issue_id = 6001 AND related_type = 'FIELD' AND related_id = 4101"));
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM dm_issue_relation WHERE issue_id = 6002 AND related_type = 'MEETING' AND related_id = 7002"));

            // 会议-系统关联
            assertEquals(2, count(connection, "SELECT COUNT(*) FROM dm_meeting_system WHERE meeting_id = 7001 AND subsystem_id IN (3001, 3002)"));

            // 源表未删除（V101 才删），断言过程已清理
            assertEquals(11, count(connection, "SELECT COUNT(*) FROM dm_asset"));
            assertEquals(7, count(connection, "SELECT COUNT(*) FROM dm_asset_relation"));
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM information_schema.routines WHERE routine_schema = DATABASE() AND routine_name = 'dm_v100_assert_zero'"));

            // 生成列活动唯一键行为：活动编号冲突拒绝，软删后可重建
            assertThrows(SQLException.class, () -> execute(connection,
                    "INSERT INTO dm_plan (tenant_id, project_id, doc_code, doc_name, owner_id) VALUES (1, 100, 'PLAN-001', '重复编号', 1)"));
            execute(connection, "UPDATE dm_plan SET deleted = 1 WHERE id = 10001");
            execute(connection, "INSERT INTO dm_plan (tenant_id, project_id, doc_code, doc_name, owner_id) VALUES (1, 100, 'PLAN-001', '重建编号', 1)");
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM dm_plan WHERE doc_code = 'PLAN-001' AND deleted = 0"));
            assertThrows(SQLException.class, () -> execute(connection,
                    "INSERT INTO dm_content_attachment (tenant_id, business_type, business_id, attachment_id, file_name, created_by) VALUES (1, 'PLAN', 10001, 5001, 'dup', 1)"));
        }
    }

    @Test
    void v100FailsWhenUnregisteredAssetTypeRemains() throws Exception {
        prepareV98Baseline();
        try (Connection connection = connection()) {
            execute(connection, """
                    INSERT INTO dm_asset (id, tenant_id, project_id, asset_type, asset_code, asset_name, owner_id, structured_data)
                    VALUES (10020, 1, 100, 'TABLE_STRUCTURE', 'TS-001', '历史表结构', 1, '{}')
                    """);
            execute(connection, """
                    INSERT INTO dm_asset (id, tenant_id, project_id, asset_type, asset_code, asset_name, owner_id)
                    VALUES (10021, 1, 100, 'PLAN', 'PLAN-021', '正常方案', 1)
                    """);
        }

        assertTrue(flyway("129").migrate().success);
        assertThrows(Exception.class, () -> flyway("130").migrate());

        try (Connection connection = connection()) {
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM dm_plan"));
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM dm_asset WHERE asset_type = 'TABLE_STRUCTURE'"));
        }
    }

    @Test
    void v100FailsWhenUnmappedRelationCombinationRemains() throws Exception {
        prepareV98Baseline();
        try (Connection connection = connection()) {
            execute(connection, """
                    INSERT INTO dm_asset (id, tenant_id, project_id, asset_type, asset_code, asset_name, owner_id)
                    VALUES (10030, 1, 100, 'PLAN', 'PLAN-030', '正常方案', 1)
                    """);
            execute(connection, """
                    INSERT INTO dm_asset_relation
                        (id, tenant_id, source_asset_id, source_asset_type, target_asset_id, target_asset_type, created_by)
                    VALUES (80099, 1, 6001, 'ISSUE', 6002, 'ISSUE', 1)
                    """);
        }

        assertTrue(flyway("129").migrate().success);
        assertThrows(Exception.class, () -> flyway("130").migrate());

        try (Connection connection = connection()) {
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM dm_plan"));
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM dm_issue_relation"));
        }
    }

    @Test
    void v101DropsLegacyTablesAfterCompletenessAssertions() throws Exception {
        prepareV98Baseline();
        seedSourceData();
        assertTrue(flyway("129").migrate().success);
        assertTrue(flyway("130").migrate().success);

        assertTrue(flyway("131").migrate().success);
        try (Connection connection = connection()) {
            // 三张旧表物理删除
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name IN ('dm_asset','dm_asset_relation','dm_meeting_attachment')"));
            // 内容表与新关系表数据保持完整
            assertEquals(2, count(connection, "SELECT COUNT(*) FROM dm_plan"));
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM dm_report WHERE id = 10002"));
            assertEquals(6, count(connection, "SELECT COUNT(*) FROM dm_content_attachment"));
            assertEquals(4, count(connection, "SELECT COUNT(*) FROM dm_issue_relation"));
            assertEquals(2, count(connection, "SELECT COUNT(*) FROM dm_meeting_system"));
            // 断言过程已清理
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM information_schema.routines WHERE routine_schema = DATABASE() AND routine_name = 'dm_v101_assert_zero'"));
        }
    }

    @Test
    void v101BlocksDropWhenAssetRowUnmigrated() throws Exception {
        prepareV98Baseline();
        seedSourceData();
        assertTrue(flyway("129").migrate().success);
        assertTrue(flyway("130").migrate().success);
        // V100 之后向旧表补写一行未搬迁记录，模拟残留
        try (Connection connection = connection()) {
            execute(connection, """
                    INSERT INTO dm_asset (id, tenant_id, project_id, asset_type, asset_code, asset_name, owner_id)
                    VALUES (19999, 1, 100, 'PLAN', 'PLAN-999', 'V100 后新增残留', 1)
                    """);
        }

        assertThrows(Exception.class, () -> flyway("131").migrate());

        try (Connection connection = connection()) {
            // 断言失败 => 旧表未被删除、新表未受影响
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'dm_asset'"));
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'dm_meeting_attachment'"));
            assertEquals(2, count(connection, "SELECT COUNT(*) FROM dm_plan"));
        }
    }

    private Flyway flyway(String target) {
        return Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("filesystem:" + migrationDirectory())
                .placeholders(java.util.Map.of("bootstrap_admin_password_hash", "test-hash"))
                .baselineOnMigrate(true)
                .baselineVersion(MigrationVersion.fromVersion("128"))
                .target(MigrationVersion.fromVersion(target))
                .cleanDisabled(false)
                .load();
    }

    private Connection connection() throws Exception {
        return DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    }

    /** 手工构造 V98 之后的最小源表集合（V100 只读这四张表）。 */
    private void prepareV98Baseline() {
        flyway("128").clean();
        try (Connection connection = connection()) {
            execute(connection, """
                    CREATE TABLE dm_asset (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        tenant_id BIGINT NOT NULL DEFAULT 1,
                        project_id BIGINT NOT NULL,
                        component_id BIGINT NULL,
                        asset_type VARCHAR(32) NOT NULL,
                        report_period VARCHAR(16) NULL,
                        asset_code VARCHAR(96) NOT NULL,
                        asset_name VARCHAR(255) NOT NULL,
                        report_date DATE NULL,
                        keywords VARCHAR(500) NULL,
                        content_type VARCHAR(160),
                        file_size BIGINT,
                        attachment_id BIGINT NULL,
                        checksum_md5 CHAR(32),
                        structured_data JSON,
                        owner_id BIGINT NOT NULL,
                        deleted TINYINT NOT NULL DEFAULT 0,
                        deleted_by BIGINT NULL,
                        deleted_at TIMESTAMP NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        created_by BIGINT NULL,
                        updated_by BIGINT NULL,
                        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                    )
                    """);
            execute(connection, """
                    CREATE TABLE dm_asset_relation (
                        id BIGINT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        source_asset_id BIGINT NOT NULL,
                        source_asset_type VARCHAR(32) NOT NULL,
                        target_asset_id BIGINT NOT NULL,
                        target_asset_type VARCHAR(32) NOT NULL,
                        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        created_by BIGINT NOT NULL,
                        UNIQUE KEY uk_asset_relation (tenant_id, source_asset_id, source_asset_type, target_asset_id, target_asset_type)
                    )
                    """);
            execute(connection, """
                    CREATE TABLE dm_meeting_attachment (
                        id BIGINT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        meeting_id BIGINT NOT NULL,
                        attachment_id BIGINT NOT NULL,
                        file_name VARCHAR(500) NOT NULL,
                        sort_order INT NOT NULL DEFAULT 0,
                        deleted TINYINT(1) NOT NULL DEFAULT 0,
                        deleted_by BIGINT,
                        deleted_at DATETIME(6),
                        created_by BIGINT NOT NULL,
                        created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                        active_attachment_key VARCHAR(256)
                            GENERATED ALWAYS AS (
                                CASE WHEN deleted = 0
                                     THEN CONCAT(tenant_id, ':', meeting_id, ':', attachment_id)
                                     ELSE NULL
                                END
                            ) STORED,
                        UNIQUE KEY uk_dm_meeting_att_active (active_attachment_key)
                    )
                    """);
            execute(connection, """
                    CREATE TABLE att_file (
                        id BIGINT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        file_name VARCHAR(255) NOT NULL,
                        file_size BIGINT NOT NULL,
                        object_key VARCHAR(512) NOT NULL,
                        status VARCHAR(16) NOT NULL DEFAULT 'TEMP',
                        uploader_id BIGINT NOT NULL,
                        expires_at TIMESTAMP NOT NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to prepare V98 baseline", ex);
        }
    }

    private void seedSourceData() throws Exception {
        try (Connection connection = connection()) {
            execute(connection, """
                    INSERT INTO att_file (id, tenant_id, file_name, file_size, object_key, uploader_id, expires_at)
                    VALUES (5001, 1, '方案文件.pdf', 100, 'k1', 1, '2037-01-01 00:00:00'),
                           (5002, 1, '会议附件1.docx', 120, 'k2', 1, '2037-01-01 00:00:00'),
                           (5003, 1, '会议附件2.xlsx', 140, 'k3', 1, '2037-01-01 00:00:00'),
                           (5005, 1, '汇报材料.docx', 160, 'k5', 1, '2037-01-01 00:00:00')
                    """);
            execute(connection, """
                    INSERT INTO dm_asset
                        (id, tenant_id, project_id, component_id, asset_type, asset_code, asset_name,
                         attachment_id, checksum_md5, owner_id, deleted, created_by, created_at, updated_by, updated_at)
                    VALUES
                        (10001, 1, 100, 200, 'PLAN', 'PLAN-001', '迁移方案A',
                         5001, 'aaaa0000aaaa0000aaaa0000aaaa0001', 1, 0, 1, '2026-01-05 10:00:00', 2, '2026-02-06 11:30:00'),
                        (10002, 1, 100, NULL, 'REPORT', 'RPT-001', '周报',
                         5005, 'aaaa0000aaaa0000aaaa0000aaaa0002', 1, 0, 1, '2026-01-06 10:00:00', NULL, '2026-01-06 10:00:00'),
                        (10003, 1, 100, NULL, 'MAPPING_DOC', 'MAP-001', '映射文档',
                         NULL, NULL, 1, 1, 1, '2026-01-07 10:00:00', NULL, '2026-01-07 10:00:00'),
                        (10004, 1, 100, NULL, 'DEPENDENCY', 'DEP-001', '依赖文件', NULL, NULL, 1, 0, NULL, '2026-01-08 10:00:00', NULL, '2026-01-08 10:00:00'),
                        (10005, 1, 100, NULL, 'SCRIPT', 'SCR-001', '迁移程序', NULL, NULL, 1, 0, NULL, '2026-01-09 10:00:00', NULL, '2026-01-09 10:00:00'),
                        (10006, 1, 100, NULL, 'TOPIC', 'TOP-001', '专题材料', NULL, NULL, 1, 0, NULL, '2026-01-10 10:00:00', NULL, '2026-01-10 10:00:00'),
                        (10007, 1, 100, NULL, 'RELEASE_DRILL', 'DRILL-001', '演练方案', NULL, NULL, 1, 0, NULL, '2026-01-11 10:00:00', NULL, '2026-01-11 10:00:00')
                    """);
            execute(connection, """
                    UPDATE dm_asset SET report_period = 'WEEKLY', report_date = '2026-08-01', keywords = 'migration,test'
                    WHERE id = 10002
                    """);
            execute(connection, """
                    INSERT INTO dm_asset
                        (id, tenant_id, project_id, asset_type, asset_code, asset_name, report_period, report_date, keywords,
                         attachment_id, checksum_md5, owner_id, deleted, created_by, created_at)
                    VALUES
                        (10011, 1, 100, 'PLAN', 'PLAN-011', '已删除方案',
                         NULL, NULL, NULL,
                         5004, NULL, 1, 1, 1, '2026-03-01 08:00:00')
                    """);
            execute(connection, "UPDATE dm_asset SET deleted_by = 7, deleted_at = '2026-03-03 09:00:00' WHERE id = 10011");
            execute(connection, """
                    INSERT INTO dm_asset
                        (id, tenant_id, project_id, asset_type, asset_code, asset_name, structured_data, owner_id, created_by, created_at)
                    VALUES
                        (10008, 1, 100, 'RULE', 'RULE-001', '检核规则', '{"rule": "x"}', 1, 1, '2026-01-12 10:00:00'),
                        (10009, 1, 100, 'PARAMETER', 'PARAM-001', '迁移参数', '{"p": "v"}', 1, 1, '2026-01-13 10:00:00'),
                        (10010, 1, 100, 'INTERMEDIATE_TABLE', 'IMT-001', '中间表', '{"table": "t"}', 1, 1, '2026-01-14 10:00:00')
                    """);
            execute(connection, """
                    INSERT INTO dm_meeting_attachment
                        (id, tenant_id, meeting_id, attachment_id, file_name, sort_order, deleted, created_by)
                    VALUES
                        (90001, 1, 7001, 5002, '会议附件1.docx', 0, 0, 1),
                        (90002, 1, 7001, 5003, '会议附件2.xlsx', 1, 0, 1),
                        (90003, 1, 7002, 5002, '已删附件', 0, 1, 1)
                    """);
            execute(connection, "UPDATE dm_meeting_attachment SET deleted_by = 9, deleted_at = '2026-04-01 12:00:00' WHERE id = 90003");
            execute(connection, """
                    INSERT INTO dm_asset_relation
                        (id, tenant_id, source_asset_id, source_asset_type, target_asset_id, target_asset_type, created_by)
                    VALUES
                        (80001, 1, 6001, 'ISSUE', 7001, 'MEETING', 1),
                        (80002, 1, 6001, 'ISSUE', 4001, 'TABLE', 1),
                        (80003, 1, 6001, 'ISSUE', 4101, 'FIELD', 1),
                        (80004, 1, 7001, 'MEETING', 6001, 'ISSUE', 2),
                        (80005, 1, 7002, 'MEETING', 6002, 'ISSUE', 2),
                        (80006, 1, 7001, 'MEETING', 3001, 'SYSTEM', 2),
                        (80007, 1, 7001, 'MEETING', 3002, 'SYSTEM', 2)
                    """);
        }
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
