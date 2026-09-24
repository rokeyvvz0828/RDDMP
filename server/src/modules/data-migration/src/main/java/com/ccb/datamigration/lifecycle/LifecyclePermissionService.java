package com.ccb.datamigration.lifecycle;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.datamigration.service.DataMigrationPermissionService;
import com.ccb.datamigration.lifecycle.error.LifecycleErrorCode;
import com.ccb.security.model.AuthUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** 生命周期平台服务端 RBAC 与四档数据范围（管理员/审核人/组件与项目负责人/普通执行人）解析与授权（R9）。 */
@Service
public class LifecyclePermissionService {
    private static final String LIFECYCLE_MANAGE_PERMISSION = "data-migration-lifecycle:manage";
    private static final String LIFECYCLE_REVIEW_PERMISSION = "data-migration-lifecycle:review";
    private static final String LIFECYCLE_AUDIT_PASS_PERMISSION = "data-migration-lifecycle:audit:pass";
    private static final String LIFECYCLE_AUDIT_BATCH_PERMISSION = "data-migration-lifecycle:audit:batch";
    private static final String LIFECYCLE_AUDIT_REVOKE_PERMISSION = "data-migration-lifecycle:audit:revoke";

    private final JdbcTemplate jdbc;
    private final DataMigrationPermissionService dmPermissions;

    public LifecyclePermissionService(JdbcTemplate jdbc, DataMigrationPermissionService dmPermissions) {
        this.jdbc = jdbc;
        this.dmPermissions = dmPermissions;
    }

    /** 数据迁移管理员（含超级管理员）：直接复用既有角色判定，或授予生命周期平台管理权限点。 */
    public boolean isAdmin(AuthUser user) {
        return dmPermissions.isAdmin(user) || userHasPermission(user, LIFECYCLE_MANAGE_PERMISSION);
    }

    /** 审核人：管理员或持有生命周期审核权限点。 */
    public boolean isReviewer(AuthUser user) {
        return isAdmin(user) || userHasPermission(user, LIFECYCLE_REVIEW_PERMISSION);
    }

    /** 组件负责人：dm_component.owner_id 且未删除。 */
    public boolean isOwnerOfComponent(AuthUser user, long componentId) {
        return exists("SELECT COUNT(*) FROM dm_component WHERE id = ? AND tenant_id = ? AND owner_id = ? AND deleted = 0",
                componentId, user.tenantId(), user.id());
    }

    /** 项目负责人：pm_project.owner_id 且未删除。 */
    public boolean isOwnerOfProject(AuthUser user, long projectId) {
        return exists("SELECT COUNT(*) FROM pm_project WHERE id = ? AND tenant_id = ? AND owner_id = ? AND deleted = 0",
                projectId, user.tenantId(), user.id());
    }

    /** 普通执行人：工单/工序归属人（后续批次传入 assignee/executor 用户主键）。 */
    public boolean isExecutor(AuthUser user, long executorId) {
        return user.id() == executorId;
    }

    /** 四档数据范围解析：管理员/审核人/负责人/执行人逐档判定，列表与统计必须先过本方法再过滤（铁律 #8）。 */
    public LifecycleDataScopeSet resolveScope(AuthUser user, Long componentId, Long projectId, Long executorId) {
        boolean admin = isAdmin(user);
        boolean reviewer = admin || isReviewer(user);
        boolean owner = (componentId != null && isOwnerOfComponent(user, componentId))
                || (projectId != null && isOwnerOfProject(user, projectId));
        boolean executor = admin || (executorId != null && isExecutor(user, executorId));
        return new LifecycleDataScopeSet(admin, reviewer, owner, executor);
    }

    public void requireAdmin(AuthUser user) {
        if (!isAdmin(user)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "需要数据迁移管理员权限");
        }
    }

    /** 审核操作刚性判定（基线 15.2.1）：仅配置为工序审核角色的用户可执行通过/打回；
     *  数据迁移管理员可查看全量台账但不得代审（谁审核谁负责）。 */
    public void requireReviewAction(AuthUser user) {
        if (isAdmin(user)) {
            throw new BusinessException(LifecycleErrorCode.AUDIT_PERMISSION_DENIED, "管理员不得代审，审核权责仅归审核角色");
        }
        if (!userHasPermission(user, LIFECYCLE_AUDIT_PASS_PERMISSION)) {
            throw new BusinessException(LifecycleErrorCode.AUDIT_PERMISSION_DENIED, "仅配置为工序审核角色的用户可执行审核");
        }
    }

    /** 审核/打回操作权限点（菜单 750 权限点 7502）。 */
    public boolean canAudit(AuthUser user) {
        return !isAdmin(user) && userHasPermission(user, LIFECYCLE_AUDIT_PASS_PERMISSION);
    }

    /** 批量打回/撤销操作权限点（菜单 750 权限点 7503/7504）。 */
    public boolean canAuditBatch(AuthUser user) {
        return canAudit(user) && userHasPermission(user, LIFECYCLE_AUDIT_BATCH_PERMISSION)
                && userHasPermission(user, LIFECYCLE_AUDIT_REVOKE_PERMISSION);
    }

    /** 按四档数据范围要求授权，不满足时返回 40300（无权限写操作必拒）。 */
    public void requireDataScope(AuthUser user, LifecycleDataScope required, Long componentId, Long projectId, Long executorId) {
        LifecycleDataScopeSet actual = resolveScope(user, componentId, projectId, executorId);
        boolean allowed = switch (required) {
            case ADMIN -> actual.admin();
            case REVIEWER -> actual.reviewer();
            case OWNER -> actual.owner();
            case EXECUTOR -> actual.executor();
        };
        if (!allowed) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无该数据范围的操作权限：" + required);
        }
    }

    /** 服务端权限点判定（与 JWT 注入同源：sys_menu_permission -> sys_role_permission -> sys_user_role）。 */
    public boolean userHasPermission(AuthUser user, String permission) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_menu_permission p "
                        + "JOIN sys_role_permission rp ON rp.permission_id = p.id AND rp.tenant_id = p.tenant_id "
                        + "JOIN sys_user_role ur ON ur.role_id = rp.role_id AND ur.tenant_id = rp.tenant_id "
                        + "JOIN sys_role r ON r.id = ur.role_id AND r.tenant_id = ur.tenant_id "
                        + "WHERE ur.user_id = ? AND p.tenant_id = ? AND p.permission_code = ? AND p.status = 1 "
                        + "AND r.status = 1 AND r.deleted = 0",
                Integer.class, user.id(), user.tenantId(), permission);
        return count != null && count > 0;
    }

    private boolean exists(String sql, Object... args) {
        Integer count = jdbc.queryForObject(sql, Integer.class, args);
        return count != null && count > 0;
    }
}
