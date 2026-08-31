package com.ccb.architecture.model;

import java.time.LocalDateTime;

public record PhysicalSubsystem(
        long id,
        String code,
        String shortName,
        String name,
        long logicalSubsystemId,
        String businessGroupName,
        String businessContinuityLevel,
        String collectedSystemLevel,
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
        long createdBy,
        long updatedBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String numberSlot,
        String englishName,
        String status,
        long rowVersion) {

    /** 兼容既有主数据查询调用，新增生命周期字段使用发布默认值。 */
    public PhysicalSubsystem(long id, String code, String shortName, String name, long logicalSubsystemId,
                             String businessGroupName, long responsibleTeamOrgId,
                             String responsibleTeamNameSnapshot, String runtimeCode, String systemLevelCode,
                             String developmentFrameworkCode, Long ownerUserId, String description, String remark,
                             long createdBy, long updatedBy, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this(id, code, shortName, name, logicalSubsystemId, businessGroupName, responsibleTeamOrgId,
                responsibleTeamNameSnapshot, runtimeCode, systemLevelCode, developmentFrameworkCode, ownerUserId,
                description, remark, createdBy, updatedBy, createdAt, updatedAt, null, null, "ACTIVE", 0);
    }

    /** 兼容既有主数据查询调用，登记表来源字段使用空值。 */
    public PhysicalSubsystem(long id, String code, String shortName, String name, long logicalSubsystemId,
                             String businessGroupName, long responsibleTeamOrgId,
                             String responsibleTeamNameSnapshot, String runtimeCode, String systemLevelCode,
                             String developmentFrameworkCode, Long ownerUserId, String description, String remark,
                             long createdBy, long updatedBy, LocalDateTime createdAt, LocalDateTime updatedAt,
                             String numberSlot, String englishName, String status, long rowVersion) {
        this(id, code, shortName, name, logicalSubsystemId, businessGroupName, null, null, null, null,
                responsibleTeamOrgId, responsibleTeamNameSnapshot, runtimeCode, systemLevelCode,
                developmentFrameworkCode, ownerUserId, description, remark, createdBy, updatedBy,
                createdAt, updatedAt, numberSlot, englishName, status, rowVersion);
    }
}
