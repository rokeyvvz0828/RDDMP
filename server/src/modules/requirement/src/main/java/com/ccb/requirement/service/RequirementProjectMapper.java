package com.ccb.requirement.service;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

@Mapper
public interface RequirementProjectMapper {
    List<Map<String, Object>> list(@Param("tenantId") long tenantId, @Param("keyword") String keyword);
    Map<String, Object> findProject(@Param("tenantId") long tenantId, @Param("id") long id);
    int projectCodeCount(@Param("tenantId") long tenantId, @Param("code") String code);
    int differenceCount(@Param("tenantId") long tenantId, @Param("projectId") long projectId);
    int insertProject(@Param("p") Map<String, Object> values);
    int updateProject(@Param("p") Map<String, Object> values);
    int softDeleteProject(@Param("tenantId") long tenantId, @Param("id") long id, @Param("operatorId") long operatorId);
    List<Map<String, Object>> members(@Param("tenantId") long tenantId, @Param("projectId") long projectId);
    int userCount(@Param("tenantId") long tenantId, @Param("userId") long userId);
    int memberCount(@Param("tenantId") long tenantId, @Param("projectId") long projectId, @Param("userId") long userId);
    int insertMember(@Param("p") Map<String, Object> values);
    Map<String, Object> findMember(@Param("tenantId") long tenantId, @Param("id") long id);
    int softDeleteMember(@Param("tenantId") long tenantId, @Param("id") long id);
}
