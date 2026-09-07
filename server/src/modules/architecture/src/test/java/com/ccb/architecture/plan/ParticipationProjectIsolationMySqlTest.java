package com.ccb.architecture.plan;
import com.ccb.architecture.plan.persistence.PlanStore;

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
class ParticipationProjectIsolationMySqlTest {
    private static final String DATABASE = "participation_scope";
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


    @BeforeEach void seed() {
        for (String table : List.of("arch_plan_check_item","arch_plan_task_participant","arch_plan_block","arch_plan_task",
                "arch_plan_stage","arch_setup_plan","arch_plan_template","arch_environment","arch_deployment_unit",
                "arch_subsystem_participant","arch_physical_subsystem")) jdbc.update("DELETE FROM "+table+" WHERE tenant_id=94");
        jdbc.update("INSERT INTO arch_physical_subsystem(id,tenant_id,project_id,code,short_name,name,responsible_team_org_id,responsible_team_name_snapshot,owner_user_id,created_by,updated_by) VALUES(9401,94,70,'S9401','系统A','系统A',1,'测试团队',20,9,9)");
        seedResponsibility();
        jdbc.update("INSERT INTO arch_plan_check_item(id,tenant_id,project_id,task_id,check_no,name,created_by,updated_by) VALUES(9601,94,70,9494,1,'检查项',9,9)");
    }
    private PlanStore.VisiblePlanPage query(long tenant,long project,long user,boolean admin,boolean active,int limit,long offset) {
        return new PlanStore(jdbc).searchVisiblePlans(tenant,project,user,admin,active,null,null,null,false,false,false,null,null,null,limit,offset);
    }
    @Test void 数据库先过滤任务后分页统计且不存在租户项目侧漏() {
        var mine=query(94,70,20,false,true,10,0);
        assertThat(mine.total()).isEqualTo(1);
        assertThat(mine.rows().get(0).row().taskCount()).isEqualTo(1);
        assertThat(mine.rows().get(0).row().totalCheckItems()).isEqualTo(1);
        assertThat(query(94,71,20,true,true,10,0).total()).isZero();
        assertThat(query(95,70,20,true,true,10,0).total()).isZero();
        assertThat(query(94,70,21,false,true,10,0).total()).isZero();
        assertThat(query(94,70,20,false,false,10,0).total()).isZero();
        assertThat(query(94,70,99,true,true,10,0).total()).isEqualTo(1);
    }
    @Test void 失去系统资格即使任务快照存在也不返回计数() {
        jdbc.update("UPDATE arch_physical_subsystem SET owner_user_id=21 WHERE id=9401");
        assertThat(query(94,70,20,false,true,10,0).total()).isZero();
        jdbc.update("INSERT INTO arch_subsystem_participant(tenant_id,project_id,subsystem_id,user_id,created_by) VALUES(94,70,9401,20,9)");
        assertThat(query(94,70,20,false,true,10,0).total()).isEqualTo(1);
        jdbc.update("DELETE FROM arch_subsystem_participant WHERE tenant_id=94 AND user_id=20");
        assertThat(query(94,70,20,false,true,10,0).total()).isZero();
    }
    @Test void 部署单元继承系统而公共任务仅限快照人员() {
        jdbc.update("INSERT INTO arch_deployment_unit(id,tenant_id,project_id,physical_subsystem_id,code,name,kind,created_by,updated_by) VALUES(9496,94,70,9401,'D00009496','D00009496_AP','APPLICATION',9,9)");
        jdbc.update("UPDATE arch_plan_task SET target_type='DEPLOYMENT_UNIT',target_id=9496 WHERE id=9494");
        assertThat(query(94,70,20,false,true,10,0).total()).isEqualTo(1);
        jdbc.update("UPDATE arch_plan_task SET target_id=NULL WHERE id=9494");
        assertThat(query(94,70,20,false,true,10,0).total()).isEqualTo(1);
        assertThat(query(94,70,21,false,true,10,0).total()).isZero();
    }
    @Test void 分页越界仍返回准确总数而非零() {
        var result=query(94,70,20,false,true,1,100);
        assertThat(result.rows()).isEmpty(); assertThat(result.total()).isEqualTo(1);
    }
    @Test void 任务分派CAS与名单更新在真实事务中整体回滚() {
        var plans=new PlanStore(jdbc);
        transactions.executeWithoutResult(tx->{
            assertThat(plans.updateTaskAssignment(94,70,9494,21,0,9)).isTrue();
            plans.insertParticipant(94,70,9610,9494,21,9);
            tx.setRollbackOnly();
        });
        assertThat(plans.findTask(94,70,9494).orElseThrow().ownerUserId()).isEqualTo(20);
        assertThat(plans.findParticipantUserIds(94,70,9494)).isEmpty();
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
