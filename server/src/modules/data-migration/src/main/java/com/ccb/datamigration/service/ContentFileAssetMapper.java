package com.ccb.datamigration.service;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ContentFileAssetMapper {
    Long count(@Param("table") String table, @Param("type") String type, @Param("tenantId") long tenantId, @Param("projectId") long projectId, @Param("systemCode") String systemCode, @Param("keyword") String keyword);
    List<Map<String, Object>> page(@Param("table") String table, @Param("type") String type, @Param("tenantId") long tenantId, @Param("projectId") long projectId, @Param("systemCode") String systemCode, @Param("keyword") String keyword, @Param("limit") int limit, @Param("offset") long offset);
    int insert(@Param("table") String table, @Param("p") Map<String, Object> p);
    List<Map<String, Object>> findActive(@Param("table") String table, @Param("tenantId") long tenantId, @Param("id") long id);
    int update(@Param("table") String table, @Param("p") Map<String, Object> p);
    int softDelete(@Param("table") String table, @Param("tenantId") long tenantId, @Param("id") long id, @Param("actorId") long actorId);
    int restore(@Param("table") String table, @Param("tenantId") long tenantId, @Param("id") long id, @Param("actorId") long actorId);
    int purge(@Param("table") String table, @Param("tenantId") long tenantId, @Param("id") long id);
    List<Long> mainAttachmentIds(@Param("table") String table, @Param("type") String type, @Param("tenantId") long tenantId, @Param("id") long id);
    Long deletedCount(@Param("table") String table, @Param("tenantId") long tenantId, @Param("projectId") long projectId, @Param("keyword") String keyword);
    List<Map<String, Object>> deletedPage(@Param("table") String table, @Param("type") String type, @Param("tenantId") long tenantId, @Param("projectId") long projectId, @Param("keyword") String keyword, @Param("limit") int limit);
    List<Map<String, Object>> findDeleted(@Param("table") String table, @Param("type") String type, @Param("tenantId") long tenantId, @Param("id") long id);
    List<Map<String, Object>> findAny(@Param("table") String table, @Param("tenantId") long tenantId, @Param("id") long id);
    List<Long> deletedProjectIds(@Param("table") String table, @Param("tenantId") long tenantId, @Param("id") long id);
    Integer enabledComponentCount(@Param("tenantId") long tenantId, @Param("projectId") long projectId, @Param("systemCode") String systemCode);
    List<Long> currentMainAttachmentIds(@Param("tenantId") long tenantId, @Param("businessType") String businessType, @Param("businessId") long businessId);
    int insertAudit(@Param("tenantId") long tenantId, @Param("actorId") long actorId, @Param("projectId") long projectId, @Param("operation") String operation, @Param("entityId") long entityId);
}
