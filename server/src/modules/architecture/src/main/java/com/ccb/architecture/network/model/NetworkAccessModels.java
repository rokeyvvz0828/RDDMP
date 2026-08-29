package com.ccb.architecture.network.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;

/** 网络分区、外部网络地址、访问申请与访问关系模型（REQ-20260826-054）。 */
public final class NetworkAccessModels {
    private NetworkAccessModels() {
    }

    public enum RecordStatus {
        ACTIVE,
        INACTIVE;

        public static RecordStatus fromDatabase(String value) {
            return enumValue(RecordStatus.class, value, "status");
        }
    }

    public enum AddressType {
        IP,
        CIDR,
        DOMAIN;

        public static AddressType fromDatabase(String value) {
            return enumValue(AddressType.class, value, "address_type");
        }
    }

    public enum EndpointKind {
        MANAGED,
        EXTERNAL;

        public static EndpointKind fromDatabase(String value) {
            return enumValue(EndpointKind.class, value, "endpoint_kind");
        }
    }

    public enum AccessProtocol {
        TCP,
        UDP,
        HTTP,
        HTTPS,
        OTHER;

        public static AccessProtocol fromDatabase(String value) {
            return enumValue(AccessProtocol.class, value, "protocol");
        }
    }

    public enum ValidityType {
        LIMITED,
        LONG_TERM;

        public static ValidityType fromDatabase(String value) {
            return enumValue(ValidityType.class, value, "validity_type");
        }
    }

    public enum AccessDecision {
        NEEDS_APPLICATION,
        NOT_REQUIRED
    }

    public enum DecisionBasis {
        SUBNET_INTERNAL,
        RELATION_COVERED,
        RULE_EXEMPT,
        STRICT_REQUIRED
    }

    public enum NetworkAccessActionType {
        OPEN,
        MODIFY,
        RENEW,
        CLOSE;

        public static NetworkAccessActionType fromDatabase(String value) {
            return enumValue(NetworkAccessActionType.class, value, "action_type");
        }
    }

    public enum ApplicationStatus {
        DRAFT,
        RETURNED,
        IN_REVIEW,
        APPROVED,
        REJECTED,
        CANCELLED;

        public static ApplicationStatus fromDatabase(String value) {
            return enumValue(ApplicationStatus.class, value, "application_status");
        }
    }

    public enum RelationCloseType {
        SUPERSEDED,
        CLOSED_BY_APPLICATION,
        LEGACY_DIRECT;

        public static RelationCloseType fromDatabase(String value) {
            return value == null || value.isBlank() ? null
                    : enumValue(RelationCloseType.class, value, "close_type");
        }
    }

    public enum ExemptionRuleStatus {
        ACTIVE,
        DISABLED;

