package com.ccb.datamigration.lifecycle.dashboard.model;

import java.util.List;

/** 维度 1：全生命周期阶段-活动-工单层级统计（仅普通活动归属阶段；专题独立不归属）。 */
public record StageActivityOrderView(long stageId, String stageCode, String stageName, long orderTotal,
                                     long waitPreCnt, long waitAcceptCnt, long executingCnt, long suspendedCnt,
                                     long reviewingCnt, long reviewRejectedCnt, long closedCnt, long archivedCnt,
                                     long cancelledCnt, double closeRate, List<ActivityOrderView> activities) {
}
