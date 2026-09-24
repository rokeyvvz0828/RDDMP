package com.ccb.datamigration.lifecycle.dashboard.model;

import java.util.Map;

/** 维度 2：活动进展状态（按活动类型标签区分普通/专题，按颗粒度分列）。 */
public record ActivityStatusView(long total, long activeCnt, long inactiveCnt, long obsoleteCnt,
                                 long linkedTopicCnt, long unlinkedTopicCnt,
                                 Map<String, Long> byType, Map<String, Long> byGranularity) {
}
