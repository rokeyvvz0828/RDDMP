package com.ccb.datamigration.service;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DataMigrationPermissionMapper {
    Integer adminPermissionCount(@Param("userId") long userId, @Param("tenantId") long tenantId);
    Integer actionPermissionCount(@Param("userId") long userId, @Param("tenantId") long tenantId, @Param("permissionCode") String permissionCode, @Param("action") String action);
}
