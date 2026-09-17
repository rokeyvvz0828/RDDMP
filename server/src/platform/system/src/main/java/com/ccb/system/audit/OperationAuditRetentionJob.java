package com.ccb.system.audit;

import org.springframework.beans.factory.annotation.Autowired;
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

    private final OperationAuditRetentionRepository repository;
    private final Clock clock;

    @Autowired
    public OperationAuditRetentionJob(OperationAuditRetentionRepository repository) {
        this(repository, Clock.systemDefaultZone());
    }

    OperationAuditRetentionJob(OperationAuditRetentionRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Scheduled(cron = "${ccb.audit.retention-cron:0 20 3 * * *}")
    @Transactional
    public void cleanup() {
        Timestamp cutoff = Timestamp.valueOf(LocalDateTime.now(clock).minusDays(RETENTION_DAYS));
        int operationRows = repository.deleteOperations(cutoff);
        int loginRows = repository.deleteLogins(cutoff);
        repository.insertSummary(nextId(), "operation=" + operationRows + ",login=" + loginRows);
    }

    private long nextId() {
        return System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000);
    }
}
