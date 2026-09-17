package com.ccb.datamigration.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class IssueRepository {
    private final IssueMapper mapper;

    public IssueRepository(IssueMapper mapper) { this.mapper = mapper; }
    public long count(long tenantId, long projectId, boolean deleted, String granularity, String systemCode, String issueSource, String defectType, String frequency, String keyword) { Long count = mapper.count(tenantId, projectId, deleted, granularity, systemCode, issueSource, defectType, frequency, keyword); return count == null ? 0L : count; }
    public List<Map<String, Object>> page(long tenantId, long projectId, boolean deleted, String granularity, String systemCode, String issueSource, String defectType, String frequency, String keyword, int limit, long offset) { return mapper.page(tenantId, projectId, deleted, granularity, systemCode, issueSource, defectType, frequency, keyword, limit, offset); }
    public List<Map<String, Object>> exportRows(long tenantId, long projectId, String granularity, String systemCode, String issueSource, String defectType, String frequency, String keyword) { return mapper.exportRows(tenantId, projectId, granularity, systemCode, issueSource, defectType, frequency, keyword); }
    public Map<String, Object> require(long tenantId, long id, boolean deleted) { List<Map<String, Object>> rows = mapper.find(tenantId, id, deleted); if (rows.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "问题不存在"); return rows.get(0); }
    public List<Long> relationIds(long tenantId, long issueId, String type) { return mapper.relationIds(tenantId, issueId, type); }
    public void insert(long id, long tenantId, long projectId, String issueCode, String issueName, String granularity, String systemCode, String issueSource, String defectType, String issueDescription, String solution, String meetingConclusion, String processingSteps, String businessScenario, String handler, String responsibleParty, String keywords, String frequency, long ownerId, long createdBy, long updatedBy) { mapper.insert(id, tenantId, projectId, issueCode, issueName, granularity, systemCode, issueSource, defectType, issueDescription, solution, meetingConclusion, processingSteps, businessScenario, handler, responsibleParty, keywords, frequency, ownerId, createdBy, updatedBy); }
    public int update(long id, long tenantId, String issueCode, String issueName, String granularity, String systemCode, String issueSource, String defectType, String issueDescription, String solution, String meetingConclusion, String processingSteps, String businessScenario, String handler, String responsibleParty, String keywords, String frequency, long updatedBy) { return mapper.update(id, tenantId, issueCode, issueName, granularity, systemCode, issueSource, defectType, issueDescription, solution, meetingConclusion, processingSteps, businessScenario, handler, responsibleParty, keywords, frequency, updatedBy); }
    public int softDelete(long tenantId, long id, long deletedBy) { return mapper.softDelete(tenantId, id, deletedBy); }
    public int restore(long tenantId, long id, long updatedBy) { return mapper.restore(tenantId, id, updatedBy); }
    public void replaceRelations(long tenantId, long issueId, String type, List<Long> relatedIds, long createdBy) { mapper.deleteRelations(tenantId, issueId, type); for (Long relatedId : relatedIds) mapper.insertRelation(tenantId, issueId, type, relatedId, createdBy); }
    public void deleteAllRelations(long tenantId, long issueId) { mapper.deleteAllRelations(tenantId, issueId); }
    public int purge(long tenantId, long id) { return mapper.purge(tenantId, id); }
    public void purgeAll(long tenantId, long projectId) { mapper.purgeAllRelations(tenantId, projectId); mapper.purgeAll(tenantId, projectId); }
    public String systemName(long tenantId, String systemCode) { List<Map<String, Object>> rows = mapper.systemName(tenantId, systemCode); return rows.isEmpty() ? null : String.valueOf(rows.get(0).get("system_name")); }
    public List<Map<String, Object>> meetingOptions(long tenantId, long projectId) { return mapper.meetingOptions(tenantId, projectId); }
    public List<Map<String, Object>> targetTableOptions(long tenantId, long projectId) { return mapper.targetTableOptions(tenantId, projectId); }
    public List<Long> targetTableProjects(long tenantId, long tableCode) { return mapper.targetTableProjects(tenantId, tableCode); }
    public List<Map<String, Object>> targetFieldOptions(long tenantId, long tableCode) { return mapper.targetFieldOptions(tenantId, tableCode); }
    public boolean relationTargetExists(long tenantId, long projectId, long id, String type) { Integer count = mapper.relationTargetCount(tenantId, projectId, id, type); return count != null && count > 0; }
    public boolean hasInvalidFieldRelations(long tenantId, long issueId) { Integer count = mapper.invalidFieldRelationCount(tenantId, issueId); return count != null && count > 0; }
    public boolean issueCodeExists(long tenantId, long projectId, String issueCode, Long currentId) { Integer count = mapper.issueCodeCount(tenantId, projectId, issueCode, currentId); return count != null && count > 0; }
    public boolean enabledComponentExists(long tenantId, long projectId, String systemCode) { Integer count = mapper.enabledComponentCount(tenantId, projectId, systemCode); return count != null && count > 0; }
    public void insertAudit(long tenantId, long actorId, long projectId, String operation, long entityId) { mapper.insertAudit(tenantId, actorId, projectId, operation, entityId); }
}
