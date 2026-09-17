package com.ccb.requirement.service;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.Map;

@Mapper
public interface RequirementWorkflowMapper {
    Map<String, Object> findActiveDifference(@Param("tenantId") long tenantId, @Param("differenceId") long differenceId);

    int updateReviewStatus(@Param("tenantId") long tenantId, @Param("differenceId") long differenceId,
                           @Param("reviewStatus") String reviewStatus, @Param("operatorId") long operatorId);

    Map<String, Object> findLatestWorkflowAction(@Param("tenantId") long tenantId, @Param("instanceId") long instanceId);

    int insertReviewRecord(@Param("id") long id, @Param("tenantId") long tenantId, @Param("differenceId") long differenceId,
                           @Param("reviewerId") long reviewerId, @Param("reviewerName") String reviewerName,
                           @Param("reviewTime") LocalDateTime reviewTime, @Param("conclusion") String conclusion,
                           @Param("comment") String comment, @Param("reportDocName") String reportDocName,
                           @Param("createdBy") long createdBy);
}
