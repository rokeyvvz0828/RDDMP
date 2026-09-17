package com.ccb.requirement.support;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WorkflowBizContextMapper {
    int update(@Param("instanceId") long instanceId, @Param("tenantId") long tenantId,
               @Param("moduleCode") String moduleCode, @Param("moduleName") String moduleName,
               @Param("businessType") String businessType, @Param("businessTitle") String businessTitle,
               @Param("round") int round, @Param("projectRef") String projectRef,
               @Param("projectName") String projectName, @Param("actionPath") String actionPath,
               @Param("dataDigest") String dataDigest);
}
