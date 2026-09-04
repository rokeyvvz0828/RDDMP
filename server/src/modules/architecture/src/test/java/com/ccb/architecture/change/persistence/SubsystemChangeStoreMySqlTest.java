package com.ccb.architecture.change.persistence;

import com.ccb.architecture.change.model.SubsystemChangeModels.ActionType;
import com.ccb.architecture.change.model.SubsystemChangeModels.ApplicationStatus;
import com.ccb.architecture.change.model.SubsystemChangeModels.ChangeApplication;
import com.ccb.architecture.change.model.SubsystemChangeModels.ChangeHistoryEvent;
import com.ccb.architecture.change.model.SubsystemChangeModels.PhysicalDraft;
import com.ccb.architecture.change.model.SubsystemChangeModels.PhysicalPublishedState;
import com.ccb.architecture.change.model.SubsystemChangeModels.PhysicalReplacement;
import com.ccb.architecture.change.model.SubsystemChangeModels.PublishedStatus;
import com.ccb.architecture.change.model.SubsystemChangeModels.TargetKind;
import com.ccb.architecture.change.model.SubsystemChangeModels.TargetLock;
import com.ccb.architecture.change.model.SubsystemChangeModels.ValueReservation;
import com.ccb.architecture.change.model.SubsystemChangeModels.WorkflowReceipt;
import com.ccb.architecture.change.model.SubsystemChangeModels.WorkflowReceiptStart;
import com.ccb.architecture.change.model.SubsystemChangeModels.WorkflowReceiptStatus;
import com.ccb.architecture.change.model.SubsystemChangeModels.WorkflowRound;
import com.ccb.architecture.change.model.SubsystemChangeModels.WorkflowRoundStatus;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
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

@Testcontainers
class SubsystemChangeStoreMySqlTest {
    private static final String DATABASE = "architecture_change_store";
    private static final long TENANT_1 = 91L;
    private static final long TENANT_2 = 92L;
    private static final String SUBSCRIBER = "architecture.subsystem.change.lifecycle.v1";

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName(DATABASE)
            .withUsername("test")
            .withPassword("test");

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
                .target(MigrationVersion.fromVersion("147"))
                .cleanDisabled(false)
                .load();
        flyway.clean();
        flyway.migrate();
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @AfterAll
    static void clearStatics() {
        transactions = null;
        jdbc = null;
        dataSource = null;
    }

    @BeforeEach
    void resetData() {
        jdbc.update("DELETE FROM arch_subsystem_workflow_receipt WHERE tenant_id IN (?, ?)", TENANT_1, TENANT_2);
        jdbc.update("DELETE FROM arch_subsystem_workflow_round WHERE tenant_id IN (?, ?)", TENANT_1, TENANT_2);
        jdbc.update("DELETE FROM arch_subsystem_replacement WHERE tenant_id IN (?, ?)", TENANT_1, TENANT_2);
        jdbc.update("DELETE FROM arch_subsystem_value_reservation WHERE tenant_id IN (?, ?)", TENANT_1, TENANT_2);
        jdbc.update("DELETE FROM arch_subsystem_change_lock WHERE tenant_id IN (?, ?)", TENANT_1, TENANT_2);
        jdbc.update("DELETE FROM arch_subsystem_change_history WHERE tenant_id IN (?, ?)", TENANT_1, TENANT_2);
        jdbc.update("DELETE FROM arch_subsystem_physical_draft WHERE tenant_id IN (?, ?)", TENANT_1, TENANT_2);
        jdbc.update("DELETE FROM arch_subsystem_change_application WHERE tenant_id IN (?, ?)", TENANT_1, TENANT_2);
        jdbc.update("DELETE FROM arch_physical_subsystem WHERE tenant_id IN (?, ?)", TENANT_1, TENANT_2);
        store = new SubsystemChangeStore(jdbc);
    }

