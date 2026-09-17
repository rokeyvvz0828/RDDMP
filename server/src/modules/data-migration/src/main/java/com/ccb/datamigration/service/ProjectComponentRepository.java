package com.ccb.datamigration.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class ProjectComponentRepository {
    private final ProjectComponentMapper mapper;

    public ProjectComponentRepository(ProjectComponentMapper mapper) { this.mapper = mapper; }
    public long count(long tenantId, long projectId, String businessGroupName, String systemCode, String responsibleTeam, String systemKeyword, Integer totalCheck, String keyword) { Long value = mapper.count(tenantId, projectId, businessGroupName, systemCode, responsibleTeam, systemKeyword, totalCheck, keyword); return value == null ? 0L : value; }
    public List<Map<String, Object>> page(long tenantId, long projectId, String businessGroupName, String systemCode, String responsibleTeam, String systemKeyword, Integer totalCheck, String keyword, long limit, long offset) { return mapper.page(tenantId, projectId, businessGroupName, systemCode, responsibleTeam, systemKeyword, totalCheck, keyword, limit, offset); }
    public List<Map<String, Object>> exportRows(long tenantId, long projectId, String businessGroupName, String systemCode, String responsibleTeam, String systemKeyword, Integer totalCheck, String keyword) { return mapper.exportRows(tenantId, projectId, businessGroupName, systemCode, responsibleTeam, systemKeyword, totalCheck, keyword); }
    public List<Map<String, Object>> systemOptions(long tenantId, long projectId) { return mapper.systemOptions(tenantId, projectId); }
    public boolean exists(long tenantId, long projectId, String systemCode) { Integer count = mapper.componentCount(tenantId, projectId, systemCode); return count != null && count > 0; }
    public void insert(long tenantId, long projectId, String systemCode, int totalCheck, long ownerId, long createdBy) { mapper.insert(tenantId, projectId, systemCode, totalCheck, ownerId, createdBy); }
    public void updateTotalCheck(int totalCheck, long updatedBy, long tenantId, long projectId, String systemCode) { mapper.updateTotalCheck(totalCheck, updatedBy, tenantId, projectId, systemCode); }
    public void delete(long tenantId, long projectId, String systemCode) { mapper.delete(tenantId, projectId, systemCode); }
    public void updateEnabled(boolean enabled, long updatedBy, long tenantId, long projectId, String systemCode) { mapper.updateEnabled(enabled ? 1 : 0, updatedBy, tenantId, projectId, systemCode); }
    public Map<String, Object> view(long tenantId, long projectId, String systemCode) { return mapper.view(tenantId, projectId, systemCode); }
    public Map<String, Object> require(long tenantId, long projectId, String systemCode) { List<Map<String, Object>> rows = mapper.find(tenantId, projectId, systemCode); if (rows.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "Component not found"); return rows.get(0); }
    public boolean hasActiveContent(long tenantId, long projectId, String systemCode) { Integer total = mapper.activeContentCount(tenantId, projectId, systemCode); return total != null && total > 0; }
    public void insertAudit(long tenantId, long actorId, long projectId, String operation, String detailJson) { mapper.insertAudit(tenantId, actorId, projectId, operation, detailJson); }
}
