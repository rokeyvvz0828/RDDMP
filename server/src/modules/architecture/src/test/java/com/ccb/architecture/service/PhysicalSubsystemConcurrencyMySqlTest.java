package com.ccb.architecture.service;

import com.ccb.architecture.model.PhysicalSubsystemCommand;
import com.ccb.architecture.repository.ArchitectureSubsystemRepository;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.SystemOperationAudit;
import com.ccb.system.capability.SystemReferenceQuery;
import com.ccb.system.org.OrgTreeNode;
import com.ccb.system.org.OrganizationService;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@Testcontainers
class PhysicalSubsystemConcurrencyMySqlTest {
    private static final AuthUser ACTOR = new AuthUser(9, 7, "architect", "hash", "架构管理员", 11, true);
    private static final long LOGICAL_ID = 101;

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("architecture_concurrency")
            .withUsername("test")
            .withPassword("test");

    private JdbcTemplate jdbc;
    private TransactionTemplate transactions;

    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("filesystem:" + migrationDirectory())
                .placeholders(java.util.Map.of("bootstrap_admin_password_hash", "test-hash"))
                .target(MigrationVersion.fromVersion("36"))
                .load()
                .migrate();
    }

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        jdbc.update("DELETE FROM arch_physical_subsystem");
        jdbc.update("DELETE FROM arch_logical_subsystem");
        jdbc.update("""
                INSERT INTO arch_logical_subsystem
                    (id, tenant_id, code, short_name, name, business_org_id, contact_user_id, created_by, updated_by)
                VALUES (?, ?, 'AP_201', '员工渠道', '员工渠道整合平台', 11, 21, 9, 9)
                """, LOGICAL_ID, ACTOR.tenantId());
    }

    @Test
    void physicalCreateLocksFirstSoConcurrentLogicalDeleteReturns409() throws Exception {
        ArchitectureSubsystemRepository repository = spy(new ArchitectureSubsystemRepository(jdbc));
        CountDownLatch physicalHasParentLock = new CountDownLatch(1);
        CountDownLatch deleteAttemptedParentLock = new CountDownLatch(1);
        CountDownLatch allowPhysicalCommit = new CountDownLatch(1);
        AtomicBoolean pausePhysicalOnce = new AtomicBoolean(true);

        doAnswer(invocation -> {
            boolean physicalThread = Thread.currentThread().getName().contains("physical-create");
            if (!physicalThread) {
                deleteAttemptedParentLock.countDown();
                return invocation.callRealMethod();
            }
            Object result = invocation.callRealMethod();
            if (pausePhysicalOnce.compareAndSet(true, false)) {
                physicalHasParentLock.countDown();
                await(allowPhysicalCommit, "物理事务未获准提交");
            }
            return result;
        }).when(repository).lockLogical(anyLong(), anyLong());

        Services services = services(repository);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> physical = executor.submit(() -> {
                Thread.currentThread().setName("physical-create");
                services.physical().create(ACTOR, command(), "trace-physical-first");
            });
            await(physicalHasParentLock, "物理事务未取得逻辑父锁");
            Future<?> deletion = executor.submit(() -> {
                Thread.currentThread().setName("logical-delete");
                services.logical().delete(ACTOR, LOGICAL_ID, "trace-delete-second");
            });
            await(deleteAttemptedParentLock, "删除事务未尝试逻辑父锁");
            allowPhysicalCommit.countDown();

            physical.get(20, TimeUnit.SECONDS);
            BusinessException conflict = businessFailure(deletion);
            assertThat(conflict.code()).isEqualTo(ErrorCode.CONFLICT);
            assertThat(activeLogicalCount()).isEqualTo(1);
            assertThat(activePhysicalCount()).isEqualTo(1);
            assertThat(danglingActivePhysicalCount()).isZero();
        } finally {
            allowPhysicalCommit.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void logicalDeleteLocksFirstSoAlreadyPrecheckedPhysicalCreateReturns409() throws Exception {
        ArchitectureSubsystemRepository repository = spy(new ArchitectureSubsystemRepository(jdbc));
        CountDownLatch physicalPrecheckedParent = new CountDownLatch(1);
        CountDownLatch allowPhysicalTransaction = new CountDownLatch(1);
        AtomicBoolean pausePhysicalPrecheckOnce = new AtomicBoolean(true);

        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            boolean physicalThread = Thread.currentThread().getName().contains("physical-create");
            if (physicalThread && pausePhysicalPrecheckOnce.compareAndSet(true, false)) {
                physicalPrecheckedParent.countDown();
                await(allowPhysicalTransaction, "物理事务未获准继续");
            }
            return result;
        }).when(repository).findLogical(anyLong(), anyLong());

        Services services = services(repository);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> physical = executor.submit(() -> {
                Thread.currentThread().setName("physical-create");
                services.physical().create(ACTOR, command(), "trace-prechecked");
            });
            await(physicalPrecheckedParent, "物理事务未完成活动父记录初检");
            Future<?> deletion = executor.submit(() -> {
                Thread.currentThread().setName("logical-delete");
                services.logical().delete(ACTOR, LOGICAL_ID, "trace-delete-first");
            });
            deletion.get(20, TimeUnit.SECONDS);
            allowPhysicalTransaction.countDown();

            BusinessException conflict = businessFailure(physical);
            assertThat(conflict.code()).isEqualTo(ErrorCode.CONFLICT);
            assertThat(activeLogicalCount()).isZero();
            assertThat(activePhysicalCount()).isZero();
            assertThat(danglingActivePhysicalCount()).isZero();
        } finally {
            allowPhysicalTransaction.countDown();
            executor.shutdownNow();
        }
    }

    private Services services(ArchitectureSubsystemRepository repository) {
        OrganizationService organizations = mock(OrganizationService.class);
        when(organizations.tree(ACTOR)).thenReturn(List.of(
                new OrgTreeNode(12, 0, "TEAM", "平台研发团队", 1, 1, new ArrayList<>(), new ArrayList<>())));
        SystemReferenceQuery references = mock(SystemReferenceQuery.class);
        SystemOperationAudit audit = mock(SystemOperationAudit.class);
        return new Services(
                new PhysicalSubsystemService(repository, organizations, references, audit, transactions),
                new LogicalSubsystemService(repository, organizations, references, audit, transactions));
    }

    private PhysicalSubsystemCommand command() {
        return new PhysicalSubsystemCommand("WP_201", "员工渠道物理", "员工渠道物理平台", LOGICAL_ID,
                null, 12L, null, null, null, null, null, null, null);
    }

    private BusinessException businessFailure(Future<?> future) throws Exception {
        try {
            future.get(20, TimeUnit.SECONDS);
            return fail("预期并发操作返回 BusinessException");
        } catch (ExecutionException exception) {
            assertThat(exception.getCause()).isInstanceOf(BusinessException.class);
            return (BusinessException) exception.getCause();
        }
    }

    private long activeLogicalCount() {
        Long value = jdbc.queryForObject(
                "SELECT COUNT(*) FROM arch_logical_subsystem WHERE tenant_id = ? AND id = ? AND deleted = 0",
                Long.class, ACTOR.tenantId(), LOGICAL_ID);
        return value == null ? 0 : value;
    }

    private long activePhysicalCount() {
        Long value = jdbc.queryForObject(
                "SELECT COUNT(*) FROM arch_physical_subsystem WHERE tenant_id = ? AND deleted = 0",
                Long.class, ACTOR.tenantId());
        return value == null ? 0 : value;
    }

    private long danglingActivePhysicalCount() {
        Long value = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM arch_physical_subsystem p
                LEFT JOIN arch_logical_subsystem l
                  ON l.tenant_id = p.tenant_id AND l.id = p.logical_subsystem_id AND l.deleted = 0
                WHERE p.tenant_id = ? AND p.deleted = 0 AND l.id IS NULL
                """, Long.class, ACTOR.tenantId());
        return value == null ? 0 : value;
    }

    private static void await(CountDownLatch latch, String message) throws InterruptedException {
        if (!latch.await(10, TimeUnit.SECONDS)) {
            fail(message);
        }
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

    private record Services(PhysicalSubsystemService physical, LogicalSubsystemService logical) {
    }
}
