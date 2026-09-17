package com.ccb.system.audit;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;
import java.util.Map;
import java.util.List;

@Mapper
public interface OperationAuditMapper {
    Map<String, Object> findProjectById(@Param("tenantId") long tenantId, @Param("projectId") long projectId);

    Map<String, Object> findProjectByCode(@Param("tenantId") long tenantId, @Param("projectCode") String projectCode);

    int insertLog(Map<String, Object> row);
    List<Map<String, Object>> operations(Map<String, Object> params);
    Long countOperations(Map<String, Object> params);
    List<Map<String, Object>> logins(Map<String, Object> params);
    Long countLogins(Map<String, Object> params);
    List<Map<String, Object>> auditProjects(Map<String, Object> params);
    Integer manageableProjectCount(Map<String, Object> params);
    Integer superAdminCount(Map<String, Object> params);
}

@Configuration
@MapperScan(basePackageClasses = OperationAuditMapper.class)
class OperationAuditMapperConfiguration {}
