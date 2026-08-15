package com.ccb.architecture.model;

public record LogicalSubsystemQuery(String code, String shortName, String name, Long businessOrgId) {
    public static LogicalSubsystemQuery empty() {
        return new LogicalSubsystemQuery(null, null, null, null);
    }
}
