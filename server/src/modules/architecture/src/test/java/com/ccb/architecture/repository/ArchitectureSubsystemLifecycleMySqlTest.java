package com.ccb.architecture.repository;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class ArchitectureSubsystemLifecycleMySqlTest {
    private static final String DATABASE = "architecture_lifecycle";

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName(DATABASE)
            .withUsername("test")
            .withPassword("test");

    @BeforeEach
    void cleanDatabase() throws Exception {
        configureProjectCollation();
        flyway("93").clean();
        configureProjectCollation();
    }

    @Test
    void migratesFromV92ToV93WithStableGlobalNumbersAndLifecycleSchema() throws Exception {
        assertTrue(flyway("92").migrate().success);
        JdbcTemplate jdbc = jdbc();
        Timestamp first = Timestamp.valueOf("2026-01-01 08:00:00");
        Timestamp second = Timestamp.valueOf("2026-01-02 08:00:00");
        Timestamp third = Timestamp.valueOf("2026-01-03 08:00:00");

        insertLogicals(jdbc, List.of(
                new LogicalRow(101L, 1L, "LEGACY-LOGICAL-1", "逻辑一", "旧逻辑一", false, second),
                new LogicalRow(102L, 1L, "LEGACY-LOGICAL-2", "逻辑二", "旧逻辑二", true, third),
                new LogicalRow(201L, 2L, "LEGACY-LOGICAL-3", "逻辑三", "旧逻辑三", false, first)
        ));
        insertPhysicals(jdbc, physicalRows(1L, 101L, 1_001L, 10, first, true));

        var result = flyway("93").migrate();
        assertTrue(result.success);
        assertEquals(1, result.migrationsExecuted);

        assertEquals("LEGACY-LOGICAL-1", value(jdbc, "SELECT code FROM arch_logical_subsystem WHERE id = 101"));
        assertEquals("LEGACY-PHYSICAL-1010", value(jdbc, "SELECT code FROM arch_physical_subsystem WHERE id = 1010"));
        assertEquals(List.of(1, 2, 3), jdbc.queryForList(
                "SELECT number_sequence FROM arch_logical_subsystem ORDER BY tenant_id, created_at, id", Integer.class));
        assertEquals(3, count(jdbc, "SELECT COUNT(DISTINCT number_sequence) FROM arch_logical_subsystem"));
        assertEquals("ACTIVE", value(jdbc, "SELECT status FROM arch_logical_subsystem WHERE id = 102"));
        assertEquals("1", value(jdbc, "SELECT number_slot FROM arch_physical_subsystem WHERE id = 1001"));
        assertEquals("9", value(jdbc, "SELECT number_slot FROM arch_physical_subsystem WHERE id = 1009"));
        assertEquals("A", value(jdbc, "SELECT number_slot FROM arch_physical_subsystem WHERE id = 1010"));
        assertEquals(1, count(jdbc, "SELECT COUNT(*) FROM arch_physical_subsystem WHERE deleted = 1 AND number_slot = 'A'"));
        assertEquals(4, integerValue(jdbc, "SELECT next_ordinal FROM arch_subsystem_number_namespace WHERE allocation_scope = 0 AND namespace_code = 'LOGICAL'"));
        assertEquals(11, integerValue(jdbc, "SELECT next_ordinal FROM arch_subsystem_number_namespace WHERE allocation_scope = 0 AND namespace_code = 'PHYSICAL:1'"));
        assertEquals(1, integerValue(jdbc, "SELECT next_ordinal FROM arch_subsystem_number_namespace WHERE allocation_scope = 0 AND namespace_code = 'PHYSICAL:2'"));

        try (Connection connection = connection()) {
            assertLifecycleStructure(connection);
        }
        assertLifecycleChecksAndTenantForeignKeys(jdbc);
    }

    @Test
    void migratesThirtyFivePhysicalHistoryRowsAndFailsClosedBeforeDdlForThirtySix() {
        assertTrue(flyway("92").migrate().success);
        JdbcTemplate jdbc = jdbc();
        insertLogicals(jdbc, List.of(new LogicalRow(301L, 1L, "LEGACY-35", "逻辑三五", "三十五容量", false,
                Timestamp.valueOf("2026-02-01 08:00:00"))));
        insertPhysicals(jdbc, physicalRows(1L, 301L, 3_001L, 35, Timestamp.valueOf("2026-02-01 08:00:00"), false));

        assertTrue(flyway("93").migrate().success);
        assertEquals(35, count(jdbc, "SELECT COUNT(*) FROM arch_physical_subsystem WHERE logical_subsystem_id = 301 AND number_slot IS NOT NULL"));
        assertEquals("Z", value(jdbc, "SELECT number_slot FROM arch_physical_subsystem WHERE id = 3035"));

        flyway("93").clean();
        configureProjectCollationUnchecked();
        assertTrue(flyway("92").migrate().success);
        jdbc = jdbc();
        insertLogicals(jdbc, List.of(new LogicalRow(302L, 1L, "LEGACY-36", "逻辑三六", "三十六容量", false,
                Timestamp.valueOf("2026-02-02 08:00:00"))));
        insertPhysicals(jdbc, physicalRows(1L, 302L, 3_101L, 36, Timestamp.valueOf("2026-02-02 08:00:00"), true));

        assertMigrationFailsAtCapacity();
        assertNoPersistentV93Structure(jdbc);
    }

    @Test
    void migratesNineThousandNineHundredNinetyNineLogicalHistoryRowsAndFailsClosedForTenThousand() {
        assertTrue(flyway("92").migrate().success);
        JdbcTemplate jdbc = jdbc();
        insertLogicalRange(jdbc, 4_001L, 1L, 9_999, Timestamp.valueOf("2026-03-01 08:00:00"));

        assertTrue(flyway("93").migrate().success);
        assertEquals(9_999, count(jdbc, "SELECT COUNT(*) FROM arch_logical_subsystem"));
        assertEquals(1, integerValue(jdbc, "SELECT MIN(number_sequence) FROM arch_logical_subsystem"));
        assertEquals(9_999, integerValue(jdbc, "SELECT MAX(number_sequence) FROM arch_logical_subsystem"));
        assertEquals(10_000, integerValue(jdbc, "SELECT next_ordinal FROM arch_subsystem_number_namespace WHERE allocation_scope = 0 AND namespace_code = 'LOGICAL'"));

        flyway("93").clean();
        configureProjectCollationUnchecked();
        assertTrue(flyway("92").migrate().success);
        jdbc = jdbc();
        insertLogicalRange(jdbc, 20_001L, 1L, 10_000, Timestamp.valueOf("2026-03-02 08:00:00"));

        assertMigrationFailsAtCapacity();
        assertNoPersistentV93Structure(jdbc);
    }

    private void assertLifecycleChecksAndTenantForeignKeys(JdbcTemplate jdbc) {
        insertLogicals(jdbc, List.of(new LogicalRow(901L, 1L, "POST-V93-L", "迁移后逻辑", "迁移后逻辑", false,
                Timestamp.valueOf("2026-04-01 08:00:00"))));
        insertPhysicals(jdbc, physicalRows(1L, 901L, 9_001L, 1, Timestamp.valueOf("2026-04-01 08:00:00"), false));

        assertThrows(Exception.class, () -> jdbc.update("UPDATE arch_logical_subsystem SET status = 'BROKEN' WHERE id = 901"));
        assertThrows(Exception.class, () -> jdbc.update("UPDATE arch_physical_subsystem SET number_slot = '0' WHERE id = 9001"));

        jdbc.update("INSERT INTO arch_subsystem_change_application "
                        + "(id,tenant_id,target_kind,action_type,applicant_id,created_by,updated_by) "
                        + "VALUES (9001,1,'LOGICAL','CREATE',1,1,1)");
        assertThrows(Exception.class, () -> jdbc.update("INSERT INTO arch_subsystem_logical_draft "
                        + "(application_id,tenant_id,short_name,name,business_org_id,contact_user_id) "
                        + "VALUES (9001,2,'跨租户草稿','跨租户草稿',1,1)"));
    }

    private void assertLifecycleStructure(Connection connection) throws Exception {
        assertEquals(12, count(connection, "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() "
                + "AND table_name IN ('arch_subsystem_change_application','arch_subsystem_logical_draft','arch_subsystem_physical_draft',"
                + "'arch_subsystem_change_history','arch_subsystem_change_lock','arch_subsystem_value_reservation','arch_subsystem_replacement',"
                + "'arch_subsystem_workflow_round','arch_subsystem_workflow_receipt','arch_subsystem_number_namespace',"
                + "'arch_subsystem_number_recycled','arch_subsystem_number_reservation')"));
        assertColumn(connection, "arch_logical_subsystem", "number_sequence", "YES", null);
        assertColumn(connection, "arch_logical_subsystem", "status", "NO", "ACTIVE");
        assertColumn(connection, "arch_logical_subsystem", "sort_no", "NO", "0");
        assertColumn(connection, "arch_logical_subsystem", "row_version", "NO", "0");
        assertColumn(connection, "arch_physical_subsystem", "number_slot", "YES", null);
        assertColumn(connection, "arch_physical_subsystem", "english_name", "YES", null);
        assertColumn(connection, "arch_physical_subsystem", "status", "NO", "ACTIVE");
        assertColumn(connection, "arch_physical_subsystem", "row_version", "NO", "0");
        assertEquals("number_sequence", indexColumns(connection, "arch_logical_subsystem", "uk_arch_logical_number_sequence"));
        assertEquals("tenant_id,id", indexColumns(connection, "arch_physical_subsystem", "uk_arch_physical_tenant_id"));
        assertEquals("tenant_id,logical_subsystem_id,number_slot", indexColumns(connection, "arch_physical_subsystem", "uk_arch_physical_parent_slot"));
        assertEquals("tenant_id,english_name", indexColumns(connection, "arch_physical_subsystem", "uk_arch_physical_english_name"));
        assertEquals("tenant_id,id", indexColumns(connection, "arch_subsystem_change_application", "uk_arch_subsystem_application_tenant_id"));
        assertEquals("allocation_scope,namespace_code", indexColumns(connection, "arch_subsystem_number_namespace", "PRIMARY"));
        assertEquals("allocation_scope,namespace_code,ordinal", indexColumns(connection, "arch_subsystem_number_recycled", "PRIMARY"));
        assertEquals("tenant_id,application_id,reservation_kind,line_no", indexColumns(connection,
                "arch_subsystem_number_reservation", "uk_arch_subsystem_number_reservation_application_line"));
        assertCheck(connection, "arch_logical_subsystem", "chk_arch_logical_status");
        assertCheck(connection, "arch_physical_subsystem", "chk_arch_physical_number_slot");
        assertCheck(connection, "arch_subsystem_change_application", "chk_arch_subsystem_application_status");
        assertForeignKey(connection, "arch_subsystem_logical_draft", "fk_arch_subsystem_logical_draft_application",
                "tenant_id,application_id", "arch_subsystem_change_application", "tenant_id,id");
        assertForeignKey(connection, "arch_subsystem_number_reservation", "fk_arch_subsystem_number_reservation_application",
                "tenant_id,application_id", "arch_subsystem_change_application", "tenant_id,id");
        assertEquals("utf8mb4_unicode_ci", value(connection, "SELECT table_collation FROM information_schema.tables "
                + "WHERE table_schema = DATABASE() AND table_name = 'arch_subsystem_change_application'"));
    }

    private void assertMigrationFailsAtCapacity() {
        assertThrows(FlywayException.class, () -> flyway("93").migrate());
    }

    private void assertNoPersistentV93Structure(JdbcTemplate jdbc) {
        assertEquals(0, count(jdbc, "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() "
                + "AND table_name = 'arch_logical_subsystem' AND column_name = 'number_sequence'"));
        assertEquals(0, count(jdbc, "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() "
                + "AND table_name = 'arch_subsystem_change_application'"));
        assertEquals(0, count(jdbc, "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '93' AND success = 1"));
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

    private JdbcTemplate jdbc() {
        return new JdbcTemplate(new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()));
    }

    private Connection connection() throws Exception {
        return DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    }

    private void configureProjectCollation() throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("ALTER DATABASE `" + DATABASE + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
    }

    private void configureProjectCollationUnchecked() {
        try {
            configureProjectCollation();
        } catch (Exception exception) {
            throw new IllegalStateException("无法设置测试库排序规则", exception);
        }
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

    private void insertLogicals(JdbcTemplate jdbc, List<LogicalRow> rows) {
        jdbc.batchUpdate("INSERT INTO arch_logical_subsystem "
                        + "(id,tenant_id,code,short_name,name,business_org_id,contact_user_id,deleted,created_by,updated_by,created_at,updated_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                rows.stream().map(row -> new Object[]{
                        row.id(), row.tenantId(), row.code(), row.shortName(), row.name(), 1L, 1L,
                        row.deleted() ? 1 : 0, 1L, 1L, row.createdAt(), row.createdAt()
                }).toList());
    }

    private void insertPhysicals(JdbcTemplate jdbc, List<PhysicalRow> rows) {
        jdbc.batchUpdate("INSERT INTO arch_physical_subsystem "
                        + "(id,tenant_id,code,short_name,name,logical_subsystem_id,responsible_team_org_id,responsible_team_name_snapshot,deleted,created_by,updated_by,created_at,updated_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                rows.stream().map(row -> new Object[]{
                        row.id(), row.tenantId(), row.code(), row.shortName(), row.name(), row.logicalSubsystemId(),
                        1L, "架构团队", row.deleted() ? 1 : 0, 1L, 1L, row.createdAt(), row.createdAt()
                }).toList());
    }

    private void insertLogicalRange(JdbcTemplate jdbc, long firstId, long tenantId, int count, Timestamp createdAt) {
        List<Object[]> rows = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            long id = firstId + index;
            rows.add(new Object[]{id, tenantId, "LEGACY-L-" + id, "逻辑" + id, "批量逻辑" + id,
                    1L, 1L, 0, 1L, 1L, createdAt, createdAt});
        }
        jdbc.batchUpdate("INSERT INTO arch_logical_subsystem "
                        + "(id,tenant_id,code,short_name,name,business_org_id,contact_user_id,deleted,created_by,updated_by,created_at,updated_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)", rows);
    }

    private List<PhysicalRow> physicalRows(long tenantId, long logicalSubsystemId, long firstId, int count,
                                           Timestamp createdAt, boolean deleteLast) {
        List<PhysicalRow> rows = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            long id = firstId + index;
            rows.add(new PhysicalRow(id, tenantId, logicalSubsystemId, "LEGACY-PHYSICAL-" + id,
                    "物理" + id, "批量物理" + id, deleteLast && index == count - 1, createdAt));
        }
        return rows;
    }

    private void assertColumn(Connection connection, String table, String column, String nullable, String defaultValue) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("SELECT IS_NULLABLE, COLUMN_DEFAULT FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?")) {
            statement.setString(1, table);
            statement.setString(2, column);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals(nullable, result.getString("IS_NULLABLE"));
                assertEquals(defaultValue, result.getString("COLUMN_DEFAULT"));
                assertFalse(result.next());
            }
        }
    }

    private String indexColumns(Connection connection, String table, String index) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) AS columns_list "
                + "FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ?")) {
            statement.setString(1, table);
            statement.setString(2, index);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getString("columns_list");
            }
        }
    }

    private void assertCheck(Connection connection, String table, String constraint) throws Exception {
        assertEquals(1, count(connection, "SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema = DATABASE() "
                + "AND table_name = '" + table + "' AND constraint_name = '" + constraint + "' AND constraint_type = 'CHECK'"));
    }

    private void assertForeignKey(Connection connection, String table, String constraint, String columns,
                                  String referencedTable, String referencedColumns) throws Exception {
        String sql = "SELECT GROUP_CONCAT(column_name ORDER BY ordinal_position) AS columns_list, "
                + "GROUP_CONCAT(referenced_column_name ORDER BY ordinal_position) AS referenced_columns_list, "
                + "MAX(referenced_table_name) AS referenced_table "
                + "FROM information_schema.key_column_usage WHERE table_schema = DATABASE() AND table_name = '" + table + "' "
                + "AND constraint_name = '" + constraint + "'";
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            assertEquals(columns, result.getString("columns_list"));
            assertEquals(referencedColumns, result.getString("referenced_columns_list"));
            assertEquals(referencedTable, result.getString("referenced_table"));
        }
    }

    private long count(JdbcTemplate jdbc, String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0L : value;
    }

    private long count(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getLong(1);
        }
    }

    private String value(JdbcTemplate jdbc, String sql) {
        return jdbc.queryForObject(sql, String.class);
    }

    private String value(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getString(1);
        }
    }

    private int integerValue(JdbcTemplate jdbc, String sql) {
        Integer value = jdbc.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }

    private record LogicalRow(long id, long tenantId, String code, String shortName, String name, boolean deleted,
                              Timestamp createdAt) {
    }

    private record PhysicalRow(long id, long tenantId, long logicalSubsystemId, String code, String shortName,
                               String name, boolean deleted, Timestamp createdAt) {
    }
}
