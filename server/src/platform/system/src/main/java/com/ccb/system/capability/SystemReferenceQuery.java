package com.ccb.system.capability;

import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.security.model.AuthUser;

import java.util.List;
import java.util.Optional;

/** 供业务模块读取当前租户用户和参数的安全查询契约。 */
public interface SystemReferenceQuery {
    PageResult<SystemUserReference> searchActiveUsers(AuthUser actor, PageQuery page, String keyword);

    Optional<SystemUserReference> findUser(AuthUser actor, long userId, boolean activeOnly);

    List<SystemParameterReference> activeParameters(AuthUser actor, String categoryCode);
}
