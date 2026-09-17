package com.ccb.workflow.service;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

@Mapper
public interface WorkflowAssigneeMapper {
    List<Long> usersForRoles(@Param("tenantId") long tenantId, @Param("roleIds") List<Long> roleIds);
    List<Map<String,Object>> activeUsers(@Param("tenantId") long tenantId, @Param("ids") List<Long> ids);
    Map<String,Object> organizationOwner(@Param("starterId") long starterId, @Param("tenantId") long tenantId);
}

@org.springframework.context.annotation.Configuration
@org.mybatis.spring.annotation.MapperScan(basePackageClasses = WorkflowAssigneeMapper.class)
class WorkflowAssigneeMapperConfiguration {}
