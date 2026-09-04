package com.ccb.datamigration.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.ProjectWorkflowDirectoryService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DataMigrationPermissionService {
    private final JdbcTemplate jdbc;
    private final ProjectWorkflowDirectoryService projectAccess;

    public DataMigrationPermissionService(JdbcTemplate jdbc, ProjectWorkflowDirectoryService projectAccess) {
        this.jdbc = jdbc;
        this.projectAccess = projectAccess;
    }

    /**
     * 项目隔离校验（T32）：数据迁移的每一次查询与写入都必须落在单个项目内。
     *
     * <p>项目可达性口径（租户内项目存在且未删除 + 平台超级管理员豁免 + 活动成员行）由 platform/system
     * 的公开契约 {@link ProjectWorkflowDirectoryService} 单一提供，本模块<b>不复制任何项目/成员 SQL</b>
     * （T32-r1：调用平台侧成员口径）。因此项目缺失/跨租户/已删除按 {@code BAD_REQUEST} 拒绝，
     * 非成员按 {@code FORBIDDEN} 拒绝，文案与 release 等模块保持一致。
     *
     * <p>口径收紧提示：本模块的 {@code ADMIN}/{@code DATA_MIGRATION_ADMIN} 只豁免功能权限
     * （RBAC 动作码），不再豁免项目数据范围；数据范围的豁免由平台判定。
     *
     * @param projectId 前端携带（新增/查询）或库中记录（维护）的项目标识
     * @return 校验通过的项目标识，便于调用方直接用于 SQL 绑定
     */
    public long requireAccessible(long projectId, AuthUser user) {
        projectAccess.requireAccessible(projectId, user);
        return projectId;
    }

    /** 调用者可访问的项目标识集合：直接取平台成员口径，用于看板计数与项目选项，不在本模块重算。 */
    public List<Long> accessibleProjectIds(AuthUser user) {
        return projectAccess.accessibleProjectIds(user);
    }

    /** 项目隔离校验的必填入口：查询与新增未携带 {@code projectId} 时直接拒绝，不回退为全项目范围。 */
    public long requireProject(Long projectId, AuthUser user) {
        if (projectId == null || projectId <= 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "projectId is required");
        return requireAccessible(projectId, user);
    }

    /** 维护类操作的项目归属校验：恒取库中记录的 {@code project_id}，入参项目不参与归属判定。 */
    public long requireStoredProject(Object storedProjectId, AuthUser user) {
        if (!(storedProjectId instanceof Number number)) throw new BusinessException(ErrorCode.BAD_REQUEST, "Record project ownership is missing");
        return requireAccessible(number.longValue(), user);
    }

    /** 功能权限（RBAC 动作码）口径下的管理员；不参与项目数据范围判定。 */
    public boolean isAdmin(AuthUser user) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM sys_user_role ur JOIN sys_role r ON r.id = ur.role_id AND r.tenant_id = ur.tenant_id WHERE ur.user_id = ? AND ur.tenant_id = ? AND r.role_code IN ('ADMIN','SUPER_ADMIN','DATA_MIGRATION_ADMIN') AND r.status = 1 AND r.deleted = 0", Integer.class, user.id(), user.tenantId());
        return count != null && count > 0;
    }

    public void requireCategoryPermission(AuthUser user, String category, String action) {
        if (isAdmin(user)) return;
        String normalizedCategory;
        if ("INTERMEDIATE".equalsIgnoreCase(category)) normalizedCategory = "intermediate";
        else if ("TARGET".equalsIgnoreCase(category)) normalizedCategory = "target";
        else throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的表结构类别");
        String base = "data-migration:base:table-fields-" + normalizedCategory;
        String permission = "read".equals(action) ? base : base + ":" + action;
        Integer allowed = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_menu_permission p "
                        + "JOIN sys_role_permission rp ON rp.permission_id = p.id AND rp.tenant_id = p.tenant_id "
                        + "JOIN sys_user_role ur ON ur.role_id = rp.role_id AND ur.tenant_id = rp.tenant_id "
                        + "JOIN sys_role r ON r.id = ur.role_id AND r.tenant_id = ur.tenant_id "
                        + "WHERE ur.user_id = ? AND p.tenant_id = ? AND p.permission_code = ? AND p.action_code = ? "
                        + "AND p.status = 1 AND r.status = 1 AND r.deleted = 0",
                Integer.class, user.id(), user.tenantId(), permission, action);
        if (allowed == null || allowed == 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "没有" + normalizedCategory + "表结构的" + action + "权限");
        }
    }

    public void requireWrite(AuthUser user, long ownerId) {
        if (!isAdmin(user) && user.id() != ownerId) throw new BusinessException(ErrorCode.FORBIDDEN, "Only administrators or the owner can modify this record");
    }

    public void requireAdmin(AuthUser user) {
        if (!isAdmin(user)) throw new BusinessException(ErrorCode.FORBIDDEN, "Administrator permission required");
    }
}
