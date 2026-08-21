package com.ccb.architecture.repository;

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
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class EmptyDatabaseMigrationBaselineMySqlTest {
    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("migration_baseline")
            .withUsername("test")
            .withPassword("test");

    @BeforeEach
    void cleanDatabase() {
        flyway("34").clean();
    }

    @Test
    void migratesEmptyDatabaseThroughPublishedV34Baseline() throws Exception {
        var result = flyway("34").migrate();
        assertTrue(result.success);
        assertEquals(34, result.migrationsExecuted);

        try (Connection connection = connection()) {
            assertEquals(2, count(connection, "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name IN ('FLW_EV_DATABASECHANGELOG','FLW_EV_DATABASECHANGELOGLOCK')"));
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM FLW_EV_DATABASECHANGELOGLOCK WHERE id = 1 AND locked = 0"));
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM FLW_EV_DATABASECHANGELOG"));
            assertEquals(34, count(connection, "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1"));
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM flyway_schema_history WHERE script LIKE '%ensure_flowable_event_registry_metadata%'"));
        }
    }

    @Test
    void keepsCallbackIdempotentWhenDatabaseAlreadyPassedV24() throws Exception {
        assertTrue(flyway("24").migrate().success);
        var repeated = flyway("24").migrate();
        assertTrue(repeated.success);
        assertEquals(0, repeated.migrationsExecuted);

        try (Connection connection = connection()) {
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM FLW_EV_DATABASECHANGELOGLOCK WHERE id = 1"));
            assertEquals(24, count(connection, "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1"));
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM flyway_schema_history WHERE script LIKE '%ensure_flowable_event_registry_metadata%'"));
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

    private Connection connection() throws Exception {
        return DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
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
}
