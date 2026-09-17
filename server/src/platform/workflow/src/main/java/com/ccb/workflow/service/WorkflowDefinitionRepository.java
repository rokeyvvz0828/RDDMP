package com.ccb.workflow.service;

import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Map;

@Repository
public class WorkflowDefinitionRepository {
    private final WorkflowDefinitionMapper mapper;
    public WorkflowDefinitionRepository(WorkflowDefinitionMapper mapper) { this.mapper = mapper; }
    public Map<String, Object> detail(long id, long tenantId) { return mapper.detail(id, tenantId); }
    public Map<String, Object> status(long id, long tenantId) { return mapper.status(id, tenantId); }
    public List<Map<String, Object>> versions(long id, long tenantId) { return mapper.versions(id, tenantId); }
    public Map<String, Object> version(long id, long tenantId, int versionNo) { return mapper.version(id, tenantId, versionNo); }
    public List<Map<String, Object>> events(long id, long tenantId) { return mapper.events(id, tenantId); }
    public Map<String, Object> published(long id, long tenantId) { return mapper.published(id, tenantId); }
    public Map<String, Object> enterprise(long id, long tenantId) { return mapper.enterprise(id, tenantId); }
    public int updateDraft(Map<String, Object> params) { return mapper.updateDraft(params); }
    public int updateDraftVersion(Map<String, Object> params) { return mapper.updateDraftVersion(params); }
    public int softDelete(Map<String, Object> params) { return mapper.softDelete(params); }
    public int archive(Map<String, Object> params) { return mapper.archive(params); }
    public int restore(Map<String, Object> params) { return mapper.restore(params); }
    public int insertDefinition(Map<String, Object> params) { return mapper.insertDefinition(params); }
    public int insertVersion(Map<String, Object> params) { return mapper.insertVersion(params); }
    public Map<String, Object> definitionSummary(long id, long tenantId) { return mapper.definitionSummary(id, tenantId); }
    public Map<String, Object> latestVersion(long id, long tenantId) { return mapper.latestVersion(id, tenantId); }
    public int publishVersion(Map<String, Object> params) { return mapper.publishVersion(params); }
    public int publishDefinition(Map<String, Object> params) { return mapper.publishDefinition(params); }
    public Map<String, Object> publishedVersion(long id, long tenantId) { return mapper.publishedVersion(id, tenantId); }
    public int countPublishedVersions(long id, long tenantId) { return mapper.countPublishedVersions(id, tenantId); }
    public int countInstances(long id, long tenantId) { return mapper.countInstances(id, tenantId); }
    public Long nextVersion(long id, long tenantId) { return mapper.nextVersion(id, tenantId); }
    public int insertDraftVersion(Map<String, Object> params) { return mapper.insertDraftVersion(params); }
    public int unpublish(Map<String, Object> params) { return mapper.unpublish(params); }
}
