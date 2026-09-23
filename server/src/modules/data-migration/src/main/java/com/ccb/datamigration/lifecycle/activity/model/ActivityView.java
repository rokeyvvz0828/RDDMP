package com.ccb.datamigration.lifecycle.activity.model;

import java.time.LocalDateTime;

/** 活动列表/详情视图（基线 9.2.2 展示字段，无冗余字段）。 */
public record ActivityView(long id, long tenantId, String activityCode, String activityName, String activityType,
                           Long lifecycleStageId, String stageCode, String stageName, String granularity,
                           String activityStatus, String scene, String goal, String overallEntryCond,
                           String overallExitDesc, String overallDeliverables, long createdBy, long updatedBy,
                           LocalDateTime createdAt, LocalDateTime updatedAt, int processTotal, int processReady) {
}
