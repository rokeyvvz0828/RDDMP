package com.ccb.architecture.change.integration;

import com.ccb.architecture.change.model.SubsystemChangeModels.ActionType;
import com.ccb.architecture.change.model.SubsystemChangeModels.ApplicationStatus;
import com.ccb.architecture.change.model.SubsystemChangeModels.ChangeApplication;
import com.ccb.architecture.change.model.SubsystemChangeModels.TargetKind;
import com.ccb.architecture.change.model.SubsystemChangeModels.WorkflowReceipt;
import com.ccb.architecture.change.model.SubsystemChangeModels.WorkflowReceiptStart;
import com.ccb.architecture.change.model.SubsystemChangeModels.WorkflowReceiptStatus;
import com.ccb.architecture.change.model.SubsystemChangeModels.WorkflowRound;
import com.ccb.architecture.change.model.SubsystemChangeModels.WorkflowRoundStatus;
import com.ccb.architecture.change.persistence.SubsystemChangeStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证当前子系统变更工作流的跨模块持久化契约；不启动或模拟 Flowable 流程实例。
 */
@Testcontainers
class ArchitectureWorkflowIntegrationMySqlTest {
    private static final long TENANT_ID = 1L;
    private static final long DEFINITION_ID = 900000000000030L;
    private static final long VERSION_ID = 900000000000031L;
    private static final long APPLICATION_ID = 88001L;
    private static final long ROUND_ID = 88002L;
    private static final long RECEIPT_ID = 88003L;
    private static final long WORKFLOW_INSTANCE_ID = 88004L;
    private static final String SUBSCRIBER_KEY = "architecture.subsystem.change.lifecycle.v1";
    private static final String PAYLOAD_DIGEST = "a".repeat(64);
    private static final LocalDateTime STARTED_AT = LocalDateTime.of(2026, 8, 23, 12, 30);
    private static final String DATABASE = "architecture_workflow_integration";
    private static final String CONFLICT_DATABASE = "architecture_v83_conflict";

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName(DATABASE)
            .withUsername("test")
            .withPassword("test");

    private static final ObjectMapper JSON = new ObjectMapper();

    private static DriverManagerDataSource dataSource;
    private static JdbcTemplate jdbc;
    private static TransactionTemplate transactions;

    private SubsystemChangeStore store;

