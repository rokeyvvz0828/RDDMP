package com.ccb.system.internal.capability;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.ProjectAccess;
import com.ccb.system.capability.ProjectAccessService;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Objects;

@Service
public class JdbcProjectAccessService implements ProjectAccessService {
    private final SystemCapabilityRepository repository;

    public JdbcProjectAccessService(SystemCapabilityRepository repository) {
        this.repository = repository;
    }

    @Override
    public ProjectAccess requireAccessible(String projectRef, AuthUser actor) {
        Objects.requireNonNull(actor, "actor 不能为空");
        String normalizedRef = normalizeProjectRef(projectRef);
        Map<String, Object> row = repository.projectByRef(Map.of("tenantId", actor.tenantId(), "projectRef", normalizedRef));
        if (row == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "项目不存在或已删除");
        }
        ProjectAccess project = new ProjectAccess(((Number) row.get("id")).longValue(), String.valueOf(row.get("project_code")), String.valueOf(row.get("project_name")));
        if (isSuperAdmin(actor) || isActiveMember(project.id(), actor)) {
            return project;
        }
        throw new BusinessException(ErrorCode.FORBIDDEN, "无该项目数据访问权限");
    }

    private boolean isSuperAdmin(AuthUser actor) {
        return repository.superAdminCount(Map.of("userId", actor.id(), "tenantId", actor.tenantId())) > 0;
    }

    private boolean isActiveMember(long projectId, AuthUser actor) {
        return repository.activeMemberCount(Map.of("projectId", projectId, "tenantId", actor.tenantId(), "userId", actor.id())) > 0;
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
