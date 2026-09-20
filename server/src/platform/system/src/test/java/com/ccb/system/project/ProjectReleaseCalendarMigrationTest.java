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

    @Test
    void createsAnIndependentProjectCalendarWithMonthLookupAndAuditFields() throws IOException {
        String sql = migrationSql();

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

    private String migrationSql() throws IOException {
        Path moduleDirectory = Path.of(System.getProperty("user.dir"));
        Path migration = moduleDirectory.resolveSibling("infrastructure").resolve("src/main/resources").resolve(MIGRATION);
        if (!Files.isRegularFile(migration)) migration = Path.of("server/src/platform/infrastructure/src/main/resources").resolve(MIGRATION);
        if (!Files.isRegularFile(migration)) throw new IOException("Migration resource not found: " + MIGRATION);
        return Files.readString(migration, StandardCharsets.UTF_8);
    }
}
