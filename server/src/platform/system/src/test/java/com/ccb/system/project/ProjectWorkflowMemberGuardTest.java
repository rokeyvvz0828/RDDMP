package com.ccb.system.project;

import com.ccb.common.exception.BusinessException;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.ProjectMemberRemovalGuard;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectWorkflowMemberGuardTest {
    @Test
    void blocksDeactivationBeforeMemberStatusIsChanged() {
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        when(projectRepository.superAdminCount(1L, 1L)).thenReturn(0L);
        when(projectRepository.projectActionCount(9001L, 1L, 1L, "project:member:list:update", "update")).thenReturn(1L);
        when(projectRepository.projectCount(9001L, 1L)).thenReturn(1L);
        when(projectRepository.projectOwnerAccessCount(9001L, 1L, 1L)).thenReturn(1L);
        when(projectRepository.memberCount(3001L, 9001L, 1L)).thenReturn(1L);
        when(projectRepository.memberUserId(3001L, 9001L, 1L)).thenReturn(7L);
        ProjectMemberRemovalGuard guard = (tenantId, projectId, userId) -> {
            throw new BusinessException(com.ccb.common.exception.ErrorCode.CONFLICT, "仍有待办");
        };
        ProjectService service = new ProjectService(null, null, null, projectRepository);
        service.setMemberRemovalGuard(guard);
        AuthUser admin = new AuthUser(1L, 1L, "admin", "", "Admin", 1L, true);

        assertThrows(BusinessException.class, () -> service.updateMember(9001L, 3001L, Map.of("status", 0), admin));
        verify(projectRepository, never()).updateMemberStatus(3001L, 9001L, 1L, 0L);
    }
}
