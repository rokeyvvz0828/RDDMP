package com.ccb.datamigration.lifecycle;

/** 四档数据范围解析结果（R9）：管理员 > 审核人 > 负责人 > 执行人。 */
public record LifecycleDataScopeSet(boolean admin, boolean reviewer, boolean owner, boolean executor) {
}
