package com.ccb.architecture.model;

import java.time.LocalDateTime;
import java.util.List;

public record LogicalSubsystem(
        long id,
        String code,
        String shortName,
        String name,
        long businessOrgId,
        String deploymentPlatformCode,
        String systemTypeCode,
        String systemOwnershipCode,
        long contactUserId,
        String description,
        String remark,
        long createdBy,
        long updatedBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Integer numberSequence,
        String status,
        int sortNo,
        long rowVersion,
        List<PhysicalSubsystemSummary> physicalSubsystems) {

    public LogicalSubsystem {
        physicalSubsystems = physicalSubsystems == null ? List.of() : List.copyOf(physicalSubsystems);
    }

    /** 兼容既有主数据查询调用，新增生命周期字段使用发布默认值。 */
    public LogicalSubsystem(long id, String code, String shortName, String name, long businessOrgId,
                            String deploymentPlatformCode, String systemTypeCode, String systemOwnershipCode,
                            long contactUserId, String description, String remark, long createdBy, long updatedBy,
                            LocalDateTime createdAt, LocalDateTime updatedAt) {
        this(id, code, shortName, name, businessOrgId, deploymentPlatformCode, systemTypeCode,
                systemOwnershipCode, contactUserId, description, remark, createdBy, updatedBy, createdAt, updatedAt,
                null, "ACTIVE", 0, 0, List.of());
    }

    public LogicalSubsystem withPhysicalSubsystems(List<PhysicalSubsystemSummary> summaries) {
        return new LogicalSubsystem(id, code, shortName, name, businessOrgId, deploymentPlatformCode,
                systemTypeCode, systemOwnershipCode, contactUserId, description, remark, createdBy, updatedBy,
                createdAt, updatedAt, numberSequence, status, sortNo, rowVersion, summaries);
    }
}
