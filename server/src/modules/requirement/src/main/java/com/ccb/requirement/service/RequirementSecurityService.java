package com.ccb.requirement.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/** 服务端数据范围与实体授权：管理员豁免，项目组成员/业务组成员按映射表校验。 */
@Service
public class RequirementSecurityService {
    private final JdbcTemplate jdbc;

    public RequirementSecurityService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean isAdmin(AuthUser user) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM sys_user_role ur
                JOIN sys_role_permission rp ON rp.role_id = ur.role_id AND rp.tenant_id = ur.tenant_id
                JOIN sys_menu_permission mp ON mp.id = rp.permission_id AND mp.tenant_id = rp.tenant_id
                WHERE ur.user_id = ? AND ur.tenant_id = ? AND mp.permission_code = 'requirement:admin'
                """, Integer.class, user.id(), user.tenantId());
        return count != null && count > 0;
    }

    public boolean isProjectMember(AuthUser user, long projectId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM req_project_member WHERE tenant_id = ? AND project_id = ? AND user_id = ? AND deleted = 0",
                Integer.class, user.tenantId(), projectId, user.id());
        return count != null && count > 0;
    }

    public void requireProjectAccess(AuthUser user, long projectId) {
        if (!isAdmin(user) && !isProjectMember(user, projectId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无该项目数据访问权限");
        }
    }

    public boolean isBusinessGroupMember(AuthUser user, String businessGroup) {
        if (businessGroup == null || businessGroup.isBlank()) {
            return false;
        }
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM req_business_group_member WHERE tenant_id = ? AND business_group = ? AND user_id = ? AND deleted = 0",
                Integer.class, user.tenantId(), businessGroup, user.id());
        return count != null && count > 0;
    }

    public void requireLegacyAccess(AuthUser user, String businessGroup) {
        if (!isAdmin(user) && !isBusinessGroupMember(user, businessGroup)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无该业务组需求数据访问权限");
        }
    }

    public List<String> myBusinessGroups(AuthUser user) {
        return jdbc.queryForList(
                "SELECT business_group FROM req_business_group_member WHERE tenant_id = ? AND user_id = ? AND deleted = 0",
                String.class, user.tenantId(), user.id());
    }
}
