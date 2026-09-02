package com.ccb.architecture.change.persistence;

import com.ccb.architecture.change.model.SubsystemChangeModels.ActionType;
import com.ccb.architecture.change.model.SubsystemChangeModels.ApplicationStatus;
import com.ccb.architecture.change.model.SubsystemChangeModels.ChangeApplication;
import com.ccb.architecture.change.model.SubsystemChangeModels.ChangeHistoryEvent;
import com.ccb.architecture.change.model.SubsystemChangeModels.LogicalDraft;
import com.ccb.architecture.change.model.SubsystemChangeModels.PhysicalDraft;
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
                .target(MigrationVersion.fromVersion("107"))
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
        jdbc.update("DELETE FROM arch_subsystem_workflow_receipt");
        jdbc.update("DELETE FROM arch_subsystem_workflow_round");
        jdbc.update("DELETE FROM arch_subsystem_replacement");
        jdbc.update("DELETE FROM arch_subsystem_value_reservation");
        jdbc.update("DELETE FROM arch_subsystem_change_lock");
        jdbc.update("DELETE FROM arch_subsystem_change_history");
        jdbc.update("DELETE FROM arch_subsystem_number_reservation");
        jdbc.update("DELETE FROM arch_subsystem_number_recycled");
        jdbc.update("DELETE FROM arch_subsystem_physical_draft");
        jdbc.update("DELETE FROM arch_subsystem_logical_draft");
        jdbc.update("DELETE FROM arch_subsystem_change_application");
        jdbc.update("DELETE FROM arch_physical_subsystem");
        jdbc.update("DELETE FROM arch_logical_subsystem");
        store = new SubsystemChangeStore(jdbc);
    }

    @Test
    void tenantIsolationAndDraftReplacementKeepZeroOrManyRowsInStableLineOrder() {
        inTransaction(() -> {
            store.insertApplication(application(1001, 1, TargetKind.LOGICAL, ActionType.CREATE, null));
            store.insertApplication(application(2001, 2, TargetKind.LOGICAL, ActionType.CREATE, null));
            store.replaceLogicalDraft(logicalDraft(1001, 1, "逻辑一"));
            store.replacePhysicalDrafts(1, 1001, List.of());
        });

        assertThat(store.findApplication(2, 1001)).isEmpty();
        assertThat(store.listApplications(1, null, null, 20, 0)).extracting(ChangeApplication::id)
                .containsExactly(1001L);
        assertThat(store.findPhysicalDrafts(1, 1001)).isEmpty();

        inTransaction(() -> store.replacePhysicalDrafts(1, 1001, List.of(
                physicalDraft(1001, 20, 1, "物理二十"),
                physicalDraft(1001, 10, 1, "物理十"))));

        assertThat(store.findLogicalDraft(1, 1001)).isPresent();
        assertThat(store.findLogicalDraft(2, 1001)).isEmpty();
        assertThat(store.findPhysicalDrafts(1, 1001)).extracting(PhysicalDraft::lineNo)
                .containsExactly(10, 20);
        assertThat(store.findPhysicalDrafts(2, 1001)).isEmpty();
    }

    @Test
    void targetAndValueUniqueConstraintsAndApplicationCasAreTenantScoped() {
        inTransaction(() -> {
            store.insertApplication(application(3001, 1, TargetKind.PHYSICAL, ActionType.UPDATE, 900L));
            store.insertApplication(application(3002, 1, TargetKind.PHYSICAL, ActionType.UPDATE, 901L));
            store.insertTargetLock(new TargetLock(1, TargetKind.PHYSICAL, 900, 3001, null));
            store.insertValueReservation(new ValueReservation(1, "PHYSICAL_ENGLISH_NAME", "service-a", 3001, 1, null));
        });

        assertThatThrownBy(() -> inTransaction(() ->
                store.insertTargetLock(new TargetLock(1, TargetKind.PHYSICAL, 900, 3002, null))))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> inTransaction(() ->
                store.insertValueReservation(new ValueReservation(1, "PHYSICAL_ENGLISH_NAME", "service-a", 3002, 1, null))))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(inTransaction(() -> store.compareAndSetApplicationStatus(
                1, 3001, ApplicationStatus.DRAFT, 0, ApplicationStatus.IN_REVIEW, 71))).isTrue();
        assertThat(inTransaction(() -> store.compareAndSetApplicationStatus(
                1, 3001, ApplicationStatus.DRAFT, 0, ApplicationStatus.RETURNED, 71))).isFalse();
        ChangeApplication changed = store.findApplication(1, 3001).orElseThrow();
        assertThat(changed.status()).isEqualTo(ApplicationStatus.IN_REVIEW);
        assertThat(changed.rowVersion()).isEqualTo(1);
        assertThat(store.findTargetLock(2, TargetKind.PHYSICAL, 900)).isEmpty();
        assertThat(store.findValueReservation(2, "PHYSICAL_ENGLISH_NAME", "service-a")).isEmpty();
    }

    @Test
    void editableApplicationReasonUsesTenantStatusAndRowVersionCas() {
        inTransaction(() -> store.insertApplication(
                application(3501, 1, TargetKind.LOGICAL, ActionType.CREATE, null)));

        assertThat(inTransaction(() -> store.compareAndSetApplicationReason(
                1, 3501, ApplicationStatus.DRAFT, 0, "更新后的申请原因", 72))).isTrue();
        assertThat(inTransaction(() -> store.compareAndSetApplicationReason(
                1, 3501, ApplicationStatus.DRAFT, 0, "过期覆盖", 73))).isFalse();
        assertThat(inTransaction(() -> store.compareAndSetApplicationReason(
                2, 3501, ApplicationStatus.DRAFT, 1, "跨租户覆盖", 73))).isFalse();

        ChangeApplication changed = store.findApplication(1, 3501).orElseThrow();
        assertThat(changed.reason()).isEqualTo("更新后的申请原因");
        assertThat(changed.rowVersion()).isEqualTo(1);
        assertThat(changed.updatedBy()).isEqualTo(72);
    }

    @Test
    void publishedWritesUseTenantScopedRowVersionsAndPersistReplacement() {
        inTransaction(() -> {
            store.insertApplication(application(4001, 1, TargetKind.PHYSICAL, ActionType.REPLACE, 4101L));
            LogicalDraft logicalDraft = logicalDraft(4001, 1, "发布逻辑");
            store.insertLogicalPublished(4100, 1, "A0001", 1, logicalDraft, PublishedStatus.ACTIVE, 0, 81);
            store.insertPhysicalPublished(4101, 1, "W00011", "1", 4100,
                    physicalDraft(4001, 1, 1, "旧物理"), PublishedStatus.ACTIVE, 0, 81);
            store.insertPhysicalPublished(4102, 1, "W00012", "2", 4100,
                    physicalDraft(4001, 2, 1, "新物理"), PublishedStatus.ACTIVE, 0, 81);

            assertThat(store.lockLogical(1, 4100)).isPresent();
            assertThat(store.lockPhysical(1, 4101)).isPresent();
            assertThat(store.updateLogicalPublishedFields(1, 4100,
                    logicalDraft(4001, 1, "更新逻辑"), 0, 82)).isTrue();
            assertThat(store.updateLogicalPublishedStatus(1, 4100, PublishedStatus.OFFLINE, 1, 82)).isTrue();
            assertThat(store.updatePhysicalPublishedFields(1, 4101,
                    physicalDraft(4001, 1, 1, "更新物理"), 0, 82)).isTrue();
            assertThat(store.updatePhysicalPublishedStatus(1, 4101, PublishedStatus.OFFLINE, 1, 82)).isTrue();
            store.insertPhysicalReplacement(new PhysicalReplacement(4200, 1, 4101, 4102, 4001, null));
        });

        assertThat(store.findLogical(1, 4100).orElseThrow())
                .extracting(value -> value.status(), value -> value.rowVersion())
                .containsExactly(PublishedStatus.OFFLINE, 2L);
        assertThat(store.findPhysical(1, 4101).orElseThrow())
                .extracting(value -> value.status(), value -> value.rowVersion())
                .containsExactly(PublishedStatus.OFFLINE, 2L);
        assertThat(store.findPhysical(2, 4101)).isEmpty();
        assertThat(store.findPhysicalReplacementByApplication(1, 4001)).isPresent();
        assertThat(integer("SELECT COUNT(*) FROM arch_subsystem_replacement WHERE tenant_id = 2")).isZero();
    }

    @Test
    void rollbackLeavesNoPartialRowsAndHistoryOrderIsStableAtSameTimestamp() {
        LocalDateTime sameTime = LocalDateTime.of(2026, 8, 23, 1, 0);
        inTransaction(() -> {
            store.insertApplication(application(5001, 1, TargetKind.LOGICAL, ActionType.CREATE, null));
            store.insertHistory(history(5002, 5001, "后写事件", sameTime));
            store.insertHistory(history(5001, 5001, "先排序事件", sameTime));
        });
        assertThat(store.listHistory(1, 5001)).extracting(ChangeHistoryEvent::id)
                .containsExactly(5001L, 5002L);

        transactions.executeWithoutResult(status -> {
            store.insertApplication(application(5101, 1, TargetKind.LOGICAL, ActionType.CREATE, null));
            store.replaceLogicalDraft(logicalDraft(5101, 1, "待回滚逻辑"));
            store.replacePhysicalDrafts(1, 5101, List.of(physicalDraft(5101, 1, 1, "待回滚物理")));
            store.insertTargetLock(new TargetLock(1, TargetKind.LOGICAL, 777, 5101, null));
            store.insertHistory(history(5103, 5101, "待回滚事件", sameTime));
            status.setRollbackOnly();
        });

        assertThat(store.findApplication(1, 5101)).isEmpty();
        assertThat(store.findLogicalDraft(1, 5101)).isEmpty();
        assertThat(store.findPhysicalDrafts(1, 5101)).isEmpty();
        assertThat(store.findTargetLock(1, TargetKind.LOGICAL, 777)).isEmpty();
        assertThat(store.listHistory(1, 5101)).isEmpty();
    }

    @Test
    void workflowRoundBindingContextCancellationAndLatestChecksAreTenantScoped() {
        LocalDateTime startedAt = LocalDateTime.of(2026, 8, 23, 2, 0, 0);
        LocalDateTime endedAt = LocalDateTime.of(2026, 8, 23, 2, 5, 0);
        String digest = "a".repeat(64);

        inTransaction(() -> {
            store.insertApplication(application(7001, 1, TargetKind.LOGICAL, ActionType.CREATE, null));
            assertThat(store.compareAndSetApplicationStatus(1, 7001, ApplicationStatus.DRAFT, 0,
                    ApplicationStatus.IN_REVIEW, 71)).isTrue();
            store.insertPendingWorkflowRound(pendingRound(7101, 1, 7001, 1));
            assertThat(store.lockWorkflowRound(1, 7001, 1)).isPresent();
            assertThat(store.bindWorkflowRoundStarted(1, 7001, 1, 900000000000030L,
                    900000000000031L, 7003, digest, startedAt)).isTrue();
            assertThat(store.compareAndSetApplicationWorkflowContext(1, 7001, 0, 1, 1,
                    900000000000030L, 900000000000031L, 7003, digest, 72)).isTrue();
        });

        ChangeApplication application = store.findApplication(1, 7001).orElseThrow();
        assertThat(application).extracting(ChangeApplication::status, ChangeApplication::currentBusinessRound,
                        ChangeApplication::currentWorkflowDefinitionId, ChangeApplication::currentWorkflowVersionId,
                        ChangeApplication::currentWorkflowInstanceId, ChangeApplication::currentPayloadDigest,
                        ChangeApplication::rowVersion)
                .containsExactly(ApplicationStatus.IN_REVIEW, 1, 900000000000030L, 900000000000031L,
                        7003L, digest, 2L);
        assertThat(store.findWorkflowRound(1, 7001, 1)).isPresent()
                .get().extracting(WorkflowRound::status, WorkflowRound::startedAt, WorkflowRound::payloadDigest)
                .containsExactly(WorkflowRoundStatus.STARTED, startedAt, digest);
        assertThat(store.findWorkflowRound(2, 7001, 1)).isEmpty();
        assertThat(inTransaction(() -> store.lockWorkflowRoundByInstance(1, 7003))).isPresent();
        assertThat(inTransaction(() -> store.lockWorkflowRoundByInstance(2, 7003))).isEmpty();
        assertThat(store.isLatestWorkflowRound(1, 7001, 1)).isTrue();

        inTransaction(() -> store.insertPendingWorkflowRound(pendingRound(7102, 1, 7001, 2)));
        assertThat(store.isLatestWorkflowRound(1, 7001, 1)).isFalse();
        assertThat(store.isLatestWorkflowRound(1, 7001, 2)).isTrue();
        assertThat(store.isLatestWorkflowRound(1, 7001, 3)).isFalse();

        assertThat(inTransaction(() -> store.compareAndSetCancellationRequested(1, 7001, 2, 7003, 75))).isTrue();
        assertThat(inTransaction(() -> store.compareAndSetCancellationRequested(1, 7001, 2, 7003, 76))).isFalse();
        assertThat(store.findApplication(1, 7001).orElseThrow())
                .extracting(ChangeApplication::cancellationRequested, ChangeApplication::rowVersion,
                        ChangeApplication::updatedBy)
                .containsExactly(true, 3L, 75L);

        assertThat(inTransaction(() -> store.completeStartedWorkflowRound(1, 7001, 1,
                WorkflowRoundStatus.RETURNED, endedAt))).isTrue();
        assertThat(inTransaction(() -> store.completeStartedWorkflowRound(1, 7001, 1,
                WorkflowRoundStatus.RETURNED, endedAt))).isFalse();
        assertThat(store.findWorkflowRound(1, 7001, 1).orElseThrow())
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
                store.insertApplication(application(applicationId, 1, TargetKind.LOGICAL, ActionType.CREATE, null));
                store.insertPendingWorkflowRound(pendingRound(7301L + index, 1, applicationId, roundNo));
                assertThat(store.bindWorkflowRoundStarted(1, applicationId, roundNo,
                        900000000000030L, 900000000000031L, 7401L + index,
                        ("b" + index).repeat(32), startedAt)).isTrue();
                assertThat(store.completeStartedWorkflowRound(1, applicationId, roundNo,
                        outcomes.get(index), endedAt)).isTrue();
            }
        });

        for (int index = 0; index < outcomes.size(); index++) {
            assertThat(store.findWorkflowRound(1, 7201L + index, index + 1).orElseThrow().status())
                    .isEqualTo(outcomes.get(index));
        }
    }

    @Test
    void receiptsAreTenantScopedIdempotentAndRollbackWithTheirTransaction() {
        WorkflowReceiptStart processed = receipt(7501, 1, "event-processed", 7001L, 1, 7003L, "APPROVED");
        inTransaction(() -> {
            store.insertApplication(application(7001, 1, TargetKind.LOGICAL, ActionType.CREATE, null));
            assertThat(store.beginReceipt(processed)).isTrue();
            assertThat(store.completeReceipt(1, "event-processed", "architecture.subsystem.change.lifecycle.v1",
                    WorkflowReceiptStatus.PROCESSED, "已发布")).isTrue();
        });

        WorkflowReceipt receipt = store.findReceipt(1, "event-processed",
                "architecture.subsystem.change.lifecycle.v1").orElseThrow();
        assertThat(receipt).extracting(WorkflowReceipt::processingStatus, WorkflowReceipt::applicationId,
                        WorkflowReceipt::roundNo, WorkflowReceipt::workflowInstanceId, WorkflowReceipt::detail)
                .containsExactly(WorkflowReceiptStatus.PROCESSED, 7001L, 1, 7003L, "已发布");
        assertThat(inTransaction(() -> store.beginReceipt(processed))).isFalse();

        inTransaction(() -> {
            store.insertApplication(application(7002, 2, TargetKind.LOGICAL, ActionType.CREATE, null));
            WorkflowReceiptStart ignored = receipt(7502, 2, "event-processed", 7002L, 1, 7003L, "RETURNED");
            assertThat(store.beginReceipt(ignored)).isTrue();
            assertThat(store.completeReceipt(2, "event-processed", "architecture.subsystem.change.lifecycle.v1",
                    WorkflowReceiptStatus.IGNORED, "租户二事件")).isTrue();
            WorkflowReceiptStart failed = receipt(7503, 2, "event-failed", 7002L, 1, 7003L, "APPROVED");
            assertThat(store.beginReceipt(failed)).isTrue();
            assertThat(store.completeReceipt(2, "event-failed", "architecture.subsystem.change.lifecycle.v1",
                    WorkflowReceiptStatus.FAILED, "等待平台重试")).isTrue();
        });
        assertThat(store.findReceipt(2, "event-processed", "architecture.subsystem.change.lifecycle.v1"))
                .isPresent();
        assertThat(store.findReceipt(2, "event-failed", "architecture.subsystem.change.lifecycle.v1").orElseThrow())
                .extracting(WorkflowReceipt::processingStatus, WorkflowReceipt::detail)
                .containsExactly(WorkflowReceiptStatus.FAILED, "等待平台重试");

        transactions.executeWithoutResult(status -> {
            WorkflowReceiptStart rollback = receipt(7504, 1, "event-rollback", 7001L, 1, 7003L, "REJECTED");
            assertThat(store.beginReceipt(rollback)).isTrue();
            assertThat(store.completeReceipt(1, "event-rollback", "architecture.subsystem.change.lifecycle.v1",
                    WorkflowReceiptStatus.PROCESSED, "将回滚")).isTrue();
            status.setRollbackOnly();
        });
        assertThat(store.findReceipt(1, "event-rollback", "architecture.subsystem.change.lifecycle.v1")).isEmpty();
    }

    @Test
    void writeLockAndCasEntrypointsRequireActualTransaction() {
        assertTransactionRequired(() -> store.insertApplication(application(6001, 1, TargetKind.LOGICAL, ActionType.CREATE, null)));
        assertTransactionRequired(() -> store.lockApplication(1, 6001));
        assertTransactionRequired(() -> store.compareAndSetApplicationStatus(
                1, 6001, ApplicationStatus.DRAFT, 0, ApplicationStatus.IN_REVIEW, 1));
        assertTransactionRequired(() -> store.compareAndSetApplicationReason(
                1, 6001, ApplicationStatus.DRAFT, 0, "无事务原因", 1));
        assertTransactionRequired(() -> store.compareAndSetApplicationWorkflowContext(
                1, 6001, 0, 0, 1, 900000000000030L, 900000000000031L, 6003,
                "a".repeat(64), 1));
        assertTransactionRequired(() -> store.compareAndSetCancellationRequested(1, 6001, 0, 6003, 1));
        assertTransactionRequired(() -> store.replaceLogicalDraft(logicalDraft(6001, 1, "无事务逻辑")));
        assertTransactionRequired(() -> store.replacePhysicalDrafts(1, 6001, List.of()));
        assertTransactionRequired(() -> store.insertHistory(history(6002, 6001, "无事务历史", LocalDateTime.now())));
        assertTransactionRequired(() -> store.insertPendingWorkflowRound(pendingRound(6005, 1, 6001, 1)));
        assertTransactionRequired(() -> store.lockWorkflowRound(1, 6001, 1));
        assertTransactionRequired(() -> store.lockWorkflowRoundByInstance(1, 6003));
        assertTransactionRequired(() -> store.bindWorkflowRoundStarted(1, 6001, 1,
                900000000000030L, 900000000000031L, 6003, "a".repeat(64), LocalDateTime.now()));
        assertTransactionRequired(() -> store.completeStartedWorkflowRound(1, 6001, 1,
                WorkflowRoundStatus.APPROVED, LocalDateTime.now()));
        assertTransactionRequired(() -> store.beginReceipt(receipt(6006, 1, "event-no-tx", 6001L, 1, 6003L,
                "APPROVED")));
        assertTransactionRequired(() -> store.completeReceipt(1, "event-no-tx",
                "architecture.subsystem.change.lifecycle.v1", WorkflowReceiptStatus.PROCESSED, "无事务"));
        assertTransactionRequired(() -> store.insertTargetLock(new TargetLock(1, TargetKind.LOGICAL, 6003, 6001, null)));
        assertTransactionRequired(() -> store.insertValueReservation(new ValueReservation(1, "LOGICAL_NAME", "无事务", 6001, 0, null)));
        assertTransactionRequired(() -> store.insertPhysicalReplacement(new PhysicalReplacement(6004, 1, 1, 2, 6001, null)));
        assertTransactionRequired(() -> store.lockLogical(1, 1));
        assertTransactionRequired(() -> store.lockPhysical(1, 1));
        assertTransactionRequired(() -> store.insertLogicalPublished(1, 1, "A0001", 1,
                logicalDraft(6001, 1, "无事务发布"), PublishedStatus.ACTIVE, 0, 1));
        assertTransactionRequired(() -> store.insertPhysicalPublished(2, 1, "W00011", "1", 1,
                physicalDraft(6001, 1, 1, "无事务物理"), PublishedStatus.ACTIVE, 0, 1));
    }

    private static ChangeApplication application(long id, long tenantId, TargetKind targetKind,
                                                 ActionType actionType, Long targetId) {
        return new ChangeApplication(id, tenantId, targetKind, actionType, targetId, 61, "测试申请",
                ApplicationStatus.DRAFT, 0, null, null, null, null, false, 0, 61, 61, null, null);
    }

    private static LogicalDraft logicalDraft(long applicationId, long tenantId, String name) {
        return new LogicalDraft(applicationId, tenantId, null, name + "简称", name, 11,
                "DEPLOY", "TYPE", "OWN", 12, "描述", "备注", 7, null, null, 0,
                "{\"source\":\"test\"}", null, null);
    }

    private static PhysicalDraft physicalDraft(long applicationId, int lineNo, long tenantId, String name) {
        return new PhysicalDraft(applicationId, lineNo, tenantId, null, null, name + "简称", name,
                "service-" + applicationId + '-' + lineNo, "架构组", 21, "架构团队", "RUNTIME", "L1",
                "FRAMEWORK", 22L, "描述", "备注", null, null, 0, "{\"source\":\"test\"}", null, null);
    }

    private static WorkflowRound pendingRound(long id, long tenantId, long applicationId, int roundNo) {
        return new WorkflowRound(id, tenantId, applicationId, roundNo, null, null, null, null,
                WorkflowRoundStatus.PENDING, null, null, null, null);
    }

    private static WorkflowReceiptStart receipt(long id, long tenantId, String eventId, Long applicationId,
                                                Integer roundNo, Long workflowInstanceId, String eventType) {
        return new WorkflowReceiptStart(id, tenantId, eventId,
                "architecture.subsystem.change.lifecycle.v1", applicationId, roundNo, workflowInstanceId, eventType);
    }

    private static ChangeHistoryEvent history(long id, long applicationId, String type, LocalDateTime occurredAt) {
        return new ChangeHistoryEvent(id, 1, applicationId, type, ApplicationStatus.DRAFT,
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

    private long integer(String sql) {
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
