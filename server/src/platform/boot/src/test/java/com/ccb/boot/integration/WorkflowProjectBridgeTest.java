package com.ccb.boot.integration;

import com.ccb.common.exception.BusinessException;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.ProjectWorkflowDirectoryService;
import com.ccb.system.capability.ProjectWorkflowMember;
import com.ccb.system.capability.ProjectWorkflowRole;
import com.ccb.workflow.integration.WorkflowPendingTaskQuery;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Collection;
import java.util.List;
import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkflowProjectBridgeTest {
    private static final AuthUser USER = new AuthUser(7L, 1L, "member", "", "成员", 1L, true);

    @Test
    void mapsProjectDirectoryWithoutLeakingSystemTypes() {
        WorkflowProjectAccessBridge bridge = new WorkflowProjectAccessBridge(new StubDirectory());

        assertEquals("P1", bridge.requireAccessible("P1", USER).ref());
        assertEquals("成员", bridge.members(9001L, USER).get(0).displayName());
        assertEquals("PM", bridge.roles(9001L, USER).get(0).code());
    }

    @Test
    void blocksMemberRemovalWhenWorkflowHasPendingTasks() {
        WorkflowPendingTaskQuery query = (tenantId, projectId, userId) -> 1L;
        ObjectProvider<WorkflowPendingTaskQuery> provider = new ObjectProvider<>() {
            @Override public WorkflowPendingTaskQuery getObject(Object... args) { return query; }
            @Override public WorkflowPendingTaskQuery getObject() { return query; }
            @Override public WorkflowPendingTaskQuery getIfAvailable() { return query; }
            @Override public WorkflowPendingTaskQuery getIfUnique() { return query; }
            @Override public Iterator<WorkflowPendingTaskQuery> iterator() { return List.of(query).iterator(); }
        };
        WorkflowProjectMemberRemovalBridge bridge = new WorkflowProjectMemberRemovalBridge(provider);

        assertThrows(BusinessException.class, () -> bridge.requireNoPendingTasks(1L, 9001L, 7L));
    }

    private static final class StubDirectory implements ProjectWorkflowDirectoryService {
        @Override public ProjectScope requireAccessible(String projectRef, AuthUser actor) { return new ProjectScope(9001L, "P1", "项目一"); }
        @Override public void requireAccessible(long projectId, AuthUser actor) { }
        @Override public void requireManageable(long projectId, AuthUser actor) { }
        @Override public List<Long> accessibleProjectIds(AuthUser actor) { return List.of(9001L); }
        @Override public List<ProjectWorkflowMember> members(long projectId, AuthUser actor) { return List.of(new ProjectWorkflowMember(7L, "member", "成员")); }
        @Override public List<ProjectWorkflowRole> roles(long projectId, AuthUser actor) { return List.of(new ProjectWorkflowRole(8001L, "PM", "项目负责人")); }
        @Override public void requireMembers(long projectId, Collection<Long> userIds, AuthUser actor) { }
        @Override public List<ProjectWorkflowMember> membersForRoles(long projectId, Collection<Long> roleIds, AuthUser actor) { return members(projectId, actor); }
    }
}
