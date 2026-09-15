package com.ccb.architecture.repository;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class DefaultProjectInitializationMigrationMySqlTest {
    private static final String PATCH = "V206_20260915143000__ensure_default_project.sql";
    private static final String VERSION = "206.20260915143000";
    @TempDir
    Path oldMigrations;
    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("default_project_migration").withUsername("test").withPassword("test")
            // These disposable databases test migration semantics, not crash durability.
            .withTmpFs(java.util.Map.of("/var/lib/mysql", "rw"))
            .withCommand("--innodb-flush-log-at-trx-commit=2", "--sync-binlog=0");

    @BeforeEach
    void reset() throws Exception {
        flyway(migrations(), "latest", true).clean();
        execute("ALTER DATABASE default_project_migration CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
    }

    @Test
    void emptyDatabaseMigratesToLatestWithoutManualSeeding() throws Exception {
        assertTrue(flyway(migrations(), "latest", false).migrate().success);
        assertEquals(1, count("SELECT COUNT(*) FROM pm_project WHERE tenant_id=1 AND project_code='RDDMP-PLATFORM' AND deleted=0"));
        assertEquals(0, count("SELECT COUNT(*) FROM flyway_schema_history WHERE success=0"));
        assertEquals(1, count("SELECT COUNT(*) FROM pm_project_role r JOIN pm_project_member_role mr ON mr.tenant_id=r.tenant_id AND mr.role_id=r.id JOIN pm_project_member m ON m.tenant_id=mr.tenant_id AND m.id=mr.member_id JOIN pm_project p ON p.id=m.project_id AND p.tenant_id=m.tenant_id WHERE p.project_code='RDDMP-PLATFORM' AND r.project_id=p.id AND r.role_code='PM' AND m.user_id=p.owner_id AND m.status=1 AND m.deleted=0"));
        assertEquals(7, count("SELECT COUNT(*) FROM pm_project_stage s JOIN pm_project p ON p.tenant_id=s.tenant_id AND p.id=s.project_id WHERE p.project_code='RDDMP-PLATFORM'"));
        assertTrue(count("SELECT COUNT(*) FROM pm_project_role_permission rp JOIN pm_project p ON p.tenant_id=rp.tenant_id AND p.id=rp.project_id WHERE p.project_code='RDDMP-PLATFORM'") > 0);
        assertEquals(0, count("SELECT COUNT(*) FROM arch_network_zone z LEFT JOIN pm_project p ON p.tenant_id=z.tenant_id AND p.id=z.project_id WHERE p.id IS NULL"));
        assertEquals(0, flyway(migrations(), "latest", false).migrate().migrationsExecuted);
    }

    @Test
    void upgradesV208WithLegacyDataAndNoDefault() throws Exception {
        assertTrue(flyway(legacy(), "208", false).migrate().success);
        long zones = count("SELECT COUNT(*) FROM arch_network_zone");
        assertTrue(zones > 0, "Published migrations must provide the regression fixture");
        assertTrue(flyway(migrations(), "latest", true).migrate().success);
        assertEquals(zones, count("SELECT COUNT(*) FROM arch_network_zone"));
        assertEquals(zones, count("SELECT COUNT(*) FROM arch_network_zone z JOIN pm_project p ON p.id=z.project_id AND p.tenant_id=z.tenant_id WHERE p.project_code='RDDMP-PLATFORM'"));
    }

    @Test
    void preservesAnExistingDefaultProjectAndItsMembership() throws Exception {
        assertTrue(flyway(legacy(), "206", false).migrate().success);
        seedProject(101, 1, 0);
        execute("INSERT INTO pm_project_member (id,tenant_id,project_id,user_id,status) VALUES (801,1,101,1,0)");
        assertTrue(flyway(migrations(), "209", false).migrate().success);
        assertEquals(1, count("SELECT COUNT(*) FROM pm_project WHERE id=101 AND project_name='Existing project' AND owner_id=1"));
        assertEquals(1, count("SELECT COUNT(*) FROM pm_project_member WHERE id=801 AND status=0"));
        assertEquals(0, count("SELECT COUNT(*) FROM pm_project_role WHERE project_id=101"));
        assertEquals(0, count("SELECT COUNT(*) FROM arch_network_zone WHERE project_id<>101"));
    }

    @Test
    void alreadyCurrentDatabaseGetsOnlyNoOpBackfill() throws Exception {
        Path old = legacy();
        assertTrue(flyway(old, "206", false).migrate().success);
        seedProject(101, 1, 0);
        assertTrue(flyway(old, "latest", false).migrate().success);
        execute("UPDATE pm_project SET deleted=1 WHERE id=101");
        long projects = count("SELECT COUNT(*) FROM pm_project");
        long zones = count("SELECT COUNT(*) FROM arch_network_zone WHERE project_id=101");
        assertEquals(1, flyway(migrations(), "latest", true).migrate().migrationsExecuted);
        assertEquals(projects, count("SELECT COUNT(*) FROM pm_project"));
        assertEquals(1, count("SELECT deleted FROM pm_project WHERE id=101"));
        assertEquals(zones, count("SELECT COUNT(*) FROM arch_network_zone WHERE project_id=101"));
        assertEquals(0, count("SELECT COUNT(*) FROM pm_project_member WHERE project_id=101"));
        assertEquals(0, flyway(migrations(), "latest", true).migrate().migrationsExecuted);
    }

    @Test
    void deletedDefaultFailsBeforePersistentWrites() throws Exception {
        assertTrue(flyway(legacy(), "206", false).migrate().success);
        seedProject(101, 1, 1);
        long projects = count("SELECT COUNT(*) FROM pm_project");
        var error = assertThrows(FlywayException.class, () -> flyway(migrations(), VERSION, false).migrate());
        assertTrue(error.getMessage().contains("deleted default project"));
        assertEquals(projects, count("SELECT COUNT(*) FROM pm_project"));
        assertEquals(1, count("SELECT deleted FROM pm_project WHERE id=101"));
        assertEquals(0, count("SELECT COUNT(*) FROM pm_project_member WHERE project_id=101"));
    }

    @Test
    void missingAdminFailsBeforeAnyTenantIsCreated() throws Exception {
        assertTrue(flyway(legacy(), "206", false).migrate().success);
        seedWorkOrder(2);
        long projects = count("SELECT COUNT(*) FROM pm_project");
        var error = assertThrows(FlywayException.class, () -> flyway(migrations(), VERSION, false).migrate());
        assertTrue(error.getMessage().contains("active same-tenant admin"));
        assertEquals(projects, count("SELECT COUNT(*) FROM pm_project"));
        assertEquals(0, count("SELECT COUNT(*) FROM pm_project WHERE project_code='RDDMP-PLATFORM'"));
    }

    @Test
    void initializesEachAffectedTenantWithItsOwnAdminAndUnusedIds() throws Exception {
        assertTrue(flyway(legacy(), "206", false).migrate().success);
        execute("INSERT INTO sys_user (id,tenant_id,username,password_hash,display_name) VALUES (42,2,'admin','test-hash','Tenant two admin')");
        seedWorkOrder(2);
        execute("INSERT INTO pm_project (id,tenant_id,project_code,project_name,owner_id,created_by) VALUES (900000,1,'OTHER','Other project',1,1)");
        assertTrue(flyway(migrations(), "209", false).migrate().success);
        assertEquals(2, count("SELECT COUNT(*) FROM pm_project WHERE project_code='RDDMP-PLATFORM' AND id>900000"));
        assertEquals(1, count("SELECT COUNT(*) FROM pm_project WHERE tenant_id=2 AND project_code='RDDMP-PLATFORM' AND owner_id=42 AND created_by=42"));
        assertEquals(1, count("SELECT COUNT(*) FROM arch_network_work_order w JOIN pm_project p ON p.id=w.project_id AND p.tenant_id=w.tenant_id WHERE w.id=777 AND p.tenant_id=2"));
        assertEquals(1, count("SELECT COUNT(*) FROM pm_project_member WHERE tenant_id=2 AND user_id=42"));
        assertEquals(0, count("SELECT COUNT(*) FROM pm_project_member WHERE tenant_id=2 AND user_id=1"));
    }

    @Test
    void documentedRepairRecoversFailedV209Guard() throws Exception {
        Path old = legacy();
        assertTrue(flyway(old, "208", false).migrate().success);
        assertThrows(FlywayException.class, () -> flyway(old, "209", false).migrate());
        assertEquals(1, count("SELECT COUNT(*) FROM flyway_schema_history WHERE version='209' AND success=0"));
        assertEquals(0, count("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='arch_network_work_order' AND column_name='project_id'"));
        // The runbook limits repair to the original locations after confirming guard-only failure.
        flyway(old, "209", false).repair();
        assertEquals(1, flyway(migrations(), VERSION, true).migrate().migrationsExecuted);
        assertEquals(0, count("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='arch_network_work_order' AND column_name='project_id'"));
        assertTrue(flyway(migrations(), "latest", false).migrate().success);
        assertEquals(1, count("SELECT COUNT(*) FROM flyway_schema_history WHERE version='209' AND success=1"));
        assertEquals(0, count("SELECT COUNT(*) FROM flyway_schema_history WHERE success=0"));
    }

    @Test
    void insertionFailureRollsBackProjectRoleAndMemberAndCanRetry() throws Exception {
        assertTrue(flyway(legacy(), "206", false).migrate().success);
        long projects = count("SELECT COUNT(*) FROM pm_project");
        long roles = count("SELECT COUNT(*) FROM pm_project_role");
        long members = count("SELECT COUNT(*) FROM pm_project_member");
        long assignments = count("SELECT COUNT(*) FROM pm_project_member_role");
        long stages = count("SELECT COUNT(*) FROM pm_project_stage");
        execute("ALTER TABLE pm_project_stage ADD CONSTRAINT fail_default_stage CHECK (stage_code <> 'PLAN_TEST_ACCEPTANCE')");
        var error = assertThrows(FlywayException.class, () -> flyway(migrations(), VERSION, false).migrate());
        assertTrue(error.getMessage().contains("fail_default_stage"));
        assertEquals(projects, count("SELECT COUNT(*) FROM pm_project"));
        assertEquals(roles, count("SELECT COUNT(*) FROM pm_project_role"));
        assertEquals(members, count("SELECT COUNT(*) FROM pm_project_member"));
        assertEquals(assignments, count("SELECT COUNT(*) FROM pm_project_member_role"));
        assertEquals(stages, count("SELECT COUNT(*) FROM pm_project_stage"));
        execute("ALTER TABLE pm_project_stage DROP CHECK fail_default_stage");
        flyway(migrations(), VERSION, false).repair();
        assertTrue(flyway(migrations(), VERSION, false).migrate().success);
        assertEquals(projects + 1, count("SELECT COUNT(*) FROM pm_project"));
        assertEquals(roles + 1, count("SELECT COUNT(*) FROM pm_project_role"));
        assertEquals(members + 1, count("SELECT COUNT(*) FROM pm_project_member"));
        assertEquals(stages + 7, count("SELECT COUNT(*) FROM pm_project_stage"));
    }

    private Path legacy() throws Exception {
        try (var paths = Files.list(migrations())) {
            for (Path path : paths.filter(p -> !p.getFileName().toString().equals(PATCH)).toList()) {
                Files.copy(path, oldMigrations.resolve(path.getFileName()));
            }
        }
        return oldMigrations;
    }

    private void seedProject(long id, long tenant, int deleted) throws Exception {
        execute("INSERT INTO pm_project (id,tenant_id,project_code,project_name,owner_id,created_by,deleted) VALUES ("
                + id + "," + tenant + ",'RDDMP-PLATFORM','Existing project',1,1," + deleted + ")");
    }

    private void seedWorkOrder(long tenant) throws Exception {
        execute("INSERT INTO arch_network_work_order (id,tenant_id,kind,action_type,subject,applicant_id,status,business_payload,created_by,updated_by) VALUES (777,"
                + tenant + ",'DNS','ADD','legacy.example.test',42,'DRAFT',JSON_OBJECT(),42,42)");
    }

    private void execute(String sql) throws Exception {
        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private Flyway flyway(Path location, String target, boolean outOfOrder) {
        return Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("filesystem:" + location).target(target).outOfOrder(outOfOrder)
                .placeholders(java.util.Map.of("bootstrap_admin_password_hash", "test-hash"))
                .cleanDisabled(false).load();
    }

    private Path migrations() {
        Path cursor = Path.of("").toAbsolutePath();
        while (cursor != null) {
            Path candidate = cursor.resolve("server/src/platform/infrastructure/src/main/resources/db/migration");
            if (Files.isDirectory(candidate)) return candidate;
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("Migration directory not found");
    }

    private long count(String sql) throws Exception {
        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getLong(1);
        }
    }
}
