package com.ccb.development.integration;

import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.security.model.AuthUser;

import java.util.List;
import java.util.Set;

/** 开发域的来源端口；系统编码必须由服务端已授权的系统集合派生。 */
public interface DevelopmentSourceDirectory {
    PageResult<SourceRequirement> search(AuthUser actor, SourceQuery query);
    SourceRequirement requireCurrent(AuthUser actor, String projectRef, long requirementId);

    record SourceQuery(String projectRef, Set<String> systemCodes, String keyword, PageQuery page) {
        public SourceQuery { systemCodes = Set.copyOf(systemCodes); }
    }

    enum Role { LEAD, CHANGE, TEST }

    record SourceSystem(String systemCode, Set<Role> roles, Long suggestedOwnerId) {
        public SourceSystem { roles = Set.copyOf(roles); }
    }

    /** projectId 由组合根转换为已授权的平台项目主键。 */
    record SourceRequirement(long id, String number, String name, String summary, long projectId,
                             String revision, boolean active, List<SourceSystem> systems) {
        public SourceRequirement { systems = List.copyOf(systems); }
    }
}
