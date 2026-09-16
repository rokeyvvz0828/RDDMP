package com.ccb.release.operations.persistence;

import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.release.operations.model.ReleaseOperationsModels.DrillPlan;
import com.ccb.release.operations.model.ReleaseOperationsModels.DrillRound;
import com.ccb.release.operations.model.ReleaseOperationsModels.DrillEnvironment;
import com.ccb.release.operations.model.ReleaseOperationsModels.DrillStep;
import com.ccb.release.operations.model.ReleaseOperationsModels.Group;
import com.ccb.release.operations.model.ReleaseOperationsModels.GroupMember;
import com.ccb.release.operations.model.ReleaseOperationsModels.Issue;
import com.ccb.release.operations.model.ReleaseOperationsModels.PlanItem;
import com.ccb.release.operations.model.ReleaseOperationsModels.PlanItemType;
import com.ccb.release.operations.model.ReleaseOperationsModels.PlanTimeline;
import com.ccb.release.operations.model.ReleaseOperationsModels.ReleaseDrillRound;
import com.ccb.release.operations.model.ReleaseOperationsModels.ReleasePlan;
import com.ccb.release.operations.model.ReleaseOperationsModels.Timeline;
import com.ccb.release.operations.model.ReleaseOperationsModels.TimelineItem;
import com.ccb.release.operations.model.ReleaseOperationsModels.TimelineType;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class ReleaseOperationsStore {
    private final ReleaseOperationsMapper mapper;

    public ReleaseOperationsStore(ReleaseOperationsMapper mapper) {
        this.mapper = mapper;
    }

    public Optional<DrillPlan> findDrillPlan(long tenantId, long projectId) {
        return Optional.ofNullable(mapper.findDrillPlan(params("tenantId", tenantId, "projectId", projectId)));
    }

    public void insertDrillPlan(DrillPlan plan, long operatorId) {
        mapper.insertDrillPlan(params("id", plan.id(), "tenantId", plan.tenantId(), "projectId", plan.projectId(),
                "scenarioContent", plan.scenarioContent(), "environmentContent", plan.environmentContent(), "operatorId", operatorId));
    }

    public boolean updateDrillPlan(long id, long tenantId, long expectedVersion, String scenario, String environment, long operatorId) {
        return mapper.updateDrillPlan(params("id", id, "tenantId", tenantId, "expectedVersion", expectedVersion,
                "scenarioContent", scenario, "environmentContent", environment, "operatorId", operatorId)) == 1;
    }

    public List<DrillRound> findDrillRounds(long tenantId, long projectId) {
        return mapper.findDrillRounds(params("tenantId", tenantId, "projectId", projectId));
    }

    public Optional<DrillRound> findDrillRound(long id, long tenantId, long projectId) {
        return Optional.ofNullable(mapper.findDrillRoundForUpdate(params("id", id, "tenantId", tenantId, "projectId", projectId)));
    }

    public int nextRoundNo(long tenantId, long projectId) {
        Integer value = mapper.nextDrillRoundNo(params("tenantId", tenantId, "projectId", projectId));
        return value == null ? 1 : value;
    }

    public void insertDrillRound(DrillRound round, long tenantId, long drillPlanId, long operatorId) {
        mapper.insertDrillRound(params("id", round.id(), "tenantId", tenantId, "projectId", round.projectId(), "drillPlanId", drillPlanId, "roundNo", round.roundNo(), "roundName", round.roundName(), "plannedAt", timestamp(round.plannedAt()), "status", round.status().name(), "resultContent", round.resultContent(), "operatorId", operatorId));
    }

    public boolean updateDrillRound(DrillRound round, long tenantId, long expectedVersion, long operatorId) {
        return mapper.updateDrillRound(params("id", round.id(), "tenantId", tenantId, "projectId", round.projectId(), "expectedVersion", expectedVersion, "roundName", round.roundName(), "plannedAt", timestamp(round.plannedAt()), "status", round.status().name(), "resultContent", round.resultContent(), "operatorId", operatorId)) == 1;
    }

    public boolean deleteDrillRound(long id, long tenantId, long projectId, long expectedVersion, long operatorId) {
        return mapper.deleteDrillRound(params("id", id, "tenantId", tenantId, "projectId", projectId, "expectedVersion", expectedVersion, "operatorId", operatorId)) == 1;
    }

    public Optional<Timeline> findTimeline(long tenantId, long projectId, TimelineType type) {
        return Optional.ofNullable(mapper.findTimeline(params("tenantId", tenantId, "projectId", projectId, "type", type.name())));
    }

    public void insertTimeline(Timeline timeline, long tenantId, long operatorId) {
        mapper.insertTimeline(params("id", timeline.id(), "tenantId", tenantId, "projectId", timeline.projectId(), "type", timeline.timelineType().name(), "name", timeline.timelineName(), "description", timeline.description(), "operatorId", operatorId));
    }

    public boolean updateTimeline(Timeline timeline, long tenantId, long expectedVersion, long operatorId) {
        return mapper.updateTimeline(params("id", timeline.id(), "tenantId", tenantId, "projectId", timeline.projectId(), "expectedVersion", expectedVersion, "name", timeline.timelineName(), "description", timeline.description(), "operatorId", operatorId)) == 1;
    }

    public List<TimelineItem> findTimelineItems(long tenantId, long projectId, long timelineId) {
        return mapper.findTimelineItems(params("tenantId", tenantId, "projectId", projectId, "timelineId", timelineId));
    }

    public Optional<TimelineItem> findTimelineItem(long id, long tenantId, long projectId, long timelineId) {
        return Optional.ofNullable(mapper.findTimelineItemForUpdate(params("id", id, "tenantId", tenantId, "projectId", projectId, "timelineId", timelineId)));
    }

    public void insertTimelineItem(TimelineItem item, long tenantId, long timelineId, long operatorId) {
        mapper.insertTimelineItem(params("id", item.id(), "tenantId", tenantId, "projectId", item.projectId(), "timelineId", timelineId, "seqNo", item.seqNo(), "name", item.itemName(), "plannedStart", timestamp(item.plannedStart()), "plannedEnd", timestamp(item.plannedEnd()), "ownerId", item.ownerId(), "ownerName", item.ownerName(), "status", item.status(), "description", item.description(), "operatorId", operatorId));
    }

    public boolean updateTimelineItem(TimelineItem item, long tenantId, long timelineId, long expectedVersion, long operatorId) {
        return mapper.updateTimelineItem(params("id", item.id(), "tenantId", tenantId, "projectId", item.projectId(), "timelineId", timelineId, "expectedVersion", expectedVersion, "seqNo", item.seqNo(), "name", item.itemName(), "plannedStart", timestamp(item.plannedStart()), "plannedEnd", timestamp(item.plannedEnd()), "ownerId", item.ownerId(), "ownerName", item.ownerName(), "status", item.status(), "description", item.description(), "operatorId", operatorId)) == 1;
    }

    public boolean deleteTimelineItem(long id, long tenantId, long projectId, long timelineId, long expectedVersion, long operatorId) {
        return mapper.deleteTimelineItem(params("id", id, "tenantId", tenantId, "projectId", projectId, "timelineId", timelineId, "expectedVersion", expectedVersion, "operatorId", operatorId)) == 1;
    }

    public PageResult<Issue> findIssues(long tenantId, long projectId, String keyword, String priority, String status, PageQuery page) {
        Map<String, Object> params = issueSearchParams(tenantId, projectId, keyword, priority, status);
        params.put("size", page.size());
        params.put("offset", (page.page() - 1) * page.size());
        Long total = mapper.countIssues(params);
        return new PageResult<>(mapper.findIssues(params), total == null ? 0 : total, page.page(), page.size());
    }

    public Optional<Issue> findIssue(long id, long tenantId, long projectId) {
        return Optional.ofNullable(mapper.findIssueForUpdate(params("id", id, "tenantId", tenantId, "projectId", projectId)));
    }

    public void insertIssue(Issue issue, long tenantId, long operatorId) {
        mapper.insertIssue(issueParams(issue, tenantId, operatorId));
    }

    public boolean updateIssue(Issue issue, long tenantId, long expectedVersion, long operatorId) {
        Map<String, Object> params = issueParams(issue, tenantId, operatorId); params.put("expectedVersion", expectedVersion); return mapper.updateIssue(params) == 1;
    }

    public boolean deleteIssue(long id, long tenantId, long projectId, long expectedVersion, long operatorId) {
        return mapper.deleteIssue(params("id", id, "tenantId", tenantId, "projectId", projectId, "expectedVersion", expectedVersion, "operatorId", operatorId)) == 1;
    }

    public List<Group> findGroups(long tenantId, long projectId) {
        return mapper.findGroups(params("tenantId", tenantId, "projectId", projectId));
    }

    public Optional<Group> findGroup(long id, long tenantId, long projectId) {
        return Optional.ofNullable(mapper.findGroupForUpdate(params("id", id, "tenantId", tenantId, "projectId", projectId)));
    }

    public void insertGroup(Group group, long tenantId, long operatorId) {
        mapper.insertGroup(params("id", group.id(), "tenantId", tenantId, "projectId", group.projectId(), "name", group.groupName(), "description", group.description(), "operatorId", operatorId));
    }

    public boolean updateGroup(Group group, long tenantId, long expectedVersion, long operatorId) {
        return mapper.updateGroup(params("id", group.id(), "tenantId", tenantId, "projectId", group.projectId(), "expectedVersion", expectedVersion, "name", group.groupName(), "description", group.description(), "operatorId", operatorId)) == 1;
    }

    public boolean deleteGroup(long id, long tenantId, long projectId, long expectedVersion, long operatorId) {
        return mapper.deleteGroup(params("id", id, "tenantId", tenantId, "projectId", projectId, "expectedVersion", expectedVersion, "operatorId", operatorId)) == 1;
    }

    public List<GroupMember> findGroupMembers(long tenantId, long projectId, long groupId) {
        return mapper.findGroupMembers(params("tenantId", tenantId, "projectId", projectId, "groupId", groupId));
    }

    public boolean groupMemberExists(long tenantId, long projectId, long groupId, long projectMemberId) {
        Integer count = mapper.groupMemberCount(params("tenantId", tenantId, "projectId", projectId, "groupId", groupId, "memberId", projectMemberId));
        return count != null && count > 0;
    }

    public void insertGroupMember(GroupMember member, long tenantId, long projectId, long operatorId) {
        mapper.insertGroupMember(params("id", member.id(), "tenantId", tenantId, "projectId", projectId, "groupId", member.groupId(), "memberId", member.projectMemberId(), "userId", member.userId(), "name", member.memberName(), "operatorId", operatorId));
    }

    public boolean deleteGroupMember(long tenantId, long projectId, long groupId, long projectMemberId) {
        return mapper.deleteGroupMember(params("tenantId", tenantId, "projectId", projectId, "groupId", groupId, "memberId", projectMemberId)) == 1;
    }

    public List<ReleasePlan> findReleasePlans(long tenantId, long projectId) {
        return mapper.findReleasePlans(params("tenantId", tenantId, "projectId", projectId));
    }

    public Optional<ReleasePlan> findReleasePlan(long id, long tenantId, long projectId) {
        return Optional.ofNullable(mapper.findReleasePlanForUpdate(params("id", id, "tenantId", tenantId, "projectId", projectId)));
    }

    public void insertReleasePlan(ReleasePlan value, long operatorId) {
        mapper.insertReleasePlan(planParams(value, operatorId));
    }

    public boolean updateReleasePlan(ReleasePlan value, long tenantId, long expectedVersion, long operatorId) {
        Map<String, Object> params = planParams(value, operatorId); params.put("tenantId", tenantId); params.put("expectedVersion", expectedVersion); return mapper.updateReleasePlan(params) == 1;
    }

    public boolean deleteReleasePlan(long id, long tenantId, long projectId, long expectedVersion, long operatorId) {
        return mapper.deleteReleasePlan(params("id", id, "tenantId", tenantId, "projectId", projectId, "expectedVersion", expectedVersion, "operatorId", operatorId)) == 1;
    }

    public List<PlanTimeline> findPlanTimelines(long tenantId, long projectId, long planId, PlanItemType type) {
        return mapper.findPlanTimelines(params("tenantId", tenantId, "projectId", projectId, "planId", planId, "type", type.name()));
    }

    public Optional<PlanTimeline> findPlanTimeline(long id, long tenantId, long projectId, long planId, PlanItemType type) {
        return Optional.ofNullable(mapper.findPlanTimelineForUpdate(params("id", id, "tenantId", tenantId, "projectId", projectId, "planId", planId, "type", type.name())));
    }

    public void insertPlanTimeline(PlanTimeline value, long tenantId, long operatorId) {
        mapper.insertPlanTimeline(timelineParams(value, tenantId, operatorId));
    }

    public boolean updatePlanTimeline(PlanTimeline value, long tenantId, long expectedVersion, long operatorId) {
        Map<String, Object> params = timelineParams(value, tenantId, operatorId); params.put("expectedVersion", expectedVersion); return mapper.updatePlanTimeline(params) == 1;
    }

    public boolean deletePlanTimeline(long id, long tenantId, long projectId, long planId, PlanItemType type, long expectedVersion, long operatorId) {
        return mapper.deletePlanTimeline(params("id", id, "tenantId", tenantId, "projectId", projectId, "planId", planId, "type", type.name(), "expectedVersion", expectedVersion, "operatorId", operatorId)) == 1;
    }

    public int deletePlanItemsByTimeline(long tenantId, long projectId, long planId, PlanItemType type, long timelineId, long operatorId) {
        return mapper.deletePlanItemsByTimeline(params("tenantId", tenantId, "projectId", projectId, "planId", planId, "type", type.name(), "timelineId", timelineId, "operatorId", operatorId));
    }

    public List<PlanItem> findPlanItems(long tenantId, long projectId, long planId, PlanItemType type) {
        return mapper.findPlanItems(params("tenantId", tenantId, "projectId", projectId, "planId", planId, "type", type.name()));
    }

    public List<PlanItem> findPlanItems(long tenantId, long projectId, long planId, PlanItemType type, long timelineId) {
        return mapper.findPlanItemsByTimeline(params("tenantId", tenantId, "projectId", projectId, "planId", planId, "type", type.name(), "timelineId", timelineId));
    }

    public Optional<PlanItem> findPlanItem(long id, long tenantId, long projectId, long planId, PlanItemType type, long timelineId) {
        return Optional.ofNullable(mapper.findPlanItemForUpdate(params("id", id, "tenantId", tenantId, "projectId", projectId, "planId", planId, "type", type.name(), "timelineId", timelineId)));
    }

    public void insertPlanItem(PlanItem value, long tenantId, long timelineId, long operatorId) {
        mapper.insertPlanItem(planItemParams(value, tenantId, timelineId, operatorId));
    }

    public boolean updatePlanItem(PlanItem value, long tenantId, long timelineId, long expectedVersion, long operatorId) {
        Map<String, Object> params = planItemParams(value, tenantId, timelineId, operatorId); params.put("expectedVersion", expectedVersion); return mapper.updatePlanItem(params) == 1;
    }

    public boolean deletePlanItem(long id, long tenantId, long projectId, long planId, PlanItemType type, long timelineId, long expectedVersion, long operatorId) {
        return mapper.deletePlanItem(params("id", id, "tenantId", tenantId, "projectId", projectId, "planId", planId, "type", type.name(), "timelineId", timelineId, "expectedVersion", expectedVersion, "operatorId", operatorId)) == 1;
    }

    public List<DrillEnvironment> findDrillEnvironments(long tenantId, long projectId) {
        return mapper.findDrillEnvironments(params("tenantId", tenantId, "projectId", projectId));
    }

    public Optional<DrillEnvironment> findDrillEnvironment(long id, long tenantId, long projectId) {
        return Optional.ofNullable(mapper.findDrillEnvironmentForUpdate(params("id", id, "tenantId", tenantId, "projectId", projectId)));
    }

    public void insertDrillEnvironment(DrillEnvironment value, long operatorId) {
        mapper.insertDrillEnvironment(environmentParams(value, operatorId));
    }

    public boolean updateDrillEnvironment(DrillEnvironment value, long tenantId, long expectedVersion, long operatorId) {
        Map<String, Object> params = environmentParams(value, operatorId); params.put("tenantId", tenantId); params.put("expectedVersion", expectedVersion); return mapper.updateDrillEnvironment(params) == 1;
    }

    public boolean deleteDrillEnvironment(long id, long tenantId, long projectId, long expectedVersion, long operatorId) {
        return mapper.deleteDrillEnvironment(params("id", id, "tenantId", tenantId, "projectId", projectId, "expectedVersion", expectedVersion, "operatorId", operatorId)) == 1;
    }

    public List<ReleaseDrillRound> findReleaseDrillRounds(long tenantId, long projectId) {
        return mapper.findReleaseDrillRounds(params("tenantId", tenantId, "projectId", projectId));
    }

    public Optional<ReleaseDrillRound> findReleaseDrillRound(long id, long tenantId, long projectId) {
        return Optional.ofNullable(mapper.findReleaseDrillRoundForUpdate(params("id", id, "tenantId", tenantId, "projectId", projectId)));
    }

    public int nextReleaseRoundNo(long tenantId, long projectId) {
        Integer value = mapper.nextReleaseDrillRoundNo(params("tenantId", tenantId, "projectId", projectId));
        return value == null ? 1 : value;
    }

    public void insertReleaseDrillRound(ReleaseDrillRound value, long tenantId, long operatorId) {
        mapper.insertReleaseDrillRound(params("id", value.id(), "tenantId", tenantId, "projectId", value.projectId(), "releasePlanId", value.releasePlanId(), "environmentId", value.environmentId(), "roundNo", value.roundNo(), "name", value.roundName(), "plannedAt", timestamp(value.plannedAt()), "status", value.status().name(), "result", value.resultContent(), "operatorId", operatorId));
    }

    public boolean updateReleaseDrillRound(ReleaseDrillRound value, long tenantId, long expectedVersion, long operatorId) {
        return mapper.updateReleaseDrillRound(params("id", value.id(), "tenantId", tenantId, "projectId", value.projectId(), "releasePlanId", value.releasePlanId(), "environmentId", value.environmentId(), "name", value.roundName(), "plannedAt", timestamp(value.plannedAt()), "status", value.status().name(), "result", value.resultContent(), "expectedVersion", expectedVersion, "operatorId", operatorId)) == 1;
    }

    public boolean deleteReleaseDrillRound(long id, long tenantId, long projectId, long expectedVersion, long operatorId) {
        return mapper.deleteReleaseDrillRound(params("id", id, "tenantId", tenantId, "projectId", projectId, "expectedVersion", expectedVersion, "operatorId", operatorId)) == 1;
    }

    public List<DrillStep> findDrillSteps(long tenantId, long projectId, long roundId) {
        return mapper.findDrillSteps(params("tenantId", tenantId, "projectId", projectId, "roundId", roundId));
    }

    public Optional<DrillStep> findDrillStep(long id, long tenantId, long projectId, long roundId) {
        return Optional.ofNullable(mapper.findDrillStepForUpdate(params("id", id, "tenantId", tenantId, "projectId", projectId, "roundId", roundId)));
    }

    public void insertDrillStep(DrillStep value, long tenantId, long operatorId) {
        mapper.insertDrillStep(stepParams(value, tenantId, operatorId));
    }

    public boolean updateDrillStep(DrillStep value, long tenantId, long expectedVersion, long operatorId) {
        Map<String, Object> params = stepParams(value, tenantId, operatorId); params.put("expectedVersion", expectedVersion); return mapper.updateDrillStep(params) == 1;
    }

    public boolean deleteDrillStep(long id, long tenantId, long projectId, long roundId, long expectedVersion, long operatorId) {
        return mapper.deleteDrillStep(params("id", id, "tenantId", tenantId, "projectId", projectId, "roundId", roundId, "expectedVersion", expectedVersion, "operatorId", operatorId)) == 1;
    }

    private static Timestamp timestamp(LocalDateTime value) { return value == null ? null : Timestamp.valueOf(value); }
    private static Map<String, Object> params(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) result.put((String) values[index], values[index + 1]);
        return result;
    }
    private static Map<String, Object> environmentParams(DrillEnvironment value, long operatorId) { return params("id", value.id(), "tenantId", value.tenantId(), "projectId", value.projectId(), "name", value.environmentName(), "description", value.description(), "carry", value.carryDataLineEnvironment(), "infrastructure", value.infrastructureDeployment(), "hardware", value.hardwareCheck(), "network", value.networkOpening(), "middleware", value.middlewareCheck(), "component", value.componentCheck(), "database", value.databaseCheck(), "operatorId", operatorId); }
    private static Map<String, Object> stepParams(DrillStep value, long tenantId, long operatorId) { return params("id", value.id(), "tenantId", tenantId, "projectId", value.projectId(), "roundId", value.drillRoundId(), "seqNo", value.seqNo(), "name", value.stepName(), "ownerId", value.ownerId(), "ownerName", value.ownerName(), "plannedStart", timestamp(value.plannedStart()), "plannedEnd", timestamp(value.plannedEnd()), "status", value.status(), "result", value.resultContent(), "description", value.description(), "operatorId", operatorId); }
    private static Map<String, Object> planParams(ReleasePlan value, long operatorId) { return params("id", value.id(), "tenantId", value.tenantId(), "projectId", value.projectId(), "name", value.planName(), "code", value.planCode(), "description", value.description(), "versionNo", value.versionNo(), "status", value.status(), "normalTimeline", value.normalTimelineName(), "rollbackTimeline", value.rollbackTimelineName(), "operatorId", operatorId); }
    private static Map<String, Object> timelineParams(PlanTimeline value, long tenantId, long operatorId) { return params("id", value.id(), "tenantId", tenantId, "projectId", value.projectId(), "planId", value.planId(), "type", value.itemType().name(), "seqNo", value.seqNo(), "name", value.timelineName(), "description", value.description(), "operatorId", operatorId); }
    private static Map<String, Object> issueParams(Issue value, long tenantId, long operatorId) { return params("id", value.id(), "tenantId", tenantId, "projectId", value.projectId(), "roundId", value.drillRoundId(), "number", value.issueNo(), "title", value.issueTitle(), "priority", value.priority().name(), "status", value.issueStatus().name(), "discoveredAt", timestamp(value.discoveredAt()), "ownerId", value.ownerId(), "ownerName", value.ownerName(), "description", value.issueDescription(), "analysis", value.analysisContent(), "action", value.actionContent(), "followUp", value.followUpContent(), "closedAt", timestamp(value.closedAt()), "operatorId", operatorId); }
    private static Map<String, Object> planItemParams(PlanItem value, long tenantId, long timelineId, long operatorId) { return params("id", value.id(), "tenantId", tenantId, "projectId", value.projectId(), "planId", value.planId(), "timelineId", timelineId, "type", value.itemType().name(), "seqNo", value.seqNo(), "name", value.itemName(), "plannedStart", timestamp(value.plannedStart()), "plannedEnd", timestamp(value.plannedEnd()), "ownerId", value.ownerId(), "ownerName", value.ownerName(), "status", value.status(), "description", value.description(), "operatorId", operatorId); }
    private static Map<String, Object> issueSearchParams(long tenantId, long projectId, String keyword, String priority, String status) {
        return params("tenantId", tenantId, "projectId", projectId,
                "keyword", keyword == null || keyword.isBlank() ? null : "%" + keyword.trim() + "%",
                "priority", priority == null || priority.isBlank() ? null : priority.trim().toUpperCase(),
                "status", status == null || status.isBlank() ? null : status.trim().toUpperCase());
    }
}
