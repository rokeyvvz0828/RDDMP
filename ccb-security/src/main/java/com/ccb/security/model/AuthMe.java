package com.ccb.security.model;

import java.util.List;

public record AuthMe(long id, long tenantId, String username, String displayName,
                     long orgId, String orgName, String avatarUrl,
                     List<String> roles, List<String> permissions) {
    public AuthMe(long id, long tenantId, String username, String displayName,
                  long orgId, List<String> roles, List<String> permissions) {
        this(id, tenantId, username, displayName, orgId, null, null, roles, permissions);
    }
}