package com.ccb.project.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.project.model.ProjectAction;
import com.ccb.project.model.ProjectRole;
import com.ccb.project.model.ProjectStatus;
import com.ccb.project.repository.ProjectRepository;
import com.ccb.project.web.ProjectCommands;
import com.ccb.security.model.AuthUser;
import com.ccb.system.model.UserDirectory;
import com.ccb.system.model.UserDirectoryUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {
    @Mock
    private ProjectRepository repository;
    @Mock
    private ProjectContextService context;
    @Mock
    private UserDirectory userDirectory;

    private ProjectService service;
    private final AuthUser owner = new AuthUser(7L, 1L, "alice", "", "Alice", 3L, true);

    @BeforeEach
    void setUp() {
        service = new ProjectService(repository, context, userDirectory);
    }

    @Test
    void rejectsDuplicateProjectCodeInsideTenant() {
        when(repository.existsByCode(1L, "PRJ-001", null)).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.create(new ProjectCommands.CreateProject(" prj-001 ", "Project"), owner));

        assertEquals(ErrorCode.CONFLICT, exception.code());
        verify(repository, never()).insertProject(any(), anyLong());
    }

    @Test
    void createsProjectWithExactlyOneOwnerAndAudit() {
        when(repository.existsByCode(1L, "PRJ-001", null)).thenReturn(false);
        when(userDirectory.requireActive(1L, Set.of(7L))).thenReturn(Map.of(
                7L, new UserDirectoryUser(7L, "alice", "Alice", 3L, "研发部")));
        when(context.requireAccess(anyLong(), eq(owner), eq(ProjectAction.VIEW)))
                .thenReturn(null);

        service.create(new ProjectCommands.CreateProject(" prj-001 ", " Project "), owner);

        ArgumentCaptor<ProjectRepository.ProjectRecord> project = ArgumentCaptor.forClass(ProjectRepository.ProjectRecord.class);
        verify(repository).insertProject(project.capture(), eq(7L));
        assertEquals("PRJ-001", project.getValue().projectCode());
        assertEquals("Project", project.getValue().projectName());
        assertEquals(ProjectStatus.ACTIVE, project.getValue().status());
        assertEquals(7L, project.getValue().ownerUserId());
        verify(repository).insertMember(1L, project.getValue().id(), 7L, ProjectRole.OWNER, 7L);
        verify(repository).audit(eq(1L), eq(project.getValue().id()), eq(7L), eq("PROJECT_CREATED"), any());
    }

    @Test
    void reportsOptimisticConflictWithoutAuditingUpdate() {
        when(context.requireAccess(12L, owner, ProjectAction.MANAGE_PROJECT)).thenReturn(summaryRecord());
        when(repository.updateProjectName(1L, 12L, "Renamed", 3L, 7L)).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.update(12L, new ProjectCommands.UpdateProject("Renamed", 3L), owner));

        assertEquals(ErrorCode.CONFLICT, exception.code());
        verify(repository, never()).audit(anyLong(), anyLong(), anyLong(), any(), any());
    }

    @Test
    void transferOwnerDemotesPreviousOwnerAndPreservesMembership() {
        when(context.requireAccess(12L, owner, ProjectAction.MANAGE_PROJECT)).thenReturn(summaryRecord());
        when(userDirectory.requireActive(1L, Set.of(8L))).thenReturn(Map.of(
                8L, new UserDirectoryUser(8L, "bob", "Bob", 3L, "研发部")));
        when(repository.findMembership(1L, 12L, 8L)).thenReturn(Optional.of(
                new ProjectRepository.MemberRecord(12L, 8L, ProjectRole.MEMBER)));
        when(repository.claimVersion(1L, 12L, 3L, 7L)).thenReturn(1);
        when(repository.updateMemberRole(1L, 12L, 7L, ProjectRole.ADMIN, 7L)).thenReturn(1);
        when(repository.updateMemberRole(1L, 12L, 8L, ProjectRole.OWNER, 7L)).thenReturn(1);
        when(repository.updateOwner(1L, 12L, 8L, 7L)).thenReturn(1);

        service.transferOwner(12L, new ProjectCommands.TransferOwner(8L, 3L), owner);

        verify(repository).updateMemberRole(1L, 12L, 7L, ProjectRole.ADMIN, 7L);
        verify(repository).updateMemberRole(1L, 12L, 8L, ProjectRole.OWNER, 7L);
        verify(repository).updateOwner(1L, 12L, 8L, 7L);
        verify(repository).audit(eq(1L), eq(12L), eq(7L), eq("OWNER_TRANSFERRED"), any());
    }

    @Test
    void memberApiCannotAssignOwnerRole() {
        when(context.requireAccess(12L, owner, ProjectAction.MANAGE_MEMBERS)).thenReturn(summaryRecord());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.addMember(12L, new ProjectCommands.AddMember(8L, ProjectRole.OWNER, 3L), owner));

        assertEquals(ErrorCode.BAD_REQUEST, exception.code());
        verify(repository, never()).claimVersion(anyLong(), anyLong(), anyLong(), anyLong());
    }

    @Test
    void restoresArchivedProjectWithoutChangingMembership() {
        com.ccb.project.model.ProjectSummary archived = new com.ccb.project.model.ProjectSummary(
                12L, "PRJ-012", "Project", ProjectStatus.ARCHIVED, 7L, "Alice",
                ProjectRole.OWNER, Set.of(ProjectAction.VIEW), 4L);
        when(context.requireOwner(12L, owner)).thenReturn(archived);
        when(repository.updateStatus(1L, 12L, ProjectStatus.ARCHIVED, ProjectStatus.ACTIVE, 4L, 7L))
                .thenReturn(1);
        when(context.requireAccess(12L, owner, ProjectAction.VIEW)).thenReturn(summaryRecord());

        service.restore(12L, new ProjectCommands.VersionCommand(4L), owner);

        verify(repository, never()).deleteMember(anyLong(), anyLong(), anyLong());
        verify(repository).audit(1L, 12L, 7L, "PROJECT_RESTORED", "status=ACTIVE");
    }

    private com.ccb.project.model.ProjectSummary summaryRecord() {
        return new com.ccb.project.model.ProjectSummary(12L, "PRJ-012", "Project", ProjectStatus.ACTIVE,
                7L, "Alice", ProjectRole.OWNER, Set.of(ProjectAction.values()), 3L);
    }
}
