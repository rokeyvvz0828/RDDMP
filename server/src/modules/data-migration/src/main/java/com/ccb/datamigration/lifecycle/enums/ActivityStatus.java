package com.ccb.datamigration.lifecycle.enums;

/** 活动状态（3 态，基线文档 3.4；作废为终态，满足三不准）。 */
public enum ActivityStatus implements LifecycleStatus {
    ACTIVE("启用", false),
    INACTIVE("停用", false),
    OBSOLETE("作废", true);

    private final String displayName;
    private final boolean terminal;

    ActivityStatus(String displayName, boolean terminal) {
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
