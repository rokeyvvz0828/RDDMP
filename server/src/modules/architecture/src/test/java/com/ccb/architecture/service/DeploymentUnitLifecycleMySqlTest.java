package com.ccb.architecture.service;

import com.ccb.architecture.model.DeploymentUnitModels.DeploymentUnitCommand;
import com.ccb.architecture.persistence.DeploymentUnitStore;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.SystemOperationAudit;
import com.ccb.system.capability.SystemReferenceQuery;
import org.flywaydb.core.Flyway;
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
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * 部署单元生命周期 MySQL 集成测试：真实 MySQL 8.4 + 完整 Flyway 迁移。
 * 覆盖编号确定性/并发、版本不可改写、名称永久占用、状态机与作废守卫。
 */
@Testcontainers
class DeploymentUnitLifecycleMySqlTest {
    private static final String DATABASE = "deployment_unit_lifecycle";
    private static final long TENANT_ID = 1L;
    private static final long PHYSICAL_ID = 501L;

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName(DATABASE)
            .withUsername("test")
            .withPassword("test");

    private static JdbcTemplate jdbc;
    private static DeploymentUnitStore store;
    private static TransactionTemplate transactions;
    private static DeploymentUnitService service;
    private static AtomicInteger identifiers = new AtomicInteger(90_000);

    private final AuthUser actor = new AuthUser(88L, TENANT_ID, "tech", "-", "技术架构师", 1L, true);

    @BeforeAll
    static void migrate() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("ALTER DATABASE `" + DATABASE + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("filesystem:" + migrationDirectory())
                .placeholders(java.util.Map.of("bootstrap_admin_password_hash", "test-hash"))
                .target(MigrationVersion.fromVersion("124"))
                .cleanDisabled(false)
                .load();
        flyway.clean();
        flyway.migrate();

        store = new DeploymentUnitStore(jdbc);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        LongSupplier idSupplier = () -> identifiers.incrementAndGet();
        service = new DeploymentUnitService(store, new DeploymentUnitReferenceGuard(List.of()),
                mock(SystemReferenceQuery.class), mock(SystemOperationAudit.class),
                transactions, idSupplier);
    }

    @BeforeEach
    void seedArchitecture() {
        jdbc.update("DELETE FROM arch_deployment_unit_relation_history");
        jdbc.update("DELETE FROM arch_deployment_unit_relation");
        jdbc.update("DELETE FROM arch_deployment_unit_version");
        jdbc.update("DELETE FROM arch_deployment_unit");
        jdbc.update("DELETE FROM arch_deployment_unit_import_item");
        jdbc.update("DELETE FROM arch_deployment_unit_import_batch");
        jdbc.update("DELETE FROM arch_deployment_unit_number_seq");
        jdbc.update("DELETE FROM arch_physical_subsystem WHERE tenant_id = ?", TENANT_ID);
        jdbc.update("INSERT INTO arch_physical_subsystem "
                        + "(id, tenant_id, code, short_name, name, logical_subsystem_name, responsible_team_org_id,"
                        + " responsible_team_name_snapshot, status, row_version, created_by, updated_by) "
                        + "VALUES (?, ?, 'W0001A', '渠道接入', '渠道接入系统', '渠道域逻辑子系统', 1, '渠道团队', 'ACTIVE', 0, 1, 1)",
                PHYSICAL_ID, TENANT_ID);
    }

    @AfterAll
    static void clearStatics() {
        jdbc = null;
        store = null;
        transactions = null;
        service = null;
    }

    @Test
    void createPublishesVersionOneWithDeterministicNumber() {
        var view = service.create(actor, command("ECIP_AP", "APPLICATION"), "trace");

        assertThat(view.code()).isEqualTo("DW0001A001");
        assertThat(view.currentVersion()).isEqualTo(1);
        assertThat(view.status()).isEqualTo("ACTIVE");
        Integer versions = jdbc.queryForObject(
                "SELECT COUNT(*) FROM arch_deployment_unit_version WHERE tenant_id = ? AND unit_id = ?",
                Integer.class, TENANT_ID, view.id());
        assertThat(versions).isEqualTo(1);
    }

