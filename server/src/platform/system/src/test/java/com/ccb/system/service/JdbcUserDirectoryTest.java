package com.ccb.system.service;

import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.system.model.UserDirectoryUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JdbcUserDirectoryTest {
    @Mock
    private JdbcTemplate jdbc;

    private JdbcUserDirectory directory;

    @BeforeEach
    void setUp() {
        directory = new JdbcUserDirectory(jdbc);
    }

    @Test
    void searchesOnlyActiveUsersInsideTenant() {
        UserDirectoryUser expected = new UserDirectoryUser(7L, "alice", "Alice", 3L, "研发部");
        when(jdbc.query(anyString(), ArgumentMatchers.<RowMapper<UserDirectoryUser>>any(), any(Object[].class)))
                .thenReturn(List.of(expected));
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(1L);

        PageResult<UserDirectoryUser> result = directory.searchActive(9L, " alice ", new PageQuery(3, 10));

        assertEquals(List.of(expected), result.records());
        assertEquals(1L, result.total());
        assertEquals(3L, result.page());
        assertEquals(10L, result.size());
        verify(jdbc).query(ArgumentMatchers.<String>argThat(sql -> sql.contains("u.tenant_id = ?")
                        && sql.contains("u.deleted = 0") && sql.contains("u.status = 1")
                        && sql.contains("ORDER BY u.display_name, u.id") && sql.contains("LIMIT ?, ?")),
                ArgumentMatchers.<RowMapper<UserDirectoryUser>>any(), eq(9L), eq("%alice%"), eq("%alice%"), eq(20L), eq(10L));
        verify(jdbc).queryForObject(ArgumentMatchers.<String>argThat(sql -> sql.contains("u.tenant_id = ?")
                        && sql.contains("u.deleted = 0") && sql.contains("u.status = 1")),
                eq(Long.class), eq(9L), eq("%alice%"), eq("%alice%"));
    }

    @Test
    void rejectsMissingOrInactiveUsersAsOneBatch() {
        when(jdbc.query(anyString(), ArgumentMatchers.<RowMapper<UserDirectoryUser>>any(), any(Object[].class)))
                .thenReturn(List.of(new UserDirectoryUser(7L, "alice", "Alice", 3L, "研发部")));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> directory.requireActive(9L, Set.of(7L, 8L)));

        assertEquals(ErrorCode.BAD_REQUEST, exception.code());
        assertTrue(exception.getMessage().contains("不存在、已停用或不属于当前租户"));
        verify(jdbc).query(ArgumentMatchers.<String>argThat(sql -> sql.contains("u.id IN (?, ?)")
                        && sql.contains("u.tenant_id = ?") && sql.contains("u.deleted = 0")
                        && sql.contains("u.status = 1")),
                ArgumentMatchers.<RowMapper<UserDirectoryUser>>any(), eq(9L), any(Long.class), any(Long.class));
    }
}
