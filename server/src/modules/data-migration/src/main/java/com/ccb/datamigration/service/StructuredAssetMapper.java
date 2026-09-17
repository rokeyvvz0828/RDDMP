package com.ccb.datamigration.service;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface StructuredAssetMapper {
    List<Map<String, Object>> list(@Param("table") String table, @Param("assetType") String assetType,
                                   @Param("tenantId") long tenantId, @Param("projectId") long projectId,
                                   @Param("keyword") String keyword);
    int insert(@Param("table") String table, @Param("id") long id, @Param("tenantId") long tenantId,
               @Param("projectId") long projectId, @Param("systemCode") String systemCode,
               @Param("docCode") String docCode, @Param("docName") Object docName,
               @Param("structuredData") String structuredData, @Param("ownerId") long ownerId,
               @Param("createdBy") long createdBy, @Param("updatedBy") long updatedBy);
    List<Map<String, Object>> find(@Param("table") String table, @Param("assetType") String assetType,
                                   @Param("id") long id, @Param("tenantId") long tenantId,
                                   @Param("deleted") int deleted);
    int update(@Param("table") String table, @Param("systemCode") String systemCode,
               @Param("docName") String docName, @Param("structuredData") String structuredData,
               @Param("id") long id, @Param("tenantId") long tenantId);
    int softDelete(@Param("table") String table, @Param("deletedBy") long deletedBy,
                   @Param("id") long id, @Param("tenantId") long tenantId);
    Long countDeleted(@Param("table") String table, @Param("tenantId") long tenantId,
                      @Param("projectId") long projectId, @Param("keyword") String keyword);
    List<Map<String, Object>> listDeletedPage(@Param("table") String table, @Param("assetType") String assetType,
                                               @Param("tenantId") long tenantId, @Param("projectId") long projectId,
                                               @Param("keyword") String keyword, @Param("limit") int limit);
    List<Map<String, Object>> findDeletedDetail(@Param("table") String table, @Param("assetType") String assetType,
                                                 @Param("id") long id, @Param("tenantId") long tenantId);
    int restore(@Param("table") String table, @Param("id") long id, @Param("tenantId") long tenantId);
    int purge(@Param("table") String table, @Param("id") long id, @Param("tenantId") long tenantId);
    List<Long> relatedCounts(@Param("tenantId") long tenantId, @Param("id") long id, @Param("idText") String idText);
    Integer enabledComponentCount(@Param("systemCode") String systemCode, @Param("projectId") long projectId,
                                  @Param("tenantId") long tenantId);
    int insertAudit(@Param("tenantId") long tenantId, @Param("actorId") long actorId,
                    @Param("projectId") long projectId, @Param("operation") String operation,
                    @Param("entityId") long entityId);
}
