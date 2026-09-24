package com.ccb.datamigration.lifecycle.dashboard.model;

import java.util.List;

/** 维度 3：专题进度总览（运行状态与派工状态汇总）+ 逐专题明细。 */
public record TopicProgressOverview(long topicTotal, long activeCnt, long inactiveCnt, long obsoleteCnt,
                                    long taskedCnt, long untaskedCnt, long orderTotal,
                                    List<TopicProgressView> topics) {
}
