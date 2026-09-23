package com.ccb.datamigration.lifecycle.model;

/** 成员选择器选项：仅返回启用成员（复用 platform/system 只读契约）。 */
public record LifecycleMemberOption(long id, String displayName, String username, String phone, boolean active) {
}
