package com.ccb.requirement.service;

import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public class RequirementSecurityRepository {
    private final RequirementSecurityMapper mapper;
    public RequirementSecurityRepository(RequirementSecurityMapper mapper) { this.mapper = mapper; }
    public boolean hasPermission(long tenantId, long userId, String permissionCode) { return mapper.permissionCount(tenantId, userId, permissionCode) > 0; }
    public boolean isLegacySystemOwner(long tenantId, long requirementId, long userId) { return mapper.legacySystemOwnerCount(tenantId, requirementId, userId) > 0; }
    public boolean isLegacySystemMember(long tenantId, long requirementId, long userId) { return mapper.legacySystemMemberCount(tenantId, requirementId, userId) > 0; }
    public boolean isCurrentFlowAssignee(long tenantId, long requirementId, long userId) { return mapper.currentFlowAssigneeCount(tenantId, requirementId, userId) > 0; }
    public boolean isLegacyMember(long tenantId, long requirementId, long userId) { return mapper.legacyMemberCount(tenantId, requirementId, userId) > 0; }
    public boolean isProjectMember(long tenantId, long projectId, long userId) { return mapper.projectMemberCount(tenantId, projectId, userId) > 0; }
    public boolean hasActiveProject(long tenantId, long projectId) { return mapper.activeProjectCount(tenantId, projectId) > 0; }
    public boolean isBusinessGroupMember(long tenantId, String businessGroup, long userId) { return mapper.businessGroupMemberCount(tenantId, businessGroup, userId) > 0; }
    public List<String> listBusinessGroups(long tenantId, long userId) { return mapper.listBusinessGroups(tenantId, userId); }
}
