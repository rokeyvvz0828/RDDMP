package com.ccb.datamigration.lifecycle.dashboard.model;

/** 维度 5：风险状态分项（6 态）+ 等级分层（单一等级值）。 */
public record RiskStatusView(long total, long waitPreventCnt, long preventingCnt, long avoidedCnt,
                             long occurredCnt, long closedCnt, long cancelledCnt, long levelHighCnt,
                             long levelMidCnt, long levelLowCnt) {
}
