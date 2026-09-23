package com.ccb.datamigration.lifecycle.enums;

/** 工序配置状态（2 态，辅助标记，非状态枚举、不参与流转判定，基线文档 3.9）。 */
public enum ProcessConfigStatus implements LifecycleStatus {
    DRAFT("草稿", false),
    READY("就绪", false);

    private final String displayName;
    private final boolean terminal;

    ProcessConfigStatus(String displayName, boolean terminal) {
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
