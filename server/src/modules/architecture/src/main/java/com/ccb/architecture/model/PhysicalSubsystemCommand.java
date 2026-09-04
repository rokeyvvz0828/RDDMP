package com.ccb.architecture.model;

public record PhysicalSubsystemCommand(
        String code,
        String shortName,
        String name,
        String logicalSubsystemName,
        String businessComponentCode,
        String businessGroupName,
        String deploymentPlatform,
        String disasterRecoveryMode,
        Long responsibleTeamOrgId,
        String runtimeCode,
        String systemLevelCode,
        String developmentFrameworkCode,
        Long ownerUserId,
        String description,
        String remark) {

    public PhysicalSubsystemCommand(String code, String shortName, String name, String logicalSubsystemName,
                                    String businessGroupName, Long responsibleTeamOrgId,
                                    String runtimeCode, String systemLevelCode,
                                    String developmentFrameworkCode, Long ownerUserId,
                                    String description, String remark) {
        this(code, shortName, name, logicalSubsystemName, null, businessGroupName, null, null,
                responsibleTeamOrgId, runtimeCode, systemLevelCode, developmentFrameworkCode, ownerUserId,
                description, remark);
    }
}
