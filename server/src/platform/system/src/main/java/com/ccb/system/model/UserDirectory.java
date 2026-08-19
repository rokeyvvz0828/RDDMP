package com.ccb.system.model;

import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;

import java.util.Map;
import java.util.Set;

/** Read-only tenant user directory for business modules. */
public interface UserDirectory {
    PageResult<UserDirectoryUser> searchActive(long tenantId, String keyword, PageQuery pageQuery);

    Map<Long, UserDirectoryUser> requireActive(long tenantId, Set<Long> userIds);
}
