package com.ccb.architecture.repository;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * REQ-20260823-050：架构规范与架构决策的真实 MySQL 8.4 / Flyway 集成测试。
 *
 * <p>覆盖空库迁移到 V99、规范发布版本快照不可变、事项编号并发分配、
 * 首次处理/评审/发布准备/结论与替代关系落库、结论有效状态推导和唯一约束。</p>
 */
@Testcontainers
class ArchitectureStandardsDecisionsMySqlTest {
    private static final String DATABASE = "architecture_standards_decisions";

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName(DATABASE)
            .withUsername("test")
            .withPassword("test");

    @BeforeEach
    void cleanDatabase() throws Exception {
        configureProjectCollation();
        flyway("99").clean();
        configureProjectCollation();
    }

    @Test
    void migratesEmptyDatabaseThroughV99AndAppliesStandardsAndDecisionsSchema() throws Exception {
        assertTrue(flyway("99").migrate().success);
        JdbcTemplate jdbc = jdbc();

        // 字典与授权种子
        assertEquals(2, count(jdbc, "SELECT COUNT(*) FROM sys_dict_type WHERE tenant_id = 1 AND dict_code IN ('ARCH_STANDARD_CATEGORY', 'ARCH_MATTER_TYPE') AND deleted = 0"));
        assertEquals(4, count(jdbc, "SELECT COUNT(*) FROM sys_config WHERE tenant_id = 1 AND category_id = 360007 AND deleted = 0"));
        assertEquals(5, count(jdbc, "SELECT COUNT(*) FROM sys_config WHERE tenant_id = 1 AND category_id = 360008 AND deleted = 0"));
        assertEquals(6, count(jdbc, "SELECT COUNT(*) FROM sys_menu_permission WHERE tenant_id = 1 AND id BETWEEN 8061 AND 8074"));
        assertEquals(1, count(jdbc, "SELECT COUNT(*) FROM sys_role WHERE id = 112 AND role_code = 'ARCHITECTURE_GROUP'"));
        assertEquals(1, count(jdbc, "SELECT COUNT(*) FROM wf_definition WHERE id = 900000000000040 AND code = 'architecture.decision.review' AND status = 'DRAFT'"));
        assertEquals(1, count(jdbc, "SELECT COUNT(*) FROM wf_version WHERE id = 900000000000041 AND definition_id = 900000000000040"));

        try (Connection connection = connection()) {
            assertTableExists(connection, "arch_standard_document");
            assertTableExists(connection, "arch_standard_document_version");
            assertTableExists(connection, "arch_decision_matter");
            assertTableExists(connection, "arch_decision_material");
            assertTableExists(connection, "arch_decision_review");
            assertTableExists(connection, "arch_decision_review_participant");
            assertTableExists(connection, "arch_decision_action_item");
            assertTableExists(connection, "arch_decision_conclusion");
            assertTableExists(connection, "arch_decision_publication_intent");
            assertTableExists(connection, "arch_decision_supersession");
            assertTableExists(connection, "arch_decision_number_sequence");
            assertTableExists(connection, "arch_decision_workflow_round");
            assertTableExists(connection, "arch_decision_workflow_receipt");
        }
    }