    @Test
    void tenantIsolationAndPhysicalDraftReplacementKeepStableLineOrder() {
        inTransaction(() -> {
            store.insertApplication(application(1001, TENANT_1, ActionType.CREATE, null));
            store.insertApplication(application(2001, TENANT_2, ActionType.CREATE, null));
            store.replacePhysicalDrafts(TENANT_1, 1001, List.of(
                    physicalDraft(1001, 20, TENANT_1, "PHY_TWENTY", "物理二十"),
                    physicalDraft(1001, 10, TENANT_1, "PHY_TEN", "物理十")));
        });

        assertThat(store.findApplication(TENANT_2, 1001)).isEmpty();
        assertThat(store.listApplications(TENANT_1, null, null, 20, 0))
                .extracting(ChangeApplication::id)
                .containsExactly(1001L);
        assertThat(store.findPhysicalDrafts(TENANT_1, 1001))
                .extracting(PhysicalDraft::lineNo)
                .containsExactly(10, 20);
        assertThat(store.findPhysicalDrafts(TENANT_2, 1001)).isEmpty();
    }

    @Test
    void targetAndValueUniqueConstraintsAndApplicationCasAreTenantScoped() {
        inTransaction(() -> {
            store.insertApplication(application(3001, TENANT_1, ActionType.UPDATE, 900L));
            store.insertApplication(application(3002, TENANT_1, ActionType.UPDATE, 901L));
            store.insertTargetLock(new TargetLock(TENANT_1, TargetKind.PHYSICAL, 900, 3001, null));
            store.insertValueReservation(new ValueReservation(TENANT_1, "PHYSICAL_CODE", "PHY_A", 3001, 1, null));
        });

        assertThatThrownBy(() -> inTransaction(() ->
                store.insertTargetLock(new TargetLock(TENANT_1, TargetKind.PHYSICAL, 900, 3002, null))))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> inTransaction(() ->
                store.insertValueReservation(new ValueReservation(TENANT_1, "PHYSICAL_CODE", "PHY_A", 3002, 1, null))))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(inTransaction(() -> store.compareAndSetApplicationStatus(
                TENANT_1, 3001, ApplicationStatus.DRAFT, 0, ApplicationStatus.IN_REVIEW, 71))).isTrue();
        assertThat(inTransaction(() -> store.compareAndSetApplicationStatus(
                TENANT_1, 3001, ApplicationStatus.DRAFT, 0, ApplicationStatus.RETURNED, 71))).isFalse();
        assertThat(inTransaction(() -> store.compareAndSetApplicationReason(
                TENANT_1, 3002, ApplicationStatus.DRAFT, 0, "更新后的申请原因", 72))).isTrue();
        assertThat(inTransaction(() -> store.compareAndSetApplicationReason(
                TENANT_2, 3002, ApplicationStatus.DRAFT, 1, "跨租户覆盖", 73))).isFalse();

