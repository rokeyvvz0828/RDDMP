package com.ccb.datamigration.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ccb.common.api.PageResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class DashboardServiceTest {
    private static final AuthUser USER = new AuthUser(7L, 3L, "developer", "", "研发人员", 11L, true);
    private static final long PROJECT = 91L;

    @Test
    void metricUsesTenantProjectDeletedAndDefinitionPredicate() {
        CapturingJdbcTemplate jdbc = new CapturingJdbcTemplate();
        DashboardService service = new DashboardService(jdbc, permissions());

        Map<String, Object> result = service.metric("OVERALL_PLAN", PROJECT, USER);

        assertEquals("OVERALL_PLAN", result.get("metricCode"));
        assertEquals(7L, result.get("count"));
        assertTrue(result.get("calculatedAt") instanceof Instant);
        assertTrue(jdbc.objectSql.contains("FROM dm_plan"));
        assertTrue(jdbc.objectSql.contains("tenant_id = ? AND project_id = ? AND deleted = 0"));
        assertTrue(jdbc.objectSql.contains("granularity = ?"));
        assertArrayEquals(new Object[]{3L, PROJECT, "PROJECT"}, jdbc.objectArgs);
    }

    @Test
    void drilldownUsesSameScopeStableOrderAndFixedPageSize() {
        CapturingJdbcTemplate jdbc = new CapturingJdbcTemplate();
        DashboardService service = new DashboardService(jdbc, permissions());

        PageResult<Map<String, Object>> result = service.drilldown("MEETING", PROJECT, 2, 20, USER);

        assertEquals(7L, result.total());
        assertEquals(2L, result.page());
        assertEquals(20L, result.size());
        assertEquals("会议纪要", result.records().get(0).get("name"));
        assertTrue(jdbc.listSql.contains("FROM dm_meeting"));
        assertTrue(jdbc.listSql.contains("tenant_id = ? AND project_id = ? AND deleted = 0"));
        assertTrue(jdbc.listSql.contains("ORDER BY updated_at DESC, meeting_id DESC LIMIT ? OFFSET ?"));
        assertArrayEquals(new Object[]{3L, PROJECT, 20, 20L}, jdbc.listArgs);
    }

    @Test
    void invalidPageSizeAndUnknownMetricAreRejectedBeforeQuery() {
        CapturingJdbcTemplate jdbc = new CapturingJdbcTemplate();
        DashboardService service = new DashboardService(jdbc, permissions());

        BusinessException sizeError = assertThrows(BusinessException.class,
                () -> service.drilldown("REPORT", PROJECT, 1, 50, USER));
        assertEquals(ErrorCode.BAD_REQUEST, sizeError.code());
        assertThrows(BusinessException.class, () -> service.metric("UNKNOWN", PROJECT, USER));
        assertTrue(jdbc.objectSql == null && jdbc.listSql == null);
    }

    private DataMigrationPermissionService permissions() {
        return new DataMigrationPermissionService(new JdbcTemplate(), StubProjectAccess.allow());
    }

    private static final class CapturingJdbcTemplate extends JdbcTemplate {
        String objectSql;
        Object[] objectArgs;
        String listSql;
        Object[] listArgs;

        @Override
        @SuppressWarnings("unchecked")
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            objectSql = sql;
            objectArgs = args;
            return (T) Long.valueOf(7L);
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            listSql = sql;
            listArgs = args;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", 17L);
            row.put("code", "MTG-17");
            row.put("name", "会议纪要");
            row.put("granularity", "PROJECT");
            row.put("systemCode", null);
            row.put("updatedAt", "2026-09-09T08:00:00");
            return new ArrayList<>(List.of(row));
        }
    }
}
