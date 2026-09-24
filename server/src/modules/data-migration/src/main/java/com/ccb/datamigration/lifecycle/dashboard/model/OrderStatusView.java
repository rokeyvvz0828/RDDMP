package com.ccb.datamigration.lifecycle.dashboard.model;

import java.util.List;

/** 维度 6：工单进度状态（9 态分项之和=总数）+ 时效分桶（叠加维度，仅「在办工单」参与）。 */
public record OrderStatusView(long orderTotal,
                              long waitPreCnt, long waitAcceptCnt, long executingCnt, long suspendedCnt,
                              long reviewingCnt, long reviewRejectedCnt, long closedCnt, long archivedCnt,
                              long cancelledCnt, double closeRate, double normalRate, double rejectRate,
                              double archiveRate, long deadlineNormalCnt, long deadlineNearCnt,
                              long deadlineOverdueCnt, List<GranularityOrderStatus> byGranularity) {
}
