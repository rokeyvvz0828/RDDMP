package com.ccb.system.capability;

/** 用户的最小安全引用，不包含密码、头像对象键和登录信息。 */
public record SystemUserReference(long id, String displayName, String username, String phone, boolean active) {
}
