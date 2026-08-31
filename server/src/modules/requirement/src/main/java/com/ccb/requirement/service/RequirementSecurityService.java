package com.ccb.requirement.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

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

    /** PMO：需求管理统筹角色，全量查看、阶段推进、发起流转、标记完成。 */
    public boolean isPmo(AuthUser user) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM sys_user_role ur
                JOIN sys_role_permission rp ON rp.role_id = ur.role_id AND rp.tenant_id = ur.tenant_id
                JOIN sys_menu_permission mp ON mp.id = rp.permission_id AND mp.tenant_id = rp.tenant_id
                WHERE ur.user_id = ? AND ur.tenant_id = ? AND mp.permission_code = 'requirement:pmo'
                """, Integer.class, user.id(), user.tenantId());
        return count != null && count > 0;
    }

    public boolean isLegacySystemOwner(AuthUser user, long requirementId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM req_legacy_system_item
                WHERE tenant_id = ? AND requirement_id = ? AND owner_user_id = ? AND deleted = 0
                """, Integer.class, user.tenantId(), requirementId, user.id());
        return count != null && count > 0;
    }

    /** 系统人员：主责/协同系统行负责人或系统人员表中的成员。 */
    public boolean isLegacySystemMember(AuthUser user, long requirementId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM req_legacy_system_item si
                LEFT JOIN req_legacy_system_member m
                       ON m.system_item_id = si.id AND m.tenant_id = si.tenant_id AND m.deleted = 0
                WHERE si.tenant_id = ? AND si.requirement_id = ? AND si.deleted = 0
                  AND (si.owner_user_id = ? OR m.user_id = ?)
                """, Integer.class, user.tenantId(), requirementId, user.id(), user.id());
        return count != null && count > 0;
    }

    /** 当前流转处理人（整条需求流转）。 */
    public boolean isCurrentFlowAssignee(AuthUser user, long requirementId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM req_legacy_requirement
                WHERE tenant_id = ? AND id = ? AND deleted = 0 AND current_flow_user_id = ?
                """, Integer.class, user.tenantId(), requirementId, user.id());
        return count != null && count > 0;
    }

    /** 存量需求显式成员（参考新建项目 req_project_member）。 */
    public boolean isLegacyMember(AuthUser user, long requirementId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM req_legacy_member
                WHERE tenant_id = ? AND requirement_id = ? AND user_id = ? AND deleted = 0
                """, Integer.class, user.tenantId(), requirementId, user.id());
        return count != null && count > 0;
    }

    /** 存量需求数据范围：PMO/管理员全量；否则需求显式成员、业务组成员、系统行负责人/成员或当前流转处理人可见。 */
    public void requireLegacyRequirementAccess(AuthUser user, long requirementId, String businessGroup) {
        if (isPmo(user) || isAdmin(user) || isBusinessGroupMember(user, businessGroup)
                || isLegacySystemOwner(user, requirementId)
                || isLegacySystemMember(user, requirementId)
                || isCurrentFlowAssignee(user, requirementId)
                || isLegacyMember(user, requirementId)) {
            return;
        }
        throw new BusinessException(ErrorCode.FORBIDDEN, "无该存量需求数据访问权限");
    }

    /** 存量需求成员维护权限：仅 PMO/管理员。 */
    public void requireLegacyMemberManage(AuthUser user) {
        if (!isPmo(user) && !isAdmin(user)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅 PMO/管理员可维护存量需求成员");
        }
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

    /** 项目可见性：需求模块内所有登录用户可查看项目及差异（不再限制项目成员）。 */
    public void requireProjectVisible(AuthUser user, long projectId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM req_project WHERE tenant_id = ? AND id = ? AND deleted = 0",
                Integer.class, user.tenantId(), projectId);
        if (count == null || count == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "项目不存在");
        }
    }

    /** 差异是否可编辑：管理员、创建人或当前处理人。admin 由调用方预计算，避免列表逐行查库。 */
    public boolean canEditDifference(AuthUser user, Map<String, Object> row, boolean admin) {
        if (admin) return true;
        Number creator = (Number) row.get("created_by");
        Number handler = (Number) row.get("current_handler_user_id");
        return (creator != null && creator.longValue() == user.id())
                || (handler != null && handler.longValue() == user.id());
    }

    /** 差异编辑授权：管理员、创建人或当前处理人，否则 403。 */
    public void requireDifferenceEditable(AuthUser user, Map<String, Object> row) {
        if (canEditDifference(user, row, isAdmin(user))) return;
        throw new BusinessException(ErrorCode.FORBIDDEN, "仅管理员、创建人或当前处理人可编辑该差异");
    }

    /** 存量需求是否可编辑：管理员、创建人或当前流转处理人（与新建项目差异同一规则）。admin 由调用方预计算。 */
    public boolean canEditLegacy(AuthUser user, Map<String, Object> row, boolean admin) {
        if (admin) return true;
        Number creator = (Number) row.get("created_by");
        Number handler = (Number) row.get("current_flow_user_id");
        return (creator != null && creator.longValue() == user.id())
                || (handler != null && handler.longValue() == user.id());
    }

    /** 存量需求编辑授权：管理员、创建人或当前流转处理人，否则 403。 */
    public void requireLegacyEditable(AuthUser user, Map<String, Object> row) {
        if (canEditLegacy(user, row, isAdmin(user))) return;
        throw new BusinessException(ErrorCode.FORBIDDEN, "仅管理员、创建人或当前流转处理人可编辑该需求");
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
