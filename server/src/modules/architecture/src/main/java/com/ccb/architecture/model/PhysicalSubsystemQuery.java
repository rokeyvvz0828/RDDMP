package com.ccb.architecture.model;

public record PhysicalSubsystemQuery(
        String code,
        String shortName,
        String name,
        String logicalSubsystemName,
        String businessComponentCode,
        String businessGroupName,
        Long responsibleTeamOrgId,
        String status) {
    public PhysicalSubsystemQuery(String code, String shortName, String name, String businessGroupName,
                                  Long responsibleTeamOrgId) {
        this(code, shortName, name, null, null, businessGroupName, responsibleTeamOrgId, null);
    }

    public static PhysicalSubsystemQuery empty() {
        return new PhysicalSubsystemQuery(null, null, null, null, null, null, null, null);
    }
}
