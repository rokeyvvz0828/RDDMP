package com.ccb.architecture.change.number;

import com.ccb.architecture.change.model.SubsystemNumberKind;

/** 全局逻辑序号或父逻辑下物理槽位超过批准容量时抛出。 */
public final class SubsystemNumberCapacityExceededException extends IllegalStateException {
    public SubsystemNumberCapacityExceededException(SubsystemNumberKind kind, int ordinal, int maximum) {
        super(kind + " 编号序号 " + ordinal + " 超出允许上限 " + maximum);
    }
}
