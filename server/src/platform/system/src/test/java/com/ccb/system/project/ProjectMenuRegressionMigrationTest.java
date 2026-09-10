package com.ccb.system.project;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectMenuRegressionMigrationTest {
    private static final String MIGRATION = "db/migration/V202__repair_dynamic_menu_regressions.sql";

    @Test
    void repairsPermissionMenuWithCharsetIndependentUtf8Bytes() throws IOException {
        String sql = migrationSql();

        assertTrue(sql.contains("CONVERT(0xE69D83E99990E7BBB4E68AA4 USING utf8mb4)"));
        assertTrue(sql.contains("id = 102"));
        assertTrue(sql.contains("tenant_id = 1"));
        assertFalse(sql.contains("DROP "));
        assertFalse(sql.contains("DELETE "));
    }

    private String migrationSql() throws IOException {
        try (InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream(MIGRATION)) {
            if (input == null) {
                throw new IOException("Migration resource not found: " + MIGRATION);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
