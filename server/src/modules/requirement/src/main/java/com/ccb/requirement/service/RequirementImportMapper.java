package com.ccb.requirement.service;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

@Mapper
public interface RequirementImportMapper {
    int insertBatch(@Param("p") Map<String,Object> values);
    List<Map<String,Object>> listBatches(@Param("tenantId") long tenantId);
    int projectCount(@Param("tenantId") long tenantId, @Param("projectId") long projectId);
    Long maxDifferenceSequence(@Param("tenantId") long tenantId, @Param("projectId") long projectId);
    int insertDifference(@Param("p") Map<String,Object> values);
    int insertLegacy(@Param("p") Map<String,Object> values);
    int legacyRequirementCount(@Param("tenantId") long tenantId, @Param("requirementNo") String requirementNo);
}
