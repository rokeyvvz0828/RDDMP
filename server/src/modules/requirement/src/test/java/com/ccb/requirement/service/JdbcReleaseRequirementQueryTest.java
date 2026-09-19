package com.ccb.requirement.service;

import com.ccb.common.api.PageQuery;
import com.ccb.common.exception.BusinessException;
import com.ccb.security.model.AuthUser;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcReleaseRequirementQueryTest {
    private static final AuthUser ACTOR = new AuthUser(9, 7, "fixture", "", "虚构申请人", 1, true);

    @Test
    void searchesOnlyActiveRequirementsInMappedTenantProject() {
        Fixture jdbc = new Fixture();
        var result = new JdbcReleaseRequirementQuery(jdbc)
                .searchActive(ACTOR, "PROJECT-A", new PageQuery(2, 5), "登录%_");

        assertEquals(1, result.total());
        assertEquals("REQ-001", result.records().get(0).number());
        assertTrue(jdbc.lastRequirementSql.contains("tenant_id = ?"));
        assertTrue(jdbc.lastRequirementSql.contains("project_id = ?"));
        assertTrue(jdbc.lastRequirementSql.contains("deleted = 0"));
        assertTrue(jdbc.lastRequirementSql.contains("需求终止"));
        assertEquals(7L, jdbc.lastRequirementArgs[0]);
        assertEquals(3100L, jdbc.lastRequirementArgs[1]);
        assertEquals("%登录\\%\\_%", jdbc.lastRequirementArgs[2]);
        assertEquals(5L, jdbc.lastRequirementArgs[jdbc.lastRequirementArgs.length - 2]);
        assertEquals(5L, jdbc.lastRequirementArgs[jdbc.lastRequirementArgs.length - 1]);
    }

    @Test
    void exactResolutionFailsClosedWhenAnyRequirementIsMissingOrInactive() {
        Fixture jdbc = new Fixture();
        var result = new JdbcReleaseRequirementQuery(jdbc)
                .resolveActive(ACTOR, "PROJECT-A", List.of("REQ-001", "REQ-TERMINATED"));

        assertTrue(result.isEmpty());
        assertTrue(jdbc.lastRequirementSql.contains("requirement_no IN"));
        assertArrayEquals(new Object[]{7L, 3100L, "REQ-001", "REQ-TERMINATED"},
                jdbc.lastRequirementArgs);
    }

    @Test
    void missingOrAmbiguousProjectMappingAndDisabledActorFailClosed() {
        Fixture jdbc = new Fixture();
        JdbcReleaseRequirementQuery query = new JdbcReleaseRequirementQuery(jdbc);
        jdbc.projects = List.of();
        assertThrows(BusinessException.class,
                () -> query.searchActive(ACTOR, "PROJECT-A", new PageQuery(1, 20), null));
        jdbc.projects = List.of(Map.of("id", 3100L), Map.of("id", 3200L));
        assertThrows(BusinessException.class,
                () -> query.resolveActive(ACTOR, "PROJECT-A", List.of("REQ-001")));
        AuthUser disabled = new AuthUser(9, 7, "fixture", "", "虚构申请人", 1, false);
        assertThrows(BusinessException.class,
                () -> query.searchActive(disabled, "PROJECT-A", new PageQuery(1, 20), null));
    }

    private static final class Fixture extends JdbcTemplate {
        private List<Map<String, Object>> projects = List.of(Map.of("id", 3100L));
        private final List<Map<String, Object>> requirements = new ArrayList<>(List.of(requirement()));
        private String lastRequirementSql;
        private Object[] lastRequirementArgs;

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            if (sql.contains("FROM pm_project")) {
                assertArrayEquals(new Object[]{7L, "PROJECT-A"}, args);
                return projects;
            }
            lastRequirementSql = sql;
            lastRequirementArgs = args;
            return List.copyOf(requirements);
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> type, Object... args) {
            assertTrue(sql.contains("需求终止"));
            return type.cast(1L);
        }

        private static Map<String, Object> requirement() {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", 101L);
            row.put("requirement_no", "REQ-001");
            row.put("requirement_name", "统一登录改造");
            row.put("requirement_status", "软需编制");
            return row;
        }
    }
}
