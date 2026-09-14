package com.ccb.release.integration;

import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.security.model.AuthUser;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 投产管理消费需求主数据的只读端口。 */
public interface ReleaseRequirementDirectory {
    PageResult<Requirement> searchActive(AuthUser actor, String projectRef, PageQuery page, String keyword);

    Optional<List<Requirement>> resolveActive(AuthUser actor, String projectRef, Collection<String> numbers);

    record Requirement(long id, String number, String name, String status) {}
}
