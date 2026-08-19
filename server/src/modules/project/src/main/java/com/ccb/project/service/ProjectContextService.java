package com.ccb.project.service;

import com.ccb.common.api.PageResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.project.api.ProjectContextPort;
import com.ccb.project.model.ProjectAction;
import com.ccb.project.model.ProjectMembership;
import com.ccb.project.model.ProjectRole;
import com.ccb.project.model.ProjectStatus;
import com.ccb.project.model.ProjectSummary;
import com.ccb.project.repository.ProjectRepository;
import com.ccb.security.model.AuthUser;
import com.ccb.system.model.UserDirectory;
import com.ccb.system.model.UserDirectoryUser;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ProjectContextService implements ProjectContextPort {
    private final ProjectRepository repository;
    private final UserDirectory userDirectory;

    public ProjectContextService(ProjectRepository repository, UserDirectory userDirectory) {
        this.repository = repository;
        this.userDirectory = userDirectory;
    }

    @Override
    public ProjectSummary requireAccess(long projectId, AuthUser user, ProjectAction action) {
        ResolvedAccess resolved = resolve(projectId, user);
        if (!allowedActions(resolved.membership().role(), resolved.project().status()).contains(action)) {
            throw inaccessible();
        }
        return toSummary(resolved.project(), resolved.membership().role());
    }

    @Override
    public ProjectMembership membership(long projectId, AuthUser user) {
        ResolvedAccess resolved = resolve(projectId, user);
        UserDirectoryUser directoryUser = userDirectory.requireActive(user.tenantId(), Set.of(user.id())).get(user.id());
        return new ProjectMembership(projectId, user.id(), directoryUser.username(), directoryUser.displayName(),
                resolved.membership().role(), allowedActions(resolved.membership().role(), resolved.project().status()));
    }

    @Override
    public List<ProjectSummary> available(AuthUser user) {
        List<ProjectRepository.ProjectAccessRecord> records = repository.findAvailable(user.tenantId(), user.id());
        Map<Long, UserDirectoryUser> owners = ownerDirectory(user.tenantId(), records.stream()
                .map(record -> record.project().ownerUserId()).collect(Collectors.toSet()));
        return records.stream().map(record -> toSummary(record.project(), record.role(), owners)).toList();
    }

    public PageResult<ProjectSummary> summaries(PageResult<ProjectRepository.ProjectAccessRecord> page, long tenantId) {
        Map<Long, UserDirectoryUser> owners = ownerDirectory(tenantId, page.records().stream()
                .map(record -> record.project().ownerUserId()).collect(Collectors.toSet()));
        List<ProjectSummary> records = page.records().stream()
                .map(record -> toSummary(record.project(), record.role(), owners)).toList();
        return new PageResult<>(records, page.total(), page.page(), page.size());
    }

    public ProjectSummary requireOwner(long projectId, AuthUser user) {
        ResolvedAccess resolved = resolve(projectId, user);
        if (resolved.membership().role() != ProjectRole.OWNER) {
            throw inaccessible();
        }
        return toSummary(resolved.project(), resolved.membership().role());
    }

    public static Set<ProjectAction> allowedActions(ProjectRole role, ProjectStatus status) {
        if (status == ProjectStatus.ARCHIVED) {
            return Set.of(ProjectAction.VIEW);
        }
        EnumSet<ProjectAction> actions = EnumSet.of(ProjectAction.VIEW);
        if (role != ProjectRole.VIEWER) {
            actions.add(ProjectAction.WRITE);
        }
        if (role == ProjectRole.OWNER || role == ProjectRole.ADMIN) {
            actions.add(ProjectAction.MANAGE_MEMBERS);
        }
        if (role == ProjectRole.OWNER) {
            actions.add(ProjectAction.MANAGE_PROJECT);
        }
        return Set.copyOf(actions);
    }

    private ResolvedAccess resolve(long projectId, AuthUser user) {
        ProjectRepository.ProjectRecord project = repository.findById(user.tenantId(), projectId)
                .orElseThrow(this::inaccessible);
        ProjectRepository.MemberRecord membership = repository.findMembership(user.tenantId(), projectId, user.id())
                .orElseThrow(this::inaccessible);
        return new ResolvedAccess(project, membership);
    }

    private ProjectSummary toSummary(ProjectRepository.ProjectRecord project, ProjectRole role) {
        return toSummary(project, role, ownerDirectory(project.tenantId(), Set.of(project.ownerUserId())));
    }

    private ProjectSummary toSummary(ProjectRepository.ProjectRecord project, ProjectRole role,
                                     Map<Long, UserDirectoryUser> owners) {
        UserDirectoryUser owner = owners.get(project.ownerUserId());
        String ownerName = owner == null ? "用户 " + project.ownerUserId() : owner.displayName();
        return new ProjectSummary(project.id(), project.projectCode(), project.projectName(), project.status(),
                project.ownerUserId(), ownerName, role, allowedActions(role, project.status()), project.version());
    }

    private Map<Long, UserDirectoryUser> ownerDirectory(long tenantId, Set<Long> ownerIds) {
        if (ownerIds.isEmpty()) {
            return Map.of();
        }
        try {
            return userDirectory.requireActive(tenantId, ownerIds);
        } catch (BusinessException exception) {
            return Map.of();
        }
    }

    private BusinessException inaccessible() {
        return new BusinessException(ErrorCode.FORBIDDEN, "项目不可访问");
    }

    private record ResolvedAccess(ProjectRepository.ProjectRecord project,
                                  ProjectRepository.MemberRecord membership) {
    }
}
