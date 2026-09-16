package com.ccb.release.operations.persistence;

import com.ccb.release.operations.model.ReleaseOperationsModels.DrillPlan;
import com.ccb.release.operations.model.ReleaseOperationsModels.DrillRound;
import com.ccb.release.operations.model.ReleaseOperationsModels.Timeline;
import com.ccb.release.operations.model.ReleaseOperationsModels.TimelineItem;
import com.ccb.release.operations.model.ReleaseOperationsModels.Group;
import com.ccb.release.operations.model.ReleaseOperationsModels.GroupMember;
import com.ccb.release.operations.model.ReleaseOperationsModels.DrillEnvironment;
import com.ccb.release.operations.model.ReleaseOperationsModels.DrillStep;
import com.ccb.release.operations.model.ReleaseOperationsModels.ReleasePlan;
import com.ccb.release.operations.model.ReleaseOperationsModels.PlanTimeline;
import com.ccb.release.operations.model.ReleaseOperationsModels.PlanItem;
import com.ccb.release.operations.model.ReleaseOperationsModels.Issue;
import com.ccb.release.operations.model.ReleaseOperationsModels.ReleaseDrillRound;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ReleaseOperationsMapper {
    DrillPlan findDrillPlan(Map<String, Object> params);

    int insertDrillPlan(Map<String, Object> params);

    int updateDrillPlan(Map<String, Object> params);

    java.util.List<DrillRound> findDrillRounds(Map<String, Object> params);

    DrillRound findDrillRoundForUpdate(Map<String, Object> params);

    Integer nextDrillRoundNo(Map<String, Object> params);

    int insertDrillRound(Map<String, Object> params);

    int updateDrillRound(Map<String, Object> params);

    int deleteDrillRound(Map<String, Object> params);

    Timeline findTimeline(Map<String, Object> params);
    int insertTimeline(Map<String, Object> params);
    int updateTimeline(Map<String, Object> params);
    java.util.List<TimelineItem> findTimelineItems(Map<String, Object> params);
    TimelineItem findTimelineItemForUpdate(Map<String, Object> params);
    int insertTimelineItem(Map<String, Object> params);
    int updateTimelineItem(Map<String, Object> params);
    int deleteTimelineItem(Map<String, Object> params);
    java.util.List<Group> findGroups(Map<String, Object> params);
    Group findGroupForUpdate(Map<String, Object> params);
    int insertGroup(Map<String, Object> params);
    int updateGroup(Map<String, Object> params);
    int deleteGroup(Map<String, Object> params);
    java.util.List<GroupMember> findGroupMembers(Map<String, Object> params);
    Integer groupMemberCount(Map<String, Object> params);
    int insertGroupMember(Map<String, Object> params);
    int deleteGroupMember(Map<String, Object> params);
    java.util.List<DrillEnvironment> findDrillEnvironments(Map<String, Object> params);
    DrillEnvironment findDrillEnvironmentForUpdate(Map<String, Object> params);
    int insertDrillEnvironment(Map<String, Object> params);
    int updateDrillEnvironment(Map<String, Object> params);
    int deleteDrillEnvironment(Map<String, Object> params);
    java.util.List<DrillStep> findDrillSteps(Map<String, Object> params);
    DrillStep findDrillStepForUpdate(Map<String, Object> params);
    int insertDrillStep(Map<String, Object> params);
    int updateDrillStep(Map<String, Object> params);
    int deleteDrillStep(Map<String, Object> params);
    java.util.List<ReleasePlan> findReleasePlans(Map<String, Object> params);
    ReleasePlan findReleasePlanForUpdate(Map<String, Object> params);
    int insertReleasePlan(Map<String, Object> params);
    int updateReleasePlan(Map<String, Object> params);
    int deleteReleasePlan(Map<String, Object> params);
    java.util.List<PlanTimeline> findPlanTimelines(Map<String, Object> params);
    PlanTimeline findPlanTimelineForUpdate(Map<String, Object> params);
    int insertPlanTimeline(Map<String, Object> params);
    int updatePlanTimeline(Map<String, Object> params);
    int deletePlanTimeline(Map<String, Object> params);
    int deletePlanItemsByTimeline(Map<String, Object> params);
    Integer nextReleaseDrillRoundNo(Map<String, Object> params);
    int insertReleaseDrillRound(Map<String, Object> params);
    int updateReleaseDrillRound(Map<String, Object> params);
    int deleteReleaseDrillRound(Map<String, Object> params);
    int deleteIssue(Map<String, Object> params);
    int insertIssue(Map<String, Object> params);
    int updateIssue(Map<String, Object> params);
    Long countIssues(Map<String, Object> params);
    java.util.List<Issue> findIssues(Map<String, Object> params);
    Issue findIssueForUpdate(Map<String, Object> params);
    int deletePlanItem(Map<String, Object> params);
    int insertPlanItem(Map<String, Object> params);
    int updatePlanItem(Map<String, Object> params);
    java.util.List<PlanItem> findPlanItems(Map<String, Object> params);
    java.util.List<PlanItem> findPlanItemsByTimeline(Map<String, Object> params);
    PlanItem findPlanItemForUpdate(Map<String, Object> params);
    java.util.List<ReleaseDrillRound> findReleaseDrillRounds(Map<String, Object> params);
    ReleaseDrillRound findReleaseDrillRoundForUpdate(Map<String, Object> params);
}
