package com.ccb.datamigration.service;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

@Mapper
public interface ReportMapper {
    Long count(@Param("tenantId") long tenantId, @Param("projectId") long projectId, @Param("deleted") boolean deleted, @Param("reportPeriod") String reportPeriod, @Param("keyword") String keyword);
    List<Map<String,Object>> page(@Param("tenantId") long tenantId, @Param("projectId") long projectId, @Param("deleted") boolean deleted, @Param("reportPeriod") String reportPeriod, @Param("keyword") String keyword, @Param("limit") int limit, @Param("offset") long offset);
    List<Map<String,Object>> find(@Param("tenantId") long tenantId, @Param("id") long id, @Param("deleted") boolean deleted);
    int insert(@Param("p") Map<String,Object> p);
    int update(@Param("p") Map<String,Object> p);
    int softDelete(@Param("tenantId") long tenantId, @Param("id") long id, @Param("deletedBy") long deletedBy);
    int restore(@Param("tenantId") long tenantId, @Param("id") long id);
    int purge(@Param("tenantId") long tenantId, @Param("id") long id);
    List<Map<String,Object>> projectOptions(@Param("tenantId") long tenantId);
    int insertAudit(@Param("tenantId") long tenantId, @Param("actorId") long actorId, @Param("projectId") long projectId, @Param("operation") String operation, @Param("entityId") long entityId);
}
