package com.ccb.datamigration.lifecycle.enums;

/** 校验顺序固定（铁律 #12）：存在性 → 数量约束 → 状态约束，保证同一非法入参每次返回同一错误码。 */
public enum LifecycleValidationPhase {
    EXISTENCE,
    QUANTITY,
    STATE
}
