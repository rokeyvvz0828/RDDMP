package com.ccb.workflow.service;

import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Map;

@Repository
public class WorkflowInboxRepository {
    private final WorkflowInboxMapper mapper;
    public WorkflowInboxRepository(WorkflowInboxMapper mapper) { this.mapper = mapper; }
    public List<Map<String, Object>> page(Map<String, Object> params) { return mapper.page(params); }
    public long count(Map<String, Object> params) { return mapper.count(params); }
    public List<Map<String, Object>> seek(Map<String, Object> params) { return mapper.seek(params); }
}
