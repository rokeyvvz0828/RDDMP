package com.ccb.datamigration.service;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface ProjectComponentMapper {
    Long count(@Param("tenantId") long tenantId, @Param("projectId") long projectId, @Param("businessGroupName") String businessGroupName, @Param("systemCode") String systemCode, @Param("responsibleTeam") String responsibleTeam, @Param("systemKeyword") String systemKeyword, @Param("totalCheck") Integer totalCheck, @Param("keyword") String keyword);
    List<Map<String, Object>> page(@Param("tenantId") long tenantId, @Param("projectId") long projectId, @Param("businessGroupName") String businessGroupName, @Param("systemCode") String systemCode, @Param("responsibleTeam") String responsibleTeam, @Param("systemKeyword") String systemKeyword, @Param("totalCheck") Integer totalCheck, @Param("keyword") String keyword, @Param("limit") long limit, @Param("offset") long offset);
    List<Map<String, Object>> exportRows(@Param("tenantId") long tenantId, @Param("projectId") long projectId, @Param("businessGroupName") String businessGroupName, @Param("systemCode") String systemCode, @Param("responsibleTeam") String responsibleTeam, @Param("systemKeyword") String systemKeyword, @Param("totalCheck") Integer totalCheck, @Param("keyword") String keyword);
    List<Map<String, Object>> systemOptions(@Param("tenantId") long tenantId, @Param("projectId") long projectId);
    Integer componentCount(@Param("tenantId") long tenantId, @Param("projectId") long projectId, @Param("systemCode") String systemCode);
    int insert(@Param("tenantId") long tenantId, @Param("projectId") long projectId, @Param("systemCode") String systemCode, @Param("totalCheck") int totalCheck, @Param("ownerId") long ownerId, @Param("createdBy") long createdBy);
    int updateTotalCheck(@Param("totalCheck") int totalCheck, @Param("updatedBy") long updatedBy, @Param("tenantId") long tenantId, @Param("projectId") long projectId, @Param("systemCode") String systemCode);
    int delete(@Param("tenantId") long tenantId, @Param("projectId") long projectId, @Param("systemCode") String systemCode);
    int updateEnabled(@Param("enabled") int enabled, @Param("updatedBy") long updatedBy, @Param("tenantId") long tenantId, @Param("projectId") long projectId, @Param("systemCode") String systemCode);
    Map<String, Object> view(@Param("tenantId") long tenantId, @Param("projectId") long projectId, @Param("systemCode") String systemCode);
    List<Map<String, Object>> find(@Param("tenantId") long tenantId, @Param("projectId") long projectId, @Param("systemCode") String systemCode);
    Integer activeContentCount(@Param("tenantId") long tenantId, @Param("projectId") long projectId, @Param("systemCode") String systemCode);
    int insertAudit(@Param("tenantId") long tenantId, @Param("actorId") long actorId, @Param("projectId") long projectId, @Param("operation") String operation, @Param("detailJson") String detailJson);
}
