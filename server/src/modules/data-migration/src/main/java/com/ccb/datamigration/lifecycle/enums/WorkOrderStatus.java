package com.ccb.datamigration.lifecycle.enums;

/** 工单整体状态（9 态，基线文档 3.2）。 */
public enum WorkOrderStatus implements LifecycleStatus {
    WAIT_PRE("待前置", false),
    WAIT_ACCEPT("待接收", false),
    EXECUTING("执行中", false),
    SUSPENDED("已暂停", false),
    REVIEWING("审核中", false),
    REVIEW_REJECTED("审核打回", false),
    CLOSED("已闭环", false),
    ARCHIVED("已归档", true),
    CANCELLED("已作废", true);

    private final String displayName;
    private final boolean terminal;

    WorkOrderStatus(String displayName, boolean terminal) {
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
