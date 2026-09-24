package com.ccb.datamigration.lifecycle.order.model;

import java.time.LocalDateTime;

/** 工单台账视图（基线 11.2.2 标准化字段）。 */
public record OrderView(long id, String orderCode, long taskId, String granularity, String activityType,
                        long activityId, String activityName, Long subActivityId, Long componentId,
                        String orderStatus, String deadlineStatus, int totalProcessCount, int closedProcessCount,
                        double flowProgress, LocalDateTime planFinishTime, LocalDateTime flowStartAt,
                        LocalDateTime closedAt, long currentExecutorId, String blockReason) {
}
