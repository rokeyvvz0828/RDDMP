package com.ccb.requirement.integration;

import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.security.model.AuthUser;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** 开发任务的内部只读来源；调用方先校验平台项目访问和系统范围，不直接暴露为 HTTP。 */
public interface RequirementDevelopmentQuery {
    PageResult<SourceRequirement> search(AuthUser actor, Query query);

    Optional<SourceRequirement> find(AuthUser actor, String projectRef, long requirementId);

    record Query(String projectRef, Set<String> systemCodes, String keyword, PageQuery page) {
        public Query { systemCodes = Set.copyOf(systemCodes); }
    }

    enum Role { LEAD, CHANGE, TEST }

    record SourceSystem(String systemCode, Set<Role> roles, Long suggestedOwnerId) {
        public SourceSystem { roles = Set.copyOf(roles); }
    }

    /** projectId 是需求域主键，不能直接用于平台项目授权。 */
    record SourceRequirement(long id, String number, String name, String summary, long projectId,
                             String revision, boolean active, List<SourceSystem> systems) {
        public SourceRequirement { systems = List.copyOf(systems); }
    }
}
