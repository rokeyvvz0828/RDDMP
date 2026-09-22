package com.ccb.system.project;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectReleaseCalendarMigrationTest {
    private static final String MIGRATION = "db/migration/V214__project_release_calendar.sql";
    private static final String RANGE_MIGRATION = "db/migration/V218__project_release_calendar_date_range.sql";

    @Test
    void createsAnIndependentProjectCalendarWithMonthLookupAndAuditFields() throws IOException {
        String sql = migrationSql(MIGRATION);

        assertTrue(sql.contains("CREATE TABLE pm_project_release_calendar"));
        assertTrue(sql.contains("tenant_id BIGINT NOT NULL"));
        assertTrue(sql.contains("project_id BIGINT NOT NULL"));
        assertTrue(sql.contains("release_date DATE NOT NULL"));
        assertTrue(sql.contains("row_version BIGINT NOT NULL DEFAULT 1"));
        assertTrue(sql.contains("FOREIGN KEY (project_id) REFERENCES pm_project(id)"));
        assertTrue(sql.contains("idx_pm_project_release_calendar_month (tenant_id, project_id, release_date, deleted)"));
        assertFalse(sql.contains("DROP TABLE"));
        assertFalse(sql.contains("DELETE FROM pm_project"));
    }

    @Test
    void addsAndBackfillsInclusiveReleaseDateRange() throws IOException {
        String sql = migrationSql(RANGE_MIGRATION);

        assertTrue(sql.contains("ADD COLUMN release_start_date DATE NULL"));
        assertTrue(sql.contains("ADD COLUMN release_end_date DATE NULL"));
        assertTrue(sql.contains("release_start_date = release_date"));
        assertTrue(sql.contains("release_end_date = release_date"));
        assertTrue(sql.contains("MODIFY COLUMN release_start_date DATE NOT NULL"));
        assertTrue(sql.contains("MODIFY COLUMN release_end_date DATE NOT NULL"));
        assertTrue(sql.contains("idx_pm_project_release_calendar_range (tenant_id, project_id, release_start_date, release_end_date, deleted)"));
        assertFalse(sql.contains("DROP TABLE"));
    }

    private String migrationSql(String migrationName) throws IOException {
        Path moduleDirectory = Path.of(System.getProperty("user.dir"));
        Path migration = moduleDirectory.resolveSibling("infrastructure").resolve("src/main/resources").resolve(migrationName);
        if (!Files.isRegularFile(migration)) migration = Path.of("server/src/platform/infrastructure/src/main/resources").resolve(migrationName);
        if (!Files.isRegularFile(migration)) throw new IOException("Migration resource not found: " + migrationName);
        return Files.readString(migration, StandardCharsets.UTF_8);
    }
}
