package com.ccb.infrastructure.mock;

import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class MockDataRepository {
    private final MockDataMapper mapper;

    public MockDataRepository(MockDataMapper mapper) { this.mapper = mapper; }
    public int upsert(String table, String columns, List<Object> values, String updates) { return mapper.upsert(table, columns, values, updates); }
    public void saveDatasetState(long id, String datasetKey, String datasetVersion, String checksum) { mapper.saveDatasetState(id, datasetKey, datasetVersion, checksum); }
    public long activeTenantRootCount(long tenantId) { Long value = mapper.activeTenantRootCount(tenantId); return value == null ? 0 : value; }
    public String activeOrganizationName(long tenantId, long organizationId) { return mapper.activeOrganizationName(tenantId, organizationId); }
    public long activeUserCount(long tenantId, long userId) { Long value = mapper.activeUserCount(tenantId, userId); return value == null ? 0 : value; }
    public long parameterCount(long tenantId, String categoryCode, String configKey) { Long value = mapper.parameterCount(tenantId, categoryCode, configKey); return value == null ? 0 : value; }
}
