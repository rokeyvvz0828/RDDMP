package com.ccb.datamigration.lifecycle.enums;

/** 审核状态（4 态，工序级，基线文档 3.7）。 */
public enum AuditStatus implements LifecycleStatus {
    WAIT_REVIEW("待审核", false),
    PASSED("审核通过", true),
    REJECTED("审核打回", false),
    RECHECK("整改复审", false);

    private final String displayName;
    private final boolean terminal;

    AuditStatus(String displayName, boolean terminal) {
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
