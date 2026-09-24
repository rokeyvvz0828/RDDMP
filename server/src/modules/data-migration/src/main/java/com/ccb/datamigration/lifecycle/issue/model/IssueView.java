package com.ccb.datamigration.lifecycle.issue.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 问题台账视图（基线 13.2.2 标准化展示字段）。 */
public record IssueView(long id, String issueCode, String issueTitle, String issueSource,
                        Long projectId, Long businessGroupId, Long componentId,
                        Long orderId, Integer processSeq, Long reporterId, Long rectifierId,
                        LocalDateTime firstReportedAt, LocalDateTime rectifiedAt, LocalDateTime updatedAt,
                        String issueDesc, String scene, String impactScope, String blockDesc,
                        List<Long> attachmentIds, String adminSuggestion, String solution,
                        String rectifyProgress, List<Map<String, Object>> rectifyRecords,
                        List<Long> rectifyAttachmentIds, String issueStatus, String snapshotVersion,
                        Long syncedReportId) {
}
