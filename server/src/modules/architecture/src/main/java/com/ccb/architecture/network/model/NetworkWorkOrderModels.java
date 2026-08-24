package com.ccb.architecture.network.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;
import java.util.List;

/**
 * CLB、DNS 与证书网络专项工单的强类型基础模型（REQ-20260823-050）。
 *
 * <p>三类工单共享工单引擎（状态机、工作流轮次、事件回执、不可变历史），kind 专属
 * 字段以 {@link #payload()} JSON 快照保存；平台只登记申请、办理过程与办理结果，
 * 不执行任何外部 CLB/DNS/证书动作。</p>
 */
public final class NetworkWorkOrderModels {
    private NetworkWorkOrderModels() {
    }

    public enum Kind {
        CLB,
        DNS,
        CERT;

        public static Kind fromDatabase(String value) {
            return enumValue(Kind.class, value, "kind");
        }
    }

    /** 按 kind 受控的工单动作；跨 kind 的动作集由服务端与数据库约束双重保证。 */
    public enum ActionType {
        OPEN,
        ADJUST,
        ADD,
        CHANGE,
        REMOVE,
        APPLY,
        RENEW,
        REVOKE;

        public static ActionType fromDatabase(String value) {
            return enumValue(ActionType.class, value, "action_type");
        }

        public static boolean allowedFor(Kind kind, ActionType action) {
            if (kind == null || action == null) {
                return false;
            }
            return switch (kind) {
                case CLB -> action == OPEN || action == ADJUST;
                case DNS -> action == ADD || action == CHANGE || action == REMOVE;
                case CERT -> action == APPLY || action == RENEW || action == REVOKE;
            };
        }
    }

    public enum WorkOrderStatus {
        DRAFT,
        IN_REVIEW,
        RETURNED,
        COMPLETED,
        REJECTED,
        CANCELLED;

        public static WorkOrderStatus fromDatabase(String value) {
            return enumValue(WorkOrderStatus.class, value, "work_order_status");
        }
    }

    /** 办理结果登记状态；完成仅表示外部实际配置已办理并登记。 */
    public enum HandlingResultStatus {
        SUCCESS,
        FAILED;

        public static HandlingResultStatus fromDatabase(String value) {
            return enumValue(HandlingResultStatus.class, value, "handling_result_status");
        }
    }

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

    public enum WorkflowReceiptStatus {
        PROCESSED,
        IGNORED,
        FAILED;

        public static WorkflowReceiptStatus fromDatabase(String value) {
            return enumValue(WorkflowReceiptStatus.class, value, "workflow_receipt_status");
        }
    }

    /** V89 工单主记录。 */
    public record WorkOrder(
            long id,
            long tenantId,
            Kind kind,
            ActionType actionType,
            String subject,
            long applicantId,
            String reason,
            WorkOrderStatus status,
            String payload,
            String attachmentIds,
            HandlingResultStatus resultStatus,
            String resultDescription,
            String resultAttachmentIds,
            Long resultRegisteredBy,
            LocalDateTime resultRegisteredAt,
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

    public record WorkOrderSummary(
            long id,
            long tenantId,
            Kind kind,
            ActionType actionType,
            String subject,
            long applicantId,
            String reason,
            WorkOrderStatus status,
            HandlingResultStatus resultStatus,
            int currentBusinessRound,
            boolean cancellationRequested,
            long rowVersion,
            long createdBy,
            long updatedBy,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    /** 不可变业务历史事件。 */
    public record HistoryEvent(
            long id,
            long tenantId,
            long workOrderId,
            String eventType,
            WorkOrderStatus fromStatus,
            WorkOrderStatus toStatus,
            int businessRound,
            String summary,
            String snapshotJson,
            String diffJson,
            long operatorId,
            LocalDateTime occurredAt) {
    }

    public record WorkflowRound(
            long id,
            long tenantId,
            long workOrderId,
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

    public record WorkflowReceiptStart(
            long id,
            long tenantId,
            String eventId,
            String subscriberKey,
            long workOrderId,
            int roundNo,
            long workflowInstanceId,
            String eventType) {
    }

    public record WorkflowReceipt(
            long id,
            long tenantId,
            String eventId,
            String subscriberKey,
            Long workOrderId,
            Integer roundNo,
            Long workflowInstanceId,
            String eventType,
            WorkflowReceiptStatus processingStatus,
            String detail,
            LocalDateTime receivedAt,
            LocalDateTime processedAt) {
    }

    /** CLB 工单专属字段契约；动作 OPEN/ADJUST。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ClbPayload(String clbName, String purpose, String description) {
        public ClbPayload {
            description = blankToNull(description);
        }
    }

    /** DNS 工单专属字段契约；动作 ADD/CHANGE/REMOVE。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DnsPayload(String domainName, String purpose, String description) {
        public DnsPayload {
            description = blankToNull(description);
        }
    }

    /** 证书工单专属字段契约；动作 APPLY/RENEW/REVOKE；平台不保存私钥。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CertPayload(String certType, String subjectName, String purpose, String description) {
        public CertPayload {
            certType = normalizeCertType(certType);
            description = blankToNull(description);
        }

        private static String normalizeCertType(String value) {
            if (value == null) {
                return null;
            }
            String normalized = value.trim().toUpperCase(java.util.Locale.ROOT);
            return "SSL".equals(normalized) || "EXTERNAL".equals(normalized) ? normalized : value.trim();
        }
    }

    /** 服务端用于持久化与摘要计算的规范化载荷。 */
    public record NormalizedPayload(
            Kind kind,
            ActionType actionType,
            String subject,
            String payloadJson,
            List<Long> attachmentIds) {
    }

    /** 办理结果登记命令（HTTP 层传入，服务端补充注册人与时间）。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HandlingResultCommand(
            String resultStatus,
            String resultDescription,
            List<Long> resultAttachmentIds) {
        public HandlingResultCommand {
            resultAttachmentIds = List.copyOf(resultAttachmentIds == null ? List.of() : resultAttachmentIds);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String value, String column) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(column + " 不能为空");
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(column + " 非法: " + value);
        }
    }
}
