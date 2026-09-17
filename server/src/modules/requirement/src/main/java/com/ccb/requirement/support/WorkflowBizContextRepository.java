package com.ccb.requirement.support;

import org.springframework.stereotype.Repository;

@Repository
public class WorkflowBizContextRepository {
    private final WorkflowBizContextMapper mapper;
    public WorkflowBizContextRepository(WorkflowBizContextMapper mapper) { this.mapper = mapper; }
    public void update(long instanceId, long tenantId, String moduleCode, String moduleName, String businessType,
                       String businessTitle, int round, String projectRef, String projectName, String actionPath, String dataDigest) {
        mapper.update(instanceId, tenantId, moduleCode, moduleName, businessType, businessTitle, round, projectRef, projectName, actionPath, dataDigest);
    }
}
