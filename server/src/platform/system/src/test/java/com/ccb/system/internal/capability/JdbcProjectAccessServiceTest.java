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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JdbcProjectAccessServiceTest {
    private static final AuthUser USER = new AuthUser(7L, 9L, "tester", "", "测试用户", 1L, true);

    @Mock
    private JdbcTemplate jdbc;

    private JdbcProjectAccessService service;

    @BeforeEach
    void setUp() {
        service = new JdbcProjectAccessService(jdbc);
    }

    @Test
    @SuppressWarnings("unchecked")
    void allowsActiveProjectMemberAndNormalizesProjectReference() throws Exception {
        stubProject();
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(0, 1);

        ProjectAccess result = service.requireAccessible(" P-001 ", USER);

        assertEquals(new ProjectAccess(21L, "P-001", "交付平台项目"), result);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), args.capture());
        assertTrue(sql.getValue().contains("tenant_id = ?"));
        assertTrue(sql.getValue().contains("project_code = ?"));
        assertTrue(sql.getValue().contains("deleted = 0"));
        assertFalse(sql.getValue().contains("status ="));
        assertEquals(List.of(9L, "P-001"), List.of(args.getValue()));

        ArgumentCaptor<String> countSql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, times(2)).queryForObject(countSql.capture(), eq(Integer.class), any(Object[].class));
        assertTrue(countSql.getAllValues().get(1).contains("pm_project_member"));
        assertTrue(countSql.getAllValues().get(1).contains("status = 1"));
    }

    @Test
    void allowsSuperAdminWithoutCheckingMembership() throws Exception {
        stubProject();
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1);

        assertEquals("P-001", service.requireAccessible("P-001", USER).projectRef());

        verify(jdbc, times(1)).queryForObject(anyString(), eq(Integer.class), any(Object[].class));
    }

    @Test
    void rejectsUserWithoutActiveMembership() throws Exception {
        stubProject();
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(0, 0);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.requireAccessible("P-001", USER));

        assertEquals(ErrorCode.FORBIDDEN, error.code());
        assertEquals("无该项目数据访问权限", error.getMessage());
    }

    @Test
    void rejectsMissingOrDeletedProject() {
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.requireAccessible("P-404", USER));

        assertEquals(ErrorCode.BAD_REQUEST, error.code());
        assertEquals("项目不存在或已删除", error.getMessage());
        verify(jdbc, never()).queryForObject(anyString(), eq(Integer.class), any(Object[].class));
    }

    @Test
    void rejectsBlankProjectReferenceBeforeQuerying() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.requireAccessible("  ", USER));

        assertEquals(ErrorCode.BAD_REQUEST, error.code());
        assertEquals("请选择项目后重试", error.getMessage());
        verify(jdbc, never()).query(anyString(), any(RowMapper.class), any(Object[].class));
    }

    @SuppressWarnings("unchecked")
    private void stubProject() throws Exception {
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(invocation -> {
            RowMapper<ProjectAccess> mapper = invocation.getArgument(1);
            ResultSet row = org.mockito.Mockito.mock(ResultSet.class);
            when(row.getLong("id")).thenReturn(21L);
            when(row.getString("project_code")).thenReturn("P-001");
            when(row.getString("project_name")).thenReturn("交付平台项目");
            return List.of(mapper.mapRow(row, 0));
        });
    }
}
