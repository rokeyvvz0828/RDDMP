package com.ccb.datamigration.lifecycle;

/** 生命周期平台四档数据范围（基线文档第 8 章）：数据迁移管理员 > 审核人 > 组件/项目负责人 > 普通执行人。 */
public enum LifecycleDataScope {
    ADMIN,
    REVIEWER,
    OWNER,
    EXECUTOR
}
