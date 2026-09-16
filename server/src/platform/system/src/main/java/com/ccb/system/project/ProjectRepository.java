package com.ccb.system.project;

import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Map;

@Repository
public class ProjectRepository {
    private final ProjectMapper mapper;
    public ProjectRepository(ProjectMapper mapper) { this.mapper = mapper; }
    public ProjectMapper mapper() { return mapper; }
    public List<Map<String, Object>> organizations(long projectId, long tenantId) { return mapper.selectOrganizations(projectId, tenantId); }
    public Map<String, Object> organization(long id, long projectId, long tenantId) { return mapper.selectOrganization(id, projectId, tenantId); }
    public List<Map<String, Object>> roles(long projectId, long tenantId) { return mapper.selectRoles(projectId, tenantId); }
    public Map<String, Object> role(long id, long projectId, long tenantId) { return mapper.selectRole(id, projectId, tenantId); }
    public long organizationCount(long id, long projectId, long tenantId) { return mapper.countOrganization(id, projectId, tenantId); }
    public long organizationCodeCount(long projectId, long tenantId, String code, long excludedId) { return mapper.countOrganizationCode(projectId, tenantId, code, excludedId); }
    public Long organizationParent(long id, long projectId, long tenantId) { return mapper.selectOrganizationParent(id, projectId, tenantId); }
    public long organizationChildren(long id, long projectId, long tenantId) { return mapper.countOrganizationChildren(id, projectId, tenantId); }
    public long organizationMembers(long id, long projectId, long tenantId) { return mapper.countOrganizationMembers(id, projectId, tenantId); }
    public int createOrganization(Map<String, Object> values) { return mapper.insertOrganization(values); }
    public int updateOrganization(Map<String, Object> values) { return mapper.updateOrganization(values); }
    public int deleteOrganization(long id, long projectId, long tenantId) { return mapper.deleteOrganization(id, projectId, tenantId); }
    public long activeUserCount(long userId, long tenantId) { return mapper.countActiveUser(userId, tenantId); }
    public List<Map<String, Object>> userOptions(long tenantId, String keyword) { return mapper.selectUserOptions(tenantId, keyword); }
    public long memberCountByUser(long projectId, long userId, long tenantId) { return mapper.countMemberByUser(projectId, userId, tenantId); }
    public Map<String, Object> member(long id, long tenantId) { return mapper.selectMember(id, tenantId); }
    public long memberCount(long id, long projectId, long tenantId) { return mapper.countMember(id, projectId, tenantId); }
    public Long memberUserId(long id, long projectId, long tenantId) { return mapper.selectMemberUserId(id, projectId, tenantId); }
    public int createMember(Map<String, Object> values) { return mapper.insertMember(values); }
    public int updateMemberStatus(long id, long projectId, long tenantId, long status) { return mapper.updateMemberStatus(id, projectId, tenantId, status); }
    public int updateMemberOrganization(long id, long projectId, long tenantId, Long orgId) { return mapper.updateMemberOrganization(id, projectId, tenantId, orgId); }
    public int deleteMember(long id, long projectId, long tenantId) { return mapper.deleteMember(id, projectId, tenantId); }
    public List<Map<String, Object>> memberRoles(long memberId, long tenantId) { return mapper.selectMemberRoles(memberId, tenantId); }
    public int deleteMemberRoles(long memberId, long tenantId) { return mapper.deleteMemberRoles(memberId, tenantId); }
    public int addMemberRole(long memberId, long roleId, long tenantId) { return mapper.insertMemberRole(memberId, roleId, tenantId); }
    public long roleCount(long id, long projectId, long tenantId) { return mapper.countRole(id, projectId, tenantId); }
    public int createRole(Map<String, Object> values) { return mapper.insertRole(values); }
    public int updateRole(Map<String, Object> values) { return mapper.updateRole(values); }
    public int deleteRole(long id, long projectId, long tenantId) { return mapper.deleteRole(id, projectId, tenantId); }
    public long roleMemberCount(long roleId, long tenantId) { return mapper.countRoleMembers(roleId, tenantId); }
    public int deleteRoleMemberLinks(long roleId, long tenantId) { return mapper.deleteRoleMemberLinks(roleId, tenantId); }
    public long activeMemberCount(long id, long projectId, long tenantId) { return mapper.countActiveMember(id, projectId, tenantId); }
    public List<Map<String, Object>> roleMembers(long roleId, long projectId, long tenantId) { return mapper.selectRoleMembers(roleId, projectId, tenantId); }
    public List<Map<String, Object>> projectPermissionMenus(long tenantId) { return mapper.selectProjectPermissionMenus(tenantId); }
    public List<Map<String, Object>> projectMenuActions(long tenantId, long menuId) { return mapper.selectProjectMenuActions(tenantId, menuId); }
    public List<Long> projectRolePermissionIds(long tenantId, long projectId, long roleId) { return mapper.selectProjectRolePermissionIds(tenantId, projectId, roleId); }
    public long assignablePermissionCount(long tenantId, List<Long> ids) { return mapper.countAssignablePermissions(tenantId, ids); }
    public int deleteProjectRolePermissions(long tenantId, long projectId, long roleId) { return mapper.deleteProjectRolePermissions(tenantId, projectId, roleId); }
    public int addProjectRolePermission(long tenantId, long projectId, long roleId, long permissionId) { return mapper.insertProjectRolePermission(tenantId, projectId, roleId, permissionId); }
    public long projectCount(long projectId, long tenantId) { return mapper.countProject(projectId, tenantId); }
    public long projectMemberAccessCount(long projectId, long tenantId, long userId) { return mapper.countProjectMemberAccess(projectId, tenantId, userId); }
    public long projectOwnerAccessCount(long projectId, long tenantId, long userId) { return mapper.countProjectOwnerAccess(projectId, tenantId, userId); }
    public long superAdminCount(long userId, long tenantId) { return mapper.countSuperAdmin(userId, tenantId); }
    public Long projectOwnerId(long projectId, long tenantId) { return mapper.selectProjectOwnerId(projectId, tenantId); }
    public String userDisplayName(long userId, long tenantId) { return mapper.selectUserDisplayName(userId, tenantId); }
    public List<Map<String, Object>> organizationOptions(long tenantId) { return mapper.selectOrganizationOptions(tenantId); }
    public long activeOrganizationCount(long orgId, long tenantId) { return mapper.countActiveOrganization(orgId, tenantId); }
    public long configValueCount(long tenantId, String categoryCode, String categoryName, String configKey) { return mapper.countConfigValue(tenantId, categoryCode, categoryName, configKey); }
    public String configLabel(long tenantId, String categoryCode, String categoryName, Object configKey) { return mapper.selectConfigLabel(tenantId, categoryCode, categoryName, configKey); }
    public List<Map<String, Object>> phaseOptions(long tenantId, String categoryCode, String categoryName) { return mapper.selectPhaseOptions(tenantId, categoryCode, categoryName); }
    public int createAudit(long id, long tenantId, long operatorId, String operation, String targetId) { return mapper.insertAudit(id, tenantId, operatorId, operation, targetId); }
    public Integer nextStageSort(long projectId, long tenantId) { return mapper.selectNextStageSort(projectId, tenantId); }
    public List<Map<String, Object>> projectStages(long projectId, long tenantId) { return mapper.selectProjectStages(projectId, tenantId); }
    public Map<String, Object> projectStageForUpdate(long stageId, long projectId, long tenantId) { return mapper.selectProjectStageForUpdate(stageId, projectId, tenantId); }
    public long stageMasterPlanCount(long stageId, long projectId, long tenantId) { return mapper.countStageMasterPlans(stageId, projectId, tenantId); }
    public int createProjectStage(Map<String, Object> values) { return mapper.insertProjectStage(values); }
    public int createProjectStageIfMissing(Map<String, Object> values) { return mapper.insertProjectStageIfMissing(values); }
    public int updateProjectStage(Map<String, Object> values) { return mapper.updateProjectStage(values); }
    public int deleteProjectStage(long stageId, long projectId, long tenantId) { return mapper.deleteProjectStage(stageId, projectId, tenantId); }
    public long projectActionCount(long projectId, long tenantId, long userId, String permissionCode, String action) { return mapper.countProjectAction(projectId, tenantId, userId, permissionCode, action); }
    public List<Map<String, Object>> workbench(long tenantId, Long userId) { return userId == null ? mapper.selectWorkbenchForAdmin(tenantId) : mapper.selectWorkbenchForMember(tenantId, userId); }
    public List<Map<String, Object>> plans(long projectId, long tenantId) { return mapper.selectPlans(projectId, tenantId); }
    public List<Map<String, Object>> planGroups(long projectId, long tenantId) { return mapper.selectPlanGroups(projectId, tenantId); }
    public List<Map<String, Object>> risks(long projectId, long tenantId) { return mapper.selectRisks(projectId, tenantId); }
    public List<Map<String, Object>> riskComments(long projectId, long riskId, long tenantId) { return mapper.selectRiskComments(projectId, riskId, tenantId); }
    public int updateProjectSettings(long projectId, long tenantId, String planRule, String childRule, String riskRule) { return mapper.updateProjectSettings(projectId, tenantId, planRule, childRule, riskRule); }
    public int createProject(Map<String, Object> values) { return mapper.insertProject(values); }
    public int createProjectManagerRole(long roleId, long tenantId, long projectId) { return mapper.insertProjectManagerRole(roleId, tenantId, projectId); }
    public int updateProject(Map<String, Object> values) { return mapper.updateProject(values); }
    public int deleteProject(long projectId, long tenantId) { return mapper.deleteProject(projectId, tenantId); }
    public void deleteProjectDependents(long projectId, long tenantId) { mapper.deleteProjectPlanOrganizations(projectId, tenantId); mapper.deleteProjectPlans(projectId, tenantId); mapper.deleteProjectPlanGroups(projectId, tenantId); mapper.deleteProjectRisks(projectId, tenantId); mapper.deleteProjectMemberRoles(projectId, tenantId); mapper.deleteProjectMembers(projectId, tenantId); mapper.deleteAllProjectRolePermissions(projectId, tenantId); mapper.deleteProjectRoles(projectId, tenantId); mapper.deleteProjectOrganizations(projectId, tenantId); mapper.deleteProjectStages(projectId, tenantId); }
    public int createPlanGroup(Map<String, Object> values) { return mapper.insertPlanGroup(values); } public int updatePlanGroup(Map<String, Object> values) { return mapper.updatePlanGroup(values); } public int clearPlanGroup(long groupId, long projectId, long tenantId) { return mapper.clearPlanGroup(groupId, projectId, tenantId); } public int deletePlanGroup(long groupId, long projectId, long tenantId) { return mapper.deletePlanGroup(groupId, projectId, tenantId); }
    public int movePlansToGroup(Map<String, Object> values) { return mapper.movePlansToGroup(values); } public int updatePlanCode(String code, long planId, long projectId, long tenantId) { return mapper.updatePlanCode(code, planId, projectId, tenantId); } public int updateProjectPlanSequence(long sequence, long projectId, long tenantId) { return mapper.updateProjectPlanSequence(sequence, projectId, tenantId); } public int createPlan(Map<String, Object> values) { return mapper.insertPlan(values); } public int updateChildPlanSequence(long sequence, long planId, long projectId, long tenantId) { return mapper.updateChildPlanSequence(sequence, planId, projectId, tenantId); } public int updatePlan(Map<String, Object> values) { return mapper.updatePlan(values); } public int deletePlanOrganizationsForRoot(long planId, long projectId, long tenantId) { return mapper.deletePlanOrganizationsForRoot(planId, projectId, tenantId); } public int deletePlan(long planId, long projectId, long tenantId) { return mapper.deletePlan(planId, projectId, tenantId); }
    public int updateProjectRiskSequence(long sequence, long projectId, long tenantId) { return mapper.updateProjectRiskSequence(sequence, projectId, tenantId); } public int updateRisk(Map<String, Object> values) { return mapper.updateRisk(values); } public int deleteRisk(long riskId, long projectId, long tenantId) { return mapper.deleteRisk(riskId, projectId, tenantId); } public int createRiskComment(Map<String, Object> values) { return mapper.insertRiskComment(values); } public int updateRiskProgress(String comment, long riskId, long projectId, long tenantId) { return mapper.updateRiskProgress(comment, riskId, projectId, tenantId); } public int createRisk(Map<String, Object> values) { return mapper.insertRisk(values); }
    public Map<String, Object> risk(long riskId, long projectId, long tenantId) { return mapper.selectRisk(riskId, projectId, tenantId); } public Map<String, Object> riskComment(long commentId, long projectId, long riskId, long tenantId) { return mapper.selectRiskComment(commentId, projectId, riskId, tenantId); } public long riskCount(long riskId, long projectId, long tenantId) { return mapper.countRisk(riskId, projectId, tenantId); } public Map<String, Object> project(long projectId, long tenantId) { return mapper.selectProject(projectId, tenantId); } public Map<String, Object> projectForUpdate(long projectId, long tenantId) { return mapper.selectProjectForUpdate(projectId, tenantId); } public long activeStageCount(long projectId, long tenantId, String stageCode) { return mapper.countActiveStage(projectId, tenantId, stageCode); } public Map<String, Object> parentPlanForUpdate(long planId, long projectId, long tenantId) { return mapper.selectParentPlanForUpdate(planId, projectId, tenantId); } public Map<String, Object> plan(long planId, long projectId, long tenantId) { return mapper.selectPlan(planId, projectId, tenantId); } public Map<String, Object> planForUpdate(long planId, long projectId, long tenantId) { return mapper.selectPlanForUpdate(planId, projectId, tenantId); } public Map<String, Object> planGroup(long groupId, long projectId, long tenantId) { return mapper.selectPlanGroup(groupId, projectId, tenantId); } public long planGroupCount(long groupId, long projectId, long tenantId) { return mapper.countPlanGroup(groupId, projectId, tenantId); } public long planGroupNameCount(long projectId, long tenantId, String phase, String groupName, long excludedId) { return mapper.countPlanGroupName(projectId, tenantId, phase, groupName, excludedId); } public long maxPlanGroupSequence(long projectId, long tenantId, String phase) { return mapper.selectMaxPlanGroupSequence(projectId, tenantId, phase); } public long planCodeCount(long projectId, long tenantId, String code) { return mapper.countPlanCode(projectId, tenantId, code); }
    public List<Long> childPlanIdsForUpdate(long planId, long projectId, long tenantId) { return mapper.selectChildPlanIdsForUpdate(planId, projectId, tenantId); } public int updateGroupPlansPhase(String phase, long groupId, long projectId, long tenantId) { return mapper.updateGroupPlansPhase(phase, groupId, projectId, tenantId); } public List<Map<String, Object>> userDisplayNames(long tenantId, List<Long> userIds) { return mapper.selectUserDisplayNames(tenantId, userIds); } public List<Map<String, Object>> projectStatistics(long tenantId, List<Long> projectIds) { return mapper.selectProjectStatistics(tenantId, projectIds); } public long planCount(long planId, long projectId, long tenantId) { return mapper.countPlan(planId, projectId, tenantId); } public Map<String, Object> planDates(long planId, long projectId, long tenantId) { return mapper.selectPlanDates(planId, projectId, tenantId); } public List<Map<String, Object>> groupPlanDates(long projectId, long tenantId, long groupId) { return mapper.selectGroupPlanDates(projectId, tenantId, groupId); } public int deletePlanOrganizations(long planId, long tenantId) { return mapper.deletePlanOrganizations(planId, tenantId); } public int createPlanOrganization(long planId, long orgId, String partyType, long tenantId) { return mapper.insertPlanOrganization(planId, orgId, partyType, tenantId); } public List<Map<String, Object>> planOrganizations(long planId, long tenantId) { return mapper.selectPlanOrganizations(planId, tenantId); } public List<Long> childPlanIds(long planId, long projectId, long tenantId) { return mapper.selectChildPlanIds(planId, projectId, tenantId); }
}
