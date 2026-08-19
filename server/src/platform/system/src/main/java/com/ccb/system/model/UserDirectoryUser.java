package com.ccb.system.model;

/** Non-sensitive identity summary exposed by the system data owner. */
public record UserDirectoryUser(
        long id,
        String username,
        String displayName,
        long orgId,
        String orgName) {
}
