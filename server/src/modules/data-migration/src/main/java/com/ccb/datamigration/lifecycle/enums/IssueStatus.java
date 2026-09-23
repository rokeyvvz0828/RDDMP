package com.ccb.datamigration.lifecycle.enums;

/** 问题状态（4 态，基线文档 3.5）。 */
public enum IssueStatus implements LifecycleStatus {
    WAIT_RECTIFY("待整改", false),
    RECTIFYING("整改中", false),
    CLOSED("已闭环", true),
    CANCELLED("已作废", true);

    private final String displayName;
    private final boolean terminal;

    IssueStatus(String displayName, boolean terminal) {
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
