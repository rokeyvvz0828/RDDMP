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
        StubRepository jdbc = new StubRepository(List.of(Map.of("id", 88L, "code", "release-approval", "name", "版本审批", "scope_type", "PLATFORM", "current_version", 3)));
        StubWorkflowService workflow = new StubWorkflowService();
        WorkflowBusinessIntegrationService service = service(jdbc, workflow);

        var result = service.startByCode(new WorkflowStartCommand("release-approval",
                new WorkflowBusinessContext("release", "配置管理", "release_application", "SQ-001", "版本申请 SQ-001", 2, "P1", "项目一", "/release/applications/SQ-001", DIGEST),
                Map.of("priority", "normal")), USER);

        assertEquals(9001L, result.instanceId());
        assertEquals(88L, result.definitionId());
        assertEquals("P1", workflow.context.projectRef());
        assertEquals("项目一", workflow.context.projectName());
    }

    @Test
    void listsPublishedDefinitionsAndStartsByDefinitionId() {
        StubRepository jdbc = new StubRepository(List.of(Map.of(
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
    }

    @Test
    void explicitLegacyDefinitionIdReselectsPublishedProjectDefinitionWithSameCode() {
        Map<String, Object> legacy = Map.of("id", 88L, "code", "release-approval", "name", "版本审批",
                "scope_type", "PLATFORM", "current_version", 3);
        Map<String, Object> project = Map.of("id", 99L, "code", "release-approval", "name", "项目版本审批",
                "scope_type", "PROJECT", "project_id", 10L, "current_version", 5);
        StubRepository jdbc = new StubRepository(List.of(legacy), List.of(project));
        WorkflowBusinessIntegrationService service = service(jdbc, new StubWorkflowService());

        var result = service.startByDefinitionId(new WorkflowStartDefinitionCommand(88L,
                new WorkflowBusinessContext("release", "配置管理", "release_application", "SQ-003", "版本申请 SQ-003", 1,
                        "P1", "项目一", "/release/applications/SQ-003", DIGEST), Map.of()), USER);

        assertEquals(99L, result.definitionId());
    }

    @Test
    void missingRunnableDefinitionNamesProjectAndWorkflowCode() {
        WorkflowBusinessIntegrationService service = service(new StubRepository(List.of()), new StubWorkflowService());

        BusinessException error = assertThrows(BusinessException.class, () -> service.startByCode(
                new WorkflowStartCommand("release-approval",
                        new WorkflowBusinessContext("release", "配置管理", "release_application", "SQ-004", "版本申请 SQ-004", 1,
                                "P1", "项目一", "/release/applications/SQ-004", DIGEST), Map.of()), USER));

        assertTrue(error.getMessage().contains("项目【项目一】"));
        assertTrue(error.getMessage().contains("【release-approval】"));
    }

    @Test
    void rejectsExternalOrProtocolRelativeActionPathBeforeCreatingInstance() {
        StubWorkflowService workflow = new StubWorkflowService();
        WorkflowBusinessIntegrationService service = service(new StubRepository(List.of()), workflow);

        assertThrows(BusinessException.class, () -> service.startByCode(new WorkflowStartCommand("release-approval",
                new WorkflowBusinessContext("release", "配置管理", "release_application", "SQ-001", "版本申请", 1, null, null, "//outside.example/review", DIGEST), Map.of()), USER));
        assertEquals(0, workflow.starts);
    }

    private WorkflowBusinessIntegrationService service(StubRepository jdbc, StubWorkflowService workflow) {
        WorkflowBusinessIntegrationService service = new WorkflowBusinessIntegrationService(jdbc, workflow, new StubLifecycleEventService());
        service.setProjectAccess(new StubProjectAccess());
        return service;
    }

    private static final class StubRepository extends WorkflowBusinessIntegrationRepository {
        private final List<List<Map<String, Object>>> responses;
        private int queryIndex;
        private int countCalls;

        private StubRepository(List<Map<String, Object>> rows) {
            super(null);
            this.responses = List.of(rows);
        }

        @SafeVarargs
        private StubRepository(List<Map<String, Object>>... responses) {
            super(null);
            this.responses = List.of(responses);
        }

        @Override
        public List<Map<String, Object>> publishedByCode(long tenantId, String code, Long projectId) {
            return responses.get(Math.min(queryIndex++, responses.size() - 1));
        }

        @Override
        public List<Map<String, Object>> publishedDefinitions(long tenantId) {
            return responses.get(Math.min(queryIndex++, responses.size() - 1));
        }

        @Override
        public List<Map<String, Object>> publishedById(long tenantId, long definitionId) {
            return responses.get(Math.min(queryIndex++, responses.size() - 1));
        }
    }

    private static final class StubWorkflowService extends WorkflowService {
        private int starts;
        private WorkflowBusinessContext context;

        private StubWorkflowService() {
            super(new ObjectMapper(), null, null, null, null);
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
