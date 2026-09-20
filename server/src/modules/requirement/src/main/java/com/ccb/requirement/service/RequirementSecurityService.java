package com.ccb.requirement.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 需求管理服务端授权与数据范围。
 * <p>权限码由项目管理里的三个需求角色提供：需求提出人 / 需求分析员 / 需求统筹管理员；
 * 项目负责人与 PM 角色按平台规则自动拥有本项目全部业务权限。
 * <p>数据范围：统筹与管理员可见项目全量；其余人只见"本人提交 + 当前在本人名下 + 曾流转经手"（仅查看）。
 */
@Service
public class RequirementSecurityService {
    public static final String ACCESS = "requirement:access";
    public static final String READ_OWN = "requirement:read-own";
    public static final String READ_PROJECT = "requirement:read-project";
    public static final String PROPOSE = "requirement:propose";
    public static final String EDIT = "requirement:edit";
    public static final String TRANSFER = "requirement:transfer";
    public static final String WITHDRAW = "requirement:withdraw";
    public static final String REVIEW = "requirement:review";
    public static final String MANAGE = "requirement:manage";
    public static final String ADMIN = "requirement:admin";

    private final JdbcTemplate jdbc;

    public RequirementSecurityService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 当前请求是否持有指定权限码；无安全上下文（单元测试）时退回按权限目录查库。 */
    public boolean hasAuthority(String permissionCode) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            return authentication.getAuthorities().stream()
                    .anyMatch(authority -> permissionCode.equals(authority.getAuthority()));
        }
        Integer granted = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM sys_menu_permission p
                JOIN sys_role_permission rp ON rp.permission_id = p.id AND rp.tenant_id = p.tenant_id
                JOIN sys_user_role ur ON ur.role_id = rp.role_id AND ur.tenant_id = rp.tenant_id
                WHERE p.tenant_id = ? AND p.permission_code = ? AND p.status = 1
                  AND ur.user_id = ?
                """, Integer.class, 1L, permissionCode, 1L);
        return granted != null && granted > 0;
    }

    /** 超管兜底：需求模块配置级权限。 */
    public boolean isAdmin(AuthUser user) {
        return hasAuthority(ADMIN) || hasAuthority(MANAGE);
    }

    /** 需求统筹管理员（或 admin）：项目内全量可见、全部可操作。 */
    public boolean isPmo(AuthUser user) {
        return hasAuthority(READ_PROJECT) || isAdmin(user);
    }

    public boolean isCoordinator(AuthUser user) {
        return isPmo(user);
    }

    public void requireCoordinator(AuthUser user) {
        if (!isCoordinator(user)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅需求统筹管理员可执行该操作");
        }
    }

    /** 提出人收回：只有持有收回权限的人可以把自己重新设为当前处理人。 */
    public void requireWithdrawPermission(AuthUser user) {
        if (!hasAuthority(WITHDRAW) && !isAdmin(user)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅需求提出人可收回需求");
        }
    }

    /** 是否需要按"本人相关"范围过滤（非统筹）。 */
    public boolean restrictToOwnScope(AuthUser user) {
        return !isPmo(user);
    }

    /**
     * 差异列表数据范围条件（追加到 WHERE 之后），参数按顺序写入 args：
     * created_by / current_handler_user_id / 流转日志 to_user_id / from_user_id。
     */
    public String differenceScopeSql(String alias, AuthUser user, List<Object> args) {
        if (!restrictToOwnScope(user)) {
            return "";
        }
        args.add(user.id());
        args.add(user.id());
        args.add("NEW_PROJECT_DIFF");
        args.add(user.id());
        args.add(user.id());
        return " AND (" + alias + ".created_by = ? OR " + alias + ".current_handler_user_id = ?"
                + " OR EXISTS (SELECT 1 FROM req_flow_log l"
                + " WHERE l.tenant_id = " + alias + ".tenant_id AND l.requirement_id = " + alias + ".id"
                + " AND l.requirement_kind = ? AND l.deleted = 0 AND (l.to_user_id = ? OR l.from_user_id = ?)))";
    }

    /**
     * 存量需求列表数据范围条件，参数顺序：
     * created_by / current_flow_user_id / 流转日志 to_user_id / from_user_id。
     */
    public String legacyScopeSql(String alias, AuthUser user, List<Object> args) {
        if (!restrictToOwnScope(user)) {
            return "";
        }
        args.add(user.id());
        args.add(user.id());
        args.add("LEGACY");
        args.add(user.id());
        args.add(user.id());
        return " AND (" + alias + ".created_by = ? OR " + alias + ".current_handler_user_id = ?"
                + " OR EXISTS (SELECT 1 FROM req_flow_log l"
                + " WHERE l.tenant_id = " + alias + ".tenant_id AND l.requirement_id = " + alias + ".id"
                + " AND l.requirement_kind = ? AND l.deleted = 0 AND (l.to_user_id = ? OR l.from_user_id = ?)))";
    }

    /** 单条差异是否在当前用户的数据范围内（详情/写操作前校验）。 */
    public boolean canViewDifference(AuthUser user, Map<String, Object> row) {
        if (!restrictToOwnScope(user)) {
            return true;
        }
        Object creator = row.get("created_by");
        Object handler = row.get("current_handler_user_id");
        if (matches(creator, user) || matches(handler, user)) {
            return true;
        }
        Object id = row.get("id");
        if (id == null) {
            return false;
        }
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM req_flow_log
                WHERE tenant_id = ? AND requirement_id = ? AND requirement_kind = 'NEW_PROJECT_DIFF' AND deleted = 0
                  AND (to_user_id = ? OR from_user_id = ?)
                """, Integer.class, user.tenantId(), ((Number) id).longValue(), user.id(), user.id());
        return count != null && count > 0;
    }

    public void requireDifferenceVisible(AuthUser user, Map<String, Object> row) {
        if (!canViewDifference(user, row)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无该需求的数据访问权限");
        }
    }

    /** 单条存量需求是否在当前用户的数据范围内。 */
    public boolean canViewLegacy(AuthUser user, Map<String, Object> row) {
        if (!restrictToOwnScope(user)) {
            return true;
        }
        if (matches(row.get("created_by"), user) || matches(row.get("current_flow_user_id"), user)) {
            return true;
        }
        Object id = row.get("id");
        if (id == null) {
            return false;
        }
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM req_flow_log
                WHERE tenant_id = ? AND requirement_id = ? AND requirement_kind = 'LEGACY' AND deleted = 0
                  AND (to_user_id = ? OR from_user_id = ?)
                """, Integer.class, user.tenantId(), ((Number) id).longValue(), user.id(), user.id());
        return count != null && count > 0;
    }

    public void requireLegacyVisible(AuthUser user, Map<String, Object> row) {
        if (!canViewLegacy(user, row)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无该需求的数据访问权限");
        }
    }

    /** 按需求 id 校验可见性（差异与存量通用）：评审报告附件访问策略与外部只读方复用。 */
    public void requireRequirementVisible(AuthUser user, long requirementId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, requirement_kind, created_by, current_handler_user_id,
                       current_handler_user_id AS current_flow_user_id
                FROM req_requirement
                WHERE tenant_id = ? AND id = ? AND deleted = 0
                """, user.tenantId(), requirementId);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "需求不存在");
        }
        Map<String, Object> row = rows.get(0);
        if ("NEW_PROJECT_DIFF".equals(String.valueOf(row.get("requirement_kind")))) {
            requireDifferenceVisible(user, row);
        } else {
            requireLegacyVisible(user, row);
        }
    }

    /** 项目访问：统筹/管理员豁免，其余人必须是项目有效成员。 */
    public boolean isProjectMember(AuthUser user, long projectId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM pm_project_member
                WHERE tenant_id = ? AND project_id = ? AND user_id = ? AND status = 1 AND deleted = 0
                """, Integer.class, user.tenantId(), projectId, user.id());
        return count != null && count > 0;
    }

    public void requireProjectAccess(AuthUser user, long projectId) {
        if (isPmo(user) || isProjectMember(user, projectId)) {
            return;
        }
        throw new BusinessException(ErrorCode.FORBIDDEN, "无该项目数据访问权限");
    }

    /** 项目可见性：统筹/管理员、项目有效成员可见；其余人不可见。 */
    public void requireProjectVisible(AuthUser user, long projectId) {
        requireProjectAccess(user, projectId);
    }

    /** 差异是否可编辑：统筹/管理员、创建人、当前处理人。 */
    public boolean canEditDifference(AuthUser user, Map<String, Object> row, boolean admin) {
        if (admin || isPmo(user)) {
            return true;
        }
        return matches(row.get("created_by"), user) || matches(row.get("current_handler_user_id"), user);
    }

    public void requireDifferenceEditable(AuthUser user, Map<String, Object> row) {
        if (!canEditDifference(user, row, isAdmin(user))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅提出人、当前处理人或需求统筹管理员可编辑该需求");
        }
    }

    /** 存量需求是否可编辑：统筹/管理员、创建人、当前处理人。 */
    public boolean canEditLegacy(AuthUser user, Map<String, Object> row, boolean admin) {
        if (admin || isPmo(user)) {
            return true;
        }
        return matches(row.get("created_by"), user) || matches(row.get("current_flow_user_id"), user);
    }

    public void requireLegacyEditable(AuthUser user, Map<String, Object> row) {
        if (!canEditLegacy(user, row, isAdmin(user))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅提出人、当前处理人或需求统筹管理员可编辑该需求");
        }
    }

    public boolean isCurrentFlowAssignee(AuthUser user, long requirementId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM req_requirement
                WHERE tenant_id = ? AND id = ? AND deleted = 0 AND current_handler_user_id = ?
                """, Integer.class, user.tenantId(), requirementId, user.id());
        return count != null && count > 0;
    }

    /** 存量需求成员维护：仅统筹/管理员（原显式成员口径已下线）。 */
    public void requireLegacyMemberManage(AuthUser user) {
        requireCoordinator(user);
    }

    public void requireLegacyRequirementAccess(AuthUser user, long requirementId, String businessGroup) {
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT id, created_by, current_handler_user_id AS current_flow_user_id"
                        + " FROM req_requirement WHERE tenant_id = ? AND id = ? AND deleted = 0",
                user.tenantId(), requirementId);
        requireLegacyVisible(user, row);
    }

    /** 保留签名：业务组口径已下线，数据范围改由角色决定。 */
    public boolean isBusinessGroupMember(AuthUser user, String businessGroup) {
        return isPmo(user);
    }

    public void requireLegacyAccess(AuthUser user, String businessGroup) {
        if (!isPmo(user)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无该需求的数据访问权限");
        }
    }

    public List<String> myBusinessGroups(AuthUser user) {
        return List.of();
    }

    private boolean matches(Object value, AuthUser user) {
        return value instanceof Number number && number.longValue() == user.id();
    }
}
