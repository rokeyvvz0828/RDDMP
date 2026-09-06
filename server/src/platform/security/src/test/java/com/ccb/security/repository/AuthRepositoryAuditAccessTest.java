package com.ccb.security.repository;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthRepositoryAuditAccessTest {
    @Test
    void permissionsIncludeDynamicOwnerAndProjectManagerRule() {
        RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate();
        AuthRepository repository = new AuthRepository(jdbc);

        repository.findPermissions(7L, 1L);

        assertTrue(jdbc.sql.contains("'system:audit:list'"));
        assertTrue(jdbc.sql.contains("p.owner_id = ?"));
        assertTrue(jdbc.sql.contains("pr.role_code = 'PM'"));
    }

    @Test
    void routesIncludeOnlySystemParentAndAuditChildDynamically() {
        RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate();
        AuthRepository repository = new AuthRepository(jdbc);

        repository.findRoutes(7L, 1L);

        assertTrue(jdbc.sql.contains("m.route_path = '/system'"));
        assertTrue(jdbc.sql.contains("m.route_path = '/system/audit'"));
        assertTrue(jdbc.sql.contains("pr.role_code = 'PM'"));
    }

    private static final class RecordingJdbcTemplate extends JdbcTemplate {
        private String sql = "";

        @Override
        public <T> List<T> queryForList(String sql, Class<T> elementType, Object... args) {
            this.sql = sql;
            return List.of();
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            this.sql = sql;
            return List.of();
        }
    }
}
