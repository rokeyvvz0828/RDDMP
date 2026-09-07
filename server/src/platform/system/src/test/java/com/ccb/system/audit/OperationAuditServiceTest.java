package com.ccb.system.audit;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperationAuditServiceTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-04T08:00:00Z"), ZONE);

    @Mock
    private JdbcTemplate jdbc;

    private OperationAuditService service;
    private AuthUser actor;

    @BeforeEach
    void setUp() {
        service = new OperationAuditService(jdbc, new ObjectMapper(), CLOCK);
        actor = new AuthUser(7L, 1L, "pm", "", "项目经理", 1L, true);
    }

    @Test
    void clampsRequestedStartToMostRecentThirtyDays() {
        OperationAuditService.TimeRange range = service.timeRange(LocalDate.of(2025, 1, 1), null);

        assertEquals(Timestamp.valueOf("2026-08-05 16:00:00"), range.start());
        assertEquals(Timestamp.valueOf("2026-09-04 16:00:00"), range.endExclusive());
    }

    @Test
    void rejectsInvalidTimeRange() {
        BusinessException failure = assertThrows(BusinessException.class,
                () -> service.timeRange(LocalDate.of(2026, 9, 5), LocalDate.of(2026, 9, 5)));

        assertEquals(ErrorCode.BAD_REQUEST, failure.code());
    }

    @Test
    @SuppressWarnings("unchecked")
    void projectManagerQueryIsRestrictedInSql() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(0, 1);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        service.operations(new OperationAuditQuery(null, null, null, null, null,
                88L, null, 1, 20), actor);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), any(Object[].class));
        assertTrue(sql.getValue().contains("p.id = l.project_id"));
        assertTrue(sql.getValue().contains("p.owner_id = ?"));
        assertTrue(sql.getValue().contains("r.role_code = 'PM'"));
        assertTrue(sql.getValue().contains("l.project_id = ?"));
    }

    @Test
    void ordinaryMemberCannotReadOperationLogs() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(0, 0);

        BusinessException failure = assertThrows(BusinessException.class,
                () -> service.operations(new OperationAuditQuery(null, null, null, null,
                        null, null, null, 1, 20), actor));

        assertEquals(ErrorCode.FORBIDDEN, failure.code());
    }

    @Test
    void projectManagerCannotReadLoginLogs() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(0);

        BusinessException failure = assertThrows(BusinessException.class,
                () -> service.logins(new LoginAuditQuery(null, null, null, null, 1, 20), actor));

        assertEquals(ErrorCode.FORBIDDEN, failure.code());
    }

    @Test
    @SuppressWarnings("unchecked")
    void superAdminCanReadTenantLoginLogs() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        service.logins(new LoginAuditQuery(null, null, true, "admin", 1, 20), actor);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), any(Object[].class));
        assertTrue(sql.getValue().contains("l.tenant_id = ?"));
        assertTrue(sql.getValue().contains("l.created_at >= ?"));
        assertTrue(sql.getValue().contains("l.success = ?"));
    }
}
