package com.ccb.requirement.service;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

@Mapper
public interface RequirementChangeLogMapper {
    int insert(@Param("id") long id, @Param("tenantId") long tenantId, @Param("bizType") String bizType, @Param("bizId") long bizId, @Param("field") String field, @Param("oldValue") String oldValue, @Param("newValue") String newValue, @Param("changeType") String changeType, @Param("operatorId") long operatorId, @Param("operatorName") String operatorName, @Param("source") String source, @Param("traceId") String traceId);
    List<Map<String, Object>> list(@Param("tenantId") long tenantId, @Param("bizType") String bizType, @Param("bizId") long bizId);
}