        ChangeApplication changed = store.findApplication(TENANT_1, 3001).orElseThrow();
        assertThat(changed.status()).isEqualTo(ApplicationStatus.IN_REVIEW);
        assertThat(changed.rowVersion()).isEqualTo(1);
        assertThat(store.findTargetLock(TENANT_2, TargetKind.PHYSICAL, 900)).isEmpty();
        assertThat(store.findValueReservation(TENANT_2, "PHYSICAL_CODE", "PHY_A")).isEmpty();
        assertThat(store.findApplication(TENANT_1, 3002).orElseThrow().reason()).isEqualTo("更新后的申请原因");
    }

    @Test
    void physicalPublishedCrudPersistsNewFieldsAndReplacement() {
        inTransaction(() -> {
            store.insertApplication(application(4001, TENANT_1, ActionType.REPLACE, 7001L));
            store.insertPhysicalPublished(7001, TENANT_1,
                    physicalDraft(4001, 1, TENANT_1, "PHY_OLD", "旧物理"), PublishedStatus.ACTIVE, 0, 81);
            store.insertPhysicalPublished(7002, TENANT_1,
                    physicalDraft(4001, 2, TENANT_1, "PHY_NEW", "新物理"), PublishedStatus.ACTIVE, 0, 81);

            PhysicalDraft updated = physicalDraft(4001, 1, TENANT_1, "PHY_OLD", "更新物理");
            assertThat(store.updatePhysicalPublishedFields(TENANT_1, 7001, updated, 0, 82)).isTrue();
            assertThat(store.updatePhysicalPublishedStatus(TENANT_1, 7001, PublishedStatus.OFFLINE, 1, 82)).isTrue();
            store.insertPhysicalReplacement(new PhysicalReplacement(4200, TENANT_1, 7001, 7002, 4001, null));
        });

        PhysicalPublishedState physical = store.findPhysical(TENANT_1, 7001).orElseThrow();
        assertThat(physical)
                .extracting(PhysicalPublishedState::code, PhysicalPublishedState::logicalSubsystemName,
                        PhysicalPublishedState::businessComponentCode, PhysicalPublishedState::status,
                        PhysicalPublishedState::rowVersion)
                .containsExactly("PHY_OLD", "物理逻辑域", "architecture.business-component.employee-portal",
                        PublishedStatus.OFFLINE, 2L);
        assertThat(store.findPhysical(TENANT_2, 7001)).isEmpty();
        assertThat(store.findPhysicalReplacementByApplication(TENANT_1, 4001)).isPresent();
        assertThat(count("SELECT COUNT(*) FROM arch_subsystem_replacement WHERE tenant_id = " + TENANT_2)).isZero();
    }

    @Test
    void rollbackLeavesNoPartialRowsAndHistoryOrderIsStableAtSameTimestamp() {
        LocalDateTime sameTime = LocalDateTime.of(2026, 8, 23, 1, 0);
        inTransaction(() -> {
            store.insertApplication(application(5001, TENANT_1, ActionType.CREATE, null));
            store.insertHistory(history(5002, 5001, "后写事件", sameTime));
            store.insertHistory(history(5001, 5001, "先排序事件", sameTime));
        });
        assertThat(store.listHistory(TENANT_1, 5001))
                .extracting(ChangeHistoryEvent::id)
                .containsExactly(5001L, 5002L);

        transactions.executeWithoutResult(status -> {
            store.insertApplication(application(5101, TENANT_1, ActionType.CREATE, null));
            store.replacePhysicalDrafts(TENANT_1, 5101,
                    List.of(physicalDraft(5101, 1, TENANT_1, "PHY_ROLLBACK", "待回滚物理")));
            store.insertTargetLock(new TargetLock(TENANT_1, TargetKind.PHYSICAL, 777, 5101, null));
            store.insertHistory(history(5103, 5101, "待回滚事件", sameTime));
            status.setRollbackOnly();
        });

        assertThat(store.findApplication(TENANT_1, 5101)).isEmpty();
        assertThat(store.findPhysicalDrafts(TENANT_1, 5101)).isEmpty();
        assertThat(store.findTargetLock(TENANT_1, TargetKind.PHYSICAL, 777)).isEmpty();
        assertThat(store.listHistory(TENANT_1, 5101)).isEmpty();
    }

    @Test
    void workflowRoundBindingContextCancellationAndLatestChecksAreTenantScoped() {
        LocalDateTime startedAt = LocalDateTime.of(2026, 8, 23, 2, 0, 0);
        LocalDateTime endedAt = LocalDateTime.of(2026, 8, 23, 2, 5, 0);
        String digest = "a".repeat(64);

        inTransaction(() -> {
            store.insertApplication(application(7001, TENANT_1, ActionType.CREATE, null));
            assertThat(store.compareAndSetApplicationStatus(TENANT_1, 7001, ApplicationStatus.DRAFT, 0,
                    ApplicationStatus.IN_REVIEW, 71)).isTrue();
            store.insertPendingWorkflowRound(pendingRound(7101, TENANT_1, 7001, 1));
            assertThat(store.lockWorkflowRound(TENANT_1, 7001, 1)).isPresent();
            assertThat(store.bindWorkflowRoundStarted(TENANT_1, 7001, 1, 900000000000030L,
                    900000000000031L, 7003, digest, startedAt)).isTrue();
            assertThat(store.compareAndSetApplicationWorkflowContext(TENANT_1, 7001, 0, 1, 1,
                    900000000000030L, 900000000000031L, 7003, digest, 72)).isTrue();
        });

        ChangeApplication application = store.findApplication(TENANT_1, 7001).orElseThrow();
        assertThat(application)
                .extracting(ChangeApplication::status, ChangeApplication::currentBusinessRound,
                        ChangeApplication::currentWorkflowDefinitionId, ChangeApplication::currentWorkflowVersionId,
                        ChangeApplication::currentWorkflowInstanceId, ChangeApplication::currentPayloadDigest,
                        ChangeApplication::rowVersion)
                .containsExactly(ApplicationStatus.IN_REVIEW, 1, 900000000000030L, 900000000000031L,
                        7003L, digest, 2L);
        assertThat(store.findWorkflowRound(TENANT_1, 7001, 1)).isPresent()
                .get().extracting(WorkflowRound::status, WorkflowRound::startedAt, WorkflowRound::payloadDigest)
                .containsExactly(WorkflowRoundStatus.STARTED, startedAt, digest);
        assertThat(store.findWorkflowRound(TENANT_2, 7001, 1)).isEmpty();
        assertThat(inTransaction(() -> store.lockWorkflowRoundByInstance(TENANT_1, 7003))).isPresent();
        assertThat(inTransaction(() -> store.lockWorkflowRoundByInstance(TENANT_2, 7003))).isEmpty();
        assertThat(store.isLatestWorkflowRound(TENANT_1, 7001, 1)).isTrue();

        inTransaction(() -> store.insertPendingWorkflowRound(pendingRound(7102, TENANT_1, 7001, 2)));
        assertThat(store.isLatestWorkflowRound(TENANT_1, 7001, 1)).isFalse();
        assertThat(store.isLatestWorkflowRound(TENANT_1, 7001, 2)).isTrue();
        assertThat(store.isLatestWorkflowRound(TENANT_1, 7001, 3)).isFalse();

        assertThat(inTransaction(() -> store.compareAndSetCancellationRequested(TENANT_1, 7001, 2, 7003, 75))).isTrue();
        assertThat(inTransaction(() -> store.compareAndSetCancellationRequested(TENANT_1, 7001, 2, 7003, 76))).isFalse();
        assertThat(store.findApplication(TENANT_1, 7001).orElseThrow())
                .extracting(ChangeApplication::cancellationRequested, ChangeApplication::rowVersion,
                        ChangeApplication::updatedBy)
                .containsExactly(true, 3L, 75L);

        assertThat(inTransaction(() -> store.completeStartedWorkflowRound(TENANT_1, 7001, 1,
                WorkflowRoundStatus.RETURNED, endedAt))).isTrue();
        assertThat(inTransaction(() -> store.completeStartedWorkflowRound(TENANT_1, 7001, 1,
                WorkflowRoundStatus.RETURNED, endedAt))).isFalse();
        assertThat(store.findWorkflowRound(TENANT_1, 7001, 1).orElseThrow())
                .extracting(WorkflowRound::status, WorkflowRound::endedAt)
                .containsExactly(WorkflowRoundStatus.RETURNED, endedAt);
    }

    @Test
    void startedWorkflowRoundsSupportAllTerminalOutcomes() {
        List<WorkflowRoundStatus> outcomes = List.of(WorkflowRoundStatus.RETURNED, WorkflowRoundStatus.APPROVED,
                WorkflowRoundStatus.REJECTED, WorkflowRoundStatus.TERMINATED);
        LocalDateTime startedAt = LocalDateTime.of(2026, 8, 23, 3, 0, 0);
        LocalDateTime endedAt = LocalDateTime.of(2026, 8, 23, 3, 1, 0);

        inTransaction(() -> {
            for (int index = 0; index < outcomes.size(); index++) {
                long applicationId = 7201L + index;
                int roundNo = index + 1;
                store.insertApplication(application(applicationId, TENANT_1, ActionType.CREATE, null));
                store.insertPendingWorkflowRound(pendingRound(7301L + index, TENANT_1, applicationId, roundNo));
                assertThat(store.bindWorkflowRoundStarted(TENANT_1, applicationId, roundNo,
                        900000000000030L, 900000000000031L, 7401L + index,
                        ("b" + index).repeat(32), startedAt)).isTrue();
                assertThat(store.completeStartedWorkflowRound(TENANT_1, applicationId, roundNo,
                        outcomes.get(index), endedAt)).isTrue();
            }
        });

        for (int index = 0; index < outcomes.size(); index++) {
            assertThat(store.findWorkflowRound(TENANT_1, 7201L + index, index + 1).orElseThrow().status())
                    .isEqualTo(outcomes.get(index));
        }
    }

    @Test
    void receiptsAreTenantScopedIdempotentAndRollbackWithTheirTransaction() {
        WorkflowReceiptStart processed = receipt(7501, TENANT_1, "event-processed", 7001L, 1, 7003L, "APPROVED");
        inTransaction(() -> {
            store.insertApplication(application(7001, TENANT_1, ActionType.CREATE, null));
            assertThat(store.beginReceipt(processed)).isTrue();
            assertThat(store.completeReceipt(TENANT_1, "event-processed", SUBSCRIBER,
                    WorkflowReceiptStatus.PROCESSED, "已发布")).isTrue();
        });

        WorkflowReceipt receipt = store.findReceipt(TENANT_1, "event-processed", SUBSCRIBER).orElseThrow();
        assertThat(receipt)
                .extracting(WorkflowReceipt::processingStatus, WorkflowReceipt::applicationId,
                        WorkflowReceipt::roundNo, WorkflowReceipt::workflowInstanceId, WorkflowReceipt::detail)
                .containsExactly(WorkflowReceiptStatus.PROCESSED, 7001L, 1, 7003L, "已发布");
        assertThat(inTransaction(() -> store.beginReceipt(processed))).isFalse();

        inTransaction(() -> {
            store.insertApplication(application(7002, TENANT_2, ActionType.CREATE, null));
            WorkflowReceiptStart ignored = receipt(7502, TENANT_2, "event-processed", 7002L, 1, 7003L, "RETURNED");
            assertThat(store.beginReceipt(ignored)).isTrue();
            assertThat(store.completeReceipt(TENANT_2, "event-processed", SUBSCRIBER,
                    WorkflowReceiptStatus.IGNORED, "租户二事件")).isTrue();
            WorkflowReceiptStart failed = receipt(7503, TENANT_2, "event-failed", 7002L, 1, 7003L, "APPROVED");
            assertThat(store.beginReceipt(failed)).isTrue();
            assertThat(store.completeReceipt(TENANT_2, "event-failed", SUBSCRIBER,
                    WorkflowReceiptStatus.FAILED, "等待平台重试")).isTrue();
        });
        assertThat(store.findReceipt(TENANT_2, "event-processed", SUBSCRIBER)).isPresent();
        assertThat(store.findReceipt(TENANT_2, "event-failed", SUBSCRIBER).orElseThrow())
                .extracting(WorkflowReceipt::processingStatus, WorkflowReceipt::detail)
                .containsExactly(WorkflowReceiptStatus.FAILED, "等待平台重试");

        transactions.executeWithoutResult(status -> {
            WorkflowReceiptStart rollback = receipt(7504, TENANT_1, "event-rollback", 7001L, 1, 7003L, "REJECTED");
            assertThat(store.beginReceipt(rollback)).isTrue();
            assertThat(store.completeReceipt(TENANT_1, "event-rollback", SUBSCRIBER,
                    WorkflowReceiptStatus.PROCESSED, "将回滚")).isTrue();
            status.setRollbackOnly();
        });
        assertThat(store.findReceipt(TENANT_1, "event-rollback", SUBSCRIBER)).isEmpty();
    }

    @Test
    void writeLockAndCasEntrypointsRequireActualTransaction() {
        assertTransactionRequired(() -> store.insertApplication(application(6001, TENANT_1, ActionType.CREATE, null)));
        assertTransactionRequired(() -> store.lockApplication(TENANT_1, 6001));
        assertTransactionRequired(() -> store.compareAndSetApplicationStatus(
                TENANT_1, 6001, ApplicationStatus.DRAFT, 0, ApplicationStatus.IN_REVIEW, 1));
        assertTransactionRequired(() -> store.compareAndSetApplicationReason(
                TENANT_1, 6001, ApplicationStatus.DRAFT, 0, "无事务原因", 1));
        assertTransactionRequired(() -> store.compareAndSetApplicationWorkflowContext(
                TENANT_1, 6001, 0, 0, 1, 900000000000030L, 900000000000031L, 6003,
                "a".repeat(64), 1));
        assertTransactionRequired(() -> store.compareAndSetCancellationRequested(TENANT_1, 6001, 0, 6003, 1));
        assertTransactionRequired(() -> store.replacePhysicalDrafts(TENANT_1, 6001, List.of()));
        assertTransactionRequired(() -> store.insertHistory(history(6002, 6001, "无事务历史", LocalDateTime.now())));
        assertTransactionRequired(() -> store.insertPendingWorkflowRound(pendingRound(6005, TENANT_1, 6001, 1)));
        assertTransactionRequired(() -> store.lockWorkflowRound(TENANT_1, 6001, 1));
        assertTransactionRequired(() -> store.lockWorkflowRoundByInstance(TENANT_1, 6003));
        assertTransactionRequired(() -> store.bindWorkflowRoundStarted(TENANT_1, 6001, 1,
                900000000000030L, 900000000000031L, 6003, "a".repeat(64), LocalDateTime.now()));
        assertTransactionRequired(() -> store.completeStartedWorkflowRound(TENANT_1, 6001, 1,
                WorkflowRoundStatus.APPROVED, LocalDateTime.now()));
        assertTransactionRequired(() -> store.beginReceipt(receipt(6006, TENANT_1, "event-no-tx", 6001L, 1, 6003L,
                "APPROVED")));
        assertTransactionRequired(() -> store.completeReceipt(TENANT_1, "event-no-tx", SUBSCRIBER,
                WorkflowReceiptStatus.PROCESSED, "无事务"));
        assertTransactionRequired(() -> store.insertTargetLock(new TargetLock(TENANT_1, TargetKind.PHYSICAL, 6003, 6001, null)));
        assertTransactionRequired(() -> store.insertValueReservation(new ValueReservation(TENANT_1, "PHYSICAL_CODE", "PHY_TX", 6001, 0, null)));
        assertTransactionRequired(() -> store.insertPhysicalReplacement(new PhysicalReplacement(6004, TENANT_1, 1, 2, 6001, null)));
        assertTransactionRequired(() -> store.lockPhysical(TENANT_1, 1));
        assertTransactionRequired(() -> store.insertPhysicalPublished(2, TENANT_1,
                physicalDraft(6001, 1, TENANT_1, "PHY_NO_TX", "无事务物理"), PublishedStatus.ACTIVE, 0, 1));
    }

    private static ChangeApplication application(long id, long tenantId, ActionType actionType, Long targetId) {
        return new ChangeApplication(id, tenantId, TargetKind.PHYSICAL, actionType, targetId,
                61, "测试申请", ApplicationStatus.DRAFT, 0, null, null, null, null,
                false, 0, 61, 61, null, null);
    }

    private static PhysicalDraft physicalDraft(long applicationId, int lineNo, long tenantId, String code, String name) {
        return new PhysicalDraft(applicationId, lineNo, tenantId, null, code, name + "简称", name,
                "物理逻辑域", "architecture.business-component.employee-portal",
                "service-" + code.toLowerCase(), "架构组", 21,
                "架构团队", "RUNTIME", "L1", "FRAMEWORK", 22L,
                "描述", "备注", null, 0, "{\"source\":\"test\"}", null, null);
    }

    private static WorkflowRound pendingRound(long id, long tenantId, long applicationId, int roundNo) {
        return new WorkflowRound(id, tenantId, applicationId, roundNo, null, null, null, null,
                WorkflowRoundStatus.PENDING, null, null, null, null);
    }

    private static WorkflowReceiptStart receipt(long id, long tenantId, String eventId, Long applicationId,
                                                Integer roundNo, Long workflowInstanceId, String eventType) {
        return new WorkflowReceiptStart(id, tenantId, eventId, SUBSCRIBER, applicationId, roundNo, workflowInstanceId,
                eventType);
    }

    private static ChangeHistoryEvent history(long id, long applicationId, String type, LocalDateTime occurredAt) {
        return new ChangeHistoryEvent(id, TENANT_1, applicationId, type, ApplicationStatus.DRAFT,
                ApplicationStatus.IN_REVIEW, 1, type, "{\"before\":true}", "{\"after\":true}", 61, occurredAt);
    }

    private <T> T inTransaction(Supplier<T> work) {
        return transactions.execute(status -> work.get());
    }

    private void inTransaction(Runnable work) {
        transactions.executeWithoutResult(status -> work.run());
    }

    private void assertTransactionRequired(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("真实数据库事务");
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
}
