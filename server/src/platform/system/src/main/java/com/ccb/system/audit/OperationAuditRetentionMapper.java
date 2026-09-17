package com.ccb.system.audit;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.sql.Timestamp;

@Mapper
public interface OperationAuditRetentionMapper {
    int deleteOperations(@Param("cutoff") Timestamp cutoff);
    int deleteLogins(@Param("cutoff") Timestamp cutoff);
    int insertSummary(@Param("id") long id, @Param("summary") String summary);
}

@org.springframework.context.annotation.Configuration
@org.mybatis.spring.annotation.MapperScan(basePackageClasses = OperationAuditRetentionMapper.class)
class OperationAuditRetentionMapperConfiguration {}
