package com.ccb.datamigration.lifecycle.issue.model;

import java.time.LocalDateTime;
import java.util.List;

/** 历史问题知识条目视图（基线 13.2.5 三元组建模）。 */
public record KnowledgeEntryView(long id, String knowledgeCode, Long sourceIssueId,
                                 String problemDesc, String solution, List<String> rectifyRecords,
                                 List<Long> attachmentIds, List<Long> tagIds, int reuseCount,
                                 String knowledgeStatus, LocalDateTime createdAt, LocalDateTime updatedAt) {
}
