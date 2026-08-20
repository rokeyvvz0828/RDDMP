package com.ccb.workflow.service;

import com.ccb.common.api.PageQuery;
import com.ccb.security.model.AuthUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowTaskProjectionTest {
    private static final AuthUser USER = new AuthUser(7L, 1L, "reviewer", "", "审批人", 1L, true);

    @Test
    void inboxProjectsBusinessContextRequiredByDashboard() {
        ProjectionJdbcTemplate jdbc = new ProjectionJdbcTemplate();
        WorkflowService service = service(jdbc);

        var result = service.inbox(new PageQuery(1, 5), USER);

        assertEquals(1, result.records().size());
        assertBusinessProjection(result.records().get(0));
        assertTrue(jdbc.lastListSql.contains("starter.display_name AS starter_name"));
    }

    @Test
    void doneProjectsBusinessContextRequiredByDashboard() {
        ProjectionJdbcTemplate jdbc = new ProjectionJdbcTemplate();
        WorkflowService service = service(jdbc);

        var result = service.done(new PageQuery(1, 5), USER);

        assertEquals(1, result.records().size());
        assertBusinessProjection(result.records().get(0));
        assertTrue(jdbc.lastListSql.contains("d.name AS definition_name"));
    }

    private WorkflowService service(ProjectionJdbcTemplate jdbc) {
        WorkflowNodeLabelResolver labels = new WorkflowNodeLabelResolver(null, new ObjectMapper()) {
            @Override
            public List<Map<String, Object>> decorateTasks(List<Map<String, Object>> rows, long tenantId) {
                rows.forEach(row -> row.put("node_name", "测试审核"));
                return rows;
            }
        };
        return new WorkflowService(jdbc, new ObjectMapper(), null, null, labels);
    }

    private void assertBusinessProjection(Map<String, Object> row) {
        assertEquals("版本申请 SQ-001", row.get("business_title"));
        assertEquals("release", row.get("business_type"));
        assertEquals(2, row.get("business_round"));
        assertEquals("P1", row.get("project_ref"));
        assertEquals("统一研发交付平台升级项目", row.get("project_name"));
        assertEquals("/release/applications/SQ-001", row.get("action_path"));
    }

    private static final class ProjectionJdbcTemplate extends JdbcTemplate {
        private String lastListSql;

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            lastListSql = sql;
            assertTrue(sql.contains("i.business_type"));
            assertTrue(sql.contains("i.business_title"));
            assertTrue(sql.contains("i.business_round"));
            assertTrue(sql.contains("i.project_ref"));
            assertTrue(sql.contains("i.project_name"));
            assertTrue(sql.contains("i.action_path"));
            return List.of(new java.util.LinkedHashMap<>(Map.ofEntries(
                    Map.entry("id", 31L), Map.entry("instance_id", 21L), Map.entry("task_id", 31L),
                    Map.entry("task_key", "review"), Map.entry("node_id", "review"), Map.entry("task_type", "APPROVAL"),
                    Map.entry("status", "PENDING"), Map.entry("action_code", "APPROVE"), Map.entry("business_key", "SQ-001"),
                    Map.entry("business_type", "release"), Map.entry("business_title", "版本申请 SQ-001"),
                    Map.entry("business_round", 2), Map.entry("project_ref", "P1"),
                    Map.entry("project_name", "统一研发交付平台升级项目"),
                    Map.entry("action_path", "/release/applications/SQ-001"), Map.entry("instance_status", "RUNNING"))));
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            return (T) Long.valueOf(1);
        }
    }
}
