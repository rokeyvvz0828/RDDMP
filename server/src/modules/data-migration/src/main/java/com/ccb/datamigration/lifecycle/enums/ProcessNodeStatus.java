package com.ccb.datamigration.lifecycle.enums;

/** 工序节点状态（5 态，基线文档 3.3）。 */
public enum ProcessNodeStatus implements LifecycleStatus {
    LOCKED("未解锁", false),
    EXECUTING("执行中", false),
    REVIEWING("待审核", false),
    REJECTED("审核打回", false),
    CLOSED("已闭环", true);

    private final String displayName;
    private final boolean terminal;

    ProcessNodeStatus(String displayName, boolean terminal) {
        this.displayName = displayName;
        this.terminal = terminal;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public String displayName() {
        return displayName;
    }

    @Override
    public boolean terminal() {
        return terminal;
    }
}
