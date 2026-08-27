package com.ccb.architecture.decision.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 架构决策事项领域模型。
 *
 * <p>事项围绕一事一议展开：提交（材料可空）、协作补齐、首次处理（7 自然日）、
 * 异步/会议评审、正式结论发布（工作流门禁）与替代链。已发布结论不可修改，
 * 只能由后续结论「替代」或「部分修订」。</p>
 */
public final class DecisionModels {

    private DecisionModels() {
    }

    public enum MatterStatus {
        SUBMITTED, RETURNED_FOR_INFO, IN_REVIEW, PUBLISHED
    }

    public enum FirstHandlingOutcome {
        ACCEPTED, REQUESTED_INFO, REVIEW_MODE_SET
    }

    public enum ReviewMethod {
        ASYNC, MEETING
    }

    public enum MaterialKind {
        SOLUTION, IMPACT, DISPUTE, OTHER
    }

    public enum SupersessionKind {
        SUPERSEDE, PARTIALLY_REVISE
    }

    public enum ActionItemStatus {
        OPEN, DONE
    }

    public enum WorkflowRoundStatus {
        PENDING, STARTED, RETURNED, APPROVED, REJECTED, TERMINATED, IGNORED
    }

    public enum WorkflowReceiptStatus {
        PROCESSED, IGNORED, FAILED
    }

    /** 决策事项主记录。 */
    public record DecisionMatter(
            long id,
            long tenantId,
            String matterNo,
            String title,
            String problem,
            String typeCode,
            MatterStatus status,
            LocalDateTime receivedAt,
            LocalDate firstHandlingDeadline,
            FirstHandlingOutcome firstHandlingOutcome,
            String firstHandlingComment,
            LocalDateTime firstHandledAt,
            Long firstHandlerId,
            String firstHandlerName,
            ReviewMethod reviewMode,
            long proposerId,
            String proposerName,
            long submitterId,
            String submitterName,
            LocalDateTime publicationPreparedAt,
            Long publicationPreparedBy,
            int currentBusinessRound,
            Long currentWorkflowDefinitionId,
            Long currentWorkflowVersionId,
            Long currentWorkflowInstanceId,
            String currentPayloadDigest,
            long rowVersion,
            long createdBy,
            String createdByName,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    /** 创建或编辑事项标题与问题。 */
    public record MatterCommand(String title, String problem) {
    }

    /** 列表查询条件。 */
    public record MatterQuery(
            String keyword,
            String typeCode,
            String status,
            Boolean firstHandlingOverdue,
            Long proposerId
    ) {
        public static MatterQuery empty() {
            return new MatterQuery(null, null, null, null, null);
        }
    }

    /** 协作补齐材料。 */
    public record MaterialRecord(
            long id,
            long tenantId,
            long matterId,
            MaterialKind kind,
            String content,
            long createdBy,
            String createdByName,
            LocalDateTime createdAt
    ) {
    }

    public record MaterialCommand(MaterialKind kind, String content) {
    }

    /** 评审记录（含正式结论与理由；结论发布前可调整）。 */
    public record ReviewRecord(
            long id,
            long tenantId,
            long matterId,
            int reviewNo,
            ReviewMethod method,
            LocalDateTime reviewedAt,
            String processMaterialSummary,
            String keyOpinion,
            String conclusionContent,
            String conclusionRationale,
            long createdBy,
            String createdByName,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    /** 评审输入：方式、时间、过程材料、关键意见、正式结论、理由、参与人与行动项。 */
    public record ReviewCommand(
            ReviewMethod method,
            LocalDateTime reviewedAt,
            String processMaterialSummary,
            String keyOpinion,
            String conclusionContent,
            String conclusionRationale,
            List<Long> participantUserIds,
            List<ActionItemInput> actionItems
    ) {
    }

    /** 行动项输入：id 为空表示新增，否则为既有行动项更新。 */
    public record ActionItemInput(Long id, String content, Long ownerUserId, String ownerName) {
    }

    public record ActionItem(
            long id,
            long tenantId,
            long reviewId,
            String content,
            Long ownerUserId,
            String ownerName,
            ActionItemStatus status,
            long createdBy,
            String createdByName,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    /** 已发布结论（不可变）。 */
    public record Conclusion(
            long id,
            long tenantId,
            long matterId,
            long reviewId,
            String content,
            String rationale,
            LocalDateTime publishedAt,
            long publishedBy,
            String publishedByName,
            LocalDateTime createdAt
    ) {
    }

    /** 结论有效状态（由替代关系推导）。 */
    public enum ConclusionEffectiveStatus {
        EFFECTIVE, SUPERSEDED, PARTIALLY_SUPERSEDED
    }

    /** 替代/部分修订关系。 */
    public record Supersession(
            long id,
            long tenantId,
            long conclusionId,
            long supersededConclusionId,
            SupersessionKind kind,
            LocalDateTime createdAt
    ) {
    }

    /** 发布准备意图：结论来源评审与替代/部分修订目标。 */
    public record PublicationIntent(
            long matterId,
            long tenantId,
            long reviewId,
            List<SupersessionTarget> targets,
            String payloadDigest,
            long preparedBy,
            String preparedByName,
            LocalDateTime preparedAt
    ) {
    }

    public record SupersessionTarget(long conclusionId, SupersessionKind kind) {
    }

    /** 工作流轮次。 */
    public record WorkflowRound(
            long id,
            long tenantId,
            long matterId,
            int roundNo,
            Long workflowDefinitionId,
            Long workflowVersionId,
            Long workflowInstanceId,
            String payloadDigest,
            WorkflowRoundStatus status,
            LocalDateTime startedAt,
            LocalDateTime endedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    /** 回执起始信息。 */
    public record WorkflowReceiptStart(
            long id,
            long tenantId,
            String eventId,
            String subscriberKey,
            long matterId,
            int roundNo,
            long workflowInstanceId,
            String eventType
    ) {
    }
}
