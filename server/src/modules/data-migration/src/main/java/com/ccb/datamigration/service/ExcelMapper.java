package com.ccb.datamigration.service;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface ExcelMapper {
    List<Map<String, Object>> exportRows(@Param("table") String table, @Param("assetType") String assetType,
                                         @Param("tenantId") long tenantId, @Param("projectId") long projectId,
                                         @Param("systemCode") String systemCode, @Param("keyword") String keyword);

    Integer enabledComponentCount(@Param("systemCode") String systemCode, @Param("projectId") long projectId,
                                  @Param("tenantId") long tenantId);

    int insertAsset(@Param("table") String table, @Param("id") long id, @Param("tenantId") long tenantId,
                    @Param("projectId") long projectId, @Param("systemCode") String systemCode,
                    @Param("docCode") String docCode, @Param("docName") String docName,
                    @Param("structuredData") String structuredData, @Param("ownerId") long ownerId,
                    @Param("createdBy") long createdBy, @Param("updatedBy") long updatedBy);

    int insertAudit(@Param("tenantId") long tenantId, @Param("actorId") long actorId,
                    @Param("projectId") long projectId, @Param("detailJson") String detailJson);
}
