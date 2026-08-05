package com.ccb.security.model;

public record AuthUser(long id, long tenantId, String username, String passwordHash,
                       String displayName, long orgId, boolean enabled,
                       String orgName, String avatarObjectKey) {
    public AuthUser(long id, long tenantId, String username, String passwordHash,
                    String displayName, long orgId, boolean enabled) {
        this(id, tenantId, username, passwordHash, displayName, orgId, enabled, null, null);
    }
}