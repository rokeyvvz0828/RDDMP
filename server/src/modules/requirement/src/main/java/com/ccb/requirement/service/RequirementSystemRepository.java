package com.ccb.requirement.service;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class RequirementSystemRepository {
    private final RequirementSystemMapper mapper;
    public RequirementSystemRepository(RequirementSystemMapper mapper) { this.mapper = mapper; }
    public List<Map<String, Object>> list(long tenantId) { return mapper.list(tenantId); }
    public Map<String, Object> find(long tenantId, long id) { return mapper.find(tenantId, id); }
    public boolean existsByCode(long tenantId, String systemCode) { return mapper.countByCode(tenantId, systemCode) > 0; }
    public void insert(Map<String, Object> values) { mapper.insert(values); }
    public void update(Map<String, Object> values) { mapper.update(values); }
    public void softDelete(long tenantId, long id, long operatorId) { mapper.softDelete(tenantId, id, operatorId); }
    public long findIdByCode(long tenantId, String systemCode) { Long id = mapper.findIdByCode(tenantId, systemCode); return id == null ? 0L : id; }
}
