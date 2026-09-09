package com.ccb.architecture.environment.persistence;

import com.ccb.architecture.environment.model.EnvironmentResourceModels.DisasterRecoveryMode;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.Environment;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.EnvironmentInstance;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.FulfillmentMode;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.InstanceDisasterRecovery;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.InstanceStatus;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.RecordStatus;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.RequestStatus;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.RequestType;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.ResourceRequest;
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

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class EnvironmentResourceProjectIsolationMySqlTest {
    private static final String DATABASE = "environment_resource_project_scope";
    private static final long TENANT = 93L;
    private static final long PROJECT_A = 9301L;
    private static final long PROJECT_B = 9302L;
    private static final long PHYSICAL_A = 9311L;
    private static final long PHYSICAL_B = 9312L;
    private static final long UNIT_A = 9321L;
    private static final long UNIT_B = 9322L;
    private static final LocalDateTime TIME = LocalDateTime.of(2026, 9, 5, 11, 0);

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName(DATABASE)
            .withUsername("test")
            .withPassword("test");

    private static JdbcTemplate jdbc;
    private static TransactionTemplate transactions;
    private EnvironmentResourceStore store;

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
                        + "created_by, deleted) VALUES (?, ?, 'RDDMP-PLATFORM', ?, 'RUNNING', 1, 1, 0)",
                990001L, 1L, "环境资源隔离迁移测试项目");
    }

    @AfterAll
    static void clearStatics() {
        transactions = null;
        jdbc = null;
    }

    @BeforeEach
    void resetData() {
        jdbc.update("DELETE FROM arch_instance_disaster_recovery WHERE tenant_id = ?", TENANT);
        jdbc.update("DELETE FROM arch_environment_instance WHERE tenant_id = ?", TENANT);
        jdbc.update("DELETE FROM arch_resource_request_workflow_receipt WHERE tenant_id = ?", TENANT);
        jdbc.update("DELETE FROM arch_resource_request_workflow_round WHERE tenant_id = ?", TENANT);
        jdbc.update("DELETE FROM arch_resource_request_history WHERE tenant_id = ?", TENANT);
        jdbc.update("DELETE FROM arch_resource_request_item WHERE tenant_id = ?", TENANT);
        jdbc.update("DELETE FROM arch_resource_request WHERE tenant_id = ?", TENANT);
        jdbc.update("DELETE FROM arch_environment WHERE tenant_id = ?", TENANT);
        jdbc.update("DELETE FROM arch_deployment_unit_version WHERE tenant_id = ?", TENANT);
        jdbc.update("DELETE FROM arch_deployment_unit WHERE tenant_id = ?", TENANT);
        jdbc.update("DELETE FROM arch_physical_subsystem WHERE tenant_id = ?", TENANT);
        insertPhysical(PHYSICAL_A, PROJECT_A, "项目A物理");
        insertPhysical(PHYSICAL_B, PROJECT_B, "项目B物理");
        insertUnit(UNIT_A, PROJECT_A, PHYSICAL_A);
        insertUnit(UNIT_B, PROJECT_B, PHYSICAL_B);
        store = new EnvironmentResourceStore(jdbc);
    }

    @Test
    void 同租户双项目允许复用编号且直接Id查询不跨项目() {
        transactions.executeWithoutResult(status -> {
            store.insertEnvironment(environment(9401L, PROJECT_A));
            store.insertEnvironment(environment(9402L, PROJECT_B));
            store.insertResourceRequest(request(9501L, PROJECT_A, PHYSICAL_A, 9401L));
            store.insertResourceRequest(request(9502L, PROJECT_B, PHYSICAL_B, 9402L));
            store.insertInstance(instance(9601L, PROJECT_A, "INS-SAME", 9401L, UNIT_A,
                    PHYSICAL_A, 9501L, "vm-a-1", "10.10.1.1"));
            store.insertInstance(instance(9602L, PROJECT_A, "INS-A-STANDBY", 9401L, UNIT_A,
                    PHYSICAL_A, 9501L, "vm-a-2", "10.10.1.2"));
            store.insertInstance(instance(9603L, PROJECT_B, "INS-SAME", 9402L, UNIT_B,
                    PHYSICAL_B, 9502L, "vm-b-1", "10.20.1.1"));
            store.insertDisasterRecovery(new InstanceDisasterRecovery(
                    9701L, TENANT, PROJECT_A, UNIT_A, null, null,
                    9601L, null, null, null, null,
                    9602L, null, null, null, null,
                    DisasterRecoveryMode.PRIMARY_STANDBY, "项目A主备", 9L, TIME, TIME));
        });

        assertThat(store.listEnvironments(TENANT, PROJECT_A, null, null, null, 20, 0))
                .extracting(Environment::code).containsExactly("DEV-SAME");
        assertThat(store.listEnvironments(TENANT, PROJECT_B, null, null, null, 20, 0))
                .extracting(Environment::code).containsExactly("DEV-SAME");
        assertThat(store.findEnvironment(TENANT, PROJECT_A, 9402L)).isEmpty();
        assertThat(store.findRequest(TENANT, PROJECT_A, 9502L)).isEmpty();
        assertThat(store.findInstance(TENANT, PROJECT_A, 9603L)).isEmpty();
        assertThat(store.findDisasterRecovery(TENANT, PROJECT_B, 9701L)).isEmpty();
        assertThat(store.findRequest(TENANT, PROJECT_A, 9501L)).isPresent();
        assertThat(store.findInstance(TENANT, PROJECT_A, 9601L)).isPresent();
        assertThat(store.findDisasterRecovery(TENANT, PROJECT_A, 9701L)).isPresent();
    }

    private void insertPhysical(long id, long projectId, String name) {
        jdbc.update("INSERT INTO arch_physical_subsystem "
                        + "(id, tenant_id, project_id, code, short_name, name, logical_subsystem_name, "
                        + "responsible_team_org_id, responsible_team_name_snapshot, status, row_version, "
                        + "created_by, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, 1, '架构团队', 'ACTIVE', 0, 9, 9)",
                id, TENANT, projectId, "PHY-" + projectId, name, name, name + "逻辑域");
    }

    private void insertUnit(long id, long projectId, long physicalId) {
        jdbc.update("INSERT INTO arch_deployment_unit "
                        + "(id, tenant_id, project_id, code, physical_subsystem_id, name, kind, status, "
                        + "current_version, row_version, created_by, updated_by) "
                        + "VALUES (?, ?, ?, 'DUNIT0001', ?, 'DUNIT0001_AP', 'APPLICATION', 'ACTIVE', 1, 0, 9, 9)",
                id, TENANT, projectId, physicalId);
    }

    private Environment environment(long id, long projectId) {
        return new Environment(id, TENANT, projectId, "DEV-SAME", "开发环境", "architecture.environment-type.dev",
                "开发环境", RecordStatus.ACTIVE, null, null, 0L, 9L, 9L, TIME, TIME);
    }

    private ResourceRequest request(long id, long projectId, long physicalId, long environmentId) {
        return new ResourceRequest(id, TENANT, projectId, "RR-SAME", physicalId, null, null, null,
                null, null, null, null, environmentId, null, null, null,
                9L, 9L, RequestType.INITIAL, "项目隔离测试", RequestStatus.APPROVED,
                0, null, null, null, null, false, 0L, 9L, 9L, TIME, TIME);
    }

    private EnvironmentInstance instance(long id, long projectId, String instanceNo,
                                         long environmentId, long unitId, long physicalId,
                                         long requestId, String machineName, String ipAddress) {
        return new EnvironmentInstance(id, TENANT, projectId, instanceNo, environmentId,
                null, null, null, unitId, null, null, null, null, 1, 1, false,
                physicalId, null, null, requestId, null, null, machineName, ipAddress,
                "architecture.server-type.container", "P8", "开放区", InstanceStatus.ACTIVE,
                BigDecimal.valueOf(2), BigDecimal.valueOf(4), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, null, null, null, null, null,
                false, false, false, FulfillmentMode.MANUAL, null, null, null, null, null,
                0L, 9L, 9L, TIME, TIME);
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
