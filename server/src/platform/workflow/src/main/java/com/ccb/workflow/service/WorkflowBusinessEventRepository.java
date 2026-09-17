package com.ccb.workflow.service;

import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Map;

@Repository
public class WorkflowBusinessEventRepository {
    private final WorkflowBusinessEventMapper mapper;
    public WorkflowBusinessEventRepository(WorkflowBusinessEventMapper mapper) { this.mapper = mapper; }
    public List<Map<String,Object>> selectDeliveries(long tenantId, String status, long offset, long size) { return mapper.selectDeliveries(tenantId, status, offset, size); }
    public long countDeliveries(long tenantId, String status) { return mapper.countDeliveries(tenantId, status); }
}
