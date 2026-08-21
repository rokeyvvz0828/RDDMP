package com.ccb.architecture.model;

public record LogicalSubsystemCommand(
        String code,
        String shortName,
        String name,
        Long businessOrgId,
        String deploymentPlatformCode,
        String systemTypeCode,
        String systemOwnershipCode,
        Long contactUserId,
        String description,
        String remark) {
}
