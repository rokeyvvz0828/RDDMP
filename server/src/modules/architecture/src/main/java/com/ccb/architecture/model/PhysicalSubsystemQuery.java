package com.ccb.architecture.model;

public record PhysicalSubsystemQuery(
        String code,
        String shortName,
        String name,
        String businessGroupName,
        Long responsibleTeamOrgId,
        Long logicalSubsystemId,
        String status) {
    public PhysicalSubsystemQuery(String code, String shortName, String name, String businessGroupName,
                                  Long responsibleTeamOrgId, Long logicalSubsystemId) {
        this(code, shortName, name, businessGroupName, responsibleTeamOrgId, logicalSubsystemId, null);
    }

    public static PhysicalSubsystemQuery empty() {
        return new PhysicalSubsystemQuery(null, null, null, null, null, null, null);
    }
}
