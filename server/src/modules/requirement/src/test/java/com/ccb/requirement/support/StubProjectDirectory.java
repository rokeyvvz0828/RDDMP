package com.ccb.requirement.support;

import com.ccb.requirement.service.RequirementProjectMemberService;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.ProjectAccess;
import com.ccb.system.capability.ProjectAccessService;
import com.ccb.system.capability.ProjectMemberReference;
import com.ccb.system.capability.ProjectMemberReferenceQuery;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;

/** 测试用项目组织架构目录：固定项目与成员列表，不访问真实平台数据。 */
public final class StubProjectDirectory {
    private StubProjectDirectory() {
    }

    public static RequirementProjectMemberService service(JdbcTemplate jdbc, List<Long> memberUserIds) {
        ProjectAccessService projectAccess = (projectRef, actor) -> new ProjectAccess(1L, projectRef, "测试项目");
        ProjectMemberReferenceQuery members = new ProjectMemberReferenceQuery() {
            @Override
            public List<ProjectMemberReference> findActiveMembers(AuthUser actor, long projectId) {
                return memberUserIds.stream()
                        .map(id -> new ProjectMemberReference(id, id, "用户" + id, "user" + id))
                        .toList();
            }

            @Override
            public Optional<ProjectMemberReference> findActiveMember(AuthUser actor, long projectId,
                                                                     long projectMemberId) {
                return Optional.empty();
            }
        };
        return new RequirementProjectMemberService(jdbc, projectAccess, members);
    }
}
