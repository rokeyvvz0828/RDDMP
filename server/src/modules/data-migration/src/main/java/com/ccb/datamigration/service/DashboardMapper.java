package com.ccb.datamigration.service;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface DashboardMapper {
    Long activeComponentCount(@Param("tenantId") long tenantId, @Param("projectId") long projectId);
    List<Map<String, Object>> assetsByType(@Param("tenantId") long tenantId, @Param("projectId") long projectId);
    List<Map<String, Object>> components(@Param("tenantId") long tenantId, @Param("projectId") long projectId);
}
