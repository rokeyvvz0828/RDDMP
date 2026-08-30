package com.ccb.architecture.network.persistence;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * REQ-20260828-055：网络访问判定与生命周期的真实 MySQL 8.4 / Flyway 迁移检查。
 */
@Testcontainers
class NetworkAccessMigrationMySqlTest {
    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("network_access_lifecycle")
            .withUsername("test")
            .withPassword("test");

    @BeforeEach
    void cleanDatabase() {
        flyway("105").clean();
    }

    @Test
    void migratesToV105WithDecisionLifecycleTablesAndSeedData() throws Exception {
        assertTrue(flyway("105").migrate().success);

        try (Connection connection = DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
            assertEquals(4, count(connection, "SELECT COUNT(*) FROM information_schema.tables "
                    + "WHERE table_schema = DATABASE() AND table_name IN ("
                    + "'arch_network_access_application_history',"
                    + "'arch_network_access_workflow_round',"
                    + "'arch_network_access_workflow_receipt',"
                    + "'arch_network_access_exemption_rule')"));
            assertEquals(10, count(connection, "SELECT COUNT(*) FROM information_schema.columns "
                    + "WHERE table_schema = DATABASE() AND table_name = 'arch_network_access_application' "
                    + "AND column_name IN ('action_type','target_relation_id','validity_type',"
                    + "'current_business_round','current_workflow_definition_id','current_workflow_version_id',"
                    + "'current_workflow_instance_id','current_payload_digest','cancellation_requested','status')"));
            assertEquals(6, count(connection, "SELECT COUNT(*) FROM information_schema.columns "
                    + "WHERE table_schema = DATABASE() AND table_name = 'arch_network_access_relation' "
                    + "AND column_name IN ('replaces_relation_id','replaced_by_relation_id',"
                    + "'closed_application_id','validity_type','close_type','close_reason')"));
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM arch_network_access_exemption_rule "
                    + "WHERE tenant_id = 1 AND rule_code = 'EXEMPT_APP_SELF_HTTPS' "
                    + "AND protocol = 'HTTPS' AND ports = '443' AND validity_type = 'LONG_TERM'"));
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM wf_definition "
                    + "WHERE tenant_id = 1 AND code = 'architecture.network-access-application' "
                    + "AND status = 'DRAFT'"));
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM wf_version "
                    + "WHERE tenant_id = 1 AND definition_id = 900000000000104 AND status = 'DRAFT'"));

            execute(connection, "INSERT INTO arch_network_access_application "
                    + "(id, tenant_id, application_no, applicant_id, source_kind, target_kind, protocol, "
                    + "ports, purpose, valid_from, valid_until, status, created_by, updated_by) "
                    + "VALUES (910001, 1, 'NAA910001', 1, 'MANAGED', 'EXTERNAL', 'TCP', '443', "
                    + "'退回后修改', '2026-08-28 00:00:00', NULL, 'RETURNED', 1, 1)");
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM arch_network_access_application "
                    + "WHERE id = 910001 AND status = 'RETURNED' AND validity_type = 'LONG_TERM'"));
            assertThrows(Exception.class, () -> execute(connection, "INSERT INTO arch_network_access_application "
                    + "(id, tenant_id, application_no, applicant_id, source_kind, target_kind, protocol, "
                    + "ports, purpose, valid_from, valid_until, validity_type, status, created_by, updated_by) "
                    + "VALUES (910002, 1, 'NAA910002', 1, 'MANAGED', 'EXTERNAL', 'TCP', '443', "
                    + "'非法长期', '2026-08-28 00:00:00', '2026-08-29 00:00:00', "
                    + "'LONG_TERM', 'DRAFT', 1, 1)"));
        }
    }

    @Test
    void migratesLegacyAccessRowsWithMissingValidFromToV105() throws Exception {
        assertTrue(flyway("103").migrate().success);

        try (Connection connection = DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
            execute(connection, "INSERT INTO arch_network_access_application "
                    + "(id, tenant_id, application_no, applicant_id, source_kind, target_kind, protocol, "
                    + "ports, purpose, valid_from, valid_until, status, created_by, updated_by) "
                    + "VALUES (920001, 1, 'NAA920001', 1, 'MANAGED', 'EXTERNAL', 'TCP', '443', "
                    + "'历史有限期缺开始', NULL, '2026-08-28 00:00:00', 'APPROVED', 1, 1)");
            execute(connection, "INSERT INTO arch_network_access_relation "
                    + "(id, tenant_id, relation_no, application_id, source_kind, target_kind, protocol, "
                    + "ports, purpose, valid_from, valid_until, status, created_by, updated_by) "
                    + "VALUES (920101, 1, 'NAR920101', 920001, 'MANAGED', 'EXTERNAL', 'TCP', '443', "
                    + "'历史有限期缺开始', NULL, '2026-08-28 00:00:00', 'ACTIVE', 1, 1)");
            execute(connection, "INSERT INTO arch_network_access_application "
                    + "(id, tenant_id, application_no, applicant_id, source_kind, target_kind, protocol, "
                    + "ports, purpose, valid_from, valid_until, status, created_by, updated_by) "
                    + "VALUES (920002, 1, 'NAA920002', 1, 'MANAGED', 'EXTERNAL', 'TCP', '8443', "
                    + "'历史长期缺开始', NULL, NULL, 'APPROVED', 1, 1)");
            execute(connection, "INSERT INTO arch_network_access_relation "
                    + "(id, tenant_id, relation_no, application_id, source_kind, target_kind, protocol, "
                    + "ports, purpose, valid_from, valid_until, status, created_by, updated_by) "
                    + "VALUES (920102, 1, 'NAR920102', 920002, 'MANAGED', 'EXTERNAL', 'TCP', '8443', "
                    + "'历史长期缺开始', NULL, NULL, 'ACTIVE', 1, 1)");
        }

        assertTrue(flyway("105").migrate().success);

        try (Connection connection = DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM arch_network_access_application "
                    + "WHERE id = 920001 AND validity_type = 'LIMITED' "
                    + "AND valid_from = '2026-08-27 23:59:59' AND valid_until = '2026-08-28 00:00:00'"));
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM arch_network_access_relation "
                    + "WHERE id = 920101 AND validity_type = 'LIMITED' "
                    + "AND valid_from = '2026-08-27 23:59:59' AND valid_until = '2026-08-28 00:00:00'"));
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM arch_network_access_application "
                    + "WHERE id = 920002 AND validity_type = 'LONG_TERM' "
                    + "AND valid_from IS NOT NULL AND valid_until IS NULL"));
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM arch_network_access_relation "
                    + "WHERE id = 920102 AND validity_type = 'LONG_TERM' "
                    + "AND valid_from IS NOT NULL AND valid_until IS NULL"));
        }
    }

    private Flyway flyway(String target) {
        return Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("filesystem:" + migrationDirectory())
                .placeholders(java.util.Map.of("bootstrap_admin_password_hash", "test-hash"))
                .target(MigrationVersion.fromVersion(target))
                .cleanDisabled(false)
                .load();
    }

    private String migrationDirectory() {
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

    private long count(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(sql)) {
            assertTrue(rs.next());
            return rs.getLong(1);
        }
    }

    private void execute(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
