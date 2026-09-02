package com.ccb.workflow.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.security.model.AuthUser;
import com.ccb.workflow.integration.WorkflowBusinessContext;
import com.ccb.workflow.integration.WorkflowStartCommand;
import com.ccb.workflow.integration.WorkflowStartDefinitionCommand;
import com.ccb.workflow.integration.WorkflowProjectAccessGateway;
import com.ccb.workflow.integration.WorkflowProjectMember;
import com.ccb.workflow.integration.WorkflowProjectRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowBusinessIntegrationServiceTest {
    private static final AuthUser USER = new AuthUser(7L, 1L, "reviewer", "", "审批人", 1L, true);
    private static final String DIGEST = "a".repeat(64);

    @Test
    void startsPublishedDefinitionByCodeAndPersistsValidatedContext() {
        StubJdbcTemplate jdbc = new StubJdbcTemplate(List.of(Map.of("id", 88L, "code", "release-approval", "name", "版本审批", "scope_type", "PLATFORM", "current_version", 3)));
        StubWorkflowService workflow = new StubWorkflowService();
        WorkflowBusinessIntegrationService service = service(jdbc, workflow);

        var result = service.startByCode(new WorkflowStartCommand("release-approval",
                new WorkflowBusinessContext("release", "配置管理", "release_application", "SQ-001", "版本申请 SQ-001", 2, "P1", "项目一", "/release/applications/SQ-001", DIGEST),
                Map.of("priority", "normal")), USER);

        assertEquals(9001L, result.instanceId());
        assertEquals(88L, result.definitionId());
        assertEquals(0, jdbc.updateCount);
        assertEquals("P1", workflow.context.projectRef());
        assertEquals("项目一", workflow.context.projectName());
        assertTrue(jdbc.queries.get(0).contains("d.deployment_id IS NOT NULL"));
        assertTrue(jdbc.queries.get(0).contains("v.deployment_id IS NOT NULL"));
    }

    @Test
    void listsPublishedDefinitionsAndStartsByDefinitionId() {
        StubJdbcTemplate jdbc = new StubJdbcTemplate(List.of(Map.of(
                "id", 88L, "code", "release-approval", "name", "版本审批", "scope_type", "PLATFORM", "current_version", 3)));
        WorkflowBusinessIntegrationService service = service(jdbc, new StubWorkflowService());

        var definitions = service.publishedDefinitions(USER);
        var result = service.startByDefinitionId(new WorkflowStartDefinitionCommand(88L,
                new WorkflowBusinessContext("release", "配置管理", "release_application", "SQ-002", "版本申请 SQ-002", 1, "P1", "项目一", "/release/applications/SQ-002", DIGEST),
                Map.of()), USER);

        assertEquals(1, definitions.size());
        assertEquals("release-approval", definitions.get(0).code());
        assertEquals(88L, result.definitionId());
        assertEquals(3, result.definitionVersion());
        assertTrue(jdbc.queries.stream().allMatch(sql -> sql.contains("d.deployment_id IS NOT NULL")));
        assertTrue(jdbc.queries.stream().allMatch(sql -> sql.contains("v.deployment_id IS NOT NULL")));
        assertTrue(jdbc.queries.get(0).contains("d.scope_type IN ('PLATFORM', 'PROJECT')"));
    }

    @Test
    void rejectsExternalOrProtocolRelativeActionPathBeforeCreatingInstance() {
        StubWorkflowService workflow = new StubWorkflowService();
        WorkflowBusinessIntegrationService service = service(new StubJdbcTemplate(List.of()), workflow);

        assertThrows(BusinessException.class, () -> service.startByCode(new WorkflowStartCommand("release-approval",
                new WorkflowBusinessContext("release", "配置管理", "release_application", "SQ-001", "版本申请", 1, null, null, "//outside.example/review", DIGEST), Map.of()), USER));
        assertEquals(0, workflow.starts);
    }

    private WorkflowBusinessIntegrationService service(StubJdbcTemplate jdbc, StubWorkflowService workflow) {
        WorkflowBusinessIntegrationService service = new WorkflowBusinessIntegrationService(jdbc, workflow, new StubLifecycleEventService());
        service.setProjectAccess(new StubProjectAccess());
        return service;
    }

    private static final class StubJdbcTemplate extends JdbcTemplate {
        private final List<Map<String, Object>> rows;
        private final List<String> queries = new ArrayList<>();
        private int updateCount;
        private String lastUpdateSql;
        private Object[] lastUpdateArgs;

        private StubJdbcTemplate(List<Map<String, Object>> rows) {
            this.rows = rows;
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            queries.add(sql);
            return rows;
        }

        @Override
        public int update(String sql, Object... args) {
            updateCount++;
            lastUpdateSql = sql;
            lastUpdateArgs = args;
            return 1;
        }
    }

    private static final class StubWorkflowService extends WorkflowService {
        private int starts;
        private WorkflowBusinessContext context;

        private StubWorkflowService() {
            super(null, new ObjectMapper(), null, null, null);
        }

        @Override
        public Map<String, Object> start(long definitionId, String businessKey, Map<String, Object> variables, AuthUser user) {
            starts++;
            return Map.of("id", 9001L, "version_no", 3, "status", "RUNNING");
        }

        @Override
        public Map<String, Object> start(long definitionId, String businessKey, Map<String, Object> variables,
                                         WorkflowBusinessContext context, AuthUser user) {
            this.context = context;
            return start(definitionId, businessKey, variables, user);
        }
    }

    private static final class StubProjectAccess implements WorkflowProjectAccessGateway {
        @Override public ProjectScope requireAccessible(String projectRef, AuthUser actor) { return new ProjectScope(10L, projectRef, "项目一"); }
        @Override public void requireAccessible(long projectId, AuthUser actor) {}
        @Override public void requireManageable(long projectId, AuthUser actor) {}
        @Override public List<Long> accessibleProjectIds(AuthUser actor) { return List.of(10L); }
        @Override public List<WorkflowProjectMember> members(long projectId, AuthUser actor) { return List.of(); }
        @Override public List<WorkflowProjectRole> roles(long projectId, AuthUser actor) { return List.of(); }
        @Override public void requireMembers(long projectId, java.util.Collection<Long> userIds, AuthUser actor) {}
        @Override public List<WorkflowProjectMember> membersForRoles(long projectId, java.util.Collection<Long> roleIds, AuthUser actor) { return List.of(); }
    }

    private static final class StubLifecycleEventService extends WorkflowLifecycleEventService {
        private StubLifecycleEventService() {
            super(null, List.of());
        }

        @Override
        public String emit(long instanceId, com.ccb.workflow.integration.WorkflowLifecycleEventType eventType, AuthUser operator) {
            return "event-1";
        }
    }
}
