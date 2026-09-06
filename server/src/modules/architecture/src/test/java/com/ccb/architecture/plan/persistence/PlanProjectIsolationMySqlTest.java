package com.ccb.architecture.plan.persistence;

import com.ccb.architecture.plan.model.PlanModels.Block;
import com.ccb.architecture.plan.model.PlanModels.CancelSuggestion;
import com.ccb.architecture.plan.model.PlanModels.CheckItem;
import com.ccb.architecture.plan.model.PlanModels.CheckItemStatus;
import com.ccb.architecture.plan.model.PlanModels.EventType;
import com.ccb.architecture.plan.model.PlanModels.Plan;
import com.ccb.architecture.plan.model.PlanModels.PlanEvent;
import com.ccb.architecture.plan.model.PlanModels.PlanStatus;
import com.ccb.architecture.plan.model.PlanModels.PlanTarget;
import com.ccb.architecture.plan.model.PlanModels.Stage;
import com.ccb.architecture.plan.model.PlanModels.TargetType;
import com.ccb.architecture.plan.model.PlanModels.Task;
import com.ccb.architecture.plan.model.PlanModels.TaskStatus;
import com.ccb.architecture.plan.model.PlanModels.TaskWorkOrder;
import com.ccb.architecture.plan.model.PlanModels.WorkOrderSource;
import com.ccb.architecture.plan.model.PlanModels.WorkOrderType;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class PlanProjectIsolationMySqlTest {
    private static final String DATABASE = "plan_project_scope";
    private static final long TENANT = 94L;
    private static final long PROJECT_A = 9401L;
    private static final long PROJECT_B = 9402L;
    private static final long TEMPLATE = 9410L;
    private static final long PLAN_A = 9421L;
    private static final long PLAN_B = 9422L;
    private static final long STAGE_A = 9431L;
    private static final long STAGE_A_2 = 9432L;
    private static final long STAGE_B = 9433L;
    private static final long TASK_A = 9441L;
    private static final long TASK_A_2 = 9442L;
    private static final long TASK_B = 9443L;
    private static final long ITEM_A = 9451L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 5, 12, 0);

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName(DATABASE)
            .withUsername("test")
            .withPassword("test");

    private static JdbcTemplate jdbc;
    private static TransactionTemplate transactions;
    private PlanStore store;
    private PlanTemplateStore templateStore;

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
                .target(MigrationVersion.fromVersion("158"))
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

    @AfterAll
    static void clearStatics() {
        transactions = null;
        jdbc = null;
    }

    @BeforeEach
    void resetData() {
        jdbc.update("DELETE FROM arch_setup_plan_activity WHERE tenant_id = ?", TENANT);
        jdbc.update("DELETE FROM arch_plan_stage_dependency WHERE tenant_id = ?", TENANT);
        jdbc.update("DELETE FROM arch_plan_work_order WHERE tenant_id = ?", TENANT);
        jdbc.update("DELETE FROM arch_plan_event WHERE tenant_id = ?", TENANT);
        jdbc.update("DELETE FROM arch_plan_check_item_cancel_suggestion WHERE tenant_id = ?", TENANT);
        jdbc.update("DELETE FROM arch_plan_block WHERE tenant_id = ?", TENANT);
        jdbc.update("DELETE FROM arch_plan_task_dependency WHERE tenant_id = ?", TENANT);
        jdbc.update("DELETE FROM arch_plan_task_participant WHERE tenant_id = ?", TENANT);
        jdbc.update("DELETE FROM arch_plan_check_item WHERE tenant_id = ?", TENANT);
        jdbc.update("DELETE FROM arch_plan_task WHERE tenant_id = ?", TENANT);
        jdbc.update("DELETE FROM arch_plan_stage WHERE tenant_id = ?", TENANT);
        jdbc.update("DELETE FROM arch_plan_target WHERE tenant_id = ?", TENANT);
        jdbc.update("DELETE FROM arch_setup_plan WHERE tenant_id = ?", TENANT);
        jdbc.update("DELETE FROM arch_plan_template_version WHERE tenant_id = ?", TENANT);
        jdbc.update("DELETE FROM arch_plan_template WHERE tenant_id = ?", TENANT);
        jdbc.update("DELETE FROM arch_environment WHERE tenant_id = ?", TENANT);
        seedReferences();
        store = new PlanStore(jdbc);
        templateStore = new PlanTemplateStore(jdbc, new ObjectMapper());
    }

    @Test
    void 计划执行链按项目隔离且模板保持租户共享() {
        transactions.executeWithoutResult(status -> {
            store.insertPlan(TENANT, plan(PLAN_A, PROJECT_A, 9461L, NOW.minusDays(2)));
            store.insertPlan(TENANT, plan(PLAN_B, PROJECT_B, 9462L, NOW.plusDays(2)));

            store.insertTarget(TENANT, PROJECT_A,
                    new PlanTarget(9471L, PLAN_A, TargetType.PHYSICAL_SUBSYSTEM,
                            9481L, "PHY-SAME", "项目 A 物理子系统", false, null), null);
            store.insertStage(TENANT, PROJECT_A, stage(STAGE_A, PLAN_A, 1));
            store.insertStage(TENANT, PROJECT_A, stage(STAGE_A_2, PLAN_A, 2));
            store.insertStage(TENANT, PROJECT_B, stage(STAGE_B, PLAN_B, 1));
            store.insertTask(TENANT, PROJECT_A, task(TASK_A, PLAN_A, STAGE_A, 1, NOW.minusHours(1)));
            store.insertTask(TENANT, PROJECT_A, task(TASK_A_2, PLAN_A, STAGE_A, 2, NOW.plusDays(1)));
            store.insertTask(TENANT, PROJECT_B, task(TASK_B, PLAN_B, STAGE_B, 1, NOW.plusDays(1)));
            store.insertCheckItem(TENANT, PROJECT_A, new CheckItem(ITEM_A, TASK_A, 1,
                    "检查网络", 1, null, CheckItemStatus.PENDING, null, null, null,
                    false, null, null, null, 0, 9L));
            store.insertParticipant(TENANT, PROJECT_A, 9491L, TASK_A, 9L, 9L);
            store.insertDependency(TENANT, PROJECT_A, 9501L, TASK_A_2, TASK_A, 9L);
            store.insertBlock(TENANT, PROJECT_A, new Block(9511L, TASK_A, "等待资源", null,
                    9L, NOW.plusHours(1), false, null, null, null, 9L));
            store.insertCancelSuggestion(TENANT, PROJECT_A,
                    new CancelSuggestion(9521L, ITEM_A, "不再需要", 9L,
                            "PENDING", null, null, null));
            store.insertEvent(TENANT, PROJECT_A, new PlanEvent(9531L, PLAN_A, "TASK", TASK_A,
                    EventType.START, NOW.minusHours(2), 9L, null, null));
            store.insertWorkOrder(TENANT, PROJECT_A, new TaskWorkOrder(9541L, TASK_A, PLAN_A,
                    WorkOrderType.NETWORK_DNS, 9551L, WorkOrderSource.ATTACHED_LATER, false));
            store.insertStageDependency(TENANT, PROJECT_A, 9561L, PLAN_A, STAGE_A_2, STAGE_A, 9L);
            store.insertActivity(TENANT, PROJECT_A, 9571L, "PLAN", PLAN_A, "PLAN", PLAN_A,
                    "PLAN_CREATED", 9L, null, null, null);
        });

        assertThat(store.findPlan(TENANT, PROJECT_A, PLAN_A)).isPresent();
        assertThat(store.findPlan(TENANT, PROJECT_A, PLAN_B)).isEmpty();
        assertThat(store.findStage(TENANT, PROJECT_B, STAGE_A)).isEmpty();
        assertThat(store.findTask(TENANT, PROJECT_B, TASK_A)).isEmpty();
        assertThat(store.findCheckItem(TENANT, PROJECT_B, ITEM_A)).isEmpty();
        assertThat(store.findDependencyById(TENANT, PROJECT_B, 9501L)).isEmpty();
        assertThat(store.findBlock(TENANT, PROJECT_B, 9511L)).isEmpty();
        assertThat(store.findSuggestion(TENANT, PROJECT_B, 9521L)).isEmpty();
        assertThat(store.findEvent(TENANT, PROJECT_B, 9531L)).isEmpty();
        assertThat(store.findWorkOrder(TENANT, PROJECT_B, 9541L)).isEmpty();

        transactions.executeWithoutResult(status ->
                store.updateTaskSchedule(TENANT, PROJECT_A, TASK_B, NOW, NOW.plusHours(2)));
        assertThat(store.findTask(TENANT, PROJECT_B, TASK_B).orElseThrow().plannedStart()).isNull();

        assertThat(store.searchPlans(TENANT, PROJECT_A, null, null, null,
                false, false, false, null, null, null, 20, 0))
                .extracting(row -> row.plan().id()).containsExactly(PLAN_A);
        assertThat(store.searchPlans(TENANT, PROJECT_B, null, null, null,
                false, false, false, null, null, null, 20, 0))
                .extracting(row -> row.plan().id()).containsExactly(PLAN_B);
        assertThat(store.countPlans(TENANT, PROJECT_A, null, null, null,
                false, false, false, null, null, null)).isEqualTo(1);
        assertThat(store.countOverdueTasks(TENANT, PROJECT_A, PLAN_A, NOW)).isEqualTo(1);
        assertThat(store.countOverdueTasks(TENANT, PROJECT_B, PLAN_B, NOW)).isZero();
        assertThat(store.planIdsNeedingAlert()).filteredOn(alert -> alert.tenantId() == TENANT)
                .extracting(PlanStore.AlertPlan::projectId).containsExactlyInAnyOrder(PROJECT_A, PROJECT_B);

        assertProject("arch_setup_plan", PLAN_A, PROJECT_A);
        assertProject("arch_plan_target", 9471L, PROJECT_A);
        assertProject("arch_plan_stage", STAGE_A, PROJECT_A);
        assertProject("arch_plan_task", TASK_A, PROJECT_A);
        assertProject("arch_plan_check_item", ITEM_A, PROJECT_A);
        assertProject("arch_plan_task_participant", 9491L, PROJECT_A);
        assertProject("arch_plan_task_dependency", 9501L, PROJECT_A);
        assertProject("arch_plan_block", 9511L, PROJECT_A);
        assertProject("arch_plan_check_item_cancel_suggestion", 9521L, PROJECT_A);
        assertProject("arch_plan_event", 9531L, PROJECT_A);
        assertProject("arch_plan_work_order", 9541L, PROJECT_A);
        assertProject("arch_plan_stage_dependency", 9561L, PROJECT_A);
        assertProject("arch_setup_plan_activity", 9571L, PROJECT_A);

        assertThat(templateStore.findTemplate(TENANT, TEMPLATE)).isPresent();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name = 'arch_plan_template' "
                + "AND column_name = 'project_id'", Integer.class)).isZero();
        assertThat(jdbc.queryForList("SELECT project_id FROM arch_setup_plan "
                + "WHERE tenant_id = ? AND template_id = ? ORDER BY project_id", Long.class, TENANT, TEMPLATE))
                .containsExactly(PROJECT_A, PROJECT_B);
    }

    private void seedReferences() {
        jdbc.update("INSERT INTO arch_environment "
                        + "(id, tenant_id, project_id, code, name, type_code, status, row_version, created_by, updated_by) "
                        + "VALUES (9461, ?, ?, 'ENV-SAME', '项目 A 环境', "
                        + "'architecture.environment-type.dev', 'ACTIVE', 0, 9, 9), "
                        + "(9462, ?, ?, 'ENV-SAME', '项目 B 环境', "
                        + "'architecture.environment-type.dev', 'ACTIVE', 0, 9, 9)",
                TENANT, PROJECT_A, TENANT, PROJECT_B);
        jdbc.update("INSERT INTO arch_plan_template "
                        + "(id, tenant_id, name, status, latest_version_no, row_version, created_by, updated_by) "
                        + "VALUES (?, ?, '共享模板', 'ACTIVE', 1, 0, 9, 9)", TEMPLATE, TENANT);
    }

    private Plan plan(long id, long projectId, long environmentId, LocalDateTime plannedEnd) {
        return new Plan(id, projectId, "SP-SAME", "项目搭建计划", environmentId,
                PlanStatus.NOT_STARTED, TEMPLATE, 1, 9L, NOW.minusDays(3), plannedEnd,
                null, null, false, null, null, null, 0);
    }

    private Stage stage(long id, long planId, int stageNo) {
        return new Stage(id, planId, stageNo, "环节 " + stageNo, stageNo, 9L,
                NOW.minusDays(2), NOW.plusDays(2), null, null, PlanStatus.NOT_STARTED,
                false, null, null, null, null);
    }

    private Task task(long id, long planId, long stageId, int taskNo, LocalDateTime plannedEnd) {
        return new Task(id, planId, stageId, taskNo, "任务 " + taskNo,
                TargetType.PHYSICAL_SUBSYSTEM, null, null, null, null, null, "NONE", null,
                9L, null, plannedEnd, null, null, TaskStatus.NOT_STARTED,
                false, false, null, null, null, 0);
    }

    private void assertProject(String table, long id, long expectedProjectId) {
        assertThat(jdbc.queryForObject("SELECT project_id FROM " + table
                + " WHERE tenant_id = ? AND id = ?", Long.class, TENANT, id)).isEqualTo(expectedProjectId);
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
