package com.ccb.requirement.service;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class RequirementAttachmentRepository {
    private final RequirementAttachmentMapper mapper;
    public RequirementAttachmentRepository(RequirementAttachmentMapper mapper) { this.mapper = mapper; }
    public List<Map<String, Object>> list(long tenantId, String bizType, long bizId) { return mapper.list(tenantId, bizType, bizId); }
    public void insert(Map<String, Object> values) { mapper.insert(values); }
    public Map<String, Object> findActive(long tenantId, long id) { return mapper.findActive(tenantId, id); }
    public void softDelete(long tenantId, long id) { mapper.softDelete(tenantId, id); }
    public long findDifferenceProjectId(long tenantId, long differenceId) { Long id = mapper.findDifferenceProjectId(tenantId, differenceId); return id == null ? 0L : id; }
    public String findLegacyBusinessGroup(long tenantId, long requirementId) { return mapper.findLegacyBusinessGroup(tenantId, requirementId); }
}
