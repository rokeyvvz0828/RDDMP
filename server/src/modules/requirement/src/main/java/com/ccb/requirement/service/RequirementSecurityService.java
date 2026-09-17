package com.ccb.requirement.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** 服务端数据范围与实体授权：管理员豁免，项目组成员/业务组成员按映射表校验。 */
@Service
public class RequirementSecurityService {
    private final RequirementSecurityRepository repository;

    public RequirementSecurityService(RequirementSecurityRepository repository) {
        this.repository = repository;
    }

    public boolean isAdmin(AuthUser user) {
        return repository.hasPermission(user.tenantId(), user.id(), "requirement:admin");
    }

    /** PMO：需求管理统筹角色，全量查看、阶段推进、发起流转、标记完成。 */
    public boolean isPmo(AuthUser user) {
        return repository.hasPermission(user.tenantId(), user.id(), "requirement:pmo");
    }

    public boolean isLegacySystemOwner(AuthUser user, long requirementId) {
        return repository.isLegacySystemOwner(user.tenantId(), requirementId, user.id());
    }

    /** 系统人员：主责/协同系统行负责人或系统人员表中的成员。 */
    public boolean isLegacySystemMember(AuthUser user, long requirementId) {
        return repository.isLegacySystemMember(user.tenantId(), requirementId, user.id());
    }

    /** 当前流转处理人（整条需求流转）。 */
    public boolean isCurrentFlowAssignee(AuthUser user, long requirementId) {
        return repository.isCurrentFlowAssignee(user.tenantId(), requirementId, user.id());
    }

    /** 存量需求显式成员（参考新建项目 req_project_member）。 */
    public boolean isLegacyMember(AuthUser user, long requirementId) {
        return repository.isLegacyMember(user.tenantId(), requirementId, user.id());
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
        return repository.isProjectMember(user.tenantId(), projectId, user.id());
    }

    public void requireProjectAccess(AuthUser user, long projectId) {
        if (!isAdmin(user) && !isProjectMember(user, projectId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无该项目数据访问权限");
        }
    }

    /** 项目可见性：需求模块内所有登录用户可查看项目及差异（不再限制项目成员）。 */
    public void requireProjectVisible(AuthUser user, long projectId) {
        if (!repository.hasActiveProject(user.tenantId(), projectId)) {
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
        return repository.isBusinessGroupMember(user.tenantId(), businessGroup, user.id());
    }

    public void requireLegacyAccess(AuthUser user, String businessGroup) {
        if (!isAdmin(user) && !isBusinessGroupMember(user, businessGroup)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无该业务组需求数据访问权限");
        }
    }

    public List<String> myBusinessGroups(AuthUser user) {
        return repository.listBusinessGroups(user.tenantId(), user.id());
    }
}
