package com.ccb.datamigration.service;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class DashboardRepository {
    private final DashboardMapper mapper;
    public DashboardRepository(DashboardMapper mapper) { this.mapper = mapper; }
    public long activeComponentCount(long tenantId, long projectId) { Long value = mapper.activeComponentCount(tenantId, projectId); return value == null ? 0 : value; }
    public List<Map<String, Object>> assetsByType(long tenantId, long projectId) { return mapper.assetsByType(tenantId, projectId); }
    public List<Map<String, Object>> components(long tenantId, long projectId) { return mapper.components(tenantId, projectId); }
}
