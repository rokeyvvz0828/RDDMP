package com.ccb.system.project;

import com.ccb.common.exception.BusinessException;
import com.ccb.infrastructure.storage.MinioStorageService;
import com.ccb.security.model.AuthUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectRolePermissionServiceTest {
    @Mock private ProjectRepository projectRepository;
    @Mock private MinioStorageService storage;

    private final AuthUser admin = new AuthUser(1L, 1L, "admin", "hash", "管理员", 1L, true);

    @Test
    void savesOnlyValidatedBusinessPermissionsWithinProjectRoleScope() {
        ProjectService service = service();
        when(projectRepository.assignablePermissionCount(eq(1L), any())).thenReturn(2L);

        service.saveRolePermissions(9001L, 8001L, List.of(5001L, 5002L), admin);

        verify(projectRepository).deleteProjectRolePermissions(1L, 9001L, 8001L);
        verify(projectRepository).addProjectRolePermission(1L, 9001L, 8001L, 5001L);
        verify(projectRepository).addProjectRolePermission(1L, 9001L, 8001L, 5002L);
    }

    @Test
    void rejectsSystemOrDisabledPermissionBeforeReplacingAssignments() {
        ProjectService service = service();
        when(projectRepository.assignablePermissionCount(eq(1L), any())).thenReturn(0L);

        assertThrows(BusinessException.class,
                () -> service.saveRolePermissions(9001L, 8001L, List.of(1001L), admin));

        verify(projectRepository, never()).deleteProjectRolePermissions(1L, 9001L, 8001L);
    }

    @Test
    void projectActionUsesOwnerPmAndProjectRolePermissionUnion() {
        ProjectService service = new ProjectService(storage, null, null, projectRepository);
        AuthUser member = new AuthUser(2L, 1L, "member", "hash", "成员", 1L, true);
        when(projectRepository.superAdminCount(2L, 1L)).thenReturn(0L);
        when(projectRepository.projectActionCount(9001L, 1L, 2L, "project:role:list", "read")).thenReturn(1L);
        when(projectRepository.projectCount(9001L, 1L)).thenReturn(1L);
        when(projectRepository.projectMemberAccessCount(9001L, 1L, 2L)).thenReturn(1L);
        when(projectRepository.roles(9001L, 1L)).thenReturn(List.of());

        service.roles(9001L, member);

        verify(projectRepository).projectActionCount(9001L, 1L, 2L, "project:role:list", "read");
    }

    private ProjectService service() {
        when(projectRepository.superAdminCount(admin.id(), admin.tenantId())).thenReturn(1L);
        when(projectRepository.projectCount(9001L, 1L)).thenReturn(1L);
        when(projectRepository.roleCount(8001L, 9001L, 1L)).thenReturn(1L);
        return new ProjectService(storage, null, null, projectRepository);
    }
}
