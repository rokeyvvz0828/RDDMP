package com.ccb.datamigration.lifecycle.model;

/** 角色选择器选项：仅返回启用角色（sys_role.status=1 AND deleted=0）。 */
public record LifecycleRoleOption(long id, long tenantId, String roleCode, String roleName) {
}
