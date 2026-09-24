package com.ccb.datamigration.lifecycle.task.model;

import java.time.LocalDateTime;
import java.util.List;

/** 任务台账视图（基线 10.2.2 标准化字段）。 */
public record TaskView(long id, long tenantId, String taskCode, String taskName, String granularity,
                       String activityType, long activityId, String activityName, Long topicActivityId,
                       long projectId, Long businessGroupId, Long componentId, long defaultExecutorId,
                       long currentExecutorId, List<Long> participantIds, LocalDateTime planFinishTime,
                       String taskStatus, Integer currentProcessSeq, int finishedProcessCount, int totalProcessCount,
                       double closeProgress, String snapshotVersion, String flowConstraintDesc, long creatorId,
                       LocalDateTime createdAt, LocalDateTime updatedAt, int orderTotal, int orderClosed) {
}
