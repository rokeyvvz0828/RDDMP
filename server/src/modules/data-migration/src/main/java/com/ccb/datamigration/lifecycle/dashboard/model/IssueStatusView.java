package com.ccb.datamigration.lifecycle.dashboard.model;

/** 维度 4：问题状态分项（4 态，与问题台账同源）。 */
public record IssueStatusView(long total, long waitRectifyCnt, long rectifyingCnt, long closedCnt,
                              long cancelledCnt) {
}
