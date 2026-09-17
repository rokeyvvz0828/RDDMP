package com.ccb.requirement.service;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

@Mapper
public interface RequirementLegacyEnhanceMapper {
    List<Map<String,Object>> deliverables(@Param("tenantId") long tenantId,@Param("requirementId") long requirementId,@Param("type") String type);
    int insertDeliverable(@Param("type") String type,@Param("p") Map<String,Object> p);
    int deleteDeliverable(@Param("tenantId") long tenantId,@Param("id") long id,@Param("type") String type);
    int submitReview(@Param("tenantId") long tenantId,@Param("id") long id,@Param("type") String type,@Param("ids") String ids,@Param("names") String names,@Param("report") String report);
    int reviewDeliverable(@Param("tenantId") long tenantId,@Param("id") long id,@Param("type") String type,@Param("status") String status,@Param("recordId") long recordId);
    List<Map<String,Object>> coordinationItems(@Param("tenantId") long tenantId,@Param("requirementId") long requirementId);
    int insertCoordination(@Param("p") Map<String,Object> p);
    int updateCoordination(@Param("p") Map<String,Object> p);
    int deleteCoordination(@Param("tenantId") long tenantId,@Param("id") long id);
    List<Map<String,Object>> reviewRecords(@Param("tenantId") long tenantId,@Param("bizType") String bizType,@Param("bizId") long bizId);
    int insertReviewRecord(@Param("p") Map<String,Object> p);
    List<Map<String,Object>> approverNames(@Param("tenantId") long tenantId,@Param("ids") List<Long> ids);
    Map<String,Object> legacyRequirement(@Param("tenantId") long tenantId,@Param("id") long id);
    List<String> versions(@Param("tenantId") long tenantId,@Param("requirementId") long requirementId,@Param("systemItemId") Long systemItemId,@Param("systemCode") String systemCode,@Param("type") String type);
    Map<String,Object> deliverable(@Param("tenantId") long tenantId,@Param("id") long id,@Param("type") String type);
    Map<String,Object> coordination(@Param("tenantId") long tenantId,@Param("id") long id);
}
