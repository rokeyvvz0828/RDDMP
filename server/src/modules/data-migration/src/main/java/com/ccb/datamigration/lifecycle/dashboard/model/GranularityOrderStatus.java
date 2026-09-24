package com.ccb.datamigration.lifecycle.dashboard.model;

/** 维度 6 子项：单一颗粒度（项目级/组件级）工单 9 态分项与时效分桶，禁止跨颗粒度混合统计。 */
public record GranularityOrderStatus(String granularity, long orderTotal,
                                     long waitPreCnt, long waitAcceptCnt, long executingCnt, long suspendedCnt,
                                     long reviewingCnt, long reviewRejectedCnt, long closedCnt, long archivedCnt,
                                     long cancelledCnt, long deadlineNormalCnt, long deadlineNearCnt,
                                     long deadlineOverdueCnt) {
}
