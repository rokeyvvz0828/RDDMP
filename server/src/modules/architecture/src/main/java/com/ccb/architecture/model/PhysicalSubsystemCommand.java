package com.ccb.architecture.model;

public record PhysicalSubsystemCommand(
        String code,
        String shortName,
        String name,
        Long logicalSubsystemId,
        String businessGroupName,
        String businessContinuityLevel,
        String collectedSystemLevel,
        String deploymentPlatform,
        String disasterRecoveryMode,
        Long responsibleTeamOrgId,
        String runtimeCode,
        String systemLevelCode,
        String developmentFrameworkCode,
        Long ownerUserId,
        String description,
        String remark) {

    public PhysicalSubsystemCommand(String code, String shortName, String name, Long logicalSubsystemId,
                                    String businessGroupName, Long responsibleTeamOrgId,
                                    String runtimeCode, String systemLevelCode,
                                    String developmentFrameworkCode, Long ownerUserId,
                                    String description, String remark) {
        this(code, shortName, name, logicalSubsystemId, businessGroupName, null, null, null, null,
                responsibleTeamOrgId, runtimeCode, systemLevelCode, developmentFrameworkCode, ownerUserId,
                description, remark);
    }
}
