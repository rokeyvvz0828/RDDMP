package com.ccb.requirement.service;

import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Map;

@Repository
public class RequirementChangeLogRepository {
    private final RequirementChangeLogMapper mapper;
    public RequirementChangeLogRepository(RequirementChangeLogMapper mapper) { this.mapper = mapper; }
    public void insert(long id, long tenantId, String bizType, long bizId, String field, String oldValue, String newValue, String changeType, long operatorId, String operatorName, String source, String traceId) { mapper.insert(id, tenantId, bizType, bizId, field, oldValue, newValue, changeType, operatorId, operatorName, source, traceId); }
    public List<Map<String, Object>> list(long tenantId, String bizType, long bizId) { return mapper.list(tenantId, bizType, bizId); }
}
