package com.ccb.system.project;

import com.ccb.common.exception.BusinessException;
import com.ccb.infrastructure.storage.MinioStorageService;
import com.ccb.security.model.AuthUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectRolePermissionServiceTest {
    @Mock private JdbcTemplate jdbc;
    @Mock private MinioStorageService storage;

    private final AuthUser admin = new AuthUser(1L, 1L, "admin", "hash", "管理员", 1L, true);

    @Test
    void savesOnlyValidatedBusinessPermissionsWithinProjectRoleScope() {
        ProjectService service = new ProjectService(jdbc, storage);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1);
        when(jdbc.queryForObject(org.mockito.ArgumentMatchers.contains("permission_code NOT LIKE 'system:%'"),
                eq(Integer.class), any(Object[].class))).thenReturn(2);

        service.saveRolePermissions(9001L, 8001L, List.of(5001L, 5002L), admin);

        verify(jdbc).update("DELETE FROM pm_project_role_permission WHERE tenant_id = ? AND project_id = ? AND role_id = ?",
                1L, 9001L, 8001L);
        verify(jdbc).update("INSERT INTO pm_project_role_permission (tenant_id, project_id, role_id, permission_id) VALUES (?, ?, ?, ?)",
                1L, 9001L, 8001L, 5001L);
        verify(jdbc).update("INSERT INTO pm_project_role_permission (tenant_id, project_id, role_id, permission_id) VALUES (?, ?, ?, ?)",
                1L, 9001L, 8001L, 5002L);
    }

    @Test
    void rejectsSystemOrDisabledPermissionBeforeReplacingAssignments() {
        ProjectService service = new ProjectService(jdbc, storage);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1);
        when(jdbc.queryForObject(org.mockito.ArgumentMatchers.contains("permission_code NOT LIKE 'system:%'"),
                eq(Integer.class), any(Object[].class))).thenReturn(0);

        assertThrows(BusinessException.class,
                () -> service.saveRolePermissions(9001L, 8001L, List.of(1001L), admin));

        verify(jdbc, never()).update(org.mockito.ArgumentMatchers.startsWith("DELETE FROM pm_project_role_permission"),
                any(Object[].class));
    }

    @Test
    void projectActionUsesOwnerPmAndProjectRolePermissionUnion() {
        ProjectService service = new ProjectService(jdbc, storage);
        AuthUser member = new AuthUser(2L, 1L, "member", "hash", "成员", 1L, true);
        when(jdbc.queryForObject(org.mockito.ArgumentMatchers.contains("r.role_code = 'SUPER_ADMIN'"),
                eq(Integer.class), any(Object[].class))).thenReturn(0);
        when(jdbc.queryForObject(org.mockito.ArgumentMatchers.contains("permission.permission_code = ?"),
                eq(Integer.class), any(Object[].class))).thenReturn(1);
        when(jdbc.queryForObject(org.mockito.ArgumentMatchers.contains("FROM pm_project WHERE id = ?"),
                eq(Integer.class), any(Object[].class))).thenReturn(1);
        when(jdbc.queryForObject(org.mockito.ArgumentMatchers.startsWith("SELECT COUNT(*) FROM pm_project_member WHERE"),
                eq(Integer.class), any(Object[].class))).thenReturn(1);
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.contains("AS permission_count"), any(Object[].class)))
                .thenReturn(List.of());

        service.roles(9001L, member);

        verify(jdbc).queryForObject(org.mockito.ArgumentMatchers.contains("r.role_code = 'PM' OR (permission.permission_code = ?"),
                eq(Integer.class), any(Object[].class));
    }
}
