package com.ccb.datamigration.lifecycle.enums;

/** 风险状态（6 态，基线文档 3.6；已规避/已发生/已闭环互斥）。 */
public enum RiskStatus implements LifecycleStatus {
    WAIT_PREVENT("待防控", false),
    PREVENTING("防控中", false),
    AVOIDED("已规避", true),
    OCCURRED("已发生", false),
    CLOSED("已闭环", true),
    CANCELLED("已作废", true);

    private final String displayName;
    private final boolean terminal;

    RiskStatus(String displayName, boolean terminal) {
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
