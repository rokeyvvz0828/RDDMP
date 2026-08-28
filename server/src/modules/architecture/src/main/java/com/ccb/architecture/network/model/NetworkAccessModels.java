package com.ccb.architecture.network.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;
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

    public enum ApplicationStatus {
        DRAFT,
        IN_REVIEW,
        APPROVED,
        REJECTED,
        CANCELLED;

        public static ApplicationStatus fromDatabase(String value) {
            return enumValue(ApplicationStatus.class, value, "application_status");
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EndpointCommand(
            EndpointKind kind,
            Long physicalSubsystemId,
            Long environmentId,
            Long deploymentUnitId,
            Long externalAddressId,
            List<Long> instanceIds) {
        public EndpointCommand {
            instanceIds = List.copyOf(instanceIds == null ? List.of() : instanceIds);
        }
    }

    public record NetworkAccessApplication(
            long id,
            long tenantId,
            String applicationNo,
            long applicantId,
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
            ApplicationStatus status,
            long rowVersion,
            long createdBy,
            long updatedBy,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    public record NetworkAccessRelation(
            long id,
            long tenantId,
            String relationNo,
            long applicationId,
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
            RelationStatus status,
            String closeReason,
            Long closedBy,
            LocalDateTime closedAt,
            long rowVersion,
            long createdBy,
            long updatedBy,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
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
