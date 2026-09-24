package com.ccb.datamigration.lifecycle.risk.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 风险台账视图（基线 14.2.2 标准化展示字段）。 */
public record RiskView(long id, String riskCode, String riskTitle, String riskSource,
                       Long projectId, Long businessGroupId, Long componentId,
                       Long orderId, Integer processSeq, String riskLevel, String probability,
                       String impactScope, String riskDesc, String potentialHarm, String predictedScene,
                       List<Long> attachmentIds, Long reporterId, Long preventOwnerId,
                       LocalDateTime reportedAt, LocalDateTime preventedAt, LocalDateTime updatedAt,
                       String prePreventMeasure, String responseStrategy, String degradePlan, String emergencyPlan,
                       String preventPriority, LocalDateTime disposeDeadline, String preventProgress,
                       List<Map<String, Object>> preventRecords, List<Long> preventAttachmentIds,
                       String riskStatus, String snapshotVersion, Long syncedReportId) {
}
