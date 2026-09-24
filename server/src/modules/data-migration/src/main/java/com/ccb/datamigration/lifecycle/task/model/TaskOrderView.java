package com.ccb.datamigration.lifecycle.task.model;

import java.time.LocalDateTime;
import java.util.List;

/** 任务下生成的工单视图（台账 orders 页签 / 拆分结果）。 */
public record TaskOrderView(long id, String orderCode, long taskId, String granularity, String activityType,
                            long activityId, String activityName, Long subActivityId, Long componentId,
                            String orderStatus, String deadlineStatus, int totalProcessCount, int closedProcessCount,
                            double flowProgress, LocalDateTime planFinishTime, LocalDateTime flowStartAt,
                            LocalDateTime closedAt, long currentExecutorId, String blockReason,
                            List<String> warnings) {
}
