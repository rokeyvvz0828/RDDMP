package com.ccb.system.internal.capability;

import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.SystemParameterReference;
import com.ccb.system.capability.SystemUserReference;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JdbcSystemReferenceQueryTest {
    @Mock
    private JdbcTemplate jdbc;

    private JdbcSystemReferenceQuery query;
    private final AuthUser actor = new AuthUser(7L, 9L, "tester", "", "测试用户", 1L, true);

    @BeforeEach
    void setUp() {
        query = new JdbcSystemReferenceQuery(jdbc);
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchesOnlyActiveTenantUsersAndEscapesLikeKeyword() throws Exception {
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(1L);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(invocation -> {
            RowMapper<SystemUserReference> mapper = invocation.getArgument(1);
            ResultSet row = mock(ResultSet.class);
            when(row.getLong("id")).thenReturn(12L);
            when(row.getString("display_name")).thenReturn("演示用户");
            when(row.getString("username")).thenReturn("demo");
            when(row.getString("mobile_phone")).thenReturn("13800000000");
            when(row.getInt("status")).thenReturn(1);
            return List.of(mapper.mapRow(row, 0));
        });

        PageResult<SystemUserReference> result = query.searchActiveUsers(actor, new PageQuery(2, 5), "a%_\\b");

        assertEquals(1L, result.total());
        assertEquals(2L, result.page());
        assertEquals(5L, result.size());
        assertEquals(new SystemUserReference(12L, "演示用户", "demo", "13800000000", true), result.records().get(0));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), args.capture());
        assertTrue(sql.getValue().contains("tenant_id = ? AND deleted = 0 AND status = 1"));
        assertTrue(sql.getValue().contains("ESCAPE '\\\\'"));
        assertEquals(9L, args.getValue()[0]);
        assertEquals("%a\\%\\_\\\\b%", args.getValue()[1]);
        assertEquals(5L, args.getValue()[4]);
        assertEquals(5L, args.getValue()[5]);
    }

    @Test
    @SuppressWarnings("unchecked")
    void canReadInactiveUserOnlyWhenCallerRequestsCurrentState() {
        SystemUserReference disabled = new SystemUserReference(18L, "停用用户", "disabled", null, false);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of(disabled));

        assertEquals(disabled, query.findUser(actor, 18L, false).orElseThrow());

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), args.capture());
        assertFalse(sql.getValue().contains("status = 1"));
        assertEquals(List.of(18L, 9L), List.of(args.getValue()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void findsActiveParametersByTenantAndEnabledCategory() throws Exception {
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(invocation -> {
            RowMapper<SystemParameterReference> mapper = invocation.getArgument(1);
            ResultSet row = mock(ResultSet.class);
            when(row.getString("config_key")).thenReturn("P2");
            when(row.getString("config_value")).thenReturn("员工渠道平台（P2）");
            return List.of(mapper.mapRow(row, 0));
        });

        List<SystemParameterReference> result = query.activeParameters(actor, " arch_deployment_platform ");

        assertEquals(List.of(new SystemParameterReference("P2", "员工渠道平台（P2）")), result);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), args.capture());
        assertTrue(sql.getValue().contains("JOIN sys_config"));
        assertTrue(sql.getValue().contains("t.tenant_id = ?"));
        assertTrue(sql.getValue().contains("t.status = 1 AND t.deleted = 0"));
        assertTrue(sql.getValue().contains("c.status = 1 AND c.deleted = 0"));
        assertEquals(List.of(9L, "ARCH_DEPLOYMENT_PLATFORM"), List.of(args.getValue()));
    }
}
