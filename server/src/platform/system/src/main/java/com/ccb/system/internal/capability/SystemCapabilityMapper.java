package com.ccb.system.internal.capability;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

@Mapper
public interface SystemCapabilityMapper {
    Map<String, Object> projectByRef(Map<String, Object> params); Integer superAdminCount(Map<String, Object> params); Integer activeMemberCount(Map<String, Object> params); Integer projectCount(Map<String, Object> params); Integer manageableProjectCount(Map<String, Object> params);
    List<Map<String, Object>> activeProjectMembers(Map<String, Object> params); Map<String, Object> activeProjectMember(Map<String, Object> params); List<Map<String, Object>> activeUsers(Map<String, Object> params); Long activeUserCount(Map<String, Object> params); Map<String, Object> user(Map<String, Object> params); List<Map<String, Object>> activeParameters(Map<String, Object> params);
    List<Long> allProjectIds(@Param("tenantId") long tenantId); List<Long> memberProjectIds(Map<String, Object> params); List<Map<String, Object>> workflowMembers(Map<String, Object> params); List<Map<String, Object>> workflowRoles(Map<String, Object> params); List<Long> activeProjectUserIds(Map<String, Object> params); Integer projectRoleCount(Map<String, Object> params); List<Map<String, Object>> workflowMembersForRoles(Map<String, Object> params);
}