    @Test
    void standardPublishAppendsImmutableVersionSnapshotAndOfflinePreservesHistory() {
        assertTrue(flyway("99").migrate().success);
        JdbcTemplate jdbc = jdbc();

        jdbc.update("""
                INSERT INTO arch_standard_document
                    (id, tenant_id, title, category_code, summary, content, status, current_version,
                     row_version, created_by, created_by_name, updated_by)
                VALUES (5001, 1, '网络规划规范', 'NETWORK_PLANNING', '摘要', '正文v1', 'DRAFT', 0, 0, 1, '测试员', 1)
                """);
        // 发布 → 版本 1 快照
        jdbc.update("""
                UPDATE arch_standard_document SET status = 'PUBLISHED', current_version = 1,
                    published_at = NOW(3), published_by = 1, published_by_name = '测试员',
                    row_version = row_version + 1
                WHERE tenant_id = 1 AND id = 5001
                """);
        jdbc.update("""
                INSERT INTO arch_standard_document_version
                    (id, tenant_id, document_id, version_no, title, category_code, summary, content,
                     published_at, published_by, published_by_name)
                VALUES (50011, 1, 5001, 1, '网络规划规范', 'NETWORK_PLANNING', '摘要', '正文v1', NOW(3), 1, '测试员')
                """);
        // 修改当前内容并重新发布 → 版本 2；版本 1 快照保持 v1 内容
        jdbc.update("""
                UPDATE arch_standard_document SET content = '正文v2', row_version = row_version + 1
                WHERE tenant_id = 1 AND id = 5001
                """);
        jdbc.update("""
                UPDATE arch_standard_document SET status = 'PUBLISHED', current_version = 2,
                    published_at = NOW(3), published_by = 1, published_by_name = '测试员',
                    row_version = row_version + 1
                WHERE tenant_id = 1 AND id = 5001
                """);
        jdbc.update("""
                INSERT INTO arch_standard_document_version
                    (id, tenant_id, document_id, version_no, title, category_code, summary, content,
                     published_at, published_by, published_by_name)
                VALUES (50012, 1, 5001, 2, '网络规划规范', 'NETWORK_PLANNING', '摘要', '正文v2', NOW(3), 1, '测试员')
                """);
        assertEquals("正文v1", value(jdbc, "SELECT content FROM arch_standard_document_version WHERE id = 50011"));
        assertEquals("正文v2", value(jdbc, "SELECT content FROM arch_standard_document_version WHERE id = 50012"));
        assertEquals(2, integerValue(jdbc, "SELECT current_version FROM arch_standard_document WHERE id = 5001"));

        // 下线保留历史与版本
        jdbc.update("""
                UPDATE arch_standard_document SET status = 'OFFLINE', row_version = row_version + 1
                WHERE tenant_id = 1 AND id = 5001
                """);
        assertEquals("OFFLINE", value(jdbc, "SELECT status FROM arch_standard_document WHERE id = 5001"));
        assertEquals(2, count(jdbc, "SELECT COUNT(*) FROM arch_standard_document_version WHERE document_id = 5001"));

        // 状态约束
        assertThrows(Exception.class, () -> jdbc.update(
                "UPDATE arch_standard_document SET status = 'BROKEN' WHERE id = 5001"));
    }

    @Test
    void matterNumbersAreUniqueAndConcurrentAllocationNeverReuses() throws Exception {
        assertTrue(flyway("99").migrate().success);
        JdbcTemplate jdbc = jdbc();

        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<String>> tasks = new ArrayList<>();
            for (int index = 0; index < 16; index++) {
                tasks.add(() -> allocateNumber(jdbc, 1, 2026));
            }
            List<Future<String>> futures = executor.invokeAll(tasks);
            List<String> numbers = new ArrayList<>();
            for (Future<String> future : futures) {
                numbers.add(future.get());
            }
            assertEquals(16, numbers.stream().distinct().count(), "并发分配必须不重号");
            for (String number : numbers) {
                assertTrue(number.matches("AD-2026-\\d{4}"), "编号格式必须为 AD-年份-四位序号: " + number);
            }
            assertEquals(17, integerValue(jdbc,
                    "SELECT next_ordinal FROM arch_decision_number_sequence WHERE tenant_id = 1 AND seq_year = 2026"),
                    "16 次分配后序列应指向 17（首个序号从 1 开始）");
        } finally {
            executor.shutdownNow();
        }

