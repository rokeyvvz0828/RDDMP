package com.ccb.workflow.integration;

import com.ccb.security.model.AuthUser;

import java.util.Collection;
import java.util.List;

public interface WorkflowProjectAccessGateway {
    ProjectScope requireAccessible(String projectRef, AuthUser actor);

    void requireAccessible(long projectId, AuthUser actor);

    void requireManageable(long projectId, AuthUser actor);

    List<Long> accessibleProjectIds(AuthUser actor);

    List<WorkflowProjectMember> members(long projectId, AuthUser actor);

    List<WorkflowProjectRole> roles(long projectId, AuthUser actor);

    void requireMembers(long projectId, Collection<Long> userIds, AuthUser actor);

    List<WorkflowProjectMember> membersForRoles(long projectId, Collection<Long> roleIds, AuthUser actor);

    record ProjectScope(long id, String ref, String name) {
    }
}
