package com.ccb.architecture.service;

import com.ccb.architecture.persistence.SubsystemParticipationStore;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class SubsystemParticipationStoreMySqlTest {
    private static final String DATABASE = "subsystem_participation";
    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName(DATABASE).withUsername("test").withPassword("test");
    private static JdbcTemplate jdbc;
    private static TransactionTemplate transactions;
    private SubsystemParticipationStore store;
    @BeforeAll
    static void migrate() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("ALTER DATABASE `" + DATABASE + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        Flyway v157 = Flyway.configure()
                .dataSource(dataSource)
                .locations("filesystem:" + migrationDirectory())
                .placeholders(java.util.Map.of("bootstrap_admin_password_hash", "test-hash"))
                .target(MigrationVersion.fromVersion("157"))
                .cleanDisabled(false)
                .load();
        v157.clean();
        v157.migrate();
        prepareDefaultProject();
        Flyway.configure()
                .dataSource(dataSource)
                .locations("filesystem:" + migrationDirectory())
                .placeholders(java.util.Map.of("bootstrap_admin_password_hash", "test-hash"))
                .target(MigrationVersion.fromVersion("159"))
                .cleanDisabled(false)
                .load()
                .migrate();
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    private static void prepareDefaultProject() {
        jdbc.update("DELETE FROM pm_project WHERE tenant_id = 1 AND "
                + "(project_code = 'RDDMP-PLATFORM' OR id = 990001)");
        jdbc.update("INSERT INTO pm_project (id, tenant_id, project_code, project_name, status, owner_id, "
                        + "created_by, deleted) VALUES (?, ?, 'RDDMP-PLATFORM', '计划隔离测试项目', "
                        + "'RUNNING', 1, 1, 0)", 990001L, 1L);
    }


    @BeforeEach
    void seed() {
        store = new SubsystemParticipationStore(jdbc);
        for (String table : List.of("arch_plan_block", "arch_plan_task", "arch_plan_stage", "arch_setup_plan",
                "arch_plan_template", "arch_environment", "arch_deployment_unit")) {
            jdbc.update("DELETE FROM " + table + " WHERE tenant_id = 94");
        }
        jdbc.update("DELETE FROM arch_subsystem_participant_audit WHERE tenant_id = 94");
        jdbc.update("DELETE FROM arch_subsystem_participant WHERE tenant_id = 94");
        jdbc.update("DELETE FROM arch_physical_subsystem WHERE tenant_id = 94");
        jdbc.update("""
                INSERT INTO arch_physical_subsystem (id, tenant_id, project_id, code, short_name, name,
                    responsible_team_org_id, responsible_team_name_snapshot, owner_user_id, created_by, updated_by)
                VALUES (9401,94,70,'S1','系统一','系统一',1,'测试团队',9,9,9),
                       (9402,94,71,'S1','系统一','系统一',1,'测试团队',20,9,9)
                """);
    }

    @Test
    void 复合外键阻止跨项目关系且唯一键去重() {
        store.replace(94, 70, 9401, List.of(20L), 9);
        assertThat(store.findExplicit(94, 70, 9401)).containsExactly(20L);
        assertThat(store.findExplicit(94, 71, 9401)).isEmpty();
        assertThatThrownBy(() -> store.replace(94, 71, 9401, List.of(20L), 9))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO arch_subsystem_participant (tenant_id,project_id,subsystem_id,user_id,created_by)
                VALUES (94,70,9401,20,9)
                """)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(store.findSystem(94, 71, 9401, false)).isEmpty();
    }

    @Test
    void 成员与审计同事务回滚且执行查询兼容真实表结构() {
        transactions.executeWithoutResult(tx -> {
            store.replace(94, 70, 9401, List.of(20L), 9);
            store.recordChange(94, 70, 9401, 9, List.of(), List.of(20L), "测试变更", "test-trace");
            tx.setRollbackOnly();
        });
        assertThat(store.findExplicit(94, 70, 9401)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM arch_subsystem_participant_audit WHERE tenant_id=94", Integer.class)).isZero();
        assertThat(store.hasPendingResponsibility(94, 70, 9401, 20)).isFalse();
        assertThat(store.findUnitSystem(94, 70, 9999)).isEmpty();
    }

    @Test
    void 撤销事务持父锁使后续执行锁读只能看到最新名单() throws Exception {
        store.replace(94, 70, 9401, List.of(20L), 9);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch reading = new CountDownLatch(1);
        try {
            Future<?> removal = pool.submit(() -> transactions.executeWithoutResult(tx -> {
                long version = store.findSystem(94, 70, 9401, true).orElseThrow().rowVersion();
                store.replace(94, 70, 9401, List.of(), 9);
                assertThat(store.advanceVersion(94, 70, 9401, version, 9)).isTrue();
                locked.countDown();
                try { if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("等待超时"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            Future<List<Long>> execution = pool.submit(() -> transactions.execute(tx -> {
                reading.countDown();
                store.findSystem(94, 70, 9401, true).orElseThrow();
                return store.findExplicit(94, 70, 9401);
            }));
            assertThat(reading.await(10, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> execution.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown();
            removal.get(10, TimeUnit.SECONDS);
            assertThat(execution.get(10, TimeUnit.SECONDS)).isEmpty();
        } finally { release.countDown(); pool.shutdownNow(); }
    }
    @Test
    void 实际任务与阻塞按租户项目系统检查且公共任务也保护项目退出() {
        seedResponsibility();
        assertThat(store.hasPendingResponsibility(94, 70, 9401, 20)).isTrue();
        assertThat(store.hasPendingResponsibility(94, 71, 9401, 20)).isFalse();
        assertThat(store.hasPendingResponsibility(95, 70, 9401, 20)).isFalse();
        assertThat(store.hasPendingResponsibility(94, 70, 9402, 20)).isFalse();
        assertThat(store.hasProjectPendingResponsibility(94, 70, 20)).isTrue();
        assertThat(store.hasProjectPendingResponsibility(94, 71, 20)).isFalse();
        jdbc.update("UPDATE arch_plan_task SET status='COMPLETED' WHERE id=9494");
        assertThat(store.hasPendingResponsibility(94, 70, 9401, 20)).isFalse();
        assertThat(store.hasProjectPendingResponsibility(94, 70, 20)).isFalse();
        jdbc.update("""
                INSERT INTO arch_plan_block (id,tenant_id,project_id,task_id,description,owner_user_id,created_by,updated_by)
                VALUES (9495,94,70,9494,'测试阻塞',20,9,9)
                """);
        assertThat(store.hasPendingResponsibility(94, 70, 9401, 20)).isTrue();
        assertThat(store.hasProjectPendingResponsibility(94, 70, 20)).isTrue();
        jdbc.update("UPDATE arch_plan_block SET status='RESOLVED' WHERE id=9495");
        jdbc.update("UPDATE arch_plan_task SET target_type=NULL,target_id=NULL,status='NOT_STARTED' WHERE id=9494");
        assertThat(store.hasPendingResponsibility(94, 70, 9401, 20)).isFalse();
        assertThat(store.hasProjectPendingResponsibility(94, 70, 20)).isTrue();
        jdbc.update("UPDATE arch_plan_task SET cancelled=1,status='CANCELLED' WHERE id=9494");
        assertThat(store.hasProjectPendingResponsibility(94, 70, 20)).isFalse();
    }

    @Test
    void 部署单元继承所属系统的退出责任保护() {
        seedResponsibility();
        jdbc.update("""
                INSERT INTO arch_deployment_unit
                  (id,tenant_id,project_id,physical_subsystem_id,code,name,kind,created_by,updated_by)
                VALUES (9496,94,70,9401,'D00009496','D00009496_AP','APPLICATION',9,9)
                """);
        jdbc.update("UPDATE arch_plan_task SET target_type='DEPLOYMENT_UNIT',target_id=9496 WHERE id=9494");
        assertThat(store.hasPendingResponsibility(94, 70, 9401, 20)).isTrue();
        assertThat(store.hasPendingResponsibility(94, 70, 9402, 20)).isFalse();
    }

    @Test
    void 已建立旧快照的事务仍读取最新任务责任() throws Exception {
        seedResponsibility();
        jdbc.update("UPDATE arch_plan_task SET status='COMPLETED' WHERE id=9494");
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            transactions.executeWithoutResult(tx -> {
                assertThat(jdbc.queryForObject("SELECT status FROM arch_plan_task WHERE id=9494", String.class))
                        .isEqualTo("COMPLETED");
                try {
                    pool.submit(() -> jdbc.update("UPDATE arch_plan_task SET status='NOT_STARTED' WHERE id=9494"))
                            .get(10, TimeUnit.SECONDS);
                } catch (Exception e) { throw new IllegalStateException(e); }
                assertThat(store.hasPendingResponsibility(94, 70, 9401, 20)).isTrue();
                assertThat(store.hasProjectPendingResponsibility(94, 70, 20)).isTrue();
            });
        } finally { pool.shutdownNow(); }
    }

    private void seedResponsibility() {
        jdbc.update("INSERT INTO arch_environment (id,tenant_id,project_id,code,name,type_code,created_by,updated_by) "
                + "VALUES (9491,94,70,'TEST','测试环境','architecture.environment-type.test',9,9)");
        jdbc.update("INSERT INTO arch_plan_template (id,tenant_id,name,created_by,updated_by) "
                + "VALUES (9492,94,'测试模板',9,9)");
        jdbc.update("""
                INSERT INTO arch_setup_plan (id,tenant_id,project_id,plan_no,name,environment_id,template_id,
                    template_version_no,plan_owner_user_id,created_by,updated_by)
                VALUES (9493,94,70,'SP9493','测试计划',9491,9492,1,9,9,9)
                """);
        jdbc.update("""
                INSERT INTO arch_plan_stage (id,tenant_id,project_id,plan_id,stage_no,name,owner_user_id,created_by,updated_by)
                VALUES (9493,94,70,9493,1,'测试环节',9,9,9)
                """);
        jdbc.update("""
                INSERT INTO arch_plan_task (id,tenant_id,project_id,plan_id,stage_id,task_no,name,
                    target_type,target_id,owner_user_id,created_by,updated_by)
                VALUES (9494,94,70,9493,9493,1,'测试任务','PHYSICAL_SUBSYSTEM',9401,20,9,9)
                """);
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
