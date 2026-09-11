package com.ccb.system.audit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class OperationAuditRetentionJob {
    static final int RETENTION_DAYS = 180;

    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Autowired
    public OperationAuditRetentionJob(JdbcTemplate jdbc) {
        this(jdbc, Clock.systemDefaultZone());
    }

    OperationAuditRetentionJob(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Scheduled(cron = "${ccb.audit.retention-cron:0 20 3 * * *}")
    @Transactional
    public void cleanup() {
        Timestamp cutoff = Timestamp.valueOf(LocalDateTime.now(clock).minusDays(RETENTION_DAYS));
        int operationRows = jdbc.update("DELETE FROM sys_operation_log WHERE created_at < ?", cutoff);
        int loginRows = jdbc.update("DELETE FROM sys_login_log WHERE created_at < ?", cutoff);
        jdbc.update("""
                        INSERT INTO sys_operation_log
                            (id, tenant_id, operator_id, operator_name, operation_code, module_code, module_name,
                             operation_type, target_type, target_id, request_method, success, changed_fields)
                        VALUES (?, 1, 0, '系统任务', 'audit:retention:cleanup', 'system', '系统管理',
                                'DELETE', 'audit-retention', ?, 'SYSTEM', 1, '["operationRows","loginRows"]')
                        """, nextId(), "operation=" + operationRows + ",login=" + loginRows);
    }

    private long nextId() {
        return System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000);
    }
}
