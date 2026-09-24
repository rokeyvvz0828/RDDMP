package com.ccb.datamigration.lifecycle.audit.model;

import java.time.LocalDateTime;

/** 审核台账行视图（基线 15.2.3 标准化展示字段）。 */
public record AuditView(long id, String orderCode, long orderId, String granularity, String activityType,
                        String activityName, Long componentId, String projectName, String businessGroupName,
                        int processSeq, String processName, String processStatus, String auditStatus,
                        int rejectCount, int auditRound, String auditResult, String auditOpinion,
                        String rectifyRequirement, Long auditorId, LocalDateTime auditedAt,
                        String rectifyDone, String postUnlockStatus, String snapshotVersion,
                        Long batchId, LocalDateTime revokedAt) {
}
