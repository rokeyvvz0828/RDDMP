package com.ccb.workflow.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.security.model.AuthUser;
import com.ccb.workflow.integration.WorkflowProjectAccessGateway;
import com.ccb.workflow.integration.WorkflowProjectMember;
import com.ccb.workflow.integration.WorkflowProjectRole;
import com.ccb.workflow.model.WorkflowNodeModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowProjectScopeTest {
    private static final AuthUser USER = new AuthUser(7L, 1L, "reviewer", "", "审批人", 1L, true);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void acceptsProjectAssigneeTypesInBothModelVersions() {
        WorkflowDefinitionValidator legacy = new WorkflowDefinitionValidator(objectMapper);
        var graph = legacy.parse("""
                {"schemaVersion":1,"nodes":[
                  {"id":"start","type":"START","label":"发起","config":{}},
                  {"id":"approve","type":"APPROVAL","label":"项目审批","config":{"assigneeType":"PROJECT_MEMBER","assigneeIds":[7],"mode":"ANY"}},
                  {"id":"end","type":"END","label":"结束","config":{}}
                ],"edges":[{"id":"e1","source":"start","target":"approve"},{"id":"e2","source":"approve","target":"end"}]}
                """);
        assertEquals("PROJECT_MEMBER", graph.node("approve").config().path("assigneeType").asText());

        WorkflowModelValidator enterprise = new WorkflowModelValidator(objectMapper);
        assertTrue(enterprise.validate("""
                {"schemaVersion":2,"variables":[],"formBindings":[],"nodes":[
                  {"id":"start","type":"START","label":"发起","position":{"x":0,"y":0},"config":{}},
                  {"id":"approve","type":"APPROVAL","label":"项目角色审批","position":{"x":1,"y":0},"config":{"assigneeType":"PROJECT_ROLE","assigneeIds":[20],"mode":"ANY"}},
                  {"id":"end","type":"END","label":"结束","position":{"x":2,"y":0},"config":{}}
                ],"edges":[{"id":"e1","source":"start","target":"approve","default":false},{"id":"e2","source":"approve","target":"end","default":false}]}
                """).valid());
    }

    @Test
    void resolvesProjectRoleOnlyThroughProjectDirectory() throws Exception {
        WorkflowAssigneeResolver resolver = new WorkflowAssigneeResolver(new AssigneeJdbcTemplate());
        StubProjectAccess gateway = new StubProjectAccess();
        resolver.setProjectAccess(gateway);
        WorkflowNodeModel node = new WorkflowNodeModel("approve", "APPROVAL", "项目审批", null,
                objectMapper.readTree("{\"assigneeType\":\"PROJECT_ROLE\",\"assigneeIds\":[20]}"));

        var result = resolver.resolveNode(node, 1L, 7L, 10L, USER, Map.of());

        assertEquals(List.of(8L), result.stream().map(WorkflowAssigneeResolver.ResolvedAssignee::id).toList());
        assertEquals(10L, gateway.projectId);
        assertEquals(List.of(20L), gateway.roleIds);
    }

    @Test
    void rejectsDirectProjectWorkflowStartWithoutProjectContext() {
        WorkflowService service = new WorkflowService(new ProjectStartJdbcTemplate(), objectMapper, null, null, null);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.start(100L, "BUSINESS-1", Map.of(), USER));

        assertEquals("项目流程启动时必须提供项目上下文", error.getMessage());
    }

    private static final class AssigneeJdbcTemplate extends JdbcTemplate {
        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            return (List<T>) List.of(new WorkflowAssigneeResolver.ResolvedAssignee(8L, "项目审批人"));
        }
    }

    private static final class ProjectStartJdbcTemplate extends JdbcTemplate {
        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            return List.of(Map.of(
                    "id", 100L,
                    "code", "PROJECT_FLOW",
                    "name", "项目流程",
                    "scope_type", "PROJECT",
                    "project_id", 10L,
                    "status", "PUBLISHED",
                    "current_version", 1));
        }
    }

    private static final class StubProjectAccess implements WorkflowProjectAccessGateway {
        private long projectId;
        private List<Long> roleIds = List.of();

        @Override public ProjectScope requireAccessible(String projectRef, AuthUser actor) { return new ProjectScope(10L, projectRef, "项目一"); }
        @Override public void requireAccessible(long projectId, AuthUser actor) { this.projectId = projectId; }
        @Override public void requireManageable(long projectId, AuthUser actor) {}
        @Override public List<Long> accessibleProjectIds(AuthUser actor) { return List.of(10L); }
        @Override public List<WorkflowProjectMember> members(long projectId, AuthUser actor) { return List.of(); }
        @Override public List<WorkflowProjectRole> roles(long projectId, AuthUser actor) { return List.of(); }
        @Override public void requireMembers(long projectId, Collection<Long> userIds, AuthUser actor) { this.projectId = projectId; }
        @Override public List<WorkflowProjectMember> membersForRoles(long projectId, Collection<Long> roleIds, AuthUser actor) {
            this.projectId = projectId;
            this.roleIds = List.copyOf(roleIds);
            return List.of(new WorkflowProjectMember(8L, "project-reviewer", "项目审批人"));
        }
    }
}
