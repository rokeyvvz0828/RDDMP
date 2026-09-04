package com.ccb.system.internal.capability;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.ProjectAccess;
import com.ccb.system.capability.ProjectAccessService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class JdbcProjectAccessService implements ProjectAccessService {
    private static final RowMapper<ProjectAccess> PROJECT_MAPPER = (rs, rowNum) -> new ProjectAccess(
            rs.getLong("id"), rs.getString("project_code"), rs.getString("project_name"));

    private final JdbcTemplate jdbc;

    public JdbcProjectAccessService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public ProjectAccess requireAccessible(String projectRef, AuthUser actor) {
        Objects.requireNonNull(actor, "actor 不能为空");
        String normalizedRef = normalizeProjectRef(projectRef);
        List<ProjectAccess> projects = jdbc.query("""
                SELECT id, project_code, project_name
                FROM pm_project
                WHERE tenant_id = ? AND project_code = ? AND deleted = 0
                """, PROJECT_MAPPER, actor.tenantId(), normalizedRef);
        if (projects.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "项目不存在或已删除");
        }
        ProjectAccess project = projects.get(0);
        if (isSuperAdmin(actor) || isActiveMember(project.id(), actor)) {
            return project;
        }
        throw new BusinessException(ErrorCode.FORBIDDEN, "无该项目数据访问权限");
    }

    private boolean isSuperAdmin(AuthUser actor) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM sys_user_role ur
                JOIN sys_role r ON r.id = ur.role_id AND r.tenant_id = ur.tenant_id
                WHERE ur.user_id = ? AND ur.tenant_id = ?
                  AND r.role_code = 'SUPER_ADMIN' AND r.status = 1 AND r.deleted = 0
                """, Integer.class, actor.id(), actor.tenantId());
        return count != null && count > 0;
    }

    private boolean isActiveMember(long projectId, AuthUser actor) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM pm_project_member
                WHERE project_id = ? AND tenant_id = ? AND user_id = ?
                  AND status = 1 AND deleted = 0
                """, Integer.class, projectId, actor.tenantId(), actor.id());
        return count != null && count > 0;
    }

    private String normalizeProjectRef(String projectRef) {
        if (projectRef == null || projectRef.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择项目后重试");
        }
        String normalized = projectRef.trim();
        if (normalized.length() > 64) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "项目标识无效");
        }
        return normalized;
    }
}
