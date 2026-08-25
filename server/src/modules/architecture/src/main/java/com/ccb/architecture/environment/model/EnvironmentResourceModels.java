package com.ccb.architecture.environment.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/** 具体环境与资源申请的强类型模型（REQ-20260824-052）。 */
public final class EnvironmentResourceModels {
    private EnvironmentResourceModels() {
    }

    public enum RecordStatus {
        ACTIVE,
        INACTIVE;

        public static RecordStatus fromDatabase(String value) {
            return enumValue(RecordStatus.class, value, "status");
        }
    }

    public enum RequestType {
        INITIAL,
        EXPANSION,
        SHRINK,
        ADJUSTMENT;

        public static RequestType fromDatabase(String value) {
            return enumValue(RequestType.class, value, "request_type");
        }
    }

    public enum RequestStatus {
        DRAFT,
        IN_REVIEW,
        RETURNED,
        APPROVED,
        REJECTED,
        CANCELLED;

        public static RequestStatus fromDatabase(String value) {
            return enumValue(RequestStatus.class, value, "request_status");
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
    }

    public enum WorkflowReceiptStatus {
        PROCESSED,
        IGNORED,
        FAILED;

        public static WorkflowReceiptStatus fromDatabase(String value) {
            return enumValue(WorkflowReceiptStatus.class, value, "workflow_receipt_status");
        }
    }

    public record EnvironmentType(
            String code,
            String name) {
    }

    public record Environment(
            long id,
            long tenantId,
            String code,
            String name,
            String typeCode,
            String typeName,
            RecordStatus status,
            String description,
            String remark,
            long rowVersion,
            long createdBy,
            long updatedBy,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    public record ResourceRequest(
            long id,
            long tenantId,
            String requestNo,
            long physicalSubsystemId,
            String physicalSubsystemCode,
            String physicalSubsystemShortName,
            String physicalSubsystemName,
            String physicalSubsystemBusinessGroupName,
            String physicalSubsystemSystemLevelCode,
            String physicalSubsystemDeploymentPlatform,
            String physicalSubsystemDisasterRecoveryMode,
            long environmentId,
            String environmentCode,
            String environmentName,
            String environmentTypeName,
            long applicantId,
            long contactUserId,
            RequestType requestType,
            String reason,
            RequestStatus status,
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

    public record ResourceRequestItem(
            long id,
            long tenantId,
            long requestId,
            int itemSeq,
            long deploymentUnitId,
            String deploymentUnitCode,
            String deploymentUnitName,
            String deploymentUnitKind,
            String relatedDeploymentUnitName,
            String deploymentUnitDescription,
            String deploymentUnitType,
            BigDecimal databaseStorageGb,
            BigDecimal fileStorageGb,
            String networkZone,
            String serverType,
            BigDecimal cpuCores,
            BigDecimal memoryGb,
            int appWebGroupCount,
            int plannedNodeCount,
            BigDecimal sidecarCpuCores,
            BigDecimal sidecarMemoryGb,
            boolean hasSidecar,
            String databaseName,
            String databaseVersion,
            String jdkVersion,
            String middleware,
            String operatingSystem,
            BigDecimal extraCbsGb,
            BigDecimal localDiskGb,
            boolean needsNft,
            boolean needsFserver,
            boolean needsJobexecutor,
            String remark,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {

        public BigDecimal totalCpuCores() {
            return amount(cpuCores).multiply(BigDecimal.valueOf(Math.max(plannedNodeCount, 0)))
                    .add(effectiveSidecarCpuCores());
        }

        public BigDecimal totalMemoryGb() {
            return baseMemoryGb().add(effectiveSidecarMemoryGb());
        }

        public String sidecarMemoryRatio() {
            BigDecimal baseMemory = baseMemoryGb();
            BigDecimal sidecar = effectiveSidecarMemoryGb();
            if (!hasSidecar || baseMemory.signum() == 0 || sidecar.signum() == 0) {
                return null;
            }
            BigDecimal total = baseMemory.add(sidecar);
            BigDecimal ratio = sidecar.multiply(BigDecimal.valueOf(100))
                    .divide(total, 2, RoundingMode.HALF_UP)
                    .stripTrailingZeros();
            return ratio.toPlainString() + "%";
        }

        private static BigDecimal amount(BigDecimal value) {
            return value == null ? BigDecimal.ZERO : value;
        }

        private BigDecimal effectiveSidecarCpuCores() {
            return hasSidecar ? amount(sidecarCpuCores) : BigDecimal.ZERO;
        }

        private BigDecimal effectiveSidecarMemoryGb() {
            return hasSidecar ? amount(sidecarMemoryGb) : BigDecimal.ZERO;
        }

        private BigDecimal baseMemoryGb() {
            return amount(memoryGb).multiply(BigDecimal.valueOf(Math.max(plannedNodeCount, 0)));
        }
    }

    public record ResourceSummary(
            long environmentId,
            long requestCount,
            long approvedRequestCount,
            long pendingRequestCount,
            BigDecimal requestedCpuCores,
            BigDecimal requestedMemoryGb,
            BigDecimal requestedStorageGb,
            long requestedNodeCount,
            BigDecimal actualCpuCores,
            BigDecimal actualMemoryGb,
            BigDecimal actualStorageGb,
            long actualNodeCount) {
    }

    public record HistoryEvent(
            long id,
            long tenantId,
            long requestId,
            String eventType,
            RequestStatus fromStatus,
            RequestStatus toStatus,
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
            long requestId,
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
            long requestId,
            int roundNo,
            long workflowInstanceId,
            String eventType) {
    }

    public record WorkflowReceipt(
            long id,
            long tenantId,
            String eventId,
            String subscriberKey,
            Long requestId,
            Integer roundNo,
            Long workflowInstanceId,
            String eventType,
            WorkflowReceiptStatus processingStatus,
            String detail,
            LocalDateTime receivedAt,
            LocalDateTime processedAt) {
    }

    /** HTTP 层传入的资源规格行，tenant 与申请编号均由服务端补齐。 */
    @JsonIgnoreProperties(value = {
            "tenantId", "requestId", "businessContinuityLevel", "collectedSystemLevel",
            "businessGroupName", "deploymentPlatform", "systemLevelCode", "disasterRecoveryMode",
            "relatedDeploymentUnitName", "deploymentUnitDescription", "deploymentUnitType"
    }, ignoreUnknown = true)
    public record ResourceItemCommand(
            Long deploymentUnitId,
            BigDecimal databaseStorageGb,
            BigDecimal fileStorageGb,
            String networkZone,
            String serverType,
            BigDecimal cpuCores,
            BigDecimal memoryGb,
            Integer appWebGroupCount,
            Integer plannedNodeCount,
            BigDecimal sidecarCpuCores,
            BigDecimal sidecarMemoryGb,
            Boolean hasSidecar,
            String databaseName,
            String databaseVersion,
            String jdkVersion,
            String middleware,
            String operatingSystem,
            BigDecimal extraCbsGb,
            BigDecimal localDiskGb,
            Boolean needsNft,
            Boolean needsFserver,
            Boolean needsJobexecutor,
            String remark) {
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
