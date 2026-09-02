package com.ccb.system.capability;

/** 项目成员的最小只读引用，不暴露 system 私有字段。 */
public record ProjectMemberReference(long id, long userId, String displayName, String username) {
}
