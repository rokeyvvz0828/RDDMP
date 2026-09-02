package com.ccb.boot.integration;

import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.ProjectWorkflowDirectoryService;
import com.ccb.workflow.integration.WorkflowProjectAccessGateway;
import com.ccb.workflow.integration.WorkflowProjectMember;
import com.ccb.workflow.integration.WorkflowProjectRole;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

@Service
public class WorkflowProjectAccessBridge implements WorkflowProjectAccessGateway {
    private final ProjectWorkflowDirectoryService projects;

    public WorkflowProjectAccessBridge(ProjectWorkflowDirectoryService projects) {
        this.projects = projects;
    }

    @Override
    public ProjectScope requireAccessible(String projectRef, AuthUser actor) {
        ProjectWorkflowDirectoryService.ProjectScope project = projects.requireAccessible(projectRef, actor);
        return new ProjectScope(project.id(), project.ref(), project.name());
    }

    @Override public void requireAccessible(long projectId, AuthUser actor) { projects.requireAccessible(projectId, actor); }
    @Override public void requireManageable(long projectId, AuthUser actor) { projects.requireManageable(projectId, actor); }
    @Override public List<Long> accessibleProjectIds(AuthUser actor) { return projects.accessibleProjectIds(actor); }
    @Override public List<WorkflowProjectMember> members(long projectId, AuthUser actor) { return projects.members(projectId, actor).stream().map(item -> new WorkflowProjectMember(item.userId(), item.username(), item.displayName())).toList(); }
    @Override public List<WorkflowProjectRole> roles(long projectId, AuthUser actor) { return projects.roles(projectId, actor).stream().map(item -> new WorkflowProjectRole(item.id(), item.code(), item.name())).toList(); }
    @Override public void requireMembers(long projectId, Collection<Long> userIds, AuthUser actor) { projects.requireMembers(projectId, userIds, actor); }
    @Override public List<WorkflowProjectMember> membersForRoles(long projectId, Collection<Long> roleIds, AuthUser actor) { return projects.membersForRoles(projectId, roleIds, actor).stream().map(item -> new WorkflowProjectMember(item.userId(), item.username(), item.displayName())).toList(); }
}
