package com.ccb.requirement.integration;

import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.security.model.AuthUser;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 供投产管理消费的需求主数据最小只读投影；消费者仍需执行自己的业务授权。 */
public interface ReleaseRequirementQuery {
    PageResult<RequirementRef> searchActive(AuthUser actor, String projectRef, PageQuery page, String keyword);

    Optional<List<RequirementRef>> resolveActive(AuthUser actor, String projectRef, Collection<String> numbers);

    record RequirementRef(long id, String number, String name, String status) {}
}
