package com.ccb.architecture.model;

public record PhysicalSubsystemQuery(
        String code,
        String shortName,
        String name,
        String businessGroupName,
        Long responsibleTeamOrgId,
        Long logicalSubsystemId) {
    public static PhysicalSubsystemQuery empty() {
        return new PhysicalSubsystemQuery(null, null, null, null, null, null);
    }
}
