package com.ccb.architecture.model;

import java.time.LocalDateTime;

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
        LocalDateTime updatedAt) {
}
