package com.ccb.project.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.project.model.ProjectAction;
import com.ccb.project.model.ProjectRole;
import com.ccb.project.model.ProjectStatus;
import com.ccb.project.repository.ProjectRepository;
import com.ccb.security.model.AuthUser;
import com.ccb.system.model.UserDirectory;
import com.ccb.system.model.UserDirectoryUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectContextServiceTest {
    @Mock
    private ProjectRepository repository;
    @Mock
    private UserDirectory userDirectory;

    private ProjectContextService service;
    private final AuthUser user = new AuthUser(7L, 1L, "alice", "", "Alice", 3L, true);

    @BeforeEach
    void setUp() {
        service = new ProjectContextService(repository, userDirectory);
    }

    @Test
    void enforcesRoleMatrixForActiveProject() {
        ProjectRepository.ProjectRecord project = project(ProjectStatus.ACTIVE);
        when(repository.findById(1L, 12L)).thenReturn(Optional.of(project));
        when(userDirectory.requireActive(1L, Set.of(7L))).thenReturn(Map.of(7L, directoryUser()));

        for (ProjectRole role : ProjectRole.values()) {
            when(repository.findMembership(1L, 12L, 7L))
                    .thenReturn(Optional.of(new ProjectRepository.MemberRecord(12L, 7L, role)));
            Set<ProjectAction> allowed = service.membership(12L, user).allowedActions();
            assertTrue(allowed.contains(ProjectAction.VIEW));
            assertEquals(role != ProjectRole.VIEWER, allowed.contains(ProjectAction.WRITE));
            assertEquals(role == ProjectRole.OWNER || role == ProjectRole.ADMIN,
                    allowed.contains(ProjectAction.MANAGE_MEMBERS));
            assertEquals(role == ProjectRole.OWNER, allowed.contains(ProjectAction.MANAGE_PROJECT));
        }
    }

    @Test
    void archivedProjectIsReadOnlyForEveryMemberRole() {
        when(repository.findById(1L, 12L)).thenReturn(Optional.of(project(ProjectStatus.ARCHIVED)));
        when(repository.findMembership(1L, 12L, 7L))
                .thenReturn(Optional.of(new ProjectRepository.MemberRecord(12L, 7L, ProjectRole.OWNER)));
        when(userDirectory.requireActive(1L, Set.of(7L))).thenReturn(Map.of(7L, directoryUser()));

        assertEquals(Set.of(ProjectAction.VIEW), service.membership(12L, user).allowedActions());
        assertThrows(BusinessException.class,
                () -> service.requireAccess(12L, user, ProjectAction.WRITE));
        assertThrows(BusinessException.class,
                () -> service.requireAccess(12L, user, ProjectAction.MANAGE_MEMBERS));
        assertThrows(BusinessException.class,
                () -> service.requireAccess(12L, user, ProjectAction.MANAGE_PROJECT));
    }

    @Test
    void hidesWhetherProjectIsMissingCrossTenantOrOutsideMembership() {
        when(repository.findById(1L, 12L)).thenReturn(Optional.empty());
        BusinessException missing = assertThrows(BusinessException.class,
                () -> service.requireAccess(12L, user, ProjectAction.VIEW));

        when(repository.findById(1L, 12L)).thenReturn(Optional.of(project(ProjectStatus.ACTIVE)));
        when(repository.findMembership(1L, 12L, 7L)).thenReturn(Optional.empty());
        BusinessException nonMember = assertThrows(BusinessException.class,
                () -> service.requireAccess(12L, user, ProjectAction.VIEW));

        assertEquals(ErrorCode.FORBIDDEN, missing.code());
        assertEquals(missing.getMessage(), nonMember.getMessage());
        assertFalse(missing.getMessage().contains("不存在"));
    }

    @Test
    void availableProjectsCarryRoleAndStateActions() {
        ProjectRepository.ProjectRecord active = project(ProjectStatus.ACTIVE);
        ProjectRepository.ProjectRecord archived = new ProjectRepository.ProjectRecord(
                13L, 1L, "PRJ-013", "Archived", ProjectStatus.ARCHIVED, 7L, 2L,
                LocalDateTime.now(), LocalDateTime.now());
        when(repository.findAvailable(1L, 7L)).thenReturn(List.of(
                new ProjectRepository.ProjectAccessRecord(active, ProjectRole.ADMIN),
                new ProjectRepository.ProjectAccessRecord(archived, ProjectRole.OWNER)));
        when(userDirectory.requireActive(1L, Set.of(7L))).thenReturn(Map.of(7L, directoryUser()));

        assertEquals(Set.of(ProjectAction.VIEW, ProjectAction.WRITE, ProjectAction.MANAGE_MEMBERS),
                service.available(user).get(0).allowedActions());
        assertEquals(Set.of(ProjectAction.VIEW), service.available(user).get(1).allowedActions());
    }

    private ProjectRepository.ProjectRecord project(ProjectStatus status) {
        return new ProjectRepository.ProjectRecord(12L, 1L, "PRJ-012", "Project", status, 7L, 1L,
                LocalDateTime.now(), LocalDateTime.now());
    }

    private UserDirectoryUser directoryUser() {
        return new UserDirectoryUser(7L, "alice", "Alice", 3L, "研发部");
    }
}
