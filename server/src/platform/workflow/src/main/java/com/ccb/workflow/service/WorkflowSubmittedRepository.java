package com.ccb.workflow.service;

import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Map;

@Repository
public class WorkflowSubmittedRepository {
    private final WorkflowSubmittedMapper mapper;
    public WorkflowSubmittedRepository(WorkflowSubmittedMapper mapper) { this.mapper = mapper; }
    public List<Map<String, Object>> page(Map<String, Object> params) { return mapper.page(params); }
    public long count(Map<String, Object> params) { return mapper.count(params); }
    public List<Map<String, Object>> activeTasks(Map<String, Object> params) { return mapper.activeTasks(params); }
}
