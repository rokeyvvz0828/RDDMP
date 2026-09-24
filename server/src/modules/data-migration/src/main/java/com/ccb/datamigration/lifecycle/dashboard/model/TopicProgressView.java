package com.ccb.datamigration.lifecycle.dashboard.model;

/** 维度 3 子项：单个专题聚合活动的进度与运行状态（独立于生命周期阶段统计）。 */
public record TopicProgressView(long topicId, String topicName, String topicStatus, long orderTotal,
                                long waitPreCnt, long waitAcceptCnt, long executingCnt, long suspendedCnt,
                                long reviewingCnt, long reviewRejectedCnt, long closedCnt, long archivedCnt,
                                long cancelledCnt, double closeRate, double doingRate, double issueRate) {
}
