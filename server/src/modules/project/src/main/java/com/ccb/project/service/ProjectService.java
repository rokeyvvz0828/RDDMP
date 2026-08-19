package com.ccb.project.service;

import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.project.model.ProjectAction;
import com.ccb.project.model.ProjectMembership;
import com.ccb.project.model.ProjectRole;
import com.ccb.project.model.ProjectStatus;
import com.ccb.project.model.ProjectSummary;
import com.ccb.project.repository.ProjectRepository;
import com.ccb.project.web.ProjectCommands;
import com.ccb.security.model.AuthUser;
import com.ccb.system.model.UserDirectory;
import com.ccb.system.model.UserDirectoryUser;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class ProjectService {
    private final ProjectRepository repository;
    private final ProjectContextService context;
    private final UserDirectory userDirectory;

    public ProjectService(ProjectRepository repository, ProjectContextService context, UserDirectory userDirectory) {
        this.repository = repository;
        this.context = context;
        this.userDirectory = userDirectory;
    }

    public PageResult<ProjectSummary> list(PageQuery pageQuery, String keyword, ProjectStatus status, AuthUser user) {
        return context.summaries(repository.listAvailable(user.tenantId(), user.id(), pageQuery, keyword, status),
                user.tenantId());
    }

    public List<ProjectSummary> available(AuthUser user) {
        return context.available(user);
    }

    public ProjectSummary get(long projectId, AuthUser user) {
        return context.requireAccess(projectId, user, ProjectAction.VIEW);
    }

    @Transactional
    public ProjectSummary create(ProjectCommands.CreateProject command, AuthUser user) {
        String projectCode = normalizeCode(command.projectCode());
        String projectName = normalizeName(command.projectName());
        if (repository.existsByCode(user.tenantId(), projectCode, null)) {
            throw conflict("项目编号已存在");
        }
        userDirectory.requireActive(user.tenantId(), Set.of(user.id()));
        long projectId = nextId();
        ProjectRepository.ProjectRecord project = new ProjectRepository.ProjectRecord(
                projectId, user.tenantId(), projectCode, projectName, ProjectStatus.ACTIVE,
                user.id(), 0L, LocalDateTime.now(), LocalDateTime.now());
        try {
            repository.insertProject(project, user.id());
            repository.insertMember(user.tenantId(), projectId, user.id(), ProjectRole.OWNER, user.id());
        } catch (DuplicateKeyException exception) {
            throw conflict("项目编号已存在");
        }
        repository.audit(user.tenantId(), projectId, user.id(), "PROJECT_CREATED",
                "code=" + projectCode + ";owner=" + user.id());
        return context.requireAccess(projectId, user, ProjectAction.VIEW);
    }

    @Transactional
    public ProjectSummary update(long projectId, ProjectCommands.UpdateProject command, AuthUser user) {
        context.requireAccess(projectId, user, ProjectAction.MANAGE_PROJECT);
        String projectName = normalizeName(command.projectName());
        requireVersion(repository.updateProjectName(user.tenantId(), projectId, projectName, command.version(), user.id()));
        repository.audit(user.tenantId(), projectId, user.id(), "PROJECT_UPDATED", "name=" + projectName);
        return context.requireAccess(projectId, user, ProjectAction.VIEW);
    }

    @Transactional
    public ProjectSummary archive(long projectId, ProjectCommands.VersionCommand command, AuthUser user) {
        ProjectSummary project = context.requireOwner(projectId, user);
        if (project.status() != ProjectStatus.ACTIVE) {
            throw conflict("项目已归档");
        }
        requireVersion(repository.updateStatus(user.tenantId(), projectId, ProjectStatus.ACTIVE,
                ProjectStatus.ARCHIVED, command.version(), user.id()));
        repository.audit(user.tenantId(), projectId, user.id(), "PROJECT_ARCHIVED", "status=ARCHIVED");
        return context.requireAccess(projectId, user, ProjectAction.VIEW);
    }

    @Transactional
    public ProjectSummary restore(long projectId, ProjectCommands.VersionCommand command, AuthUser user) {
        ProjectSummary project = context.requireOwner(projectId, user);
        if (project.status() != ProjectStatus.ARCHIVED) {
            throw conflict("项目未归档");
        }
        requireVersion(repository.updateStatus(user.tenantId(), projectId, ProjectStatus.ARCHIVED,
                ProjectStatus.ACTIVE, command.version(), user.id()));
        repository.audit(user.tenantId(), projectId, user.id(), "PROJECT_RESTORED", "status=ACTIVE");
        return context.requireAccess(projectId, user, ProjectAction.VIEW);
    }

    public List<ProjectMembership> members(long projectId, AuthUser user) {
        ProjectSummary project = context.requireAccess(projectId, user, ProjectAction.VIEW);
        List<ProjectRepository.MemberRecord> members = repository.findMembers(user.tenantId(), projectId);
        Set<Long> userIds = members.stream().map(ProjectRepository.MemberRecord::userId)
                .collect(java.util.stream.Collectors.toSet());
        Map<Long, UserDirectoryUser> users = directoryUsers(user.tenantId(), userIds);
        return members.stream().map(member -> {
            UserDirectoryUser directoryUser = users.get(member.userId());
            return new ProjectMembership(projectId, member.userId(), directoryUser.username(),
                    directoryUser.displayName(), member.role(),
                    ProjectContextService.allowedActions(member.role(), project.status()));
        }).toList();
    }

    public PageResult<UserDirectoryUser> memberCandidates(PageQuery pageQuery, String keyword, AuthUser user) {
        return userDirectory.searchActive(user.tenantId(), keyword, pageQuery);
    }

    @Transactional
    public ProjectSummary addMember(long projectId, ProjectCommands.AddMember command, AuthUser user) {
        context.requireAccess(projectId, user, ProjectAction.MANAGE_MEMBERS);
        requireAssignableRole(command.role());
        userDirectory.requireActive(user.tenantId(), Set.of(command.userId()));
        if (repository.findMembership(user.tenantId(), projectId, command.userId()).isPresent()) {
            throw conflict("项目成员已存在");
        }
        requireVersion(repository.claimVersion(user.tenantId(), projectId, command.version(), user.id()));
        try {
            repository.insertMember(user.tenantId(), projectId, command.userId(), command.role(), user.id());
        } catch (DuplicateKeyException exception) {
            throw conflict("项目成员已存在");
        }
        repository.audit(user.tenantId(), projectId, user.id(), "MEMBER_ADDED",
                "user=" + command.userId() + ";role=" + command.role());
        return context.requireAccess(projectId, user, ProjectAction.VIEW);
    }

    @Transactional
    public ProjectSummary changeMemberRole(long projectId, long memberUserId,
                                           ProjectCommands.ChangeMemberRole command, AuthUser user) {
        context.requireAccess(projectId, user, ProjectAction.MANAGE_MEMBERS);
        requireAssignableRole(command.role());
        ProjectRepository.MemberRecord member = requireMember(user.tenantId(), projectId, memberUserId);
        if (member.role() == ProjectRole.OWNER) {
            throw badRequest("负责人角色只能通过负责人转移变更");
        }
        requireVersion(repository.claimVersion(user.tenantId(), projectId, command.version(), user.id()));
        if (repository.updateMemberRole(user.tenantId(), projectId, memberUserId, command.role(), user.id()) != 1) {
            throw conflict("项目成员已变化，请刷新后重试");
        }
        repository.audit(user.tenantId(), projectId, user.id(), "MEMBER_ROLE_CHANGED",
                "user=" + memberUserId + ";role=" + command.role());
        return context.requireAccess(projectId, user, ProjectAction.VIEW);
    }

    @Transactional
    public ProjectSummary removeMember(long projectId, long memberUserId, long version, AuthUser user) {
        context.requireAccess(projectId, user, ProjectAction.MANAGE_MEMBERS);
        ProjectRepository.MemberRecord member = requireMember(user.tenantId(), projectId, memberUserId);
        if (member.role() == ProjectRole.OWNER) {
            throw badRequest("负责人不能从项目成员中移除");
        }
        requireVersion(repository.claimVersion(user.tenantId(), projectId, version, user.id()));
        if (repository.deleteMember(user.tenantId(), projectId, memberUserId) != 1) {
            throw conflict("项目成员已变化，请刷新后重试");
        }
        repository.audit(user.tenantId(), projectId, user.id(), "MEMBER_REMOVED", "user=" + memberUserId);
        return context.requireAccess(projectId, user, ProjectAction.VIEW);
    }

    @Transactional
    public ProjectSummary transferOwner(long projectId, ProjectCommands.TransferOwner command, AuthUser user) {
        ProjectSummary project = context.requireAccess(projectId, user, ProjectAction.MANAGE_PROJECT);
        if (command.newOwnerUserId() == user.id()) {
            throw badRequest("新负责人不能与当前负责人相同");
        }
        userDirectory.requireActive(user.tenantId(), Set.of(command.newOwnerUserId()));
        ProjectRepository.MemberRecord nextOwner = requireMember(user.tenantId(), projectId, command.newOwnerUserId());
        requireVersion(repository.claimVersion(user.tenantId(), projectId, command.version(), user.id()));
        requireChanged(repository.updateMemberRole(user.tenantId(), projectId, project.ownerUserId(),
                ProjectRole.ADMIN, user.id()));
        requireChanged(repository.updateMemberRole(user.tenantId(), projectId, nextOwner.userId(),
                ProjectRole.OWNER, user.id()));
        requireChanged(repository.updateOwner(user.tenantId(), projectId, nextOwner.userId(), user.id()));
        repository.audit(user.tenantId(), projectId, user.id(), "OWNER_TRANSFERRED",
                "from=" + project.ownerUserId() + ";to=" + nextOwner.userId());
        return context.requireAccess(projectId, user, ProjectAction.VIEW);
    }

    private ProjectRepository.MemberRecord requireMember(long tenantId, long projectId, long userId) {
        return repository.findMembership(tenantId, projectId, userId)
                .orElseThrow(() -> badRequest("项目成员不存在"));
    }

    private void requireAssignableRole(ProjectRole role) {
        if (role == ProjectRole.OWNER) {
            throw badRequest("负责人角色只能通过负责人转移设置");
        }
    }

    private void requireVersion(int changed) {
        if (changed != 1) {
            throw conflict("项目已被其他用户修改，请刷新后重试");
        }
    }

    private void requireChanged(int changed) {
        if (changed != 1) {
            throw conflict("项目成员已变化，请刷新后重试");
        }
    }

    private String normalizeCode(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT);
        if (!normalized.matches("[A-Z0-9][A-Z0-9._-]{0,63}")) {
            throw badRequest("项目编号只能包含字母、数字、点、下划线和短横线，且不能超过 64 个字符");
        }
        return normalized;
    }

    private String normalizeName(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw badRequest("项目名称不能为空");
        }
        if (normalized.length() > 128) {
            throw badRequest("项目名称不能超过 128 个字符");
        }
        return normalized;
    }

    private Map<Long, UserDirectoryUser> directoryUsers(long tenantId, Set<Long> userIds) {
        Map<Long, UserDirectoryUser> result = new LinkedHashMap<>();
        for (Long userId : userIds.stream().sorted().toList()) {
            try {
                result.put(userId, userDirectory.requireActive(tenantId, Set.of(userId)).get(userId));
            } catch (BusinessException exception) {
                result.put(userId, new UserDirectoryUser(userId, "", "用户 " + userId, 0L, null));
            }
        }
        return result;
    }

    private BusinessException badRequest(String message) {
        return new BusinessException(ErrorCode.BAD_REQUEST, message);
    }

    private BusinessException conflict(String message) {
        return new BusinessException(ErrorCode.CONFLICT, message);
    }

    private long nextId() {
        return System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000);
    }
}
