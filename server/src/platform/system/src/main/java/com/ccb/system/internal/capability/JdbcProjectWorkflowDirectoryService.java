package com.ccb.system.internal.capability;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.ProjectAccess;
import com.ccb.system.capability.ProjectAccessService;
import com.ccb.system.capability.ProjectWorkflowDirectoryService;
import com.ccb.system.capability.ProjectWorkflowMember;
import com.ccb.system.capability.ProjectWorkflowRole;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
public class JdbcProjectWorkflowDirectoryService implements ProjectWorkflowDirectoryService {
    private final SystemCapabilityRepository repository;
    private final ProjectAccessService projectAccess;

    public JdbcProjectWorkflowDirectoryService(SystemCapabilityRepository repository, ProjectAccessService projectAccess) {
        this.repository = repository;
        this.projectAccess = projectAccess;
    }

    @Override
    public ProjectScope requireAccessible(String projectRef, AuthUser actor) {
        ProjectAccess project = projectAccess.requireAccessible(projectRef, actor);
        return new ProjectScope(project.id(), project.projectRef(), project.projectName());
    }

    @Override
    public void requireAccessible(long projectId, AuthUser actor) {
        requireProject(projectId, actor.tenantId());
        if (isSuperAdmin(actor)) {
            return;
        }
        if (repository.activeMemberCount(params(projectId, actor, null)) == 0) throw new BusinessException(ErrorCode.FORBIDDEN, "无该项目数据访问权限");
    }

    @Override
    public void requireManageable(long projectId, AuthUser actor) {
        requireProject(projectId, actor.tenantId());
        if (isSuperAdmin(actor)) return;
        if (repository.manageableProjectCount(params(projectId, actor, null)) == 0) throw new BusinessException(ErrorCode.FORBIDDEN, "没有该项目的工作流管理权限");
    }

    @Override
    public List<Long> accessibleProjectIds(AuthUser actor) {
        if (isSuperAdmin(actor)) {
            return repository.allProjectIds(actor.tenantId());
        }
        return repository.memberProjectIds(params(0, actor, null));
    }

    @Override
    public List<ProjectWorkflowMember> members(long projectId, AuthUser actor) {
        requireAccessible(projectId, actor);
        return repository.workflowMembers(params(projectId, actor, null)).stream().map(this::member).toList();
    }

    @Override
    public List<ProjectWorkflowRole> roles(long projectId, AuthUser actor) {
        requireAccessible(projectId, actor);
        return repository.workflowRoles(params(projectId, actor, null)).stream().map(row -> new ProjectWorkflowRole(((Number) row.get("id")).longValue(), String.valueOf(row.get("role_code")), String.valueOf(row.get("role_name")))).toList();
    }

    @Override
    public void requireMembers(long projectId, Collection<Long> userIds, AuthUser actor) {
        requireAccessible(projectId, actor);
        LinkedHashSet<Long> expected = positiveIds(userIds);
        if (expected.isEmpty()) return;
        List<Long> actual = repository.activeProjectUserIds(params(projectId, actor, expected));
        if (new LinkedHashSet<>(actual).size() != expected.size()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "部分审批人不是当前项目的有效成员");
        }
    }

    @Override
    public List<ProjectWorkflowMember> membersForRoles(long projectId, Collection<Long> roleIds, AuthUser actor) {
        requireAccessible(projectId, actor);
        LinkedHashSet<Long> expected = positiveIds(roleIds);
        if (expected.isEmpty()) return List.of();
        if (repository.projectRoleCount(params(projectId, actor, expected)) != expected.size()) throw new BusinessException(ErrorCode.BAD_REQUEST, "部分审批角色不属于当前项目");
        return repository.workflowMembersForRoles(params(projectId, actor, expected)).stream().map(this::member).toList();
    }

    private void requireProject(long projectId, long tenantId) {
        if (repository.projectCount(Map.of("projectId", projectId, "tenantId", tenantId)) == 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "项目不存在或已删除");
    }

    private boolean isSuperAdmin(AuthUser actor) {
        return repository.superAdminCount(Map.of("userId", actor.id(), "tenantId", actor.tenantId())) > 0;
    }

    private LinkedHashSet<Long> positiveIds(Collection<Long> values) {
        LinkedHashSet<Long> result = new LinkedHashSet<>();
        if (values != null) values.stream().filter(value -> value != null && value > 0).forEach(result::add);
        return result;
    }

    private Map<String, Object> params(long projectId, AuthUser actor, Collection<Long> ids) { Map<String, Object> result = new java.util.LinkedHashMap<>(); result.put("projectId", projectId); result.put("tenantId", actor.tenantId()); result.put("userId", actor.id()); if (ids != null) result.put("ids", ids); return result; }
    private ProjectWorkflowMember member(Map<String, Object> row) { return new ProjectWorkflowMember(((Number) row.get("id")).longValue(), String.valueOf(row.get("username")), String.valueOf(row.get("display_name"))); }
}
