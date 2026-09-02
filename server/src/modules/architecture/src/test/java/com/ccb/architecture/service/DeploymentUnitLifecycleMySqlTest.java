package com.ccb.architecture.service;

import com.ccb.architecture.model.DeploymentUnitModels.DeploymentUnitCommand;
import com.ccb.architecture.persistence.DeploymentUnitStore;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.SystemOperationAudit;
import com.ccb.system.capability.SystemReferenceQuery;
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
import java.util.List;
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
    private static final long LOGICAL_ID = 11L;
    private static final long PHYSICAL_ID = 501L;

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName(DATABASE)
            .withUsername("test")
            .withPassword("test");

    private static JdbcTemplate jdbc;
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
                .cleanDisabled(false)
                .load();
        flyway.clean();
        flyway.migrate();

        DeploymentUnitStore store = new DeploymentUnitStore(jdbc);
        LongSupplier idSupplier = () -> identifiers.incrementAndGet();
        service = new DeploymentUnitService(store, new DeploymentUnitReferenceGuard(List.of()),
                mock(SystemReferenceQuery.class), mock(SystemOperationAudit.class),
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)), idSupplier);
    }

    @BeforeEach
    void seedArchitecture() {
        jdbc.update("DELETE FROM arch_deployment_unit_version");
        jdbc.update("DELETE FROM arch_deployment_unit");
        jdbc.update("DELETE FROM arch_deployment_unit_import_item");
        jdbc.update("DELETE FROM arch_deployment_unit_import_batch");
        jdbc.update("DELETE FROM arch_deployment_unit_number_seq");
        jdbc.update("DELETE FROM arch_physical_subsystem WHERE tenant_id = ?", TENANT_ID);
        jdbc.update("DELETE FROM arch_logical_subsystem WHERE tenant_id = ?", TENANT_ID);
        jdbc.update("INSERT INTO arch_logical_subsystem "
                        + "(id, tenant_id, code, short_name, name, business_org_id, contact_user_id, number_sequence,"
                        + " status, sort_no, row_version, created_by, updated_by) "
                        + "VALUES (?, ?, 'A0001', '渠道域', '渠道域逻辑子系统', 1, 1, 1, 'ACTIVE', 0, 0, 1, 1)",
                LOGICAL_ID, TENANT_ID);
        jdbc.update("INSERT INTO arch_physical_subsystem "
                        + "(id, tenant_id, code, short_name, name, logical_subsystem_id, responsible_team_org_id,"
                        + " responsible_team_name_snapshot, number_slot, status, row_version, created_by, updated_by) "
                        + "VALUES (?, ?, 'W0001A', '渠道接入', '渠道接入系统', ?, 1, '渠道团队', 'A', 'ACTIVE', 0, 1, 1)",
                PHYSICAL_ID, TENANT_ID, LOGICAL_ID);
    }

    @AfterAll
    static void clearStatics() {
        jdbc = null;
        service = null;
    }

    @Test
    void createPublishesVersionOneWithDeterministicNumber() {
        var view = service.create(actor, command("电子渠道接入应用", "APPLICATION"), "trace");

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
                            command("并发应用-" + index, "APPLICATION"), "trace-" + index);
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
        var created = service.create(actor, command("电子渠道接入应用", "APPLICATION"), "trace");

        var updated = service.update(actor, created.id(),
                new DeploymentUnitCommand(null, "ECIP-AP", "电子渠道接入应用 V2", "DATABASE",
                        "迁移到数据库服务", null, created.rowVersion()), "trace");

        assertThat(updated.currentVersion()).isEqualTo(2);
        List<java.util.Map<String, Object>> versions = jdbc.queryForList(
                "SELECT version_no, name, kind, description FROM arch_deployment_unit_version "
                        + "WHERE tenant_id = ? AND unit_id = ? ORDER BY version_no",
                TENANT_ID, created.id());
        assertThat(versions).hasSize(2);
        assertThat(versions.get(0).get("name")).isEqualTo("电子渠道接入应用");
        assertThat(versions.get(0).get("kind")).isEqualTo("APPLICATION");
        assertThat(versions.get(1).get("name")).isEqualTo("电子渠道接入应用 V2");
        assertThat(versions.get(1).get("kind")).isEqualTo("DATABASE");
    }

    @Test
    void updateRejectsStaleRowVersionAndKeepsVersionCount() {
        var created = service.create(actor, command("电子渠道接入应用", "APPLICATION"), "trace");
        var updated = service.update(actor, created.id(),
                new DeploymentUnitCommand(null, "ECIP-AP", "第一次修改", "APPLICATION", null, null,
                        created.rowVersion()), "trace");

        assertThatThrownBy(() -> service.update(actor, created.id(),
                new DeploymentUnitCommand(null, "ECIP-AP", "陈旧并发修改", "APPLICATION", null, null,
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
        var created = service.create(actor, command("电子渠道接入应用", "APPLICATION"), "trace");

        service.deactivate(actor, created.id(), "trace");
        assertThatThrownBy(() -> service.create(actor, command("电子渠道接入应用", "APPLICATION"), "trace"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).code()).isEqualTo(ErrorCode.CONFLICT));

        service.reactivate(actor, created.id(), "trace");
        service.voidUnit(actor, created.id(), "trace");
        assertThatThrownBy(() -> service.create(actor, command("电子渠道接入应用", "APPLICATION"), "trace"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).code()).isEqualTo(ErrorCode.CONFLICT));
    }

    @Test
    void lifecycleTransitionsAndVoidedUnitIsTerminal() {
        var created = service.create(actor, command("电子渠道接入应用", "APPLICATION"), "trace");

        var inactive = service.deactivate(actor, created.id(), "trace");
        assertThat(inactive.status()).isEqualTo("INACTIVE");
        assertThatThrownBy(() -> service.update(actor, created.id(),
                new DeploymentUnitCommand(null, "ECIP-AP", "停用中修改", "APPLICATION", null, null,
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

        assertThatThrownBy(() -> service.create(actor, command("电子渠道接入应用", "APPLICATION"), "trace"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).code()).isEqualTo(ErrorCode.BAD_REQUEST));
    }

    @Test
    void numberCapacityExhaustionFailsCleanly() {
        jdbc.update("INSERT INTO arch_deployment_unit_number_seq (tenant_id, physical_subsystem_id, next_ordinal) "
                + "VALUES (?, ?, 1000)", TENANT_ID, PHYSICAL_ID);

        assertThatThrownBy(() -> service.create(actor, command("电子渠道接入应用", "APPLICATION"), "trace"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).code()).isEqualTo(ErrorCode.CONFLICT));
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM arch_deployment_unit WHERE tenant_id = ?",
                Long.class, TENANT_ID);
        assertThat(count).isZero();
    }

    private DeploymentUnitCommand command(String name, String kind) {
        return new DeploymentUnitCommand(PHYSICAL_ID, "ECIP-AP", name, kind, null, null, null);
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
