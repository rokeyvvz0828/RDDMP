package com.ccb.datamigration.service;

import org.springframework.stereotype.Repository;

@Repository
public class DataMigrationPermissionRepository {
    private final DataMigrationPermissionMapper mapper;
    public DataMigrationPermissionRepository(DataMigrationPermissionMapper mapper) { this.mapper = mapper; }
    public int adminPermissionCount(long userId, long tenantId) { Integer count = mapper.adminPermissionCount(userId, tenantId); return count == null ? 0 : count; }
    public int actionPermissionCount(long userId, long tenantId, String permissionCode, String action) { Integer count = mapper.actionPermissionCount(userId, tenantId, permissionCode, action); return count == null ? 0 : count; }
}
