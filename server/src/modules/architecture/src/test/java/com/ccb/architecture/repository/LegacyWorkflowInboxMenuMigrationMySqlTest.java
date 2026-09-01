package com.ccb.architecture.repository;

import org.flywaydb.core.Flyway;
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
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class LegacyWorkflowInboxMenuMigrationMySqlTest {
    private static final String DATABASE = "legacy_workflow_inbox";

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName(DATABASE)
            .withUsername("test")
            .withPassword("test");

    @BeforeEach
    void cleanDatabase() throws Exception {
        flyway("123").clean();
        configureProjectCollation();
    }

    @Test
    void removesReintroducedInboxMenuWithoutRevokingWorkflowAccess() {
        assertTrue(flyway("122").migrate().success);
        JdbcTemplate jdbc = jdbc();

        assertEquals(1, count(jdbc, "SELECT COUNT(*) FROM sys_menu WHERE id = 202 AND deleted = 0"));

        jdbc.update("INSERT INTO sys_role_menu (role_id,menu_id,tenant_id) VALUES (9001,200,1),(9001,202,1)");
        jdbc.update("INSERT INTO sys_role_menu (role_id,menu_id,tenant_id) VALUES (9002,200,1),(9002,201,1),(9002,202,1)");
        jdbc.update("INSERT INTO sys_role_permission (role_id,permission_id,tenant_id) VALUES (9001,2022,1),(9001,2023,1),(9001,2024,1)");

        var result = flyway("123").migrate();
        assertTrue(result.success);
        assertEquals(1, result.migrationsExecuted);

        assertRemovedLegacyInbox(jdbc);
        assertWorkflowAccessPreserved(jdbc, 110L);
        assertWorkflowAccessPreserved(jdbc, 113L);
        assertWorkflowAccessPreserved(jdbc, 114L);
        assertEquals(0, count(jdbc, "SELECT COUNT(*) FROM sys_role_menu WHERE role_id = 9001 AND menu_id = 200"));
        assertEquals(3, count(jdbc, "SELECT COUNT(*) FROM sys_role_permission WHERE role_id = 9001 AND permission_id IN (2002,2003,2004)"));
        assertEquals(1, count(jdbc, "SELECT COUNT(*) FROM sys_role_menu WHERE role_id = 9002 AND menu_id = 200"));
        assertEquals(1, count(jdbc, "SELECT COUNT(*) FROM sys_role_menu WHERE role_id = 9002 AND menu_id = 201"));
        assertEquals(1, count(jdbc, "SELECT COUNT(*) FROM sys_role_menu WHERE role_id = 1 AND menu_id = 200"));
        assertTrue(count(jdbc, "SELECT COUNT(*) FROM sys_role_menu WHERE role_id = 1 AND menu_id IN (201,203)") > 0);
    }

    @Test
    void keepsLegacyInboxRemovedWhenMigratingAnEmptyDatabase() {
        var result = flyway("123").migrate();
        assertTrue(result.success);

        JdbcTemplate jdbc = jdbc();
        assertRemovedLegacyInbox(jdbc);
        assertWorkflowAccessPreserved(jdbc, 110L);
        assertWorkflowAccessPreserved(jdbc, 113L);
        assertWorkflowAccessPreserved(jdbc, 114L);
        assertEquals(1, count(jdbc, "SELECT COUNT(*) FROM sys_role_menu WHERE role_id = 1 AND menu_id = 200"));
    }

    private void assertRemovedLegacyInbox(JdbcTemplate jdbc) {
        assertEquals(0, count(jdbc, "SELECT COUNT(*) FROM sys_menu WHERE id = 202"));
        assertEquals(0, count(jdbc, "SELECT COUNT(*) FROM sys_menu_permission WHERE menu_id = 202"));
        assertEquals(0, count(jdbc, "SELECT COUNT(*) FROM sys_role_menu WHERE menu_id = 202"));
        assertEquals(0, count(jdbc, "SELECT COUNT(*) FROM sys_role_permission WHERE permission_id IN (2021,2022,2023,2024)"));
    }

    private void assertWorkflowAccessPreserved(JdbcTemplate jdbc, long roleId) {
        assertEquals(1, count(jdbc, "SELECT COUNT(*) FROM sys_role_permission WHERE role_id = " + roleId + " AND permission_id = 2001"));
        assertEquals(0, count(jdbc, "SELECT COUNT(*) FROM sys_role_menu WHERE role_id = " + roleId + " AND menu_id = 200"));
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

    private void configureProjectCollation() throws Exception {
        try (Connection connection = MYSQL.createConnection(""); Statement statement = connection.createStatement()) {
            statement.execute("ALTER DATABASE `" + DATABASE + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
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

    private long count(JdbcTemplate jdbc, String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }
}
