package com.ccb.architecture.network.integration;

import com.ccb.architecture.network.model.NetworkWorkOrderModels.WorkflowReceiptStart;
import com.ccb.architecture.network.model.NetworkWorkOrderModels.WorkflowReceiptStatus;
import com.ccb.architecture.network.model.NetworkWorkOrderModels.WorkflowRound;
import com.ccb.architecture.network.model.NetworkWorkOrderModels.WorkflowRoundStatus;
import com.ccb.architecture.network.persistence.NetworkWorkOrderStore;
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
 * 验证 V90 的菜单/权限/角色/流程种子、身份冲突失败关闭与工作流轮次持久化契约
 * （REQ-20260823-051）；不启动或模拟 Flowable 流程实例。
 */
@Testcontainers
class NetworkWorkflowIntegrationMySqlTest {
    private static final long TENANT_ID = 1L;
    private static final long DEFINITION_ID = 900000000000032L;
    private static final long VERSION_ID = 900000000000033L;
    private static final long WORK_ORDER_ID = 88001L;
    private static final long ROUND_ID = 88002L;
    private static final long RECEIPT_ID = 88003L;
    private static final long WORKFLOW_INSTANCE_ID = 88004L;
    private static final String SUBSCRIBER_KEY = "architecture.network.work-order.lifecycle.v1";
    private static final String PAYLOAD_DIGEST = "e".repeat(64);
    private static final LocalDateTime STARTED_AT = LocalDateTime.of(2026, 8, 23, 12, 30);
    private static final String DATABASE = "network_workflow_integration";
    private static final String CONFLICT_DATABASE = "network_v90_conflict";

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName(DATABASE)
            .withUsername("test")
            .withPassword("test");

    private static final ObjectMapper JSON = new ObjectMapper();

    private static DriverManagerDataSource dataSource;
    private static JdbcTemplate jdbc;
    private static TransactionTemplate transactions;

    private NetworkWorkOrderStore store;

