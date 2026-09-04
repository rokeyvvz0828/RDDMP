package com.ccb.system.internal.capability;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.ProjectAccess;
import com.ccb.system.capability.ProjectAccessService;
import com.ccb.system.capability.ProjectWorkflowDirectoryService;
import com.ccb.system.capability.ProjectWorkflowMember;
import com.ccb.system.capability.ProjectWorkflowRole;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

@Service
public class JdbcProjectWorkflowDirectoryService implements ProjectWorkflowDirectoryService {
    private final JdbcTemplate jdbc;
    private final ProjectAccessService projectAccess;

    public JdbcProjectWorkflowDirectoryService(JdbcTemplate jdbc, ProjectAccessService projectAccess) {
        this.jdbc = jdbc;
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
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM pm_project_member WHERE project_id = ? AND tenant_id = ? AND user_id = ? AND status = 1 AND deleted = 0", Integer.class, projectId, actor.tenantId(), actor.id());
        if (count == null || count == 0) throw new BusinessException(ErrorCode.FORBIDDEN, "无该项目数据访问权限");
    }

    @Override
    public void requireManageable(long projectId, AuthUser actor) {
        requireProject(projectId, actor.tenantId());
        if (isSuperAdmin(actor)) return;
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM pm_project p
                WHERE p.id = ? AND p.tenant_id = ? AND p.deleted = 0
                  AND (p.owner_id = ? OR EXISTS (
                    SELECT 1 FROM pm_project_member m
                    JOIN pm_project_member_role mr ON mr.member_id = m.id AND mr.tenant_id = m.tenant_id
                    JOIN pm_project_role r ON r.id = mr.role_id AND r.tenant_id = mr.tenant_id
                    WHERE m.project_id = p.id AND m.user_id = ? AND m.status = 1 AND m.deleted = 0
                      AND r.project_id = p.id AND r.role_code = 'PM' AND r.deleted = 0
                  ))
                """, Integer.class, projectId, actor.tenantId(), actor.id(), actor.id());
        if (count == null || count == 0) throw new BusinessException(ErrorCode.FORBIDDEN, "没有该项目的工作流管理权限");
    }

    @Override
    public List<Long> accessibleProjectIds(AuthUser actor) {
        if (isSuperAdmin(actor)) {
            return jdbc.queryForList("SELECT id FROM pm_project WHERE tenant_id = ? AND deleted = 0 ORDER BY id", Long.class, actor.tenantId());
        }
        return jdbc.queryForList("SELECT project_id FROM pm_project_member WHERE tenant_id = ? AND user_id = ? AND status = 1 AND deleted = 0 ORDER BY project_id", Long.class, actor.tenantId(), actor.id());
    }

    @Override
    public List<ProjectWorkflowMember> members(long projectId, AuthUser actor) {
        requireAccessible(projectId, actor);
        return jdbc.query("""
                SELECT u.id, u.username, u.display_name
                FROM pm_project_member m
                JOIN sys_user u ON u.id = m.user_id AND u.tenant_id = m.tenant_id
                WHERE m.project_id = ? AND m.tenant_id = ? AND m.status = 1 AND m.deleted = 0
                  AND u.status = 1 AND u.deleted = 0
                ORDER BY u.display_name, u.id
                """, (rs, rowNum) -> new ProjectWorkflowMember(rs.getLong("id"), rs.getString("username"), rs.getString("display_name")), projectId, actor.tenantId());
    }

    @Override
    public List<ProjectWorkflowRole> roles(long projectId, AuthUser actor) {
        requireAccessible(projectId, actor);
        return jdbc.query("SELECT id, role_code, role_name FROM pm_project_role WHERE project_id = ? AND tenant_id = ? AND deleted = 0 ORDER BY role_name, id", (rs, rowNum) -> new ProjectWorkflowRole(rs.getLong("id"), rs.getString("role_code"), rs.getString("role_name")), projectId, actor.tenantId());
    }

    @Override
    public void requireMembers(long projectId, Collection<Long> userIds, AuthUser actor) {
        requireAccessible(projectId, actor);
        LinkedHashSet<Long> expected = positiveIds(userIds);
        if (expected.isEmpty()) return;
        List<Long> actual = jdbc.queryForList("SELECT user_id FROM pm_project_member WHERE project_id = ? AND tenant_id = ? AND status = 1 AND deleted = 0 AND user_id IN (" + placeholders(expected.size()) + ")", Long.class, arguments(projectId, actor.tenantId(), expected));
        if (new LinkedHashSet<>(actual).size() != expected.size()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "部分审批人不是当前项目的有效成员");
        }
    }

    @Override
    public List<ProjectWorkflowMember> membersForRoles(long projectId, Collection<Long> roleIds, AuthUser actor) {
        requireAccessible(projectId, actor);
        LinkedHashSet<Long> expected = positiveIds(roleIds);
        if (expected.isEmpty()) return List.of();
        Integer roles = jdbc.queryForObject("SELECT COUNT(*) FROM pm_project_role WHERE project_id = ? AND tenant_id = ? AND deleted = 0 AND id IN (" + placeholders(expected.size()) + ")", Integer.class, arguments(projectId, actor.tenantId(), expected));
        if (roles == null || roles != expected.size()) throw new BusinessException(ErrorCode.BAD_REQUEST, "部分审批角色不属于当前项目");
        return jdbc.query("SELECT DISTINCT u.id, u.username, u.display_name FROM pm_project_member_role mr JOIN pm_project_role r ON r.id = mr.role_id AND r.tenant_id = mr.tenant_id AND r.deleted = 0 JOIN pm_project_member m ON m.id = mr.member_id AND m.tenant_id = mr.tenant_id AND m.project_id = r.project_id AND m.status = 1 AND m.deleted = 0 JOIN sys_user u ON u.id = m.user_id AND u.tenant_id = m.tenant_id AND u.status = 1 AND u.deleted = 0 WHERE r.project_id = ? AND r.tenant_id = ? AND r.id IN (" + placeholders(expected.size()) + ") ORDER BY u.display_name, u.id", (rs, rowNum) -> new ProjectWorkflowMember(rs.getLong("id"), rs.getString("username"), rs.getString("display_name")), arguments(projectId, actor.tenantId(), expected));
    }

    private void requireProject(long projectId, long tenantId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM pm_project WHERE id = ? AND tenant_id = ? AND deleted = 0", Integer.class, projectId, tenantId);
        if (count == null || count == 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "项目不存在或已删除");
    }

    private boolean isSuperAdmin(AuthUser actor) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM sys_user_role ur JOIN sys_role r ON r.id = ur.role_id AND r.tenant_id = ur.tenant_id WHERE ur.user_id = ? AND ur.tenant_id = ? AND r.role_code = 'SUPER_ADMIN' AND r.status = 1 AND r.deleted = 0", Integer.class, actor.id(), actor.tenantId());
        return count != null && count > 0;
    }

    private LinkedHashSet<Long> positiveIds(Collection<Long> values) {
        LinkedHashSet<Long> result = new LinkedHashSet<>();
        if (values != null) values.stream().filter(value -> value != null && value > 0).forEach(result::add);
        return result;
    }

    private String placeholders(int size) {
        return String.join(",", java.util.Collections.nCopies(size, "?"));
    }

    private Object[] arguments(long projectId, long tenantId, Collection<Long> ids) {
        List<Object> result = new ArrayList<>();
        result.add(projectId);
        result.add(tenantId);
        result.addAll(ids);
        return result.toArray();
    }
}
