package com.ccb.architecture.network.persistence;

import com.ccb.architecture.network.model.NetworkWorkOrderModels.ActionType;
import com.ccb.architecture.network.model.NetworkWorkOrderModels.HistoryEvent;
import com.ccb.architecture.network.model.NetworkWorkOrderModels.Kind;
import com.ccb.architecture.network.model.NetworkWorkOrderModels.WorkOrder;
import com.ccb.architecture.network.model.NetworkWorkOrderModels.WorkOrderStatus;
import com.ccb.architecture.network.model.NetworkWorkOrderModels.WorkflowReceipt;
import com.ccb.architecture.network.model.NetworkWorkOrderModels.WorkflowReceiptStart;
import com.ccb.architecture.network.model.NetworkWorkOrderModels.WorkflowReceiptStatus;
import com.ccb.architecture.network.model.NetworkWorkOrderModels.WorkflowRound;
import com.ccb.architecture.network.model.NetworkWorkOrderModels.WorkflowRoundStatus;
import org.flywaydb.core.Flyway;
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
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 V100 表结构/约束与 Store 持久化契约（REQ-20260823-051）。
 */
@Testcontainers
class NetworkWorkOrderMySqlTest {
    private static final long TENANT_ID = 1L;
    private static final long WORK_ORDER_ID = 77001L;
    private static final long HISTORY_ID = 77002L;
    private static final long ROUND_ID = 77003L;
    private static final long RECEIPT_ID = 77004L;
    private static final long INSTANCE_ID = 77005L;
    private static final String DIGEST = "d".repeat(64);
    private static final String SUBSCRIBER_KEY = "architecture.network.work-order.lifecycle.v1";
    private static final LocalDateTime TIME = LocalDateTime.of(2026, 8, 23, 12, 30);
    private static final String DATABASE = "network_work_order";

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName(DATABASE)
            .withUsername("test")
            .withPassword("test");

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
    void v87表结构与约束完整() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema = ? AND table_name IN ('arch_network_work_order', "
                + "'arch_network_work_order_history', 'arch_network_workflow_round', "
                + "'arch_network_workflow_receipt')", Integer.class, DATABASE)).isEqualTo(4);
        // 跨 kind 动作约束
        assertThatThrownBy(() -> jdbc.update("INSERT INTO arch_network_work_order "
                + "(id, tenant_id, kind, action_type, subject, applicant_id, status, business_payload, "
                + "created_by, updated_by) VALUES (99001, 1, 'CLB', 'ADD', 'x', 1, 'DRAFT', '{}', 1, 1)"))
                .hasMessageContaining("chk_arch_network_work_order_action");
        // 状态约束
        assertThatThrownBy(() -> jdbc.update("INSERT INTO arch_network_work_order "
                + "(id, tenant_id, kind, action_type, subject, applicant_id, status, business_payload, "
                + "created_by, updated_by) VALUES (99002, 1, 'CLB', 'OPEN', 'x', 1, 'DONE', '{}', 1, 1)"))
                .hasMessageContaining("chk_arch_network_work_order_status");
        // 结果状态约束
        assertThatThrownBy(() -> jdbc.update("INSERT INTO arch_network_work_order "
                + "(id, tenant_id, kind, action_type, subject, applicant_id, status, business_payload, "
                + "result_status, created_by, updated_by) VALUES (99003, 1, 'DNS', 'ADD', 'x', 1, "
                + "'DRAFT', '{}', 'PARTIAL', 1, 1)"))
                .hasMessageContaining("chk_arch_network_work_order_result");
    }

    @Test
    void store写入查询与租户隔离() {
        inTransaction(() -> store.insertWorkOrder(workOrder(WORK_ORDER_ID, 1L, "CLB-A")));
        inTransaction(() -> store.insertWorkOrder(workOrder(77011L, 2L, "DNS-B")));

        assertThat(store.findWorkOrder(1L, WORK_ORDER_ID)).isPresent();
        assertThat(store.findWorkOrder(2L, WORK_ORDER_ID)).isEmpty();
        assertThat(store.listWorkOrders(1L, null, null, null, 20, 0)).hasSize(1);
        assertThat(store.listWorkOrders(1L, 9L, Kind.CLB, null, 20, 0)).hasSize(1);
        assertThat(store.listWorkOrders(1L, 9L, Kind.DNS, null, 20, 0)).isEmpty();
    }

    @Test
    void 草稿更新与行版本守卫() {
        inTransaction(() -> store.insertWorkOrder(workOrder(WORK_ORDER_ID, 1L, "CLB-A")));
        assertThat(inTransaction(() -> store.updateDraft(1L, WORK_ORDER_ID, WorkOrderStatus.DRAFT, 0L,
                "新原因", "{\"clbName\":\"CLB-B\",\"purpose\":\"y\",\"description\":null}", "[1,2]", 9L))).isTrue();
        assertThat(inTransaction(() -> store.updateDraft(1L, WORK_ORDER_ID, WorkOrderStatus.DRAFT, 0L,
                "x", "{}", "[]", 9L))).isFalse();

        WorkOrder updated = store.findWorkOrder(1L, WORK_ORDER_ID).orElseThrow();
        assertThat(updated.reason()).isEqualTo("新原因");
        assertThat(updated.rowVersion()).isEqualTo(1L);
    }

    @Test
    void 状态与工作流上下文CAS() {
        inTransaction(() -> store.insertWorkOrder(workOrder(WORK_ORDER_ID, 1L, "CLB-A")));
        assertThat(inTransaction(() -> store.compareAndSetStatus(1L, WORK_ORDER_ID,
                WorkOrderStatus.DRAFT, 0L, WorkOrderStatus.IN_REVIEW, 9L))).isTrue();
        assertThat(inTransaction(() -> store.compareAndSetWorkflowContext(1L, WORK_ORDER_ID,
                0, 1L, 1, 900000000000032L, 1L, INSTANCE_ID, DIGEST, 9L))).isTrue();

        WorkOrder review = store.findWorkOrder(1L, WORK_ORDER_ID).orElseThrow();
        assertThat(review.status()).isEqualTo(WorkOrderStatus.IN_REVIEW);
        assertThat(review.currentBusinessRound()).isEqualTo(1);
        assertThat(review.currentWorkflowInstanceId()).isEqualTo(INSTANCE_ID);
        assertThat(review.currentPayloadDigest()).isEqualTo(DIGEST);
        assertThat(review.rowVersion()).isEqualTo(2L);
    }

    @Test
    void 办理结果登记不改变状态() {
        inTransaction(() -> store.insertWorkOrder(workOrder(WORK_ORDER_ID, 1L, "CLB-A")));
        inTransaction(() -> store.compareAndSetStatus(1L, WORK_ORDER_ID,
                WorkOrderStatus.DRAFT, 0L, WorkOrderStatus.IN_REVIEW, 9L));
        assertThat(inTransaction(() -> store.updateHandlingResult(1L, WORK_ORDER_ID, 1L,
                "SUCCESS", "外部配置完成", "[88]", 12L))).isTrue();

        WorkOrder handled = store.findWorkOrder(1L, WORK_ORDER_ID).orElseThrow();
        assertThat(handled.status()).isEqualTo(WorkOrderStatus.IN_REVIEW);
        assertThat(handled.resultStatus()).isEqualTo(
                com.ccb.architecture.network.model.NetworkWorkOrderModels.HandlingResultStatus.SUCCESS);
        assertThat(handled.resultAttachmentIds()).isEqualTo("[88]");
        assertThat(handled.resultRegisteredBy()).isEqualTo(12L);
    }

    @Test
    void 历史事件与工作流轮次回执幂等() {
        inTransaction(() -> store.insertWorkOrder(workOrder(WORK_ORDER_ID, 1L, "CLB-A")));
        inTransaction(() -> {
            store.insertHistory(new HistoryEvent(HISTORY_ID, 1L, WORK_ORDER_ID, "CREATED", null,
                    WorkOrderStatus.DRAFT, 0, "创建", "{}", null, 9L, TIME));
            store.insertPendingWorkflowRound(new WorkflowRound(ROUND_ID, 1L, WORK_ORDER_ID, 1,
                    null, null, null, null, WorkflowRoundStatus.PENDING, null, null, null, null));
            assertThat(store.bindWorkflowRoundStarted(1L, WORK_ORDER_ID, 1,
                    900000000000032L, 1L, INSTANCE_ID, DIGEST, TIME)).isTrue();
            assertThat(store.beginReceipt(new WorkflowReceiptStart(RECEIPT_ID, 1L, "event-77001",
                    SUBSCRIBER_KEY, WORK_ORDER_ID, 1, INSTANCE_ID, "APPROVED"))).isTrue();
            assertThat(store.completeReceipt(1L, "event-77001", SUBSCRIBER_KEY,
                    WorkflowReceiptStatus.PROCESSED, "已处理")).isTrue();
        });

        assertThat(store.listHistory(1L, WORK_ORDER_ID)).hasSize(1);
        WorkflowRound round = store.findWorkflowRound(1L, WORK_ORDER_ID, 1).orElseThrow();
        assertThat(round.status()).isEqualTo(WorkflowRoundStatus.STARTED);
        assertThat(round.workflowInstanceId()).isEqualTo(INSTANCE_ID);
        assertThat(round.payloadDigest()).isEqualTo(DIGEST);

        // 回执幂等：同 eventId+subscriberKey 第二次 begin 失败
        assertThat(inTransaction(() -> store.beginReceipt(new WorkflowReceiptStart(RECEIPT_ID + 1,
                1L, "event-77001", SUBSCRIBER_KEY, WORK_ORDER_ID, 1, INSTANCE_ID, "APPROVED")))).isFalse();
        WorkflowReceipt receipt = store.findReceipt(1L, "event-77001", SUBSCRIBER_KEY).orElseThrow();
        assertThat(receipt.processingStatus()).isEqualTo(WorkflowReceiptStatus.PROCESSED);
    }

    private WorkOrder workOrder(long id, long tenantId, String subject) {
        return new WorkOrder(id, tenantId, Kind.CLB, ActionType.OPEN, subject, 9L, "原因",
                WorkOrderStatus.DRAFT, "{\"clbName\":\"" + subject + "\",\"purpose\":\"流量接入\","
                + "\"description\":null}", "[]", null, null, "[]", null, null,
                0, null, null, null, null, false, 0, 9L, 9L, TIME, TIME);
    }

    private <T> T inTransaction(Supplier<T> action) {
        return transactions.execute(status -> action.get());
    }

    private void inTransaction(Runnable action) {
        transactions.executeWithoutResult(status -> action.run());
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
}
