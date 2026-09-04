package com.ccb.datamigration.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
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
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class IssueMigrationMySqlTest {
    private static final AuthUser ADMIN = new AuthUser(1L, 1L, "admin", "", "Administrator", 1L, true);

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("data_migration_issue")
            .withUsername("test")
            .withPassword("test");

    @Test
    void v93DeletesOnlyLegacyIssuesAndV94EnforcesActiveCodeLifecycle() throws Exception {
        prepareIsolatedSchema();

        try (Connection connection = connection()) {
            execute(connection, """
                    INSERT INTO dm_asset (id, tenant_id, project_id, asset_type, asset_code, asset_name, owner_id)
                    VALUES (9101, 1, 100, 'ISSUE', 'OLD-ISSUE', 'Old issue', 1),
                           (9102, 1, 100, 'REPORT', 'KEEP-REPORT', 'Keep report', 1)
                    """);
            execute(connection, """
                    INSERT INTO dm_asset_relation
                        (id, tenant_id, source_asset_id, source_asset_type, target_asset_id, target_asset_type, created_by)
                    VALUES (9201, 1, 9101, 'ISSUE', 9102, 'MEETING', 1)
                    """);
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM dm_asset WHERE asset_type = 'ISSUE'"));
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM dm_asset WHERE asset_type = 'REPORT' AND asset_name = 'Keep report'"));
        }

        assertTrue(flyway("156").migrate().success);
        try (Connection connection = connection()) {
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM dm_asset WHERE asset_type = 'ISSUE'"));
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM dm_asset_relation WHERE source_asset_type = 'ISSUE'"));
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM dm_asset WHERE asset_type = 'REPORT' AND asset_name = 'Keep report'"));
        }

        assertTrue(flyway("157").migrate().success);
        try (Connection connection = connection()) {
            assertEquals("STORED GENERATED", value(connection, """
                    SELECT EXTRA FROM information_schema.columns
                    WHERE table_schema = DATABASE() AND table_name = 'dm_issue' AND column_name = 'active_issue_code'
                    """));
            execute(connection, issueInsert(9301, "ACTIVE-1"));
            execute(connection, "UPDATE dm_issue SET deleted = 1 WHERE id = 9301");
            execute(connection, issueInsert(9302, "ACTIVE-1"));
            execute(connection, "UPDATE dm_issue SET deleted = 1 WHERE id = 9302");
            execute(connection, issueInsert(9303, "ACTIVE-1"));
            assertEquals(2, count(connection, "SELECT COUNT(*) FROM dm_issue WHERE issue_code = 'ACTIVE-1' AND deleted = 1"));
            assertThrows(SQLException.class, () -> execute(connection, "UPDATE dm_issue SET deleted = 0 WHERE id = 9301"));
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM dm_issue WHERE issue_code = 'ACTIVE-1' AND deleted = 0"));
        }
    }

    @Test
    void restoreConflictRollsBackEarlierRowsAndAudits() throws Exception {
        migrateFreshTo94();
        try (Connection connection = connection()) {
            execute(connection, issueInsert(9401, "ROLLBACK-1", true));
            execute(connection, issueInsert(9402, "ROLLBACK-2", true));
        }

        RaceInjectingJdbcTemplate jdbc = new RaceInjectingJdbcTemplate(dataSource());
        IssueService service = new IssueService(jdbc, new DataMigrationPermissionService(jdbc, StubProjectAccess.allow()));
        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));

        BusinessException error = assertThrows(BusinessException.class,
                () -> transaction.executeWithoutResult(status -> service.restore(List.of(9401L, 9402L), ADMIN)));

        assertEquals(ErrorCode.CONFLICT, error.code());
        try (Connection connection = connection()) {
            assertEquals(2, count(connection, "SELECT COUNT(*) FROM dm_issue WHERE id IN (9401, 9402) AND deleted = 1"));
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM dm_issue WHERE issue_code = 'ROLLBACK-2' AND deleted = 0"));
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM dm_operation_log WHERE operation_code = 'ISSUE_RESTORE' AND entity_id IN (9401, 9402)"));
        }
    }

    @Test
    void invalidRelationRollsBackIssueUpdate() throws Exception {
        migrateFreshTo94();
        try (Connection connection = connection()) {
            execute(connection, issueInsert(9501, "RELATION-ROLLBACK", false));
        }

        RaceInjectingJdbcTemplate jdbc = new RaceInjectingJdbcTemplate(dataSource());
        IssueService service = new IssueService(jdbc, new DataMigrationPermissionService(jdbc, StubProjectAccess.allow()));
        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("projectId", 100L);
        body.put("issueCode", "RELATION-ROLLBACK");
        body.put("issueName", "Changed name");
        body.put("relatedTables", List.of(999999L));

        BusinessException error = assertThrows(BusinessException.class,
                () -> transaction.executeWithoutResult(status -> service.update(9501L, body, ADMIN)));

        assertEquals(ErrorCode.BAD_REQUEST, error.code());
        try (Connection connection = connection()) {
            assertEquals("Issue", value(connection, "SELECT issue_name FROM dm_issue WHERE id = 9501"));
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM dm_operation_log WHERE operation_code = 'ISSUE_UPDATE' AND entity_id = 9501"));
        }
    }

    private Flyway flyway(String target) {
        return Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("filesystem:" + migrationDirectory())
                .placeholders(java.util.Map.of("bootstrap_admin_password_hash", "test-hash"))
                .baselineOnMigrate(true)
                .baselineVersion(MigrationVersion.fromVersion("155"))
                .target(MigrationVersion.fromVersion(target))
                .cleanDisabled(false)
                .load();
    }

    private Connection connection() throws Exception {
        return DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    }

    private DriverManagerDataSource dataSource() {
        return new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    }

    private void migrateFreshTo94() {
        prepareIsolatedSchema();
        assertTrue(flyway("157").migrate().success);
    }

    private void prepareIsolatedSchema() {
        flyway("122").clean();
        try (Connection connection = connection()) {
            execute(connection, """
                    CREATE TABLE dm_asset (
                        id BIGINT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        project_id BIGINT NOT NULL,
                        asset_type VARCHAR(32) NOT NULL,
                        asset_code VARCHAR(96) NOT NULL,
                        asset_name VARCHAR(255) NOT NULL,
                        structured_data JSON NULL,
                        owner_id BIGINT NOT NULL,
                        deleted TINYINT NOT NULL DEFAULT 0,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        created_by BIGINT NULL
                    )
                    """);
            execute(connection, """
                    CREATE TABLE dm_asset_relation (
                        id BIGINT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        source_asset_id BIGINT NOT NULL,
                        source_asset_type VARCHAR(32) NOT NULL,
                        target_asset_id BIGINT NOT NULL,
                        target_asset_type VARCHAR(32) NOT NULL,
                        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        created_by BIGINT NOT NULL,
                        INDEX idx_asset_relation_source (tenant_id, source_asset_id, source_asset_type),
                        INDEX idx_asset_relation_target (tenant_id, target_asset_id, target_asset_type),
                        UNIQUE KEY uk_asset_relation (tenant_id, source_asset_id, source_asset_type, target_asset_id, target_asset_type)
                    )
                    """);
            execute(connection, """
                    CREATE TABLE dm_operation_log (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        tenant_id BIGINT NOT NULL,
                        project_id BIGINT NOT NULL DEFAULT 0,
                        actor_id BIGINT NOT NULL,
                        operation_code VARCHAR(64) NOT NULL,
                        entity_type VARCHAR(64) NOT NULL,
                        entity_id BIGINT NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            execute(connection, """
                    CREATE TABLE pm_project (
                        id BIGINT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        project_name VARCHAR(128) NOT NULL,
                        deleted TINYINT NOT NULL DEFAULT 0
                    )
                    """);
            execute(connection, """
                    CREATE TABLE dm_meeting (
                        meeting_id BIGINT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        project_id BIGINT NOT NULL,
                        meeting_title VARCHAR(500) NOT NULL,
                        deleted TINYINT NOT NULL DEFAULT 0
                    )
                    """);
            execute(connection, """
                    CREATE TABLE arch_physical_subsystem (
                        id BIGINT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        code VARCHAR(160) NOT NULL,
                        short_name VARCHAR(160),
                        name VARCHAR(160),
                        deleted TINYINT NOT NULL DEFAULT 0
                    )
                    """);
            execute(connection, """
                    CREATE TABLE sys_user (
                        id BIGINT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        display_name VARCHAR(128) NOT NULL
                    )
                    """);
            execute(connection, """
                    CREATE TABLE dm_target_table (
                        id BIGINT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        project_id BIGINT,
                        table_code BIGINT,
                        table_name_en VARCHAR(128),
                        deleted TINYINT NOT NULL DEFAULT 0
                    )
                    """);
            execute(connection, """
                    CREATE TABLE dm_target_table_field (
                        id BIGINT PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        field_code BIGINT,
                        field_name_en VARCHAR(128),
                        table_id BIGINT,
                        deleted TINYINT NOT NULL DEFAULT 0
                    )
                    """);
            execute(connection, """
                    CREATE TABLE dm_issue_relation (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        tenant_id BIGINT NOT NULL,
                        issue_id BIGINT NOT NULL,
                        related_type VARCHAR(32) NOT NULL,
                        related_id BIGINT NOT NULL,
                        created_by BIGINT NOT NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        UNIQUE KEY uk_dm_issue_relation (tenant_id, issue_id, related_type, related_id)
                    )
                    """);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to prepare isolated V92 baseline", ex);
        }
    }

    private String migrationDirectory() {
        Path cursor = Path.of("").toAbsolutePath();
        while (cursor != null) {
            Path candidate = cursor.resolve("server/src/platform/infrastructure/src/main/resources/db/migration");
            if (Files.isDirectory(candidate)) return candidate.toString();
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("Flyway migration directory not found");
    }

    private String issueInsert(long id, String code) {
        return issueInsert(id, code, false);
    }

    private String issueInsert(long id, String code, boolean deleted) {
        return "INSERT INTO dm_issue (id, tenant_id, project_id, issue_code, issue_name, owner_id, deleted) VALUES ("
                + id + ", 1, 100, '" + code + "', 'Issue', 1, " + (deleted ? 1 : 0) + ")";
    }

    private long count(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getLong(1);
        }
    }

    private String value(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getString(1);
        }
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private final class RaceInjectingJdbcTemplate extends JdbcTemplate {
        private int restoreUpdates;

        private RaceInjectingJdbcTemplate(DriverManagerDataSource dataSource) {
            super(dataSource);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            if (sql.contains("FROM sys_user_role") || sql.contains("FROM pm_project")) {
                return (T) Integer.valueOf(1);
            }
            return super.queryForObject(sql, requiredType, args);
        }

        @Override
        public int update(String sql, Object... args) {
            if (sql.startsWith("UPDATE dm_issue SET deleted = 0") && ++restoreUpdates == 2) {
                super.update(issueInsert(9499, "ROLLBACK-2"));
            }
            return super.update(sql, args);
        }
    }
}
