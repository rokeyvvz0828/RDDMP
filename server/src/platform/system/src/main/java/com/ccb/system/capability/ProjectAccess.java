package com.ccb.system.capability;

/** 已通过当前用户访问校验的项目最小引用。 */
public record ProjectAccess(long id, String projectRef, String projectName) {
}
