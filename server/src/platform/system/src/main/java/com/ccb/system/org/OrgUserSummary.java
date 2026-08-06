package com.ccb.system.org;

public record OrgUserSummary(long id, String username, String displayName, long orgId,
                             String orgName, String avatarUrl, int status) {
}