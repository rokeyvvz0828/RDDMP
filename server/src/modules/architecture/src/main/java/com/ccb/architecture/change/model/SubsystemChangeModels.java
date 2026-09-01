package com.ccb.architecture.change.model;

import java.time.LocalDateTime;

/** 生命周期工单与发布事务使用的强类型基础模型。 */
public final class SubsystemChangeModels {
    private SubsystemChangeModels() {
    }

    public enum TargetKind {
        LOGICAL,
        PHYSICAL;

        public static TargetKind fromDatabase(String value) {
            return enumValue(TargetKind.class, value, "target_kind");
        }
    }

    public enum ActionType {
        CREATE,
        UPDATE,
        OFFLINE,
        REACTIVATE,
        VOID,
        REPLACE;

        public static ActionType fromDatabase(String value) {
            return enumValue(ActionType.class, value, "action_type");
        }
    }

    public enum ApplicationStatus {
        DRAFT,
        IN_REVIEW,
        RETURNED,
        APPROVED,
        REJECTED,
        CANCELLED;

        public static ApplicationStatus fromDatabase(String value) {
            return enumValue(ApplicationStatus.class, value, "application_status");
        }
    }

    /** V82 工作流轮次状态；业务申请状态仍由 application 表表达。 */
    public enum WorkflowRoundStatus {
        PENDING,
        STARTED,
        RETURNED,
        APPROVED,
        REJECTED,
        TERMINATED,
        IGNORED;

        public static WorkflowRoundStatus fromDatabase(String value) {
            return enumValue(WorkflowRoundStatus.class, value, "workflow_round_status");
        }

        public boolean isTerminalOutcome() {
            return this == RETURNED || this == APPROVED || this == REJECTED || this == TERMINATED;
        }
    }

    /** 生命周期事件回执的最终处理状态。 */
    public enum WorkflowReceiptStatus {
        PROCESSED,
        IGNORED,
        FAILED;

        public static WorkflowReceiptStatus fromDatabase(String value) {
            return enumValue(WorkflowReceiptStatus.class, value, "workflow_receipt_status");
        }
    }

    public enum PublishedStatus {
        ACTIVE,
        OFFLINE,
        VOIDED;

        public static PublishedStatus fromDatabase(String value) {
            return enumValue(PublishedStatus.class, value, "published_status");
        }
    }

    /** V82 工单主记录。 */
    public record ChangeApplication(
            long id,
            long tenantId,
            TargetKind targetKind,
            ActionType actionType,
            Long targetId,
            long applicantId,
            String reason,
            ApplicationStatus status,
            int currentBusinessRound,
            Long currentWorkflowDefinitionId,
            Long currentWorkflowVersionId,
            Long currentWorkflowInstanceId,
            String currentPayloadDigest,
            boolean cancellationRequested,
            long rowVersion,
            long createdBy,
            long updatedBy,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    /** V82 物理子系统草稿，lineNo 保持草稿内稳定顺序。 */
    public record PhysicalDraft(
            long applicationId,
            int lineNo,
            long tenantId,
            Long sourcePhysicalSubsystemId,
            String code,
            String shortName,
            String name,
            String logicalSubsystemName,
            String businessComponentCode,
            String englishName,
            String businessGroupName,
            String deploymentPlatform,
            String disasterRecoveryMode,
            long responsibleTeamOrgId,
            String responsibleTeamNameSnapshot,
            String runtimeCode,
            String systemLevelCode,
            String developmentFrameworkCode,
            Long ownerUserId,
            String description,
            String remark,
            Long sourceRowVersion,
            int draftRevision,
            String submittedSnapshotJson,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {

        /** 兼容 V82-V94 期间不含登记表来源字段的测试与内部构造。 */
        public PhysicalDraft(long applicationId, int lineNo, long tenantId, Long sourcePhysicalSubsystemId,
                             String code, String shortName, String name, String logicalSubsystemName,
                             String businessComponentCode, String englishName,
                             String businessGroupName, long responsibleTeamOrgId,
                             String responsibleTeamNameSnapshot, String runtimeCode, String systemLevelCode,
                             String developmentFrameworkCode, Long ownerUserId, String description, String remark,
                             Long sourceRowVersion, int draftRevision,
                             String submittedSnapshotJson, LocalDateTime createdAt, LocalDateTime updatedAt) {
            this(applicationId, lineNo, tenantId, sourcePhysicalSubsystemId, code,
                    shortName, name, logicalSubsystemName, businessComponentCode, englishName, businessGroupName,
                    null, null,
                    responsibleTeamOrgId, responsibleTeamNameSnapshot, runtimeCode, systemLevelCode,
                    developmentFrameworkCode, ownerUserId, description, remark, sourceRowVersion, draftRevision,
                    submittedSnapshotJson, createdAt, updatedAt);
        }
    }

    /** V82 不可变工单历史。 */
    public record ChangeHistoryEvent(
            long id,
            long tenantId,
            long applicationId,
            String eventType,
            ApplicationStatus fromStatus,
            ApplicationStatus toStatus,
            int businessRound,
            String summary,
            String snapshotJson,
            String diffJson,
            long operatorId,
            LocalDateTime occurredAt) {
    }

    /** V82 工作流轮次；PENDING 阶段不绑定平台工作流标识。 */
    public record WorkflowRound(
            long id,
            long tenantId,
            long applicationId,
            int roundNo,
            Long workflowDefinitionId,
            Long workflowVersionId,
            Long workflowInstanceId,
            String payloadDigest,
            WorkflowRoundStatus status,
            LocalDateTime startedAt,
            LocalDateTime endedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    /** 新建幂等回执所需的事件身份和可选业务上下文。 */
    public record WorkflowReceiptStart(
            long id,
            long tenantId,
            String eventId,
            String subscriberKey,
            Long applicationId,
            Integer roundNo,
            Long workflowInstanceId,
            String eventType) {
    }

    /** V82 已持久化的工作流事件回执。 */
    public record WorkflowReceipt(
            long id,
            long tenantId,
            String eventId,
            String subscriberKey,
            Long applicationId,
            Integer roundNo,
            Long workflowInstanceId,
            String eventType,
            WorkflowReceiptStatus processingStatus,
            String detail,
            LocalDateTime receivedAt,
            LocalDateTime processedAt) {
    }

    public record TargetLock(long tenantId, TargetKind targetKind, long targetId, long applicationId,
                             LocalDateTime acquiredAt) {
    }

    public record ValueReservation(long tenantId, String reservationScope, String normalizedValue,
                                   long applicationId, int lineNo, LocalDateTime reservedAt) {
    }

    public record PhysicalReplacement(long id, long tenantId, long oldPhysicalSubsystemId,
                                      long newPhysicalSubsystemId, long applicationId, LocalDateTime approvedAt) {
    }

    public record PhysicalPublishedState(
            long id,
            long tenantId,
            String code,
            String logicalSubsystemName,
            String businessComponentCode,
            String englishName,
            PublishedStatus status,
            long rowVersion,
            boolean deleted) {
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String value, String column) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(column + " 不能为空");
        }
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(column + " 包含非法值: " + value, exception);
        }
    }
}
