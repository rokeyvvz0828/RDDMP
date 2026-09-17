package com.ccb.requirement.service;

import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Map;

@Repository
public class RequirementWorkflowRepository {
    private final RequirementWorkflowMapper mapper;

    public RequirementWorkflowRepository(RequirementWorkflowMapper mapper) {
        this.mapper = mapper;
    }

    public Map<String, Object> findActiveDifference(long tenantId, long differenceId) {
        return mapper.findActiveDifference(tenantId, differenceId);
    }

    public int updateReviewStatus(long tenantId, long differenceId, String reviewStatus, long operatorId) {
        return mapper.updateReviewStatus(tenantId, differenceId, reviewStatus, operatorId);
    }

    public Map<String, Object> findLatestWorkflowAction(long tenantId, long instanceId) {
        return mapper.findLatestWorkflowAction(tenantId, instanceId);
    }

    public void insertReviewRecord(long id, long tenantId, long differenceId, long reviewerId, String reviewerName,
                                   LocalDateTime reviewTime, String conclusion, String comment, String reportDocName,
                                   long createdBy) {
        mapper.insertReviewRecord(id, tenantId, differenceId, reviewerId, reviewerName, reviewTime, conclusion,
                comment, reportDocName, createdBy);
    }
}
