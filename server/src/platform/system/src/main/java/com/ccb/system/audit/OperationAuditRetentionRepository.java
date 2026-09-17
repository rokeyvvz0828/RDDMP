package com.ccb.system.audit;

import org.springframework.stereotype.Repository;
import java.sql.Timestamp;

@Repository
public class OperationAuditRetentionRepository {
    private final OperationAuditRetentionMapper mapper;
    public OperationAuditRetentionRepository(OperationAuditRetentionMapper mapper) { this.mapper = mapper; }
    public int deleteOperations(Timestamp cutoff) { return mapper.deleteOperations(cutoff); }
    public int deleteLogins(Timestamp cutoff) { return mapper.deleteLogins(cutoff); }
    public void insertSummary(long id, String summary) { mapper.insertSummary(id, summary); }
}
