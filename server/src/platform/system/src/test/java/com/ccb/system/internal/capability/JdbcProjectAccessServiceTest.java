package com.ccb.system.internal.capability;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.ProjectAccess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JdbcProjectAccessServiceTest {
    private static final AuthUser USER = new AuthUser(7L, 9L, "tester", "", "测试用户", 1L, true);

    @Mock private SystemCapabilityRepository repository;
    private JdbcProjectAccessService service;

    @BeforeEach
    void setUp() {
        service = new JdbcProjectAccessService(repository);
    }

    @Test
    void allowsActiveProjectMemberAndNormalizesProjectReference() {
        stubProject();
        when(repository.superAdminCount(Map.of("userId", USER.id(), "tenantId", USER.tenantId()))).thenReturn(0);
        when(repository.activeMemberCount(Map.of("projectId", 21L, "tenantId", USER.tenantId(), "userId", USER.id()))).thenReturn(1);

        ProjectAccess result = service.requireAccessible(" P-001 ", USER);

        assertEquals(new ProjectAccess(21L, "P-001", "交付平台项目"), result);
        verify(repository).projectByRef(Map.of("tenantId", USER.tenantId(), "projectRef", "P-001"));
        verify(repository).activeMemberCount(Map.of("projectId", 21L, "tenantId", USER.tenantId(), "userId", USER.id()));
    }

    @Test
    void allowsSuperAdminWithoutCheckingMembership() {
        stubProject();
        when(repository.superAdminCount(Map.of("userId", USER.id(), "tenantId", USER.tenantId()))).thenReturn(1);

        assertEquals("P-001", service.requireAccessible("P-001", USER).projectRef());

        verify(repository, never()).activeMemberCount(any());
    }

    @Test
    void rejectsUserWithoutActiveMembership() {
        stubProject();
        when(repository.superAdminCount(Map.of("userId", USER.id(), "tenantId", USER.tenantId()))).thenReturn(0);
        when(repository.activeMemberCount(Map.of("projectId", 21L, "tenantId", USER.tenantId(), "userId", USER.id()))).thenReturn(0);

        BusinessException error = assertThrows(BusinessException.class, () -> service.requireAccessible("P-001", USER));

        assertEquals(ErrorCode.FORBIDDEN, error.code());
        assertEquals("无该项目数据访问权限", error.getMessage());
    }

    @Test
    void rejectsMissingProjectAndBlankReference() {
        when(repository.projectByRef(Map.of("tenantId", USER.tenantId(), "projectRef", "P-404"))).thenReturn(null);
        BusinessException missing = assertThrows(BusinessException.class, () -> service.requireAccessible("P-404", USER));
        BusinessException blank = assertThrows(BusinessException.class, () -> service.requireAccessible("  ", USER));

        assertEquals(ErrorCode.BAD_REQUEST, missing.code());
        assertEquals("项目不存在或已删除", missing.getMessage());
        assertEquals("请选择项目后重试", blank.getMessage());
        verify(repository, never()).superAdminCount(any());
    }

    private void stubProject() {
        when(repository.projectByRef(Map.of("tenantId", USER.tenantId(), "projectRef", "P-001")))
                .thenReturn(Map.of("id", 21L, "project_code", "P-001", "project_name", "交付平台项目"));
    }
}
