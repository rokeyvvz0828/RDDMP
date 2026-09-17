package com.ccb.workflow.service;

import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Map;

@Repository
public class WorkflowAssigneeRepository {
    private final WorkflowAssigneeMapper mapper;
    public WorkflowAssigneeRepository(WorkflowAssigneeMapper mapper) { this.mapper = mapper; }
    public List<Long> usersForRoles(long tenantId, List<Long> roleIds) { return mapper.usersForRoles(tenantId, roleIds); }
    public List<Map<String,Object>> activeUsers(long tenantId, List<Long> ids) { return mapper.activeUsers(tenantId, ids); }
    public Map<String,Object> organizationOwner(long starterId, long tenantId) { return mapper.organizationOwner(starterId, tenantId); }
}
