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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
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
                .target(MigrationVersion.fromVersion("77"))
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
    void concurrentLegacyPhysicalCreatesAllReturn409WithoutChangingPublishedFacts() throws Exception {
        ArchitectureSubsystemRepository repository = new ArchitectureSubsystemRepository(jdbc);
        Services services = services(repository);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> writes = new ArrayList<>();
            for (int index = 0; index < 8; index++) {
                int requestIndex = index;
                writes.add(executor.submit(() -> services.physical().create(
                        ACTOR, command(), "trace-legacy-create-" + requestIndex)));
            }

            for (Future<?> write : writes) {
                assertWorkOrderRequired(write);
            }
            assertThat(activeLogicalCount()).isEqualTo(1);
            assertThat(activePhysicalCount()).isZero();
            assertThat(danglingActivePhysicalCount()).isZero();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentLegacyDeleteAndCreateBothReturn409WithoutChangingPublishedFacts() throws Exception {
        ArchitectureSubsystemRepository repository = new ArchitectureSubsystemRepository(jdbc);
        Services services = services(repository);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> physical = executor.submit(() -> {
                Thread.currentThread().setName("physical-create");
                services.physical().create(ACTOR, command(), "trace-legacy-create");
            });
            Future<?> deletion = executor.submit(() -> {
                Thread.currentThread().setName("logical-delete");
                services.logical().delete(ACTOR, LOGICAL_ID, "trace-legacy-delete");
            });

            assertWorkOrderRequired(physical);
            assertWorkOrderRequired(deletion);
            assertThat(activeLogicalCount()).isEqualTo(1);
            assertThat(activePhysicalCount()).isZero();
            assertThat(danglingActivePhysicalCount()).isZero();
        } finally {
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
                null, 12L, null, null, null, null, null, null);
    }

    private void assertWorkOrderRequired(Future<?> future) throws Exception {
        try {
            future.get(20, TimeUnit.SECONDS);
            fail("预期兼容写入口返回工单冲突");
        } catch (ExecutionException exception) {
            assertThat(exception.getCause()).isInstanceOf(BusinessException.class);
            BusinessException conflict = (BusinessException) exception.getCause();
            assertThat(conflict.code()).isEqualTo(ErrorCode.CONFLICT);
            assertThat(conflict.getMessage()).startsWith("ARCHITECTURE_WORK_ORDER_REQUIRED");
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
