package com.ccb.security.repository;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthRepositoryAuditAccessTest {
    @Test
    void systemPermissionsRemainGlobalAndExcludeProjectBusinessPermissions() {
        RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate();
        AuthRepository repository = new AuthRepository(jdbc);

        repository.findPermissions(7L, 1L);

        assertTrue(jdbc.joinedSql().contains("permission_code LIKE 'system:%'"));
        assertFalse(jdbc.joinedSql().contains("pm_project_role_permission"));
    }

    @Test
    void projectPermissionsUnionOwnerPmAndAssignedRoleWithoutSystemPermissions() {
        RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate();
        AuthRepository repository = new AuthRepository(jdbc);

        repository.findPermissions(7L, 1L, 9001L);

        String sql = jdbc.joinedSql();
        assertTrue(sql.contains("project.owner_id = ?"));
        assertTrue(sql.contains("role.role_code = 'PM'"));
        assertTrue(sql.contains("pm_project_role_permission"));
        assertTrue(sql.contains("permission.permission_code NOT LIKE 'system:%'"));
    }

    @Test
    void routeCatalogIsFilteredByTheSamePermissionSetInAuthService() {
        RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate();
        AuthRepository repository = new AuthRepository(jdbc);

        repository.findRoutes(7L, 1L, 9001L);

        assertTrue(jdbc.joinedSql().contains("FROM sys_menu"));
        assertTrue(jdbc.joinedSql().contains("status = 1 AND visible = 1 AND deleted = 0"));
        assertFalse(jdbc.joinedSql().contains("pm_project"));
    }

    private static final class RecordingJdbcTemplate extends JdbcTemplate {
        private final List<String> statements = new ArrayList<>();

        @Override
        public <T> List<T> queryForList(String sql, Class<T> elementType, Object... args) {
            statements.add(sql);
            return List.of();
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            statements.add(sql);
            return List.of();
        }

        private String joinedSql() {
            return String.join("\n", statements);
        }
    }
}
