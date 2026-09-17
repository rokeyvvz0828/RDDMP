package com.ccb.requirement.service;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface RequirementSystemMapper {
    List<Map<String, Object>> list(@Param("tenantId") long tenantId);
    Map<String, Object> find(@Param("tenantId") long tenantId, @Param("id") long id);
    int countByCode(@Param("tenantId") long tenantId, @Param("systemCode") String systemCode);
    int insert(@Param("p") Map<String, Object> values);
    int update(@Param("p") Map<String, Object> values);
    int softDelete(@Param("tenantId") long tenantId, @Param("id") long id, @Param("operatorId") long operatorId);
    Long findIdByCode(@Param("tenantId") long tenantId, @Param("systemCode") String systemCode);
}
