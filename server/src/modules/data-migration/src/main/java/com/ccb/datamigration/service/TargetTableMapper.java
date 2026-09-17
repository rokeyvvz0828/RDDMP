package com.ccb.datamigration.service;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

@Mapper
public interface TargetTableMapper {
    Integer countFields(@Param("tenantId") long tenantId, @Param("category") String category, @Param("projectId") long projectId, @Param("p") Map<String, Object> p);
    List<Map<String, Object>> pageFields(@Param("tenantId") long tenantId, @Param("category") String category, @Param("projectId") long projectId, @Param("p") Map<String, Object> p, @Param("limit") long limit, @Param("offset") long offset);
    List<Map<String, Object>> detail(@Param("tenantId") long tenantId, @Param("tableCode") long tableCode, @Param("category") String category);
    List<Map<String, Object>> fields(@Param("tenantId") long tenantId, @Param("tableCode") long tableCode);
    int insertTable(@Param("p") Map<String, Object> p);
    int updateTable(@Param("p") Map<String, Object> p);
    int deleteTableFields(@Param("tenantId") long tenantId, @Param("tableCode") long tableCode);
    int deleteTable(@Param("tenantId") long tenantId, @Param("tableCode") long tableCode, @Param("updatedBy") long updatedBy);
    List<Map<String, Object>> fieldContext(@Param("tenantId") long tenantId, @Param("fieldCode") long fieldCode, @Param("category") String category);
    int insertField(@Param("p") Map<String, Object> p);
    int updateField(@Param("p") Map<String, Object> p);
    Map<String, Object> field(@Param("tenantId") long tenantId, @Param("fieldCode") long fieldCode);
    int deleteField(@Param("tenantId") long tenantId, @Param("fieldCode") long fieldCode, @Param("updatedBy") long updatedBy);
    Integer activeFieldCount(@Param("tenantId") long tenantId, @Param("tableCode") long tableCode);
    int cascadeDeleteTable(@Param("tenantId") long tenantId, @Param("tableCode") long tableCode, @Param("category") String category, @Param("updatedBy") long updatedBy);
    List<Map<String, Object>> projectByCode(@Param("tenantId") long tenantId, @Param("projectCode") String projectCode);
    List<Map<String, Object>> projectById(@Param("tenantId") long tenantId, @Param("projectId") long projectId);
    Integer enabledComponentCount(@Param("tenantId") long tenantId, @Param("projectId") long projectId, @Param("systemCode") String systemCode);
    Integer tableNameEnCount(@Param("tenantId") long tenantId, @Param("projectId") long projectId, @Param("systemCode") String systemCode, @Param("name") String name, @Param("excludeCode") long excludeCode);
    Integer tableNameCnCount(@Param("tenantId") long tenantId, @Param("projectId") long projectId, @Param("systemCode") String systemCode, @Param("name") String name, @Param("excludeCode") long excludeCode);
    Integer fieldNameEnCount(@Param("tenantId") long tenantId, @Param("tableCode") long tableCode, @Param("name") String name, @Param("excludeCode") long excludeCode);
    Integer fieldNameCnCount(@Param("tenantId") long tenantId, @Param("tableCode") long tableCode, @Param("name") String name, @Param("excludeCode") long excludeCode);
    List<Map<String, Object>> exportAll(@Param("tenantId") long tenantId, @Param("category") String category, @Param("projectId") long projectId, @Param("p") Map<String, Object> p);
    List<Map<String, Object>> exportSelected(@Param("tenantId") long tenantId, @Param("category") String category, @Param("projectId") long projectId, @Param("fieldCodes") List<Long> fieldCodes);
    int insertAudit(@Param("tenantId") long tenantId, @Param("actorId") long actorId, @Param("projectId") long projectId, @Param("operation") String operation, @Param("entityId") long entityId);
}
