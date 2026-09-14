package com.ccb.system.audit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperationAuditRetentionJobTest {
    @Mock
    private JdbcTemplate jdbc;

    @Test
    void deletesOlderThanOneHundredEightyDaysAndWritesOneSummary() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-04T08:00:00Z"), ZoneId.of("Asia/Shanghai"));
        OperationAuditRetentionJob job = new OperationAuditRetentionJob(jdbc, clock);
        when(jdbc.update(contains("DELETE FROM sys_operation_log"), any(Timestamp.class))).thenReturn(12);
        when(jdbc.update(contains("DELETE FROM sys_login_log"), any(Timestamp.class))).thenReturn(5);

        job.cleanup();

        ArgumentCaptor<Timestamp> cutoff = ArgumentCaptor.forClass(Timestamp.class);
        verify(jdbc).update(contains("DELETE FROM sys_operation_log"), cutoff.capture());
        verify(jdbc).update(contains("DELETE FROM sys_login_log"), eq(cutoff.getValue()));
        verify(jdbc).update(contains("audit:retention:cleanup"), any(Long.class),
                eq("operation=12,login=5"));
        assertEquals(Timestamp.valueOf("2026-03-08 16:00:00"), cutoff.getValue());
    }
}
