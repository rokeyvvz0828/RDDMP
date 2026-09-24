package com.ccb.datamigration.lifecycle.feedback.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 工序执行反馈视图（基线 12.2：反馈内容 + 作业日志 + 问题/风险独立上报台账）。 */
public record FeedbackView(long id, long orderId, int processSeq, String progressDesc, String exitContentFilled,
                           List<Long> deliverableIds, List<Map<String, Object>> workLog, List<Long> attachmentIds,
                           String extraRemark, long filledBy, LocalDateTime filledAt, boolean locked,
                           String snapshotVersion, List<Map<String, Object>> issues, List<Map<String, Object>> risks,
                           int rejectCount) {
}