    @BeforeAll
    static void migrate() {
        dataSource = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("ALTER DATABASE `" + DATABASE + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");

        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("filesystem:" + migrationDirectory())
                .placeholders(java.util.Map.of("bootstrap_admin_password_hash", "test-hash"))
                .target(MigrationVersion.fromVersion("123"))
                .cleanDisabled(false)
                .load();
        flyway.clean();
        assertThat(flyway.migrate().success).isTrue();
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @AfterAll
    static void clearStatics() {
        transactions = null;
        jdbc = null;
        dataSource = null;
    }

    @BeforeEach
    void resetChangeData() {
        jdbc.update("DELETE FROM arch_subsystem_workflow_receipt");
        jdbc.update("DELETE FROM arch_subsystem_workflow_round");
        jdbc.update("DELETE FROM arch_subsystem_replacement");
        jdbc.update("DELETE FROM arch_subsystem_value_reservation");
        jdbc.update("DELETE FROM arch_subsystem_change_lock");
        jdbc.update("DELETE FROM arch_subsystem_change_history");
        jdbc.update("DELETE FROM arch_subsystem_physical_draft");
        jdbc.update("DELETE FROM arch_subsystem_change_application");
        store = new SubsystemChangeStore(jdbc);
    }

    @Test
    void v83预置工单菜单三级权限和最小工作流收件箱访问() {
        assertThat(jdbc.queryForObject("SELECT CONCAT(route_name, '|', route_path, '|', permission_code) "
                        + "FROM sys_menu WHERE id = 803 AND tenant_id = 1 AND deleted = 0", String.class))
                .isEqualTo("ArchitectureSubsystemChanges|/architecture/subsystem-change-applications|architecture:view");
        assertThat(jdbc.queryForList("SELECT CONCAT(action_code, '|', permission_code) "
                        + "FROM sys_menu_permission WHERE tenant_id = 1 AND id IN (8031, 8032, 8033) ORDER BY id",
                String.class))
                .containsExactly("view|architecture:view", "apply|architecture:apply", "manage|architecture:manage");
        assertThat(jdbc.queryForObject("SELECT role_code FROM sys_role WHERE id = 110 AND tenant_id = 1 "
                        + "AND status = 1 AND deleted = 0", String.class))
                .isEqualTo("ARCHITECTURE_MANAGER");
        assertThat(count("SELECT COUNT(*) FROM sys_role_permission "
                + "WHERE tenant_id = 1 AND role_id = 110 AND permission_id IN (8031, 8032, 8033)"))
                .isEqualTo(3L);
        assertThat(jdbc.queryForList("SELECT menu_id FROM sys_role_menu "
                        + "WHERE tenant_id = 1 AND role_id = 110 AND menu_id IN (800, 801, 802, 803) "
                        + "ORDER BY menu_id", Long.class))
                .containsExactly(800L, 802L, 803L);
        assertThat(jdbc.queryForList("SELECT menu_id FROM sys_role_menu "
                        + "WHERE tenant_id = 1 AND role_id = 110 AND menu_id IN (200, 201, 202, 203, 204) "
                        + "ORDER BY menu_id", Long.class))
                .containsExactly(200L, 202L);
        assertThat(count("SELECT COUNT(*) FROM sys_role_menu role_menu "
                + "JOIN sys_menu menu ON menu.id = role_menu.menu_id AND menu.tenant_id = role_menu.tenant_id "
                + "WHERE role_menu.tenant_id = 1 AND role_menu.role_id = 110 "
                + "AND role_menu.menu_id IN (200, 202) AND menu.permission_code = 'workflow:access'"))
                .isEqualTo(2L);
        assertThat(count("SELECT COUNT(*) FROM sys_user_role WHERE tenant_id = 1 AND user_id = 1 AND role_id = 110"))
                .isEqualTo(1L);
        assertThat(count("SELECT COUNT(*) FROM sys_role_permission "
                + "WHERE tenant_id = 1 AND role_id = 1 AND permission_id IN (8031, 8032, 8033)"))
                .isEqualTo(3L);
    }

    @Test
    void v83遇到稳定菜单Id身份冲突时失败关闭() {
        try (MySQLContainer<?> conflictMysql = new MySQLContainer<>("mysql:8.4")
                .withDatabaseName(CONFLICT_DATABASE)
                .withUsername("test")
                .withPassword("test")) {
            conflictMysql.start();
            DriverManagerDataSource conflictDataSource = new DriverManagerDataSource(
                    conflictMysql.getJdbcUrl(), conflictMysql.getUsername(), conflictMysql.getPassword());
            JdbcTemplate conflictJdbc = new JdbcTemplate(conflictDataSource);
            conflictJdbc.execute("ALTER DATABASE `" + CONFLICT_DATABASE
                    + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            Flyway v93 = flyway(conflictDataSource, "93");
            assertThat(v93.migrate().success).isTrue();

            conflictJdbc.update("INSERT INTO sys_menu "
                    + "(id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, "
                    + "permission_code, icon, sort_no) VALUES "
                    + "(803, 1, 800, 'menu', '冲突菜单', 'ConflictingArchitectureMenu', '/conflict', "
                    + "'architecture/conflict', 'architecture:view', 'warning', 99)");

            Flyway v94 = flyway(conflictDataSource, "94");
            assertThatThrownBy(v94::migrate)
                    .isInstanceOf(FlywayException.class);
        }
    }

    @Test
    void v84只预置未发布的固定角色审批模型() throws Exception {
        assertThat(jdbc.queryForObject("SELECT code FROM wf_definition WHERE id = ? AND tenant_id = ?",
                String.class, DEFINITION_ID, TENANT_ID)).isEqualTo("architecture.subsystem.change");
        assertThat(jdbc.queryForObject("SELECT status FROM wf_definition WHERE id = ? AND tenant_id = ?",
                String.class, DEFINITION_ID, TENANT_ID)).isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject("SELECT deployment_id FROM wf_definition WHERE id = ? AND tenant_id = ?",
                String.class, DEFINITION_ID, TENANT_ID)).isNull();
        assertThat(jdbc.queryForObject("SELECT current_version FROM wf_definition WHERE id = ? AND tenant_id = ?",
                Integer.class, DEFINITION_ID, TENANT_ID)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT model_schema_version FROM wf_definition WHERE id = ? AND tenant_id = ?",
                Integer.class, DEFINITION_ID, TENANT_ID)).isEqualTo(2);

        assertThat(jdbc.queryForObject("SELECT definition_id FROM wf_version WHERE id = ? AND tenant_id = ?",
                Long.class, VERSION_ID, TENANT_ID)).isEqualTo(DEFINITION_ID);
        assertThat(jdbc.queryForObject("SELECT version_no FROM wf_version WHERE id = ? AND tenant_id = ?",
                Integer.class, VERSION_ID, TENANT_ID)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT model_schema_version FROM wf_version WHERE id = ? AND tenant_id = ?",
                Integer.class, VERSION_ID, TENANT_ID)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT status FROM wf_version WHERE id = ? AND tenant_id = ?",
                String.class, VERSION_ID, TENANT_ID)).isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject("SELECT deployment_id FROM wf_version WHERE id = ? AND tenant_id = ?",
                String.class, VERSION_ID, TENANT_ID)).isNull();

        String definitionJson = jdbc.queryForObject("SELECT definition_json FROM wf_version WHERE id = ? AND tenant_id = ?",
                String.class, VERSION_ID, TENANT_ID);
        JsonNode approval = approvalNode(JSON.readTree(definitionJson));
        JsonNode config = approval.path("config");
        assertThat(config.path("assigneeType").asText()).isEqualTo("ROLE");
        assertThat(config.path("assigneeIds").size()).isEqualTo(1);
        assertThat(config.path("assigneeIds").get(0).asLong()).isEqualTo(110L);
        assertThat(config.path("roleIds").size()).isEqualTo(1);
        assertThat(config.path("roleIds").get(0).asLong()).isEqualTo(110L);
        assertThat(config.path("mode").asText()).isEqualTo("ANY");
        assertThat(config.path("emptyAssigneeAction").asText()).isEqualTo("ERROR");
        assertThat(actionNames(config.path("actionPolicy").path("allowedActions")))
                .containsExactly("APPROVE", "RETURN", "REJECT");
    }

    @Test
    void v82通过Store持久化当前工作流上下文轮次和幂等回执() {
        inTransaction(() -> {
            store.insertApplication(application());
            store.insertPendingWorkflowRound(pendingRound());
            assertThat(store.bindWorkflowRoundStarted(TENANT_ID, APPLICATION_ID, 1,
                    DEFINITION_ID, VERSION_ID, WORKFLOW_INSTANCE_ID, PAYLOAD_DIGEST, STARTED_AT)).isTrue();
            assertThat(store.compareAndSetApplicationWorkflowContext(TENANT_ID, APPLICATION_ID,
                    0, 0, 1, DEFINITION_ID, VERSION_ID, WORKFLOW_INSTANCE_ID, PAYLOAD_DIGEST, 1)).isTrue();
            assertThat(store.beginReceipt(receipt(RECEIPT_ID))).isTrue();
            assertThat(store.completeReceipt(TENANT_ID, "event-88003", SUBSCRIBER_KEY,
                    WorkflowReceiptStatus.PROCESSED, "已持久化工作流事件")).isTrue();
        });

        ChangeApplication application = store.findApplication(TENANT_ID, APPLICATION_ID).orElseThrow();
        assertThat(application)
                .extracting(ChangeApplication::status, ChangeApplication::currentBusinessRound,
                        ChangeApplication::currentWorkflowDefinitionId, ChangeApplication::currentWorkflowVersionId,
                        ChangeApplication::currentWorkflowInstanceId, ChangeApplication::currentPayloadDigest,
                        ChangeApplication::rowVersion)
                .containsExactly(ApplicationStatus.IN_REVIEW, 1, DEFINITION_ID, VERSION_ID,
                        WORKFLOW_INSTANCE_ID, PAYLOAD_DIGEST, 1L);

        WorkflowRound round = store.findWorkflowRound(TENANT_ID, APPLICATION_ID, 1).orElseThrow();
        assertThat(round)
                .extracting(WorkflowRound::workflowDefinitionId, WorkflowRound::workflowVersionId,
                        WorkflowRound::workflowInstanceId, WorkflowRound::payloadDigest,
                        WorkflowRound::status, WorkflowRound::startedAt)
                .containsExactly(DEFINITION_ID, VERSION_ID, WORKFLOW_INSTANCE_ID, PAYLOAD_DIGEST,
                        WorkflowRoundStatus.STARTED, STARTED_AT);

        WorkflowReceipt receipt = store.findReceipt(TENANT_ID, "event-88003", SUBSCRIBER_KEY).orElseThrow();
        assertThat(receipt)
                .extracting(WorkflowReceipt::applicationId, WorkflowReceipt::roundNo,
                        WorkflowReceipt::workflowInstanceId, WorkflowReceipt::eventType,
                        WorkflowReceipt::processingStatus, WorkflowReceipt::detail)
                .containsExactly(APPLICATION_ID, 1, WORKFLOW_INSTANCE_ID, "APPROVED",
                        WorkflowReceiptStatus.PROCESSED, "已持久化工作流事件");

        assertThat(inTransaction(() -> store.beginReceipt(receipt(RECEIPT_ID + 1)))).isFalse();
        assertThat(count("SELECT COUNT(*) FROM arch_subsystem_workflow_receipt "
                + "WHERE tenant_id = 1 AND event_id = 'event-88003' AND subscriber_key = '"
                + SUBSCRIBER_KEY + "'"))
                .isEqualTo(1L);
    }

    private ChangeApplication application() {
        return new ChangeApplication(APPLICATION_ID, TENANT_ID, TargetKind.PHYSICAL, ActionType.CREATE,
                null, 1L, "验证工作流持久化", ApplicationStatus.IN_REVIEW, 0,
                null, null, null, null, false, 0, 1L, 1L, null, null);
    }

    private WorkflowRound pendingRound() {
        return new WorkflowRound(ROUND_ID, TENANT_ID, APPLICATION_ID, 1,
                null, null, null, null, WorkflowRoundStatus.PENDING,
                null, null, null, null);
    }

    private WorkflowReceiptStart receipt(long id) {
        return new WorkflowReceiptStart(id, TENANT_ID, "event-88003", SUBSCRIBER_KEY,
                APPLICATION_ID, 1, WORKFLOW_INSTANCE_ID, "APPROVED");
    }

    private JsonNode approvalNode(JsonNode definition) {
        for (JsonNode node : definition.path("nodes")) {
            if ("approval-architecture-manager".equals(node.path("id").asText())) {
                return node;
            }
        }
        throw new AssertionError("固定审批节点不存在");
    }

    private List<String> actionNames(JsonNode actions) {
        List<String> names = new ArrayList<>();
        actions.forEach(action -> names.add(action.asText()));
        return names;
    }

    private <T> T inTransaction(Supplier<T> action) {
        return transactions.execute(status -> action.get());
    }

    private void inTransaction(Runnable action) {
        transactions.executeWithoutResult(status -> action.run());
    }

    private long count(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }

    private static String migrationDirectory() {
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

    private static Flyway flyway(DriverManagerDataSource targetDataSource, String targetVersion) {
        return Flyway.configure()
                .dataSource(targetDataSource)
                .locations("filesystem:" + migrationDirectory())
                .placeholders(java.util.Map.of("bootstrap_admin_password_hash", "test-hash"))
                .target(MigrationVersion.fromVersion(targetVersion))
                .cleanDisabled(false)
                .load();
    }
}
