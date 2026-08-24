package com.ccb.architecture.standard.model;

import java.time.LocalDateTime;

/**
 * 架构规范文档领域模型。
 *
 * <p>文档主记录承载当前内容与状态；每次发布追加不可变版本快照。类别为平台参数
 * {@code ARCH_STANDARD_CATEGORY} 的键，业务代码不写死可管理分类。</p>
 */
public final class StandardModels {

    private StandardModels() {
    }

    public enum DocumentStatus {
        DRAFT, PUBLISHED, OFFLINE
    }

    /** 架构规范文档主记录。 */
    public record StandardDocument(
            long id,
            long tenantId,
            String title,
            String categoryCode,
            String summary,
            String content,
            DocumentStatus status,
            int currentVersion,
            LocalDateTime publishedAt,
            Long publishedBy,
            String publishedByName,
            long rowVersion,
            long createdBy,
            String createdByName,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    /** 发布版本快照，写入后不可变。 */
    public record StandardVersion(
            long id,
            long tenantId,
            long documentId,
            int versionNo,
            String title,
            String categoryCode,
            String summary,
            String content,
            LocalDateTime publishedAt,
            long publishedBy,
            String publishedByName
    ) {
    }

    /** 创建或更新文档的输入。 */
    public record StandardCommand(
            String title,
            String categoryCode,
            String summary,
            String content
    ) {
    }

    /** 列表查询条件。 */
    public record StandardQuery(
            String title,
            String categoryCode,
            String status
    ) {
        public static StandardQuery empty() {
            return new StandardQuery(null, null, null);
        }
    }
}