    @BeforeAll
    static void migrate() {
        dataSource = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("ALTER DATABASE `" + DATABASE + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");

        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("filesystem:" + migrationDirectory())
                .placeholders(java.util.Map.of("bootstrap_admin_password_hash", "test-hash"))
                .target(MigrationVersion.fromVersion("90"))
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
    void resetNetworkData() {
        jdbc.update("DELETE FROM arch_network_workflow_receipt");
        jdbc.update("DELETE FROM arch_network_workflow_round");
        jdbc.update("DELETE FROM arch_network_work_order_history");
        jdbc.update("DELETE FROM arch_network_work_order");
        store = new NetworkWorkOrderStore(jdbc);
    }

    @Test
    void v90预置工单菜单三级权限与办理角色() {
        assertThat(jdbc.queryForObject("SELECT CONCAT(route_name, '|', route_path, '|', permission_code) "
                        + "FROM sys_menu WHERE id = 808 AND tenant_id = 1 AND deleted = 0", String.class))
                .isEqualTo("ArchitectureNetworkWorkOrders|/architecture/network-work-orders"
                        + "|architecture:network-work-order:view");
        assertThat(jdbc.queryForList("SELECT CONCAT(action_code, '|', permission_code) "
                        + "FROM sys_menu_permission WHERE tenant_id = 1 AND id IN (8081, 8082, 8083) ORDER BY id",
                String.class))
                .containsExactly("view|architecture:network-work-order:view",
                        "apply|architecture:network-work-order:apply",
                        "manage|architecture:network-work-order:manage");
        assertThat(jdbc.queryForObject("SELECT role_code FROM sys_role WHERE id = 113 AND tenant_id = 1 "
                        + "AND status = 1 AND deleted = 0", String.class))
                .isEqualTo("NETWORK_MANAGER");
        assertThat(count("SELECT COUNT(*) FROM sys_role_permission "
                + "WHERE tenant_id = 1 AND role_id = 113 AND permission_id IN (8081, 8082, 8083)"))
                .isEqualTo(3L);
        assertThat(count("SELECT COUNT(*) FROM sys_role_menu "
                + "WHERE tenant_id = 1 AND role_id = 113 AND menu_id IN (800, 808)"))
                .isEqualTo(2L);
        assertThat(jdbc.queryForList("SELECT menu_id FROM sys_role_menu "
                        + "WHERE tenant_id = 1 AND role_id = 113 AND menu_id IN (200, 201, 202, 203, 204) "
                        + "ORDER BY menu_id", Long.class))
                .containsExactly(200L, 202L);
        assertThat(count("SELECT COUNT(*) FROM sys_user_role WHERE tenant_id = 1 AND user_id = 1 AND role_id = 113"))
                .isEqualTo(1L);
        assertThat(count("SELECT COUNT(*) FROM sys_role_permission "
                + "WHERE tenant_id = 1 AND role_id = 1 AND permission_id IN (8081, 8082, 8083)"))
                .isEqualTo(3L);
    }

    @Test
    void v90存量角色兼容映射() {
        assertThat(count("SELECT COUNT(*) FROM sys_role_permission role_permission "
                + "JOIN sys_role_permission inherited ON inherited.role_id = role_permission.role_id "
                + "AND inherited.tenant_id = role_permission.tenant_id AND inherited.permission_id = 8081 "
                + "WHERE role_permission.tenant_id = 1 AND role_permission.permission_id = 8031"))
                .isGreaterThanOrEqualTo(1L);
        assertThat(count("SELECT COUNT(*) FROM sys_role_permission role_permission "
                + "JOIN sys_role_permission inherited ON inherited.role_id = role_permission.role_id "
                + "AND inherited.tenant_id = role_permission.tenant_id AND inherited.permission_id = 8082 "
                + "WHERE role_permission.tenant_id = 1 AND role_permission.permission_id = 8032"))
                .isGreaterThanOrEqualTo(1L);
    }

    @Test
    void v90遇到稳定菜单Id身份冲突时失败关闭() {
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
            Flyway v89 = flyway(conflictDataSource, "89");
            assertThat(v89.migrate().success).isTrue();

            conflictJdbc.update("INSERT INTO sys_menu "
                    + "(id, tenant_id, parent_id, menu_type, menu_name, route_name, route_path, component_path, "
                    + "permission_code, icon, sort_no) VALUES "
                    + "(808, 1, 800, 'menu', '冲突菜单', 'ConflictingNetworkMenu', '/conflict', "
                    + "'architecture/conflict', 'architecture:network-work-order:view', 'warning', 99)");

            Flyway v90 = flyway(conflictDataSource, "90");
            assertThatThrownBy(v90::migrate)
                    .isInstanceOf(FlywayException.class);
        }
    }

    @Test
    void v90只预置未发布的固定角色审批模型() throws Exception {
        assertThat(jdbc.queryForObject("SELECT code FROM wf_definition WHERE id = ? AND tenant_id = ?",
                String.class, DEFINITION_ID, TENANT_ID)).isEqualTo("architecture.network.work-order");
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

        String definitionJson = jdbc.queryForObject("SELECT definition_json FROM wf_version WHERE id = ? AND tenant_id = ?",
                String.class, VERSION_ID, TENANT_ID);
        JsonNode approval = approvalNode(JSON.readTree(definitionJson));
        JsonNode config = approval.path("config");
        assertThat(config.path("assigneeType").asText()).isEqualTo("ROLE");
        assertThat(config.path("assigneeIds").size()).isEqualTo(1);
        assertThat(config.path("assigneeIds").get(0).asLong()).isEqualTo(113L);
        assertThat(config.path("roleIds").size()).isEqualTo(1);
        assertThat(config.path("roleIds").get(0).asLong()).isEqualTo(113L);
        assertThat(config.path("mode").asText()).isEqualTo("ANY");
        assertThat(config.path("emptyAssigneeAction").asText()).isEqualTo("ERROR");
        assertThat(actionNames(config.path("actionPolicy").path("allowedActions")))
                .containsExactly("APPROVE", "RETURN", "REJECT");
    }

    @Test
    void v89Store持久化工作流上下文轮次与幂等回执() {
        inTransaction(() -> {
            store.insertWorkOrder(workOrder());
            store.insertPendingWorkflowRound(pendingRound());
            assertThat(store.bindWorkflowRoundStarted(TENANT_ID, WORK_ORDER_ID, 1,
                    DEFINITION_ID, 1L, WORKFLOW_INSTANCE_ID, PAYLOAD_DIGEST, STARTED_AT)).isTrue();
            assertThat(store.compareAndSetWorkflowContext(TENANT_ID, WORK_ORDER_ID,
                    0, 0, 1, DEFINITION_ID, 1L, WORKFLOW_INSTANCE_ID, PAYLOAD_DIGEST, 1)).isTrue();
            assertThat(store.beginReceipt(receipt(RECEIPT_ID))).isTrue();
            assertThat(store.completeReceipt(TENANT_ID, "event-88003", SUBSCRIBER_KEY,
                    WorkflowReceiptStatus.PROCESSED, "已持久化工作流事件")).isTrue();
        });

        WorkflowRound round = store.findWorkflowRound(TENANT_ID, WORK_ORDER_ID, 1).orElseThrow();
        assertThat(round)
                .extracting(WorkflowRound::workflowDefinitionId, WorkflowRound::workflowVersionId,
                        WorkflowRound::workflowInstanceId, WorkflowRound::payloadDigest,
                        WorkflowRound::status, WorkflowRound::startedAt)
                .containsExactly(DEFINITION_ID, 1L, WORKFLOW_INSTANCE_ID, PAYLOAD_DIGEST,
                        WorkflowRoundStatus.STARTED, STARTED_AT);

        assertThat(inTransaction(() -> store.beginReceipt(receipt(RECEIPT_ID + 1)))).isFalse();
        assertThat(count("SELECT COUNT(*) FROM arch_network_workflow_receipt "
                + "WHERE tenant_id = 1 AND event_id = 'event-88003' AND subscriber_key = '"
                + SUBSCRIBER_KEY + "'"))
                .isEqualTo(1L);
    }

    private com.ccb.architecture.network.model.NetworkWorkOrderModels.WorkOrder workOrder() {
        return new com.ccb.architecture.network.model.NetworkWorkOrderModels.WorkOrder(
                WORK_ORDER_ID, TENANT_ID,
                com.ccb.architecture.network.model.NetworkWorkOrderModels.Kind.DNS,
                com.ccb.architecture.network.model.NetworkWorkOrderModels.ActionType.ADD,
                "demo.example.test", 1L, "验证工作流持久化",
                com.ccb.architecture.network.model.NetworkWorkOrderModels.WorkOrderStatus.IN_REVIEW,
                "{\"domainName\":\"demo.example.test\",\"purpose\":\"演示\",\"description\":null}",
                "[]", null, null, "[]", null, null, 0, null, null, null, null, false,
                0, 1L, 1L, null, null);
    }

    private WorkflowRound pendingRound() {
        return new WorkflowRound(ROUND_ID, TENANT_ID, WORK_ORDER_ID, 1,
                null, null, null, null, WorkflowRoundStatus.PENDING,
                null, null, null, null);
    }

    private WorkflowReceiptStart receipt(long id) {
        return new WorkflowReceiptStart(id, TENANT_ID, "event-88003", SUBSCRIBER_KEY,
                WORK_ORDER_ID, 1, WORKFLOW_INSTANCE_ID, "APPROVED");
    }

    private JsonNode approvalNode(JsonNode definition) {
        for (JsonNode node : definition.path("nodes")) {
            if ("approval-network-manager".equals(node.path("id").asText())) {
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
