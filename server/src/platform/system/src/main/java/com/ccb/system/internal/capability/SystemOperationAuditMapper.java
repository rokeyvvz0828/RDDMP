package com.ccb.system.internal.capability;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SystemOperationAuditMapper {
    int insert(@Param("id") long id, @Param("tenantId") long tenantId, @Param("operatorId") long operatorId,
               @Param("operationCode") String operationCode, @Param("requestMethod") String requestMethod,
               @Param("requestPath") String requestPath, @Param("success") int success,
               @Param("errorMessage") String errorMessage, @Param("traceId") String traceId);
}

@org.springframework.context.annotation.Configuration
@org.mybatis.spring.annotation.MapperScan(basePackageClasses = SystemOperationAuditMapper.class)
class SystemOperationAuditMapperConfiguration {}
