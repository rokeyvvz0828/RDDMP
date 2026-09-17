package com.ccb.system.audit;

import org.springframework.stereotype.Repository;
import java.util.Map;
import java.util.List;

@Repository
public class OperationAuditRepository {
    private final OperationAuditMapper mapper;
    public OperationAuditRepository(OperationAuditMapper mapper) { this.mapper = mapper; }
    public Map<String, Object> findProjectById(long tenantId, long projectId) { return mapper.findProjectById(tenantId, projectId); }
    public Map<String, Object> findProjectByCode(long tenantId, String projectCode) { return mapper.findProjectByCode(tenantId, projectCode); }
    public void insertLog(Map<String, Object> row) { mapper.insertLog(row); }
    public List<Map<String, Object>> operations(Map<String, Object> params) { return mapper.operations(params); }
    public long countOperations(Map<String, Object> params) { Long value = mapper.countOperations(params); return value == null ? 0 : value; }
    public List<Map<String, Object>> logins(Map<String, Object> params) { return mapper.logins(params); }
    public long countLogins(Map<String, Object> params) { Long value = mapper.countLogins(params); return value == null ? 0 : value; }
    public List<Map<String, Object>> auditProjects(Map<String, Object> params) { return mapper.auditProjects(params); }
    public int manageableProjectCount(Map<String, Object> params) { Integer value = mapper.manageableProjectCount(params); return value == null ? 0 : value; }
    public int superAdminCount(Map<String, Object> params) { Integer value = mapper.superAdminCount(params); return value == null ? 0 : value; }
}
