package com.ccb.workflow.service;

import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Map;

@Repository
public class WorkflowMonitorRepository {
    private final WorkflowMonitorMapper mapper;
    public WorkflowMonitorRepository(WorkflowMonitorMapper mapper) { this.mapper = mapper; }
    public List<Map<String,Object>> instances(Map<String,Object> params) { return mapper.instances(params); }
    public long countInstances(Map<String,Object> params) { return mapper.countInstances(params); }
    public List<Map<String,Object>> instancesSeek(Map<String,Object> params) { return mapper.instancesSeek(params); }
    public Map<String,Object> detail(long id,long tenant) { return mapper.detail(id,tenant); }
    public Map<String,Object> definitionJson(Object id,Object version,long tenant) { return mapper.definitionJson(id,version,tenant); }
    public List<Map<String,Object>> instanceStatus(long id,long tenant) { return mapper.instanceStatus(id,tenant); }
    public int softDelete(long id,long tenant) { return mapper.softDelete(id,tenant); }
    public Map<String,Object> runningInstance(long id,long tenant) { return mapper.runningInstance(id,tenant); }
    public int cancelTasks(long id,long tenant) { return mapper.cancelTasks(id,tenant); }
    public int terminateInstance(long id,long tenant) { return mapper.terminateInstance(id,tenant); }
    public List<Map<String,Object>> projectScope(long id,long tenant) { return mapper.projectScope(id,tenant); }
    public List<Map<String,Object>> nodeStates(long id,long tenant) { return mapper.nodeStates(id,tenant); }
    public List<Map<String,Object>> timeline(long id,long tenant) { return mapper.timeline(id,tenant); }
}
