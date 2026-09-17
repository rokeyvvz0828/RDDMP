package com.ccb.requirement.service;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface RequirementBaselineMapper {
    List<Map<String, Object>> list(@Param("tenantId") long tenantId, @Param("projectId") long projectId);
    Map<String, Object> findBaseline(@Param("tenantId") long tenantId, @Param("baselineId") long baselineId);
    List<Map<String, Object>> items(@Param("tenantId") long tenantId, @Param("baselineId") long baselineId);
    Map<String, Object> project(@Param("tenantId") long tenantId, @Param("projectId") long projectId);
    long pendingDifferenceCount(@Param("tenantId") long tenantId, @Param("projectId") long projectId);
    List<Map<String, Object>> reviewedDifferences(@Param("tenantId") long tenantId, @Param("projectId") long projectId);
    long baselineCount(@Param("tenantId") long tenantId, @Param("projectId") long projectId);
    int insertBaseline(@Param("p") Map<String, Object> values);
    int insertItem(@Param("p") Map<String, Object> values);
    int assignDifference(@Param("baselineId") long baselineId, @Param("operatorId") long operatorId, @Param("tenantId") long tenantId, @Param("differenceId") long differenceId);
    int markProjectBaselined(@Param("operatorId") long operatorId, @Param("tenantId") long tenantId, @Param("projectId") long projectId);
}