        public static ExemptionRuleStatus fromDatabase(String value) {
            return enumValue(ExemptionRuleStatus.class, value, "rule_status");
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

    public enum RelationStatus {
        ACTIVE,
        CLOSED;

        public static RelationStatus fromDatabase(String value) {
            return enumValue(RelationStatus.class, value, "relation_status");
        }
    }

    public record NetworkZone(
            long id,
            long tenantId,
            Long parentId,
            String parentName,
            String code,
            String name,
            int restrictionLevel,
            RecordStatus status,
            String description,
            String remark,
            long rowVersion,
            long createdBy,
            long updatedBy,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    public record NetworkZoneOption(
            long id,
            String code,
            String name,
            Long parentId,
            String parentName,
            int restrictionLevel,
            boolean leaf) {
    }

    public record NetworkZoneSubnet(
            long id,
            long tenantId,
            long networkZoneId,
            String networkZoneCode,
            String networkZoneName,
            String cidrBlock,
            String gatewayIp,
            String purpose,
            RecordStatus status,
            String remark,
            long rowVersion,
            long createdBy,
            long updatedBy,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    public record ExternalNetworkAddress(
            long id,
            long tenantId,
            AddressType addressType,
            String addressValue,
            String displayName,
            String purpose,
            RecordStatus status,
            String remark,
            long rowVersion,
            long createdBy,
            long updatedBy,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    public record ManagedEndpointInstance(
            long id,
            String instanceNo,
            long physicalSubsystemId,
            String physicalSubsystemCode,
            String physicalSubsystemName,
            long environmentId,
            String environmentCode,
            String environmentName,
            long deploymentUnitId,
            String deploymentUnitCode,
            String deploymentUnitName,
            String machineName,
            String ipAddress,
            Long networkZoneId,
            String networkZoneName) {
    }

    public record EndpointInstanceStatus(
            long id,
            String machineName,
            String ipAddress,
            String status) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EndpointCommand(
            EndpointKind kind,
            Long physicalSubsystemId,
            Long environmentId,
            Long deploymentUnitId,
            Long externalAddressId,
            List<Long> instanceIds) {
        public EndpointCommand {
            instanceIds = List.copyOf(new LinkedHashSet<>(instanceIds == null ? List.of() : instanceIds));
        }
    }

    public record NetworkAccessApplication(
            long id,
            long tenantId,
            String applicationNo,
            long applicantId,
            NetworkAccessActionType actionType,
            Long targetRelationId,
            EndpointKind sourceKind,
            Long sourcePhysicalSubsystemId,
            Long sourceEnvironmentId,
            Long sourceDeploymentUnitId,
            Long sourceExternalAddressId,
            String sourceSnapshotJson,
            EndpointKind targetKind,
            Long targetPhysicalSubsystemId,
            Long targetEnvironmentId,
            Long targetDeploymentUnitId,
            Long targetExternalAddressId,
            String targetSnapshotJson,
            AccessProtocol protocol,
            String ports,
            String purpose,
            String processDescription,
            LocalDateTime validFrom,
            LocalDateTime validUntil,
            ValidityType validityType,
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
        public NetworkAccessApplication(long id, long tenantId, String applicationNo, long applicantId,
                                        EndpointKind sourceKind, Long sourcePhysicalSubsystemId,
                                        Long sourceEnvironmentId, Long sourceDeploymentUnitId,
                                        Long sourceExternalAddressId, String sourceSnapshotJson,
                                        EndpointKind targetKind, Long targetPhysicalSubsystemId,
                                        Long targetEnvironmentId, Long targetDeploymentUnitId,
                                        Long targetExternalAddressId, String targetSnapshotJson,
                                        AccessProtocol protocol, String ports, String purpose,
                                        String processDescription, LocalDateTime validFrom,
                                        LocalDateTime validUntil, ApplicationStatus status,
                                        long rowVersion, long createdBy, long updatedBy,
                                        LocalDateTime createdAt, LocalDateTime updatedAt) {
            this(id, tenantId, applicationNo, applicantId, NetworkAccessActionType.OPEN, null,
                    sourceKind, sourcePhysicalSubsystemId, sourceEnvironmentId, sourceDeploymentUnitId,
                    sourceExternalAddressId, sourceSnapshotJson, targetKind, targetPhysicalSubsystemId,
                    targetEnvironmentId, targetDeploymentUnitId, targetExternalAddressId, targetSnapshotJson,
                    protocol, ports, purpose, processDescription, validFrom, validUntil,
                    validUntil == null ? ValidityType.LONG_TERM : ValidityType.LIMITED, status,
                    0, null, null, null, null, false, rowVersion, createdBy, updatedBy, createdAt, updatedAt);
        }
    }

    public record NetworkAccessRelation(
            long id,
            long tenantId,
            String relationNo,
            long applicationId,
            Long replacesRelationId,
            Long replacedByRelationId,
            Long closedApplicationId,
            EndpointKind sourceKind,
            String sourceSnapshotJson,
            EndpointKind targetKind,
            String targetSnapshotJson,
            AccessProtocol protocol,
            String ports,
            String purpose,
            String processDescription,
            LocalDateTime validFrom,
            LocalDateTime validUntil,
            ValidityType validityType,
            RelationStatus status,
            String closeReason,
            RelationCloseType closeType,
            Long closedBy,
            LocalDateTime closedAt,
            boolean hasOfflineEndpointRisk,
            int offlineEndpointCount,
            List<String> offlineEndpointSummaries,
            long rowVersion,
            long createdBy,
            long updatedBy,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
        public NetworkAccessRelation {
            offlineEndpointSummaries = List.copyOf(offlineEndpointSummaries == null
                    ? List.of() : offlineEndpointSummaries);
        }

        public NetworkAccessRelation(long id, long tenantId, String relationNo, long applicationId,
                                     EndpointKind sourceKind, String sourceSnapshotJson,
                                     EndpointKind targetKind, String targetSnapshotJson,
                                     AccessProtocol protocol, String ports, String purpose,
                                     String processDescription, LocalDateTime validFrom,
                                     LocalDateTime validUntil, RelationStatus status,
                                     String closeReason, Long closedBy, LocalDateTime closedAt,
                                     long rowVersion, long createdBy, long updatedBy,
                                     LocalDateTime createdAt, LocalDateTime updatedAt) {
            this(id, tenantId, relationNo, applicationId, null, null, null, sourceKind, sourceSnapshotJson,
                    targetKind, targetSnapshotJson, protocol, ports, purpose, processDescription, validFrom,
                    validUntil, validUntil == null ? ValidityType.LONG_TERM : ValidityType.LIMITED, status,
                    closeReason, null, closedBy, closedAt, false, 0, List.of(), rowVersion, createdBy,
                    updatedBy, createdAt, updatedAt);
        }
    }

    public record NetworkAccessExemptionRule(
            long id,
            long tenantId,
            String ruleCode,
            String ruleName,
            long sourceNetworkZoneId,
            String sourceNetworkZoneName,
            long targetNetworkZoneId,
            String targetNetworkZoneName,
            AccessProtocol protocol,
            String ports,
            LocalDateTime validFrom,
            LocalDateTime validUntil,
            ValidityType validityType,
            ExemptionRuleStatus status,
            String remark,
            long rowVersion,
            long createdBy,
            long updatedBy,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    public record NetworkAccessHistoryEvent(
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

    public record WorkflowReceiptStart(
            long id,
            long tenantId,
            String eventId,
            String subscriberKey,
            long applicationId,
            int roundNo,
            long workflowInstanceId,
            String eventType) {
    }

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
