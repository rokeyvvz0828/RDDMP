package com.ccb.datamigration.lifecycle.enums;

/** 时效标记（3 档，辅助标记，非状态枚举、不参与流转判定，基线文档 3.8）。 */
public enum TimelinessMark implements LifecycleStatus {
    NORMAL("正常", false),
    NEAR_OVERDUE("即将超时", false),
    OVERDUE("已超时", false);

    private final String displayName;
    private final boolean terminal;

    TimelinessMark(String displayName, boolean terminal) {
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
