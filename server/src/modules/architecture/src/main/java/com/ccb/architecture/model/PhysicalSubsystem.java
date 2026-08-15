package com.ccb.architecture.model;

import java.time.LocalDateTime;

public record PhysicalSubsystem(
        long id,
        String code,
        String shortName,
        String name,
        long logicalSubsystemId,
        String businessGroupName,
        long responsibleTeamOrgId,
        String responsibleTeamNameSnapshot,
        String runtimeCode,
        String systemLevelCode,
        String developmentFrameworkCode,
        Long ownerUserId,
        Long contactUserId,
        String description,
        String remark,
        long createdBy,
        long updatedBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
