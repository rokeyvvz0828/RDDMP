package com.ccb.requirement.service;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface RequirementAttachmentMapper {
    List<Map<String, Object>> list(@Param("tenantId") long tenantId, @Param("bizType") String bizType, @Param("bizId") long bizId);
    int insert(@Param("p") Map<String, Object> values);
    Map<String, Object> findActive(@Param("tenantId") long tenantId, @Param("id") long id);
    int softDelete(@Param("tenantId") long tenantId, @Param("id") long id);
    Long findDifferenceProjectId(@Param("tenantId") long tenantId, @Param("differenceId") long differenceId);
    String findLegacyBusinessGroup(@Param("tenantId") long tenantId, @Param("requirementId") long requirementId);
}
