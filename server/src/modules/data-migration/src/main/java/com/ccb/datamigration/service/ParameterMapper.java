package com.ccb.datamigration.service;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ParameterMapper {
    Long count(@Param("p") Map<String, Object> p);
    List<Map<String, Object>> list(@Param("p") Map<String, Object> p);
    List<Map<String, Object>> find(@Param("tenantId") long tenantId, @Param("id") long id);
    List<Map<String, Object>> findDeleted(@Param("tenantId") long tenantId, @Param("id") long id);
    List<Map<String, Object>> findRaw(@Param("tenantId") long tenantId, @Param("id") long id, @Param("deleted") int deleted);
    int insert(@Param("p") Map<String, Object> p);
    int update(@Param("p") Map<String, Object> p);
    int softDelete(@Param("tenantId") long tenantId, @Param("id") long id, @Param("actorId") long actorId);
    int restore(@Param("tenantId") long tenantId, @Param("id") long id);
    int purge(@Param("tenantId") long tenantId, @Param("id") long id);
    Integer nameCount(@Param("tenantId") long tenantId, @Param("projectId") long projectId, @Param("systemCode") String systemCode, @Param("parameterName") String parameterName, @Param("excludeId") Long excludeId, @Param("activeOnly") boolean activeOnly);
    Integer enabledComponentCount(@Param("tenantId") long tenantId, @Param("projectId") long projectId, @Param("systemCode") String systemCode);
    List<Long> deletedProjectIds(@Param("tenantId") long tenantId, @Param("id") long id);
    int insertAudit(@Param("tenantId") long tenantId, @Param("actorId") long actorId, @Param("projectId") long projectId, @Param("operation") String operation, @Param("entityId") long entityId);
}