    @Test
    void concurrentCreatesAllocateDistinctNumbers() throws Exception {
        int threads = 6;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<String> codes = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        List<String> failureMessages = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        for (int i = 0; i < threads; i++) {
            final int index = i;
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    var view = service.create(actor,
                            command("APP" + index + "_AP", "APPLICATION"), "trace-" + index);
                    codes.add(view.code());
                } catch (Exception exception) {
                    failureMessages.add(exception.getClass().getSimpleName() + ": "
                            + String.valueOf(exception.getMessage()));
                }
            });
        }
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(failureMessages).as("并发失败明细").isEmpty();
        assertThat(codes).hasSize(threads);
        assertThat(codes).doesNotHaveDuplicates();
        for (String code : codes) {
            assertThat(code).matches("DW0001A\\d{3}");
        }
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM arch_deployment_unit WHERE tenant_id = ?",
                Long.class, TENANT_ID);
        assertThat(count).isEqualTo(threads);
    }

    @Test
    void updatePublishesNewVersionAndOldVersionStaysImmutable() {
        var created = service.create(actor, command("ECIP_AP", "APPLICATION"), "trace");

        var updated = service.update(actor, created.id(),
                new DeploymentUnitCommand(null, "ECIP_DB", "DATABASE", List.of(), null,
                        "迁移到数据库服务", null, created.rowVersion()), "trace");

        assertThat(updated.currentVersion()).isEqualTo(2);
        List<java.util.Map<String, Object>> versions = jdbc.queryForList(
                "SELECT version_no, name, kind, description FROM arch_deployment_unit_version "
                        + "WHERE tenant_id = ? AND unit_id = ? ORDER BY version_no",
                TENANT_ID, created.id());
        assertThat(versions).hasSize(2);
        assertThat(versions.get(0).get("name")).isEqualTo("ECIP_AP");
        assertThat(versions.get(0).get("kind")).isEqualTo("APPLICATION");
        assertThat(versions.get(1).get("name")).isEqualTo("ECIP_DB");
        assertThat(versions.get(1).get("kind")).isEqualTo("DATABASE");
    }

    @Test
    void relationIsBidirectionalIdempotentAndCanBeUnlinkedFromEitherSide() {
        var application = service.create(actor, command("ECIP_AP", "APPLICATION"), "trace-application");
        var database = service.create(actor, command("ECIP_DB", "DATABASE"), "trace-database");

        var linked = service.update(actor, application.id(),
                new DeploymentUnitCommand(null, application.name(), application.kind(), List.of(database.id()),
                        null, null, null, application.rowVersion()), "trace-link");

        assertThat(service.detail(actor, application.id()).relatedDeploymentUnits())
                .extracting(DeploymentUnitService.RelatedDeploymentUnitView::id)
                .containsExactly(database.id());
        assertThat(service.detail(actor, database.id()).relatedDeploymentUnits())
                .extracting(DeploymentUnitService.RelatedDeploymentUnitView::id)
                .containsExactly(application.id());
        assertThat(relationCount()).isEqualTo(1);
        assertThat(historyCount("LINK")).isEqualTo(1);

        var repeated = service.update(actor, application.id(),
                new DeploymentUnitCommand(null, application.name(), application.kind(), List.of(database.id()),
                        null, null, null, linked.rowVersion()), "trace-repeat");

        assertThat(relationCount()).isEqualTo(1);
        assertThat(historyCount("LINK")).isEqualTo(1);

        service.update(actor, database.id(),
                new DeploymentUnitCommand(null, database.name(), database.kind(), List.of(),
                        null, null, null, database.rowVersion()), "trace-unlink");

        assertThat(service.detail(actor, application.id()).relatedDeploymentUnits()).isEmpty();
        assertThat(service.detail(actor, database.id()).relatedDeploymentUnits()).isEmpty();
        assertThat(relationCount()).isZero();
        assertThat(historyCount("UNLINK")).isEqualTo(1);
        assertThat(repeated.currentVersion()).isEqualTo(3);
    }

    @Test
    void concurrentRelationWritesKeepOneCanonicalRowAndOneLinkHistory() throws Exception {
        var application = service.create(actor, command("ECIP_AP", "APPLICATION"), "trace-application");
        var database = service.create(actor, command("ECIP_DB", "DATABASE"), "trace-database");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<RuntimeException> failures = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

        submitRelationWrite(executor, ready, start, failures, application.id(), database.id());
        submitRelationWrite(executor, ready, start, failures, database.id(), application.id());
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(failures).hasSizeLessThanOrEqualTo(1);
        assertThat(relationCount()).isEqualTo(1);
        assertThat(historyCount("LINK")).isEqualTo(1);
        assertThat(service.detail(actor, application.id()).relatedDeploymentUnits()).hasSize(1);
        assertThat(service.detail(actor, database.id()).relatedDeploymentUnits()).hasSize(1);
    }

    @Test
    void relationAndHistoryRollBackTogetherWhenTransactionFails() {
        var application = service.create(actor, command("ECIP_AP", "APPLICATION"), "trace-application");
        var database = service.create(actor, command("ECIP_DB", "DATABASE"), "trace-database");

        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            store.replaceRelations(TENANT_ID, application.id(), Set.of(database.id()), actor.id(), 2);
            throw new IllegalStateException("force rollback");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(relationCount()).isZero();
        assertThat(historyCount("LINK")).isZero();
    }

    @Test
    void relationConstraintsRejectSelfAndCrossTenantPairs() {
        var application = service.create(actor, command("ECIP_AP", "APPLICATION"), "trace-application");
        var database = service.create(actor, command("ECIP_DB", "DATABASE"), "trace-database");

        assertThatThrownBy(() -> jdbc.update("INSERT INTO arch_deployment_unit_relation "
                        + "(tenant_id, unit_low_id, unit_high_id, created_by) VALUES (?, ?, ?, ?)",
                TENANT_ID, application.id(), application.id(), actor.id())).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO arch_deployment_unit_relation "
                        + "(tenant_id, unit_low_id, unit_high_id, created_by) VALUES (?, ?, ?, ?)",
                2L, application.id(), database.id(), actor.id())).isInstanceOf(RuntimeException.class);
        assertThat(relationCount()).isZero();
    }

    @Test
    void updateRejectsStaleRowVersionAndKeepsVersionCount() {
        var created = service.create(actor, command("ECIP_AP", "APPLICATION"), "trace");
        var updated = service.update(actor, created.id(),
                new DeploymentUnitCommand(null, "ECIP2_AP", "APPLICATION", List.of(), null, null, null,
                        created.rowVersion()), "trace");

        assertThatThrownBy(() -> service.update(actor, created.id(),
                new DeploymentUnitCommand(null, "STALE_AP", "APPLICATION", List.of(), null, null, null,
                        created.rowVersion()), "trace"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).code()).isEqualTo(ErrorCode.CONFLICT));
        Integer versions = jdbc.queryForObject(
                "SELECT COUNT(*) FROM arch_deployment_unit_version WHERE tenant_id = ? AND unit_id = ?",
                Integer.class, TENANT_ID, created.id());
        assertThat(versions).isEqualTo(2);
        assertThat(updated.rowVersion()).isEqualTo(1);
    }

    @Test
    void nameStaysOccupiedAfterDeactivateAndVoid() {
        var created = service.create(actor, command("ECIP_AP", "APPLICATION"), "trace");

        service.deactivate(actor, created.id(), "trace");
        assertThatThrownBy(() -> service.create(actor, command("ECIP_AP", "APPLICATION"), "trace"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).code()).isEqualTo(ErrorCode.CONFLICT));

        service.reactivate(actor, created.id(), "trace");
        service.voidUnit(actor, created.id(), "trace");
        assertThatThrownBy(() -> service.create(actor, command("ECIP_AP", "APPLICATION"), "trace"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).code()).isEqualTo(ErrorCode.CONFLICT));
    }

    @Test
    void lifecycleTransitionsAndVoidedUnitIsTerminal() {
        var created = service.create(actor, command("ECIP_AP", "APPLICATION"), "trace");

        var inactive = service.deactivate(actor, created.id(), "trace");
        assertThat(inactive.status()).isEqualTo("INACTIVE");
        assertThatThrownBy(() -> service.update(actor, created.id(),
                new DeploymentUnitCommand(null, "INACTIVE_AP", "APPLICATION", List.of(), null, null, null,
                        inactive.rowVersion()), "trace"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).code()).isEqualTo(ErrorCode.CONFLICT));

        var active = service.reactivate(actor, created.id(), "trace");
        assertThat(active.status()).isEqualTo("ACTIVE");

        var voided = service.voidUnit(actor, created.id(), "trace");
        assertThat(voided.status()).isEqualTo("VOIDED");
        assertThatThrownBy(() -> service.deactivate(actor, created.id(), "trace"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).code()).isEqualTo(ErrorCode.CONFLICT));
        assertThatThrownBy(() -> service.reactivate(actor, created.id(), "trace"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).code()).isEqualTo(ErrorCode.CONFLICT));
    }

    @Test
    void createRejectsNonActivePhysicalSubsystem() {
        jdbc.update("UPDATE arch_physical_subsystem SET status = 'OFFLINE' WHERE tenant_id = ? AND id = ?",
                TENANT_ID, PHYSICAL_ID);

        assertThatThrownBy(() -> service.create(actor, command("ECIP_AP", "APPLICATION"), "trace"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).code()).isEqualTo(ErrorCode.BAD_REQUEST));
    }

    @Test
    void numberCapacityExhaustionFailsCleanly() {
        jdbc.update("INSERT INTO arch_deployment_unit_number_seq (tenant_id, physical_subsystem_id, next_ordinal) "
                + "VALUES (?, ?, 1000)", TENANT_ID, PHYSICAL_ID);

        assertThatThrownBy(() -> service.create(actor, command("ECIP_AP", "APPLICATION"), "trace"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).code()).isEqualTo(ErrorCode.CONFLICT));
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM arch_deployment_unit WHERE tenant_id = ?",
                Long.class, TENANT_ID);
        assertThat(count).isZero();
    }

    private DeploymentUnitCommand command(String name, String kind) {
        return new DeploymentUnitCommand(PHYSICAL_ID, name, kind, List.of(), null, null, null, null);
    }

    private long relationCount() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM arch_deployment_unit_relation WHERE tenant_id = ?",
                Long.class, TENANT_ID);
        return count == null ? 0 : count;
    }

    private long historyCount(String action) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM arch_deployment_unit_relation_history "
                        + "WHERE tenant_id = ? AND action = ?", Long.class, TENANT_ID, action);
        return count == null ? 0 : count;
    }

    private void submitRelationWrite(ExecutorService executor, CountDownLatch ready, CountDownLatch start,
                                     List<RuntimeException> failures, long sourceId, long targetId) {
        executor.submit(() -> {
            ready.countDown();
            try {
                start.await();
                transactions.executeWithoutResult(status ->
                        store.replaceRelations(TENANT_ID, sourceId, Set.of(targetId), actor.id(), 2));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                failures.add(new IllegalStateException("relation write interrupted", exception));
            } catch (RuntimeException exception) {
                failures.add(exception);
            }
        });
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
