package com.ccb.system.capability;

import com.ccb.security.model.AuthUser;

import java.util.List;
import java.util.Optional;

/** 供业务模块按当前租户和项目读取有效项目成员引用的只读契约。 */
public interface ProjectMemberReferenceQuery {
    List<ProjectMemberReference> findActiveMembers(AuthUser actor, long projectId);

    Optional<ProjectMemberReference> findActiveMember(AuthUser actor, long projectId, long projectMemberId);
}
