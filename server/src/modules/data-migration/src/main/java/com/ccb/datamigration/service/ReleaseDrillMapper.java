package com.ccb.datamigration.service;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ReleaseDrillMapper {
    Long count(@Param("tenantId") long tenantId, @Param("projectId") long projectId,
               @Param("granularity") String granularity, @Param("materialTypeCode") String materialTypeCode,
               @Param("systemCode") String systemCode, @Param("keyword") String keyword);

    List<Map<String, Object>> page(@Param("tenantId") long tenantId, @Param("projectId") long projectId,
                                   @Param("granularity") String granularity, @Param("materialTypeCode") String materialTypeCode,
                                   @Param("systemCode") String systemCode, @Param("keyword") String keyword,
                                   @Param("limit") int limit, @Param("offset") long offset);

    List<Map<String, Object>> find(@Param("tenantId") long tenantId, @Param("id") long id);
    int insert(@Param("p") Map<String, Object> p);
    int update(@Param("p") Map<String, Object> p);
    int softDelete(@Param("tenantId") long tenantId, @Param("id") long id, @Param("deletedBy") long deletedBy);
    List<Long> mainAttachmentIds(@Param("tenantId") long tenantId, @Param("id") long id);
    Long recycleCount(@Param("tenantId") long tenantId, @Param("projectId") long projectId, @Param("keyword") String keyword);
    List<Map<String, Object>> recyclePage(@Param("tenantId") long tenantId, @Param("projectId") long projectId,
                                          @Param("keyword") String keyword, @Param("limit") int limit);
    List<Map<String, Object>> findDeleted(@Param("tenantId") long tenantId, @Param("id") long id);
    int restore(@Param("tenantId") long tenantId, @Param("id") long id);
    int purge(@Param("tenantId") long tenantId, @Param("id") long id);
    Integer enabledComponentCount(@Param("tenantId") long tenantId, @Param("projectId") long projectId,
                                  @Param("systemCode") String systemCode);
    List<Map<String, Object>> findRaw(@Param("tenantId") long tenantId, @Param("id") long id);
    List<Long> deletedProjectIds(@Param("tenantId") long tenantId, @Param("id") long id);
    int insertAudit(@Param("tenantId") long tenantId, @Param("actorId") long actorId, @Param("projectId") long projectId,
                    @Param("operation") String operation, @Param("entityId") long entityId);
}
