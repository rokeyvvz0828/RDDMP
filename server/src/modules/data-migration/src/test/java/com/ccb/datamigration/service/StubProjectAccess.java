package com.ccb.datamigration.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.ProjectWorkflowDirectoryService;
import com.ccb.system.capability.ProjectWorkflowMember;
import com.ccb.system.capability.ProjectWorkflowRole;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 测试替身：模拟 platform/system 的项目可达性契约，即 T32-r1 之后项目成员口径的唯一来源。
 *
 * <p>数据迁移只允许使用 {@link #requireAccessible(long, AuthUser)} 与 {@link #accessibleProjectIds(AuthUser)}；
 * 其余工作流目录方法被调用即抛 {@link UnsupportedOperationException}，用作“业务模块偷偷借用无关能力”的传感器。
 */
final class StubProjectAccess implements ProjectWorkflowDirectoryService {
    /** 平台侧对单次项目可达性判定的裁决。 */
    enum Decision {
        ALLOW,
        PROJECT_NOT_FOUND,
        NOT_MEMBER
    }

    private final Decision decision;
    private final List<Long> accessible;
    private final List<Long> checkedProjectIds = new ArrayList<>();

    private StubProjectAccess(Decision decision, List<Long> accessible) {
        this.decision = decision;
        this.accessible = accessible;
    }

    static StubProjectAccess allow() {
        return new StubProjectAccess(Decision.ALLOW, List.of(10L));
    }

    static StubProjectAccess withDecision(Decision decision) {
        return new StubProjectAccess(decision, List.of(10L));
    }

    static StubProjectAccess withAccessibleProjects(Long... projectIds) {
        return new StubProjectAccess(Decision.ALLOW, List.of(projectIds));
    }

    /** 已交给平台判定的项目标识，用于断言“判定发生在契约侧而不是本模块 SQL”。 */
    List<Long> checkedProjectIds() {
        return checkedProjectIds;
    }

    @Override
    public void requireAccessible(long projectId, AuthUser actor) {
        checkedProjectIds.add(projectId);
        switch (decision) {
            case PROJECT_NOT_FOUND -> throw new BusinessException(ErrorCode.BAD_REQUEST, "项目不存在或已删除");
            case NOT_MEMBER -> throw new BusinessException(ErrorCode.FORBIDDEN, "无该项目数据访问权限");
            case ALLOW -> { /* 放行 */ }
        }
    }

    @Override
    public List<Long> accessibleProjectIds(AuthUser actor) {
        return accessible;
    }

    @Override
    public ProjectScope requireAccessible(String projectRef, AuthUser actor) {
        throw workflowOnly();
    }

    @Override
    public void requireManageable(long projectId, AuthUser actor) {
        throw workflowOnly();
    }

    @Override
    public List<ProjectWorkflowMember> members(long projectId, AuthUser actor) {
        throw workflowOnly();
    }

    @Override
    public List<ProjectWorkflowRole> roles(long projectId, AuthUser actor) {
        throw workflowOnly();
    }

    @Override
    public void requireMembers(long projectId, Collection<Long> userIds, AuthUser actor) {
        throw workflowOnly();
    }

    @Override
    public List<ProjectWorkflowMember> membersForRoles(long projectId, Collection<Long> roleIds, AuthUser actor) {
        throw workflowOnly();
    }

    private static UnsupportedOperationException workflowOnly() {
        return new UnsupportedOperationException("数据迁移模块不得调用平台的项目工作流目录能力");
    }
}
