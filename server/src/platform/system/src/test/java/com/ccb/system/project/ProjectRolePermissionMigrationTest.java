package com.ccb.system.project;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectRolePermissionMigrationTest {
    private static final String MIGRATION = "db/migration/V201__project_scoped_role_permissions.sql";

    @Test
    void definesProjectScopedRelationAndSafeInitialPermissions() throws IOException {
        String sql = migrationSql();

        assertTrue(sql.contains("PRIMARY KEY (tenant_id, project_id, role_id, permission_id)"));
        assertTrue(sql.contains("r.role_code = 'PM'"));
        assertTrue(sql.contains("permission.action_code = 'read'"));
        assertTrue(sql.contains("r.role_code <> 'PM'"));
        assertTrue(sql.contains("permission.permission_code NOT LIKE 'system:%'"));
        assertFalse(sql.contains("DROP TABLE"));
        assertFalse(sql.contains("DELETE FROM sys_role_permission"));
    }

    @Test
    void movesTheExistingSystemMenuEntryToPermissionMaintenance() throws IOException {
        String sql = migrationSql();

        assertTrue(sql.contains("menu_name = '\u6743\u9650\u7ef4\u62a4'"));
        assertTrue(sql.contains("route_path = '/system/permissions'"));
        assertTrue(sql.contains("component_path = 'system/permissions/index'"));
        assertTrue(sql.contains("permission_code = 'system:role:list'"));
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
