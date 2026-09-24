package com.ccb.datamigration.lifecycle.dashboard.model;

/** 维度 1 子项：阶段下单个普通活动的工单 9 态分项统计（基线 16.2.3.1）。 */
public record ActivityOrderView(long activityId, String activityName, String granularity, long orderTotal,
                                long waitPreCnt, long waitAcceptCnt, long executingCnt, long suspendedCnt,
                                long reviewingCnt, long reviewRejectedCnt, long closedCnt, long archivedCnt,
                                long cancelledCnt, double closeRate) {
}
