package com.ccb.workflow.service;

import com.ccb.common.api.PageQuery;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.ccb.workflow.integration.WorkflowProjectAccessGateway;
import com.ccb.workflow.integration.WorkflowProjectMember;
import com.ccb.workflow.integration.WorkflowProjectRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowPlatformRetirementTest {
    private static final AuthUser USER = new AuthUser(7L, 1L, "admin", "", "管理员", 1L, true);
    private static final String PLACEHOLDER_GRAPH = """
            {"schemaVersion":1,"nodes":[
              {"id":"start","type":"START","label":"发起","config":{}},
              {"id":"approve","type":"APPROVAL","label":"项目审批","config":{"assigneeType":"TEMPLATE_PLACEHOLDER","assigneeIds":[],"mode":"ANY"}},
              {"id":"end","type":"END","label":"结束","config":{}}
            ],"edges":[{"id":"e1","source":"start","target":"approve"},{"id":"e2","source":"approve","target":"end"}]}
            """;

    @Test
    void hidesPlatformDefinitionsAndRejectsPlatformManagementRequests() {
        RetirementJdbcTemplate jdbc = new RetirementJdbcTemplate("PLATFORM");
        WorkflowService service = service(jdbc);

        BusinessException scopeError = assertThrows(BusinessException.class,
                () -> service.definitions(new PageQuery(1, 20), null, "PLATFORM", USER));
        BusinessException readError = assertThrows(BusinessException.class, () -> service.definition(100L, USER));
        BusinessException createError = assertThrows(BusinessException.class,
                () -> service.createDefinition("legacy", "历史流程", PLACEHOLDER_GRAPH, "PLATFORM", null, USER));

        assertEquals(ErrorCode.BAD_REQUEST, scopeError.code());
        assertEquals(ErrorCode.CONFLICT, readError.code());
        assertEquals(ErrorCode.BAD_REQUEST, createError.code());
    }

    @Test
    void marksProjectPlaceholderDraftAndKeepsPlatformOutOfDefaultList() {
        RetirementJdbcTemplate jdbc = new RetirementJdbcTemplate("PROJECT");
        WorkflowService service = service(jdbc);

        var page = service.definitions(new PageQuery(1, 20), "P1", null, USER);

        assertEquals(1, page.records().size());
        assertEquals(true, page.records().get(0).get("requires_configuration"));
        assertFalse(jdbc.listSql.contains("'PLATFORM'"));
        assertTrue(jdbc.listSql.contains("scope_type = 'TEMPLATE'"));
    }

    @Test
    void allowsSavingProjectPlaceholderDraftButRejectsPublishingIt() {
        RetirementJdbcTemplate jdbc = new RetirementJdbcTemplate("PROJECT");
        WorkflowService service = service(jdbc);

        service.updateDefinition(100L, "project-review", "项目审批", PLACEHOLDER_GRAPH, USER);
        BusinessException error = assertThrows(BusinessException.class, () -> service.publish(100L, USER));

        assertTrue(jdbc.updated);
        assertEquals(ErrorCode.CONFLICT, error.code());
        assertTrue(error.getMessage().contains("待配置的审批人"));
    }

    private WorkflowService service(RetirementJdbcTemplate jdbc) {
        WorkflowService service = new WorkflowService(jdbc, new ObjectMapper(), null, null, null);
        service.setProjectAccess(new ProjectAccess());
        return service;
    }

    private static final class RetirementJdbcTemplate extends JdbcTemplate {
        private final String scope;
        private String listSql = "";
        private boolean updated;

        private RetirementJdbcTemplate(String scope) {
            this.scope = scope;
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            if (sql.contains("model_schema_version AS definition_schema_version")) {
                return List.of(Map.of("definition_schema_version", 1, "version_schema_version", 1,
                        "definition_json", PLACEHOLDER_GRAPH));
            }
            if (sql.contains("created_at") || sql.startsWith("SELECT id, code, name, status")) {
                listSql = sql;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", 100L);
                row.put("code", "project-review");
                row.put("name", "项目审批");
                row.put("scope_type", scope);
                row.put("project_id", "PROJECT".equals(scope) ? 10L : null);
                row.put("status", "DRAFT");
                row.put("current_version", 0);
                row.put("model_schema_version", 1);
                row.put("definition_json", PLACEHOLDER_GRAPH);
                return List.of(row);
            }
            return List.of();
        }

        @Override
        public Map<String, Object> queryForMap(String sql, Object... args) {
            if (sql.startsWith("SELECT status")) return Map.of("status", "DRAFT");
            if (sql.startsWith("SELECT version_no")) return Map.of("version_no", 1, "definition_json", PLACEHOLDER_GRAPH);
            return Map.of();
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            return requiredType.cast(1L);
        }

        @Override
        public int update(String sql, Object... args) {
            updated = true;
            return 1;
        }
    }

    private static final class ProjectAccess implements WorkflowProjectAccessGateway {
        @Override public ProjectScope requireAccessible(String projectRef, AuthUser actor) { return new ProjectScope(10L, projectRef, "项目一"); }
        @Override public void requireAccessible(long projectId, AuthUser actor) {}
        @Override public void requireManageable(long projectId, AuthUser actor) {}
        @Override public List<Long> accessibleProjectIds(AuthUser actor) { return List.of(10L); }
        @Override public List<WorkflowProjectMember> members(long projectId, AuthUser actor) { return List.of(); }
        @Override public List<WorkflowProjectRole> roles(long projectId, AuthUser actor) { return List.of(); }
        @Override public void requireMembers(long projectId, Collection<Long> userIds, AuthUser actor) {}
        @Override public List<WorkflowProjectMember> membersForRoles(long projectId, Collection<Long> roleIds, AuthUser actor) { return List.of(); }
    }
}
