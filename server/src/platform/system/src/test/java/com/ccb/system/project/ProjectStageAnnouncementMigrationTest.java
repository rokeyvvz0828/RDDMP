package com.ccb.system.project;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectStageAnnouncementMigrationTest {
    private static final String MIGRATION = "db/migration/V215__project_stage_announcements.sql";

    @Test
    void createsAnIndependentStageAnnouncementWithAuditAndOptimisticLockFields() throws IOException {
        String sql = migrationSql();

        assertTrue(sql.contains("CREATE TABLE pm_project_announcement"));
        assertTrue(sql.contains("stage_code VARCHAR(64) NOT NULL"));
        assertTrue(sql.contains("content_html MEDIUMTEXT NOT NULL"));
        assertTrue(sql.contains("pinned TINYINT NOT NULL DEFAULT 0"));
        assertTrue(sql.contains("row_version BIGINT NOT NULL DEFAULT 1"));
        assertTrue(sql.contains("FOREIGN KEY (project_id) REFERENCES pm_project(id)"));
        assertTrue(sql.contains("idx_pm_project_announcement_project_stage"));
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
