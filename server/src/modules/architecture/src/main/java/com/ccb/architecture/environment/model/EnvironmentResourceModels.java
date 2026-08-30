package com.ccb.architecture.environment.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

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

    public enum InstanceStatus {
        ACTIVE,
        OFFLINE;

        public static InstanceStatus fromDatabase(String value) {
            return enumValue(InstanceStatus.class, value, "status");
        }
    }

    public enum FulfillmentMode {
        MANUAL,
        AUTOMATED;

        public static FulfillmentMode fromDatabase(String value) {
            return enumValue(FulfillmentMode.class, value, "fulfillment_mode");
        }
    }

    public enum DisasterRecoveryMode {
        PRIMARY_STANDBY,
        ACTIVE_ACTIVE,
        COLD_STANDBY;

        public static DisasterRecoveryMode fromDatabase(String value) {
            return enumValue(DisasterRecoveryMode.class, value, "dr_mode");
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
        FULFILLED,
        DIFF_FULFILLED,
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
            Long networkZoneId,
            String networkZoneName,
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

        public ResourceRequestItem(long id, long tenantId, long requestId, int itemSeq, long deploymentUnitId,
                                   String deploymentUnitCode, String deploymentUnitName,
                                   String deploymentUnitKind, String relatedDeploymentUnitName,
                                   String deploymentUnitDescription, String deploymentUnitType,
                                   BigDecimal databaseStorageGb, BigDecimal fileStorageGb,
                                   String networkZone, String serverType, BigDecimal cpuCores,
                                   BigDecimal memoryGb, int appWebGroupCount, int plannedNodeCount,
                                   BigDecimal sidecarCpuCores, BigDecimal sidecarMemoryGb, boolean hasSidecar,
                                   String databaseName, String databaseVersion, String jdkVersion,
                                   String middleware, String operatingSystem, BigDecimal extraCbsGb,
                                   BigDecimal localDiskGb, boolean needsNft, boolean needsFserver,
                                   boolean needsJobexecutor, String remark,
                                   LocalDateTime createdAt, LocalDateTime updatedAt) {
            this(id, tenantId, requestId, itemSeq, deploymentUnitId, deploymentUnitCode, deploymentUnitName,
                    deploymentUnitKind, relatedDeploymentUnitName, deploymentUnitDescription, deploymentUnitType,
                    databaseStorageGb, fileStorageGb, null, null, networkZone, serverType, cpuCores, memoryGb,
                    appWebGroupCount, plannedNodeCount, sidecarCpuCores, sidecarMemoryGb, hasSidecar,
                    databaseName, databaseVersion, jdkVersion, middleware, operatingSystem, extraCbsGb,
                    localDiskGb, needsNft, needsFserver, needsJobexecutor, remark, createdAt, updatedAt);
        }

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
            Long networkZoneId,
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
        public ResourceItemCommand(Long deploymentUnitId, BigDecimal databaseStorageGb,
                                   BigDecimal fileStorageGb, String networkZone, String serverType,
                                   BigDecimal cpuCores, BigDecimal memoryGb, Integer appWebGroupCount,
                                   Integer plannedNodeCount, BigDecimal sidecarCpuCores,
                                   BigDecimal sidecarMemoryGb, Boolean hasSidecar, String databaseName,
                                   String databaseVersion, String jdkVersion, String middleware,
                                   String operatingSystem, BigDecimal extraCbsGb, BigDecimal localDiskGb,
                                   Boolean needsNft, Boolean needsFserver, Boolean needsJobexecutor,
                                   String remark) {
            this(deploymentUnitId, databaseStorageGb, fileStorageGb, null, networkZone, serverType,
                    cpuCores, memoryGb, appWebGroupCount, plannedNodeCount, sidecarCpuCores,
                    sidecarMemoryGb, hasSidecar, databaseName, databaseVersion, jdkVersion,
                    middleware, operatingSystem, extraCbsGb, localDiskGb, needsNft, needsFserver,
                    needsJobexecutor, remark);
        }
    }

    public record EnvironmentInstance(
            long id,
            long tenantId,
            String instanceNo,
            long environmentId,
            String environmentCode,
            String environmentName,
            String environmentTypeName,
            long deploymentUnitId,
            String deploymentUnitCode,
            String deploymentUnitName,
            String deploymentUnitKind,
            Long deploymentUnitVersionId,
            int deploymentUnitVersionNo,
            int latestDeploymentUnitVersionNo,
            boolean hasVersionDifference,
            long physicalSubsystemId,
            String physicalSubsystemCode,
            String physicalSubsystemName,
            long sourceRequestId,
            String sourceRequestNo,
            Long sourceItemId,
            String machineName,
            String ipAddress,
            String serverType,
            String deploymentPlatform,
            Long networkZoneId,
            String networkZoneName,
            String networkZone,
            InstanceStatus status,
            BigDecimal cpuCores,
            BigDecimal memoryGb,
            BigDecimal databaseStorageGb,
            BigDecimal fileStorageGb,
            BigDecimal extraCbsGb,
            BigDecimal localDiskGb,
            String databaseName,
            String databaseVersion,
            String jdkVersion,
            String middleware,
            String operatingSystem,
            boolean needsNft,
            boolean needsFserver,
            boolean needsJobexecutor,
            FulfillmentMode fulfillmentMode,
            String differenceReason,
            String remark,
            LocalDateTime offlinedAt,
            Long offlinedBy,
            String offlineReason,
            long rowVersion,
            long createdBy,
            long updatedBy,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {

        public EnvironmentInstance(long id, long tenantId, String instanceNo, long environmentId,
                                   String environmentCode, String environmentName, String environmentTypeName,
                                   long deploymentUnitId, String deploymentUnitCode, String deploymentUnitName,
                                   String deploymentUnitKind, Long deploymentUnitVersionId,
                                   int deploymentUnitVersionNo, int latestDeploymentUnitVersionNo,
                                   boolean hasVersionDifference, long physicalSubsystemId,
                                   String physicalSubsystemCode, String physicalSubsystemName,
                                   long sourceRequestId, String sourceRequestNo, Long sourceItemId,
                                   String machineName, String ipAddress, String serverType,
                                   String deploymentPlatform, String networkZone, InstanceStatus status,
                                   BigDecimal cpuCores, BigDecimal memoryGb, BigDecimal databaseStorageGb,
                                   BigDecimal fileStorageGb, BigDecimal extraCbsGb, BigDecimal localDiskGb,
                                   String databaseName, String databaseVersion, String jdkVersion,
                                   String middleware, String operatingSystem, boolean needsNft,
                                   boolean needsFserver, boolean needsJobexecutor, FulfillmentMode fulfillmentMode,
                                   String differenceReason, String remark, LocalDateTime offlinedAt,
                                   Long offlinedBy, String offlineReason, long rowVersion,
                                   long createdBy, long updatedBy, LocalDateTime createdAt,
                                   LocalDateTime updatedAt) {
            this(id, tenantId, instanceNo, environmentId, environmentCode, environmentName, environmentTypeName,
                    deploymentUnitId, deploymentUnitCode, deploymentUnitName, deploymentUnitKind,
                    deploymentUnitVersionId, deploymentUnitVersionNo, latestDeploymentUnitVersionNo,
                    hasVersionDifference, physicalSubsystemId, physicalSubsystemCode, physicalSubsystemName,
                    sourceRequestId, sourceRequestNo, sourceItemId, machineName, ipAddress, serverType,
                    deploymentPlatform, null, null, networkZone, status, cpuCores, memoryGb, databaseStorageGb,
                    fileStorageGb, extraCbsGb, localDiskGb, databaseName, databaseVersion, jdkVersion,
                    middleware, operatingSystem, needsNft, needsFserver, needsJobexecutor, fulfillmentMode,
                    differenceReason, remark, offlinedAt, offlinedBy, offlineReason, rowVersion,
                    createdBy, updatedBy, createdAt, updatedAt);
        }
    }

    public record InstanceDisasterRecovery(
            long id,
            long tenantId,
            long deploymentUnitId,
            String deploymentUnitCode,
            String deploymentUnitName,
            long primaryInstanceId,
            String primaryMachineName,
            String primaryIpAddress,
            String primaryEnvironmentCode,
            String primaryEnvironmentName,
            long standbyInstanceId,
            String standbyMachineName,
            String standbyIpAddress,
            String standbyEnvironmentCode,
            String standbyEnvironmentName,
            DisasterRecoveryMode drMode,
            String description,
            long createdBy,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FulfillInstanceItemCommand(
            Long sourceItemId,
            Long deploymentUnitId,
            String machineName,
            String ipAddress,
            String serverType,
            String deploymentPlatform,
            Long networkZoneId,
            String networkZone,
            BigDecimal cpuCores,
            BigDecimal memoryGb,
            BigDecimal databaseStorageGb,
            BigDecimal fileStorageGb,
            BigDecimal extraCbsGb,
            BigDecimal localDiskGb,
            String databaseName,
            String databaseVersion,
            String jdkVersion,
            String middleware,
            String operatingSystem,
            Boolean needsNft,
            Boolean needsFserver,
            Boolean needsJobexecutor,
            FulfillmentMode fulfillmentMode,
            String remark) {
        public FulfillInstanceItemCommand(Long sourceItemId, Long deploymentUnitId, String machineName,
                                          String ipAddress, String serverType, String deploymentPlatform,
                                          String networkZone, BigDecimal cpuCores, BigDecimal memoryGb,
                                          BigDecimal databaseStorageGb, BigDecimal fileStorageGb,
                                          BigDecimal extraCbsGb, BigDecimal localDiskGb,
                                          String databaseName, String databaseVersion, String jdkVersion,
                                          String middleware, String operatingSystem, Boolean needsNft,
                                          Boolean needsFserver, Boolean needsJobexecutor,
                                          FulfillmentMode fulfillmentMode, String remark) {
            this(sourceItemId, deploymentUnitId, machineName, ipAddress, serverType, deploymentPlatform,
                    null, networkZone, cpuCores, memoryGb, databaseStorageGb, fileStorageGb, extraCbsGb,
                    localDiskGb, databaseName, databaseVersion, jdkVersion, middleware, operatingSystem,
                    needsNft, needsFserver, needsJobexecutor, fulfillmentMode, remark);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FulfillmentCommand(
            FulfillmentMode fulfillmentMode,
            String differenceReason,
            List<FulfillInstanceItemCommand> instances,
            Long rowVersion) {
        public FulfillmentCommand {
            instances = List.copyOf(instances == null ? List.of() : instances);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OfflineInstanceCommand(
            String offlineReason,
            Long rowVersion) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DisasterRecoveryCommand(
            Long deploymentUnitId,
            Long primaryInstanceId,
            Long standbyInstanceId,
            DisasterRecoveryMode drMode,
            String description) {
    }

    public record ProvisionItemRequest(
            long sourceItemId,
            int itemSeq,
            long deploymentUnitId,
            String deploymentUnitCode,
            String deploymentUnitName,
            String deploymentUnitType,
            String deploymentUnitKind,
            BigDecimal cpuCores,
            BigDecimal memoryGb,
            BigDecimal databaseStorageGb,
            BigDecimal fileStorageGb,
            BigDecimal extraCbsGb,
            BigDecimal localDiskGb,
            int plannedNodeCount,
            int nextSequenceStart,
            Long networkZoneId,
            String networkZoneName,
            String networkZone,
            String networkSubnetCidr,
            String serverType,
            String deploymentPlatform,
            String databaseName,
            String databaseVersion,
            String jdkVersion,
            String middleware,
            String operatingSystem,
            boolean needsNft,
            boolean needsFserver,
            boolean needsJobexecutor,
            String remark) {
        public ProvisionItemRequest(long sourceItemId, int itemSeq, long deploymentUnitId,
                                    String deploymentUnitCode, String deploymentUnitName,
                                    String deploymentUnitType, String deploymentUnitKind,
                                    BigDecimal cpuCores, BigDecimal memoryGb, BigDecimal databaseStorageGb,
                                    BigDecimal fileStorageGb, BigDecimal extraCbsGb, BigDecimal localDiskGb,
                                    int plannedNodeCount, int nextSequenceStart, String networkZone,
                                    String serverType, String deploymentPlatform, String databaseName,
                                    String databaseVersion, String jdkVersion, String middleware,
                                    String operatingSystem, boolean needsNft, boolean needsFserver,
                                    boolean needsJobexecutor, String remark) {
            this(sourceItemId, itemSeq, deploymentUnitId, deploymentUnitCode, deploymentUnitName,
                    deploymentUnitType, deploymentUnitKind, cpuCores, memoryGb, databaseStorageGb,
                    fileStorageGb, extraCbsGb, localDiskGb, plannedNodeCount, nextSequenceStart,
                    null, null, networkZone, null, serverType, deploymentPlatform, databaseName, databaseVersion,
                    jdkVersion, middleware, operatingSystem, needsNft, needsFserver, needsJobexecutor, remark);
        }
    }

    public record ProvisionRequest(
            long tenantId,
            long requestId,
            String requestNo,
            long environmentId,
            String environmentCode,
            String environmentName,
            long physicalSubsystemId,
            String physicalSubsystemCode,
            String physicalSubsystemName,
            List<ProvisionItemRequest> items) {
        public ProvisionRequest {
            items = List.copyOf(items == null ? List.of() : items);
        }
    }

    public record ProvisionedInstance(
            long sourceItemId,
            int itemSeq,
            long deploymentUnitId,
            String deploymentUnitCode,
            String deploymentUnitName,
            String machineName,
            String ipAddress,
            String serverType,
            String deploymentPlatform,
            Long networkZoneId,
            String networkZoneName,
            String networkZone,
            BigDecimal cpuCores,
            BigDecimal memoryGb,
            BigDecimal databaseStorageGb,
            BigDecimal fileStorageGb,
            BigDecimal extraCbsGb,
            BigDecimal localDiskGb,
            String databaseName,
            String databaseVersion,
            String jdkVersion,
            String middleware,
            String operatingSystem,
            boolean needsNft,
            boolean needsFserver,
            boolean needsJobexecutor,
            String remark,
            String mockExecutionLog) {
        public ProvisionedInstance(long sourceItemId, int itemSeq, long deploymentUnitId,
                                   String deploymentUnitCode, String deploymentUnitName,
                                   String machineName, String ipAddress, String serverType,
                                   String deploymentPlatform, String networkZone, BigDecimal cpuCores,
                                   BigDecimal memoryGb, BigDecimal databaseStorageGb, BigDecimal fileStorageGb,
                                   BigDecimal extraCbsGb, BigDecimal localDiskGb, String databaseName,
                                   String databaseVersion, String jdkVersion, String middleware,
                                   String operatingSystem, boolean needsNft, boolean needsFserver,
                                   boolean needsJobexecutor, String remark, String mockExecutionLog) {
            this(sourceItemId, itemSeq, deploymentUnitId, deploymentUnitCode, deploymentUnitName,
                    machineName, ipAddress, serverType, deploymentPlatform, null, null, networkZone,
                    cpuCores, memoryGb, databaseStorageGb, fileStorageGb, extraCbsGb, localDiskGb,
                    databaseName, databaseVersion, jdkVersion, middleware, operatingSystem, needsNft,
                    needsFserver, needsJobexecutor, remark, mockExecutionLog);
        }
    }

    public record ProvisionPreviewResult(
            boolean success,
            String executionId,
            String message,
            List<ProvisionedInstance> instances) {
        public ProvisionPreviewResult {
            instances = List.copyOf(instances == null ? List.of() : instances);
        }
    }

    public record ProvisionResult(
            boolean success,
            String executionId,
            String message,
            List<ProvisionedInstance> instances) {
        public ProvisionResult {
            instances = List.copyOf(instances == null ? List.of() : instances);
        }
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
