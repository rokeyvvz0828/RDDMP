package com.ccb.architecture.model;

public record PhysicalSubsystemCommand(
        String code,
        String shortName,
        String name,
        Long logicalSubsystemId,
        String businessGroupName,
        Long responsibleTeamOrgId,
        String runtimeCode,
        String systemLevelCode,
        String developmentFrameworkCode,
        Long ownerUserId,
        String description,
        String remark) {
}
