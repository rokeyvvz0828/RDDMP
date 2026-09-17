package com.ccb.requirement.service;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RequirementSecurityMapper {
    int permissionCount(@Param("tenantId") long tenantId, @Param("userId") long userId, @Param("permissionCode") String permissionCode);
    int legacySystemOwnerCount(@Param("tenantId") long tenantId, @Param("requirementId") long requirementId, @Param("userId") long userId);
    int legacySystemMemberCount(@Param("tenantId") long tenantId, @Param("requirementId") long requirementId, @Param("userId") long userId);
    int currentFlowAssigneeCount(@Param("tenantId") long tenantId, @Param("requirementId") long requirementId, @Param("userId") long userId);
    int legacyMemberCount(@Param("tenantId") long tenantId, @Param("requirementId") long requirementId, @Param("userId") long userId);
    int projectMemberCount(@Param("tenantId") long tenantId, @Param("projectId") long projectId, @Param("userId") long userId);
    int activeProjectCount(@Param("tenantId") long tenantId, @Param("projectId") long projectId);
    int businessGroupMemberCount(@Param("tenantId") long tenantId, @Param("businessGroup") String businessGroup, @Param("userId") long userId);
    List<String> listBusinessGroups(@Param("tenantId") long tenantId, @Param("userId") long userId);
}