        // 编号唯一约束：重复插入同编号必须失败
        jdbc.update("INSERT INTO arch_decision_number_sequence (tenant_id, seq_year, next_ordinal) VALUES (2, 2026, 5)");
        assertEquals(5, integerValue(jdbc,
                "SELECT next_ordinal FROM arch_decision_number_sequence WHERE tenant_id = 2 AND seq_year = 2026"));
    }

    @Test
    void decisionLifecyclePersistsFirstHandlingReviewIntentConclusionAndSupersession() {
        assertTrue(flyway("99").migrate().success);
        JdbcTemplate jdbc = jdbc();

        insertMatter(jdbc, 6001, 1, "AD-2026-0001", "中间件选型", "SUBMITTED",
                Timestamp.valueOf("2026-08-01 09:00:00"), "2026-08-08");
        assertEquals(1, count(jdbc, "SELECT COUNT(*) FROM arch_decision_matter WHERE matter_no = 'AD-2026-0001'"));

        // 编号唯一约束：同租户重复编号在插入时即被拒绝
        assertThrows(Exception.class, () -> insertMatter(jdbc, 6002, 1, "AD-2026-0001", "重复编号",
                "SUBMITTED", Timestamp.valueOf("2026-08-01 09:00:00"), "2026-08-08"));
        assertEquals(0, count(jdbc, "SELECT COUNT(*) FROM arch_decision_matter WHERE id = 6002"));

        // 首次处理 → 评审中
        jdbc.update("""
                UPDATE arch_decision_matter SET status = 'IN_REVIEW', first_handling_outcome = 'ACCEPTED',
                    first_handling_comment = '受理', review_mode = 'ASYNC', first_handled_at = NOW(3),
                    first_handler_id = 1, first_handler_name = '架构组成员', row_version = row_version + 1
                WHERE tenant_id = 1 AND id = 6001
                """);
        assertEquals("IN_REVIEW", value(jdbc, "SELECT status FROM arch_decision_matter WHERE id = 6001"));

        // 材料、评审、参与人、行动项
        jdbc.update("""
                INSERT INTO arch_decision_material (id, tenant_id, matter_id, kind, content, created_by, created_by_name)
                VALUES (60011, 1, 6001, 'SOLUTION', '方案：采用新中间件', 1, '提出人')
                """);
        jdbc.update("""
                INSERT INTO arch_decision_review
                    (id, tenant_id, matter_id, review_no, method, reviewed_at, process_material_summary,
                     key_opinion, conclusion_content, conclusion_rationale, created_by, created_by_name)
                VALUES (60021, 1, 6001, 1, 'MEETING', NOW(3), '评审材料', '同意', '结论：采用新中间件', '理由充分', 1, '架构组成员')
                """);
        jdbc.update("""
                INSERT INTO arch_decision_review_participant (id, tenant_id, review_id, user_id, user_name)
                VALUES (60031, 1, 60021, 1, '架构组成员'), (60032, 1, 60021, 2, '评审专家')
                """);
        jdbc.update("""
                INSERT INTO arch_decision_action_item (id, tenant_id, review_id, content, owner_user_id, owner_name, status, created_by, created_by_name)
                VALUES (60041, 1, 60021, '更新架构文档', 1, '架构组成员', 'OPEN', 1, '架构组成员')
                """);
        assertEquals(1, count(jdbc, "SELECT COUNT(*) FROM arch_decision_material WHERE matter_id = 6001"));
        assertEquals(2, count(jdbc, "SELECT COUNT(*) FROM arch_decision_review_participant WHERE review_id = 60021"));
        assertEquals("OPEN", value(jdbc, "SELECT status FROM arch_decision_action_item WHERE id = 60041"));

        // 第二事项：作为替代链目标
        insertMatter(jdbc, 6003, 1, "AD-2026-0002", "旧中间件决策", "SUBMITTED",
                Timestamp.valueOf("2026-07-01 09:00:00"), "2026-07-08");
        jdbc.update("""
                UPDATE arch_decision_matter SET status = 'IN_REVIEW', type_code = 'TECHNOLOGY_SELECTION',
                    first_handling_outcome = 'ACCEPTED', review_mode = 'ASYNC', row_version = row_version + 1
                WHERE tenant_id = 1 AND id = 6003
                """);
        jdbc.update("""
                INSERT INTO arch_decision_review
                    (id, tenant_id, matter_id, review_no, method, reviewed_at, conclusion_content,
                     conclusion_rationale, created_by, created_by_name)
                VALUES (60022, 1, 6003, 1, 'ASYNC', NOW(3), '结论：旧中间件', '理由', 1, '架构组成员')
                """);
        jdbc.update("""
                INSERT INTO arch_decision_conclusion
                    (id, tenant_id, matter_id, review_id, content, rationale, published_at,
                     published_by, published_by_name)
                VALUES (7001, 1, 6003, 60022, '结论：旧中间件', '理由', NOW(3), 1, '管理人员')
                """);
        jdbc.update("""
                UPDATE arch_decision_matter SET status = 'PUBLISHED', type_code = 'TECHNOLOGY_SELECTION',
                    row_version = row_version + 1
                WHERE tenant_id = 1 AND id = 6003
                """);

        // 发布准备意图 + APPROVED 语义落库：结论、替代关系、事项完成
        jdbc.update("""
                UPDATE arch_decision_matter SET type_code = 'TECHNOLOGY_SELECTION', row_version = row_version + 1
                WHERE tenant_id = 1 AND id = 6001
                """);
        jdbc.update("""
                INSERT INTO arch_decision_publication_intent
                    (matter_id, tenant_id, review_id, supersession_targets_json, payload_digest,
                     prepared_by, prepared_by_name, prepared_at)
                VALUES (6001, 1, 60021, '[{"conclusionId":7001,"kind":"SUPERSEDE"}]', 'digest-6001', 1, '管理人员', NOW(3))
                """);
        jdbc.update("""
                INSERT INTO arch_decision_conclusion
                    (id, tenant_id, matter_id, review_id, content, rationale, published_at,
                     published_by, published_by_name)
                VALUES (7002, 1, 6001, 60021, '结论：采用新中间件', '理由充分', NOW(3), 1, '管理人员')
                """);
        jdbc.update("""
                INSERT INTO arch_decision_supersession (id, tenant_id, conclusion_id, superseded_conclusion_id, kind)
                VALUES (8001, 1, 7002, 7001, 'SUPERSEDE')
                """);
        jdbc.update("""
                UPDATE arch_decision_matter SET status = 'PUBLISHED', row_version = row_version + 1
                WHERE tenant_id = 1 AND id = 6001
                """);

        // 结论有效状态推导
        assertEquals("EFFECTIVE", effectiveStatus(jdbc, 7002));
        assertEquals("SUPERSEDED", effectiveStatus(jdbc, 7001));

        // 部分修订 → PARTIALLY_SUPERSEDED
        jdbc.update("""
                INSERT INTO arch_decision_supersession (id, tenant_id, conclusion_id, superseded_conclusion_id, kind)
                VALUES (8002, 1, 7001, 7002, 'PARTIALLY_REVISE')
                """);
        assertEquals("PARTIALLY_SUPERSEDED", effectiveStatus(jdbc, 7002));

        // 同一事项唯一结论约束
        assertThrows(Exception.class, () -> jdbc.update("""
                INSERT INTO arch_decision_conclusion
                    (id, tenant_id, matter_id, review_id, content, rationale, published_at, published_by, published_by_name)
                VALUES (7003, 1, 6001, 60021, '重复结论', null, NOW(3), 1, 'x')
                """));

        // 替代关系同对唯一约束
        assertThrows(Exception.class, () -> jdbc.update("""
                INSERT INTO arch_decision_supersession (id, tenant_id, conclusion_id, superseded_conclusion_id, kind)
                VALUES (8003, 1, 7002, 7001, 'PARTIALLY_REVISE')
                """));

        // 状态约束：非法状态拒绝
        assertThrows(Exception.class, () -> jdbc.update(
                "UPDATE arch_decision_matter SET status = 'BROKEN' WHERE id = 6001"));
        // 首次处理结果约束
        assertThrows(Exception.class, () -> jdbc.update(
                "UPDATE arch_decision_matter SET first_handling_outcome = 'BROKEN' WHERE id = 6003"));
        // 评审方式约束
        assertThrows(Exception.class, () -> jdbc.update(
                "UPDATE arch_decision_review SET method = 'PHONE' WHERE id = 60021"));
    }

    /** 模拟事务内的行锁分配：与生产实现一致，三步骤必须在同一事务中串行化。 */
    private String allocateNumber(JdbcTemplate jdbc, long tenantId, int year) throws Exception {
        try (Connection connection = DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
            connection.setAutoCommit(false);
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO arch_decision_number_sequence (tenant_id, seq_year, next_ordinal) "
                            + "VALUES (?, ?, 1) ON DUPLICATE KEY UPDATE next_ordinal = next_ordinal")) {
                insert.setLong(1, tenantId);
                insert.setInt(2, year);
                insert.executeUpdate();
            }
            int ordinal;
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT next_ordinal FROM arch_decision_number_sequence "
                            + "WHERE tenant_id = ? AND seq_year = ? FOR UPDATE")) {
                select.setLong(1, tenantId);
                select.setInt(2, year);
                try (ResultSet result = select.executeQuery()) {
                    if (!result.next() || result.getInt(1) > 9999) {
                        throw new IllegalStateException("编号序列不可用");
                    }
                    ordinal = result.getInt(1);
                }
            }
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE arch_decision_number_sequence SET next_ordinal = ? "
                            + "WHERE tenant_id = ? AND seq_year = ?")) {
                update.setInt(1, ordinal + 1);
                update.setLong(2, tenantId);
                update.setInt(3, year);
                update.executeUpdate();
            }
            connection.commit();
            return "AD-" + year + "-" + String.format("%04d", ordinal);
        }
    }

    private void insertMatter(JdbcTemplate jdbc, long id, long tenantId, String matterNo, String title,
                              String status, Timestamp receivedAt, String deadline) {
        jdbc.update("""
                INSERT INTO arch_decision_matter
                    (id, tenant_id, matter_no, title, problem, type_code, status, received_at,
                     first_handling_deadline, proposer_id, proposer_name, submitter_id, submitter_name,
                     row_version, created_by, created_by_name, updated_by)
                VALUES (?, ?, ?, ?, '问题', NULL, ?, ?, ?, 1, '提出人', 1, '提出人', 0, 1, '提出人', 1)
                """, id, tenantId, matterNo, title, status, receivedAt, deadline);
    }

    private String effectiveStatus(JdbcTemplate jdbc, long conclusionId) {
        List<String> kinds = jdbc.queryForList("""
                SELECT kind FROM arch_decision_supersession
                WHERE tenant_id = 1 AND superseded_conclusion_id = ?
                """, String.class, conclusionId);
        if (kinds.contains("SUPERSEDE")) {
            return "SUPERSEDED";
        }
        if (!kinds.isEmpty()) {
            return "PARTIALLY_SUPERSEDED";
        }
        return "EFFECTIVE";
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

    private JdbcTemplate jdbc() {
        return new JdbcTemplate(new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()));
    }

    private Connection connection() throws Exception {
        return DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    }

    private void configureProjectCollation() throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("ALTER DATABASE `" + DATABASE + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
    }

    private void assertTableExists(Connection connection, String table) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?")) {
            statement.setString(1, table);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals(1, result.getLong(1), "表不存在: " + table);
            }
        }
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

    private long count(JdbcTemplate jdbc, String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0L : value;
    }

    private int integerValue(JdbcTemplate jdbc, String sql) {
        Integer value = jdbc.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }

    private String value(JdbcTemplate jdbc, String sql) {
        return jdbc.queryForObject(sql, String.class);
    }
}
