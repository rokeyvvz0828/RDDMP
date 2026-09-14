package com.ccb.release.operations.persistence;

import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.release.operations.model.ReleaseOperationsModels.DrillPlan;
import com.ccb.release.operations.model.ReleaseOperationsModels.DrillRound;
import com.ccb.release.operations.model.ReleaseOperationsModels.DrillEnvironment;
import com.ccb.release.operations.model.ReleaseOperationsModels.DrillStep;
import com.ccb.release.operations.model.ReleaseOperationsModels.DrillStatus;
import com.ccb.release.operations.model.ReleaseOperationsModels.Group;
import com.ccb.release.operations.model.ReleaseOperationsModels.GroupMember;
import com.ccb.release.operations.model.ReleaseOperationsModels.Issue;
import com.ccb.release.operations.model.ReleaseOperationsModels.IssuePriority;
import com.ccb.release.operations.model.ReleaseOperationsModels.IssueStatus;
import com.ccb.release.operations.model.ReleaseOperationsModels.MemberOption;
import com.ccb.release.operations.model.ReleaseOperationsModels.PlanItem;
import com.ccb.release.operations.model.ReleaseOperationsModels.PlanItemType;
import com.ccb.release.operations.model.ReleaseOperationsModels.PlanTimeline;
import com.ccb.release.operations.model.ReleaseOperationsModels.ReleaseDrillRound;
import com.ccb.release.operations.model.ReleaseOperationsModels.ReleasePlan;
import com.ccb.release.operations.model.ReleaseOperationsModels.Timeline;
import com.ccb.release.operations.model.ReleaseOperationsModels.TimelineItem;
import com.ccb.release.operations.model.ReleaseOperationsModels.TimelineType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class ReleaseOperationsStore {
    private final JdbcTemplate jdbc;

    public ReleaseOperationsStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<DrillPlan> findDrillPlan(long tenantId, long projectId) {
        return first(jdbc.query("SELECT id, tenant_id, project_id, scenario_content, environment_content, row_version, updated_at "
                + "FROM rel_release_drill_plan WHERE tenant_id = ? AND project_id = ? AND deleted = 0", DRILL_PLAN, tenantId, projectId));
    }

    public void insertDrillPlan(DrillPlan plan, long operatorId) {
        jdbc.update("INSERT INTO rel_release_drill_plan (id, tenant_id, project_id, scenario_content, environment_content, row_version, created_by, updated_by) VALUES (?, ?, ?, ?, ?, 0, ?, ?)",
                plan.id(), plan.tenantId(), plan.projectId(), plan.scenarioContent(), plan.environmentContent(), operatorId, operatorId);
    }

    public boolean updateDrillPlan(long id, long tenantId, long expectedVersion, String scenario, String environment, long operatorId) {
        return jdbc.update("UPDATE rel_release_drill_plan SET scenario_content = ?, environment_content = ?, updated_by = ?, row_version = row_version + 1 "
                + "WHERE id = ? AND tenant_id = ? AND deleted = 0 AND row_version = ?", scenario, environment, operatorId, id, tenantId, expectedVersion) == 1;
    }

    public List<DrillRound> findDrillRounds(long tenantId, long projectId) {
        return jdbc.query("SELECT id, project_id, round_no, round_name, planned_at, status, result_content, row_version, updated_at "
                + "FROM rel_release_drill_round WHERE tenant_id = ? AND project_id = ? AND deleted = 0 ORDER BY round_no, id", DRILL_ROUND, tenantId, projectId);
    }

    public Optional<DrillRound> findDrillRound(long id, long tenantId, long projectId) {
        return first(jdbc.query("SELECT id, project_id, round_no, round_name, planned_at, status, result_content, row_version, updated_at "
                + "FROM rel_release_drill_round WHERE id = ? AND tenant_id = ? AND project_id = ? AND deleted = 0 FOR UPDATE", DRILL_ROUND, id, tenantId, projectId));
    }

    public int nextRoundNo(long tenantId, long projectId) {
        Integer value = jdbc.queryForObject("SELECT COALESCE(MAX(round_no), 0) + 1 FROM rel_release_drill_round WHERE tenant_id = ? AND project_id = ? AND deleted = 0", Integer.class, tenantId, projectId);
        return value == null ? 1 : value;
    }

    public void insertDrillRound(DrillRound round, long tenantId, long drillPlanId, long operatorId) {
        jdbc.update("INSERT INTO rel_release_drill_round (id, tenant_id, project_id, drill_plan_id, round_no, round_name, planned_at, status, result_content, row_version, created_by, updated_by) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)", round.id(), tenantId, round.projectId(), drillPlanId, round.roundNo(), round.roundName(), timestamp(round.plannedAt()), round.status().name(), round.resultContent(), operatorId, operatorId);
    }

    public boolean updateDrillRound(DrillRound round, long tenantId, long expectedVersion, long operatorId) {
        return jdbc.update("UPDATE rel_release_drill_round SET round_name = ?, planned_at = ?, status = ?, result_content = ?, updated_by = ?, row_version = row_version + 1 "
                + "WHERE id = ? AND tenant_id = ? AND project_id = ? AND deleted = 0 AND row_version = ?", round.roundName(), timestamp(round.plannedAt()), round.status().name(), round.resultContent(), operatorId, round.id(), tenantId, round.projectId(), expectedVersion) == 1;
    }

    public boolean deleteDrillRound(long id, long tenantId, long projectId, long expectedVersion, long operatorId) {
        return jdbc.update("UPDATE rel_release_drill_round SET deleted = 1, updated_by = ?, row_version = row_version + 1 WHERE id = ? AND tenant_id = ? AND project_id = ? AND deleted = 0 AND row_version = ?", operatorId, id, tenantId, projectId, expectedVersion) == 1;
    }

    public Optional<Timeline> findTimeline(long tenantId, long projectId, TimelineType type) {
        return first(jdbc.query("SELECT id, project_id, timeline_type, timeline_name, description, row_version, updated_at FROM rel_release_timeline "
                + "WHERE tenant_id = ? AND project_id = ? AND timeline_type = ? AND deleted = 0", TIMELINE, tenantId, projectId, type.name()));
    }

    public void insertTimeline(Timeline timeline, long tenantId, long operatorId) {
        jdbc.update("INSERT INTO rel_release_timeline (id, tenant_id, project_id, timeline_type, timeline_name, description, row_version, created_by, updated_by) VALUES (?, ?, ?, ?, ?, ?, 0, ?, ?)",
                timeline.id(), tenantId, timeline.projectId(), timeline.timelineType().name(), timeline.timelineName(), timeline.description(), operatorId, operatorId);
    }

    public boolean updateTimeline(Timeline timeline, long tenantId, long expectedVersion, long operatorId) {
        return jdbc.update("UPDATE rel_release_timeline SET timeline_name = ?, description = ?, updated_by = ?, row_version = row_version + 1 WHERE id = ? AND tenant_id = ? AND project_id = ? AND deleted = 0 AND row_version = ?",
                timeline.timelineName(), timeline.description(), operatorId, timeline.id(), tenantId, timeline.projectId(), expectedVersion) == 1;
    }

    public List<TimelineItem> findTimelineItems(long tenantId, long projectId, long timelineId) {
        return jdbc.query("SELECT id, project_id, seq_no, item_name, planned_start, planned_end, owner_id, owner_name, status, description, row_version, updated_at "
                + "FROM rel_release_timeline_item WHERE tenant_id = ? AND project_id = ? AND timeline_id = ? AND deleted = 0 ORDER BY seq_no, id", TIMELINE_ITEM, tenantId, projectId, timelineId);
    }

    public Optional<TimelineItem> findTimelineItem(long id, long tenantId, long projectId, long timelineId) {
        return first(jdbc.query("SELECT id, project_id, seq_no, item_name, planned_start, planned_end, owner_id, owner_name, status, description, row_version, updated_at "
                + "FROM rel_release_timeline_item WHERE id = ? AND tenant_id = ? AND project_id = ? AND timeline_id = ? AND deleted = 0 FOR UPDATE", TIMELINE_ITEM, id, tenantId, projectId, timelineId));
    }

    public void insertTimelineItem(TimelineItem item, long tenantId, long timelineId, long operatorId) {
        jdbc.update("INSERT INTO rel_release_timeline_item (id, tenant_id, project_id, timeline_id, seq_no, item_name, planned_start, planned_end, owner_id, owner_name, status, description, row_version, created_by, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)",
                item.id(), tenantId, item.projectId(), timelineId, item.seqNo(), item.itemName(), timestamp(item.plannedStart()), timestamp(item.plannedEnd()), item.ownerId(), item.ownerName(), item.status(), item.description(), operatorId, operatorId);
    }

    public boolean updateTimelineItem(TimelineItem item, long tenantId, long timelineId, long expectedVersion, long operatorId) {
        return jdbc.update("UPDATE rel_release_timeline_item SET seq_no = ?, item_name = ?, planned_start = ?, planned_end = ?, owner_id = ?, owner_name = ?, status = ?, description = ?, updated_by = ?, row_version = row_version + 1 WHERE id = ? AND tenant_id = ? AND project_id = ? AND timeline_id = ? AND deleted = 0 AND row_version = ?",
                item.seqNo(), item.itemName(), timestamp(item.plannedStart()), timestamp(item.plannedEnd()), item.ownerId(), item.ownerName(), item.status(), item.description(), operatorId, item.id(), tenantId, item.projectId(), timelineId, expectedVersion) == 1;
    }

    public boolean deleteTimelineItem(long id, long tenantId, long projectId, long timelineId, long expectedVersion, long operatorId) {
        return jdbc.update("UPDATE rel_release_timeline_item SET deleted = 1, updated_by = ?, row_version = row_version + 1 WHERE id = ? AND tenant_id = ? AND project_id = ? AND timeline_id = ? AND deleted = 0 AND row_version = ?", operatorId, id, tenantId, projectId, timelineId, expectedVersion) == 1;
    }

    public PageResult<Issue> findIssues(long tenantId, long projectId, String keyword, String priority, String status, PageQuery page) {
        StringBuilder where = new StringBuilder(" FROM rel_release_issue i LEFT JOIN rel_release_drill_round r ON r.id = i.drill_round_id AND r.tenant_id = i.tenant_id AND r.project_id = i.project_id AND r.deleted = 0 WHERE i.tenant_id = ? AND i.project_id = ? AND i.deleted = 0");
        List<Object> args = new ArrayList<>(List.of(tenantId, projectId));
        if (keyword != null && !keyword.isBlank()) { where.append(" AND (i.issue_no LIKE ? OR i.issue_title LIKE ?)"); String value = "%" + keyword.trim() + "%"; args.add(value); args.add(value); }
        if (priority != null && !priority.isBlank()) { where.append(" AND i.priority = ?"); args.add(priority.trim().toUpperCase()); }
        if (status != null && !status.isBlank()) { where.append(" AND i.issue_status = ?"); args.add(status.trim().toUpperCase()); }
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + where, Long.class, args.toArray());
        List<Object> queryArgs = new ArrayList<>(args); queryArgs.add(page.size()); queryArgs.add((page.page() - 1) * page.size());
        return new PageResult<>(jdbc.query("SELECT i.id, i.project_id, i.issue_no, i.issue_title, i.priority, i.issue_status, i.discovered_at, i.owner_id, i.owner_name, i.issue_description, i.analysis_content, i.action_content, i.follow_up_content, i.closed_at, i.drill_round_id, r.round_name AS drill_round_name, i.row_version, i.updated_at" + where + " ORDER BY i.updated_at DESC, i.id DESC LIMIT ? OFFSET ?", ISSUE, queryArgs.toArray()), total == null ? 0 : total, page.page(), page.size());
    }

    public Optional<Issue> findIssue(long id, long tenantId, long projectId) {
        return first(jdbc.query("SELECT i.id, i.project_id, i.issue_no, i.issue_title, i.priority, i.issue_status, i.discovered_at, i.owner_id, i.owner_name, i.issue_description, i.analysis_content, i.action_content, i.follow_up_content, i.closed_at, i.drill_round_id, r.round_name AS drill_round_name, i.row_version, i.updated_at FROM rel_release_issue i LEFT JOIN rel_release_drill_round r ON r.id = i.drill_round_id AND r.tenant_id = i.tenant_id AND r.project_id = i.project_id AND r.deleted = 0 WHERE i.id = ? AND i.tenant_id = ? AND i.project_id = ? AND i.deleted = 0 FOR UPDATE", ISSUE, id, tenantId, projectId));
    }

    public void insertIssue(Issue issue, long tenantId, long operatorId) {
        jdbc.update("INSERT INTO rel_release_issue (id, tenant_id, project_id, drill_round_id, issue_no, issue_title, priority, issue_status, discovered_at, owner_id, owner_name, issue_description, analysis_content, action_content, follow_up_content, closed_at, row_version, created_by, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)",
                issue.id(), tenantId, issue.projectId(), issue.drillRoundId(), issue.issueNo(), issue.issueTitle(), issue.priority().name(), issue.issueStatus().name(), timestamp(issue.discoveredAt()), issue.ownerId(), issue.ownerName(), issue.issueDescription(), issue.analysisContent(), issue.actionContent(), issue.followUpContent(), timestamp(issue.closedAt()), operatorId, operatorId);
    }

    public boolean updateIssue(Issue issue, long tenantId, long expectedVersion, long operatorId) {
        return jdbc.update("UPDATE rel_release_issue SET drill_round_id = ?, issue_no = ?, issue_title = ?, priority = ?, issue_status = ?, discovered_at = ?, owner_id = ?, owner_name = ?, issue_description = ?, analysis_content = ?, action_content = ?, follow_up_content = ?, closed_at = ?, updated_by = ?, row_version = row_version + 1 WHERE id = ? AND tenant_id = ? AND project_id = ? AND deleted = 0 AND row_version = ?",
                issue.drillRoundId(), issue.issueNo(), issue.issueTitle(), issue.priority().name(), issue.issueStatus().name(), timestamp(issue.discoveredAt()), issue.ownerId(), issue.ownerName(), issue.issueDescription(), issue.analysisContent(), issue.actionContent(), issue.followUpContent(), timestamp(issue.closedAt()), operatorId, issue.id(), tenantId, issue.projectId(), expectedVersion) == 1;
    }

    public boolean deleteIssue(long id, long tenantId, long projectId, long expectedVersion, long operatorId) {
        return jdbc.update("UPDATE rel_release_issue SET deleted = 1, updated_by = ?, row_version = row_version + 1 WHERE id = ? AND tenant_id = ? AND project_id = ? AND deleted = 0 AND row_version = ?", operatorId, id, tenantId, projectId, expectedVersion) == 1;
    }

    public List<Group> findGroups(long tenantId, long projectId) {
        return jdbc.query("SELECT id, project_id, group_name, description, row_version, updated_at FROM rel_release_group WHERE tenant_id = ? AND project_id = ? AND deleted = 0 ORDER BY group_name, id", GROUP, tenantId, projectId);
    }

    public Optional<Group> findGroup(long id, long tenantId, long projectId) {
        return first(jdbc.query("SELECT id, project_id, group_name, description, row_version, updated_at FROM rel_release_group WHERE id = ? AND tenant_id = ? AND project_id = ? AND deleted = 0 FOR UPDATE", GROUP, id, tenantId, projectId));
    }

    public void insertGroup(Group group, long tenantId, long operatorId) {
        jdbc.update("INSERT INTO rel_release_group (id, tenant_id, project_id, group_name, description, row_version, created_by, updated_by) VALUES (?, ?, ?, ?, ?, 0, ?, ?)", group.id(), tenantId, group.projectId(), group.groupName(), group.description(), operatorId, operatorId);
    }

    public boolean updateGroup(Group group, long tenantId, long expectedVersion, long operatorId) {
        return jdbc.update("UPDATE rel_release_group SET group_name = ?, description = ?, updated_by = ?, row_version = row_version + 1 WHERE id = ? AND tenant_id = ? AND project_id = ? AND deleted = 0 AND row_version = ?", group.groupName(), group.description(), operatorId, group.id(), tenantId, group.projectId(), expectedVersion) == 1;
    }

    public boolean deleteGroup(long id, long tenantId, long projectId, long expectedVersion, long operatorId) {
        return jdbc.update("UPDATE rel_release_group SET deleted = 1, updated_by = ?, row_version = row_version + 1 WHERE id = ? AND tenant_id = ? AND project_id = ? AND deleted = 0 AND row_version = ?", operatorId, id, tenantId, projectId, expectedVersion) == 1;
    }

    public List<GroupMember> findGroupMembers(long tenantId, long projectId, long groupId) {
        return jdbc.query("SELECT id, group_id, project_member_id, user_id, member_name, created_at FROM rel_release_group_member WHERE tenant_id = ? AND project_id = ? AND group_id = ? AND deleted = 0 ORDER BY member_name, id", GROUP_MEMBER, tenantId, projectId, groupId);
    }

    public boolean groupMemberExists(long tenantId, long projectId, long groupId, long projectMemberId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM rel_release_group_member WHERE tenant_id = ? AND project_id = ? AND group_id = ? AND project_member_id = ? AND deleted = 0", Integer.class, tenantId, projectId, groupId, projectMemberId);
        return count != null && count > 0;
    }

    public void insertGroupMember(GroupMember member, long tenantId, long projectId, long operatorId) {
        jdbc.update("INSERT INTO rel_release_group_member (id, tenant_id, project_id, group_id, project_member_id, user_id, member_name, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", member.id(), tenantId, projectId, member.groupId(), member.projectMemberId(), member.userId(), member.memberName(), operatorId);
    }

    public boolean deleteGroupMember(long tenantId, long projectId, long groupId, long projectMemberId) {
        return jdbc.update("UPDATE rel_release_group_member SET deleted = 1 WHERE tenant_id = ? AND project_id = ? AND group_id = ? AND project_member_id = ? AND deleted = 0", tenantId, projectId, groupId, projectMemberId) == 1;
    }

    public List<ReleasePlan> findReleasePlans(long tenantId, long projectId) {
        return jdbc.query("SELECT id, tenant_id, project_id, plan_name, plan_code, description, version_no, status, normal_timeline_name, rollback_timeline_name, row_version, updated_at FROM rel_release_plan WHERE tenant_id = ? AND project_id = ? AND deleted = 0 ORDER BY plan_name, id", RELEASE_PLAN, tenantId, projectId);
    }

    public Optional<ReleasePlan> findReleasePlan(long id, long tenantId, long projectId) {
        return first(jdbc.query("SELECT id, tenant_id, project_id, plan_name, plan_code, description, version_no, status, normal_timeline_name, rollback_timeline_name, row_version, updated_at FROM rel_release_plan WHERE id = ? AND tenant_id = ? AND project_id = ? AND deleted = 0 FOR UPDATE", RELEASE_PLAN, id, tenantId, projectId));
    }

    public void insertReleasePlan(ReleasePlan value, long operatorId) {
        jdbc.update("INSERT INTO rel_release_plan (id, tenant_id, project_id, plan_name, plan_code, description, version_no, status, normal_timeline_name, rollback_timeline_name, row_version, created_by, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)", value.id(), value.tenantId(), value.projectId(), value.planName(), value.planCode(), value.description(), value.versionNo(), value.status(), value.normalTimelineName(), value.rollbackTimelineName(), operatorId, operatorId);
    }

    public boolean updateReleasePlan(ReleasePlan value, long tenantId, long expectedVersion, long operatorId) {
        return jdbc.update("UPDATE rel_release_plan SET plan_name = ?, plan_code = ?, description = ?, version_no = ?, status = ?, normal_timeline_name = ?, rollback_timeline_name = ?, updated_by = ?, row_version = row_version + 1 WHERE id = ? AND tenant_id = ? AND project_id = ? AND deleted = 0 AND row_version = ?", value.planName(), value.planCode(), value.description(), value.versionNo(), value.status(), value.normalTimelineName(), value.rollbackTimelineName(), operatorId, value.id(), tenantId, value.projectId(), expectedVersion) == 1;
    }

    public boolean deleteReleasePlan(long id, long tenantId, long projectId, long expectedVersion, long operatorId) {
        return jdbc.update("UPDATE rel_release_plan SET deleted = 1, updated_by = ?, row_version = row_version + 1 WHERE id = ? AND tenant_id = ? AND project_id = ? AND deleted = 0 AND row_version = ?", operatorId, id, tenantId, projectId, expectedVersion) == 1;
    }

    public List<PlanTimeline> findPlanTimelines(long tenantId, long projectId, long planId, PlanItemType type) {
        return jdbc.query("SELECT id, project_id, plan_id, item_type, seq_no, timeline_name, description, row_version, updated_at FROM rel_release_plan_timeline WHERE tenant_id = ? AND project_id = ? AND plan_id = ? AND item_type = ? AND deleted = 0 ORDER BY seq_no, id", PLAN_TIMELINE, tenantId, projectId, planId, type.name());
    }

    public Optional<PlanTimeline> findPlanTimeline(long id, long tenantId, long projectId, long planId, PlanItemType type) {
        return first(jdbc.query("SELECT id, project_id, plan_id, item_type, seq_no, timeline_name, description, row_version, updated_at FROM rel_release_plan_timeline WHERE id = ? AND tenant_id = ? AND project_id = ? AND plan_id = ? AND item_type = ? AND deleted = 0 FOR UPDATE", PLAN_TIMELINE, id, tenantId, projectId, planId, type.name()));
    }

    public void insertPlanTimeline(PlanTimeline value, long tenantId, long operatorId) {
        jdbc.update("INSERT INTO rel_release_plan_timeline (id, tenant_id, project_id, plan_id, item_type, seq_no, timeline_name, description, row_version, created_by, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)", value.id(), tenantId, value.projectId(), value.planId(), value.itemType().name(), value.seqNo(), value.timelineName(), value.description(), operatorId, operatorId);
    }

    public boolean updatePlanTimeline(PlanTimeline value, long tenantId, long expectedVersion, long operatorId) {
        return jdbc.update("UPDATE rel_release_plan_timeline SET seq_no = ?, timeline_name = ?, description = ?, updated_by = ?, row_version = row_version + 1 WHERE id = ? AND tenant_id = ? AND project_id = ? AND plan_id = ? AND item_type = ? AND deleted = 0 AND row_version = ?", value.seqNo(), value.timelineName(), value.description(), operatorId, value.id(), tenantId, value.projectId(), value.planId(), value.itemType().name(), expectedVersion) == 1;
    }

    public boolean deletePlanTimeline(long id, long tenantId, long projectId, long planId, PlanItemType type, long expectedVersion, long operatorId) {
        return jdbc.update("UPDATE rel_release_plan_timeline SET deleted = 1, updated_by = ?, row_version = row_version + 1 WHERE id = ? AND tenant_id = ? AND project_id = ? AND plan_id = ? AND item_type = ? AND deleted = 0 AND row_version = ?", operatorId, id, tenantId, projectId, planId, type.name(), expectedVersion) == 1;
    }

    public int deletePlanItemsByTimeline(long tenantId, long projectId, long planId, PlanItemType type, long timelineId, long operatorId) {
        return jdbc.update("UPDATE rel_release_plan_item SET deleted = 1, updated_by = ?, row_version = row_version + 1 WHERE tenant_id = ? AND project_id = ? AND plan_id = ? AND item_type = ? AND timeline_id = ? AND deleted = 0", operatorId, tenantId, projectId, planId, type.name(), timelineId);
    }

    public List<PlanItem> findPlanItems(long tenantId, long projectId, long planId, PlanItemType type) {
        return jdbc.query("SELECT i.id, i.project_id, i.plan_id, i.item_type, i.seq_no, i.item_name, i.planned_start, i.planned_end, i.owner_id, i.owner_name, i.status, i.description, i.row_version, i.updated_at FROM rel_release_plan_item i JOIN rel_release_plan_timeline t ON t.id = i.timeline_id AND t.tenant_id = i.tenant_id AND t.project_id = i.project_id AND t.plan_id = i.plan_id AND t.item_type = i.item_type AND t.deleted = 0 WHERE i.tenant_id = ? AND i.project_id = ? AND i.plan_id = ? AND i.item_type = ? AND i.deleted = 0 ORDER BY t.seq_no, i.seq_no, i.id", PLAN_ITEM, tenantId, projectId, planId, type.name());
    }

    public List<PlanItem> findPlanItems(long tenantId, long projectId, long planId, PlanItemType type, long timelineId) {
        return jdbc.query("SELECT id, project_id, plan_id, item_type, seq_no, item_name, planned_start, planned_end, owner_id, owner_name, status, description, row_version, updated_at FROM rel_release_plan_item WHERE tenant_id = ? AND project_id = ? AND plan_id = ? AND item_type = ? AND timeline_id = ? AND deleted = 0 ORDER BY seq_no, id", PLAN_ITEM, tenantId, projectId, planId, type.name(), timelineId);
    }

    public Optional<PlanItem> findPlanItem(long id, long tenantId, long projectId, long planId, PlanItemType type, long timelineId) {
        return first(jdbc.query("SELECT id, project_id, plan_id, item_type, seq_no, item_name, planned_start, planned_end, owner_id, owner_name, status, description, row_version, updated_at FROM rel_release_plan_item WHERE id = ? AND tenant_id = ? AND project_id = ? AND plan_id = ? AND item_type = ? AND timeline_id = ? AND deleted = 0 FOR UPDATE", PLAN_ITEM, id, tenantId, projectId, planId, type.name(), timelineId));
    }

    public void insertPlanItem(PlanItem value, long tenantId, long timelineId, long operatorId) {
        jdbc.update("INSERT INTO rel_release_plan_item (id, tenant_id, project_id, plan_id, timeline_id, item_type, seq_no, item_name, planned_start, planned_end, owner_id, owner_name, status, description, row_version, created_by, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)", value.id(), tenantId, value.projectId(), value.planId(), timelineId, value.itemType().name(), value.seqNo(), value.itemName(), timestamp(value.plannedStart()), timestamp(value.plannedEnd()), value.ownerId(), value.ownerName(), value.status(), value.description(), operatorId, operatorId);
    }

    public boolean updatePlanItem(PlanItem value, long tenantId, long timelineId, long expectedVersion, long operatorId) {
        return jdbc.update("UPDATE rel_release_plan_item SET seq_no = ?, item_name = ?, planned_start = ?, planned_end = ?, owner_id = ?, owner_name = ?, status = ?, description = ?, updated_by = ?, row_version = row_version + 1 WHERE id = ? AND tenant_id = ? AND project_id = ? AND plan_id = ? AND item_type = ? AND timeline_id = ? AND deleted = 0 AND row_version = ?", value.seqNo(), value.itemName(), timestamp(value.plannedStart()), timestamp(value.plannedEnd()), value.ownerId(), value.ownerName(), value.status(), value.description(), operatorId, value.id(), tenantId, value.projectId(), value.planId(), value.itemType().name(), timelineId, expectedVersion) == 1;
    }

    public boolean deletePlanItem(long id, long tenantId, long projectId, long planId, PlanItemType type, long timelineId, long expectedVersion, long operatorId) {
        return jdbc.update("UPDATE rel_release_plan_item SET deleted = 1, updated_by = ?, row_version = row_version + 1 WHERE id = ? AND tenant_id = ? AND project_id = ? AND plan_id = ? AND item_type = ? AND timeline_id = ? AND deleted = 0 AND row_version = ?", operatorId, id, tenantId, projectId, planId, type.name(), timelineId, expectedVersion) == 1;
    }

    public List<DrillEnvironment> findDrillEnvironments(long tenantId, long projectId) {
        return jdbc.query("SELECT id, tenant_id, project_id, environment_name, description, carry_data_line_environment, infrastructure_deployment, hardware_check, network_opening, middleware_check, component_check, database_check, row_version, updated_at FROM rel_release_drill_environment WHERE tenant_id = ? AND project_id = ? AND deleted = 0 ORDER BY environment_name, id", DRILL_ENVIRONMENT, tenantId, projectId);
    }

    public Optional<DrillEnvironment> findDrillEnvironment(long id, long tenantId, long projectId) {
        return first(jdbc.query("SELECT id, tenant_id, project_id, environment_name, description, carry_data_line_environment, infrastructure_deployment, hardware_check, network_opening, middleware_check, component_check, database_check, row_version, updated_at FROM rel_release_drill_environment WHERE id = ? AND tenant_id = ? AND project_id = ? AND deleted = 0 FOR UPDATE", DRILL_ENVIRONMENT, id, tenantId, projectId));
    }

    public void insertDrillEnvironment(DrillEnvironment value, long operatorId) {
        jdbc.update("INSERT INTO rel_release_drill_environment (id, tenant_id, project_id, environment_name, description, carry_data_line_environment, infrastructure_deployment, hardware_check, network_opening, middleware_check, component_check, database_check, row_version, created_by, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)", value.id(), value.tenantId(), value.projectId(), value.environmentName(), value.description(), value.carryDataLineEnvironment(), value.infrastructureDeployment(), value.hardwareCheck(), value.networkOpening(), value.middlewareCheck(), value.componentCheck(), value.databaseCheck(), operatorId, operatorId);
    }

    public boolean updateDrillEnvironment(DrillEnvironment value, long tenantId, long expectedVersion, long operatorId) {
        return jdbc.update("UPDATE rel_release_drill_environment SET environment_name = ?, description = ?, carry_data_line_environment = ?, infrastructure_deployment = ?, hardware_check = ?, network_opening = ?, middleware_check = ?, component_check = ?, database_check = ?, updated_by = ?, row_version = row_version + 1 WHERE id = ? AND tenant_id = ? AND project_id = ? AND deleted = 0 AND row_version = ?", value.environmentName(), value.description(), value.carryDataLineEnvironment(), value.infrastructureDeployment(), value.hardwareCheck(), value.networkOpening(), value.middlewareCheck(), value.componentCheck(), value.databaseCheck(), operatorId, value.id(), tenantId, value.projectId(), expectedVersion) == 1;
    }

    public boolean deleteDrillEnvironment(long id, long tenantId, long projectId, long expectedVersion, long operatorId) {
        return jdbc.update("UPDATE rel_release_drill_environment SET deleted = 1, updated_by = ?, row_version = row_version + 1 WHERE id = ? AND tenant_id = ? AND project_id = ? AND deleted = 0 AND row_version = ?", operatorId, id, tenantId, projectId, expectedVersion) == 1;
    }

    public List<ReleaseDrillRound> findReleaseDrillRounds(long tenantId, long projectId) {
        return jdbc.query("SELECT r.id, r.project_id, r.round_no, r.round_name, r.planned_at, r.status, r.result_content, r.release_plan_id, p.plan_name, r.environment_id, e.environment_name, r.row_version, r.updated_at FROM rel_release_drill_round r JOIN rel_release_plan p ON p.id = r.release_plan_id AND p.tenant_id = r.tenant_id AND p.project_id = r.project_id AND p.deleted = 0 JOIN rel_release_drill_environment e ON e.id = r.environment_id AND e.tenant_id = r.tenant_id AND e.project_id = r.project_id AND e.deleted = 0 WHERE r.tenant_id = ? AND r.project_id = ? AND r.deleted = 0 ORDER BY r.round_no, r.id", RELEASE_DRILL_ROUND, tenantId, projectId);
    }

    public Optional<ReleaseDrillRound> findReleaseDrillRound(long id, long tenantId, long projectId) {
        return first(jdbc.query("SELECT r.id, r.project_id, r.round_no, r.round_name, r.planned_at, r.status, r.result_content, r.release_plan_id, p.plan_name, r.environment_id, e.environment_name, r.row_version, r.updated_at FROM rel_release_drill_round r JOIN rel_release_plan p ON p.id = r.release_plan_id AND p.tenant_id = r.tenant_id AND p.project_id = r.project_id AND p.deleted = 0 JOIN rel_release_drill_environment e ON e.id = r.environment_id AND e.tenant_id = r.tenant_id AND e.project_id = r.project_id AND e.deleted = 0 WHERE r.id = ? AND r.tenant_id = ? AND r.project_id = ? AND r.deleted = 0 FOR UPDATE", RELEASE_DRILL_ROUND, id, tenantId, projectId));
    }

    public int nextReleaseRoundNo(long tenantId, long projectId) {
        Integer value = jdbc.queryForObject("SELECT COALESCE(MAX(round_no), 0) + 1 FROM rel_release_drill_round WHERE tenant_id = ? AND project_id = ? AND deleted = 0", Integer.class, tenantId, projectId);
        return value == null ? 1 : value;
    }

    public void insertReleaseDrillRound(ReleaseDrillRound value, long tenantId, long operatorId) {
        jdbc.update("INSERT INTO rel_release_drill_round (id, tenant_id, project_id, drill_plan_id, release_plan_id, environment_id, round_no, round_name, planned_at, status, result_content, row_version, created_by, updated_by) VALUES (?, ?, ?, NULL, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)", value.id(), tenantId, value.projectId(), value.releasePlanId(), value.environmentId(), value.roundNo(), value.roundName(), timestamp(value.plannedAt()), value.status().name(), value.resultContent(), operatorId, operatorId);
    }

    public boolean updateReleaseDrillRound(ReleaseDrillRound value, long tenantId, long expectedVersion, long operatorId) {
        return jdbc.update("UPDATE rel_release_drill_round SET release_plan_id = ?, environment_id = ?, round_name = ?, planned_at = ?, status = ?, result_content = ?, updated_by = ?, row_version = row_version + 1 WHERE id = ? AND tenant_id = ? AND project_id = ? AND deleted = 0 AND row_version = ?", value.releasePlanId(), value.environmentId(), value.roundName(), timestamp(value.plannedAt()), value.status().name(), value.resultContent(), operatorId, value.id(), tenantId, value.projectId(), expectedVersion) == 1;
    }

    public boolean deleteReleaseDrillRound(long id, long tenantId, long projectId, long expectedVersion, long operatorId) {
        return jdbc.update("UPDATE rel_release_drill_round SET deleted = 1, updated_by = ?, row_version = row_version + 1 WHERE id = ? AND tenant_id = ? AND project_id = ? AND deleted = 0 AND row_version = ?", operatorId, id, tenantId, projectId, expectedVersion) == 1;
    }

    public List<DrillStep> findDrillSteps(long tenantId, long projectId, long roundId) {
        return jdbc.query("SELECT id, project_id, drill_round_id, seq_no, step_name, owner_id, owner_name, planned_start, planned_end, status, result_content, description, row_version, updated_at FROM rel_release_drill_step WHERE tenant_id = ? AND project_id = ? AND drill_round_id = ? AND deleted = 0 ORDER BY seq_no, id", DRILL_STEP, tenantId, projectId, roundId);
    }

    public Optional<DrillStep> findDrillStep(long id, long tenantId, long projectId, long roundId) {
        return first(jdbc.query("SELECT id, project_id, drill_round_id, seq_no, step_name, owner_id, owner_name, planned_start, planned_end, status, result_content, description, row_version, updated_at FROM rel_release_drill_step WHERE id = ? AND tenant_id = ? AND project_id = ? AND drill_round_id = ? AND deleted = 0 FOR UPDATE", DRILL_STEP, id, tenantId, projectId, roundId));
    }

    public void insertDrillStep(DrillStep value, long tenantId, long operatorId) {
        jdbc.update("INSERT INTO rel_release_drill_step (id, tenant_id, project_id, drill_round_id, seq_no, step_name, owner_id, owner_name, planned_start, planned_end, status, result_content, description, row_version, created_by, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)", value.id(), tenantId, value.projectId(), value.drillRoundId(), value.seqNo(), value.stepName(), value.ownerId(), value.ownerName(), timestamp(value.plannedStart()), timestamp(value.plannedEnd()), value.status(), value.resultContent(), value.description(), operatorId, operatorId);
    }

    public boolean updateDrillStep(DrillStep value, long tenantId, long expectedVersion, long operatorId) {
        return jdbc.update("UPDATE rel_release_drill_step SET seq_no = ?, step_name = ?, owner_id = ?, owner_name = ?, planned_start = ?, planned_end = ?, status = ?, result_content = ?, description = ?, updated_by = ?, row_version = row_version + 1 WHERE id = ? AND tenant_id = ? AND project_id = ? AND drill_round_id = ? AND deleted = 0 AND row_version = ?", value.seqNo(), value.stepName(), value.ownerId(), value.ownerName(), timestamp(value.plannedStart()), timestamp(value.plannedEnd()), value.status(), value.resultContent(), value.description(), operatorId, value.id(), tenantId, value.projectId(), value.drillRoundId(), expectedVersion) == 1;
    }

    public boolean deleteDrillStep(long id, long tenantId, long projectId, long roundId, long expectedVersion, long operatorId) {
        return jdbc.update("UPDATE rel_release_drill_step SET deleted = 1, updated_by = ?, row_version = row_version + 1 WHERE id = ? AND tenant_id = ? AND project_id = ? AND drill_round_id = ? AND deleted = 0 AND row_version = ?", operatorId, id, tenantId, projectId, roundId, expectedVersion) == 1;
    }

    private static Timestamp timestamp(LocalDateTime value) { return value == null ? null : Timestamp.valueOf(value); }
    private static <T> Optional<T> first(List<T> values) { return values.isEmpty() ? Optional.empty() : Optional.of(values.get(0)); }
    private static LocalDateTime dateTime(java.sql.ResultSet rs, String column) throws java.sql.SQLException { Timestamp value = rs.getTimestamp(column); return value == null ? null : value.toLocalDateTime(); }

    private static final RowMapper<DrillPlan> DRILL_PLAN = (rs, n) -> new DrillPlan(rs.getLong("id"), rs.getLong("tenant_id"), rs.getLong("project_id"), rs.getString("scenario_content"), rs.getString("environment_content"), rs.getLong("row_version"), dateTime(rs, "updated_at"), List.of());
    private static final RowMapper<DrillRound> DRILL_ROUND = (rs, n) -> new DrillRound(rs.getLong("id"), rs.getLong("project_id"), rs.getInt("round_no"), rs.getString("round_name"), dateTime(rs, "planned_at"), DrillStatus.valueOf(rs.getString("status")), rs.getString("result_content"), rs.getLong("row_version"), dateTime(rs, "updated_at"));
    private static final RowMapper<ReleasePlan> RELEASE_PLAN = (rs, n) -> new ReleasePlan(rs.getLong("id"), rs.getLong("tenant_id"), rs.getLong("project_id"), rs.getString("plan_name"), rs.getString("plan_code"), rs.getString("description"), rs.getString("version_no"), rs.getString("status"), rs.getString("normal_timeline_name"), rs.getString("rollback_timeline_name"), rs.getLong("row_version"), dateTime(rs, "updated_at"), List.of());
    private static final RowMapper<PlanTimeline> PLAN_TIMELINE = (rs, n) -> new PlanTimeline(rs.getLong("id"), rs.getLong("project_id"), rs.getLong("plan_id"), PlanItemType.valueOf(rs.getString("item_type")), rs.getInt("seq_no"), rs.getString("timeline_name"), rs.getString("description"), rs.getLong("row_version"), dateTime(rs, "updated_at"), List.of());
    private static final RowMapper<PlanItem> PLAN_ITEM = (rs, n) -> new PlanItem(rs.getLong("id"), rs.getLong("project_id"), rs.getLong("plan_id"), PlanItemType.valueOf(rs.getString("item_type")), rs.getInt("seq_no"), rs.getString("item_name"), dateTime(rs, "planned_start"), dateTime(rs, "planned_end"), (Long) rs.getObject("owner_id"), rs.getString("owner_name"), rs.getString("status"), rs.getString("description"), rs.getLong("row_version"), dateTime(rs, "updated_at"));
    private static final RowMapper<DrillEnvironment> DRILL_ENVIRONMENT = (rs, n) -> new DrillEnvironment(rs.getLong("id"), rs.getLong("tenant_id"), rs.getLong("project_id"), rs.getString("environment_name"), rs.getString("description"), rs.getString("carry_data_line_environment"), rs.getString("infrastructure_deployment"), rs.getString("hardware_check"), rs.getString("network_opening"), rs.getString("middleware_check"), rs.getString("component_check"), rs.getString("database_check"), rs.getLong("row_version"), dateTime(rs, "updated_at"));
    private static final RowMapper<DrillStep> DRILL_STEP = (rs, n) -> new DrillStep(rs.getLong("id"), rs.getLong("project_id"), rs.getLong("drill_round_id"), rs.getInt("seq_no"), rs.getString("step_name"), (Long) rs.getObject("owner_id"), rs.getString("owner_name"), dateTime(rs, "planned_start"), dateTime(rs, "planned_end"), rs.getString("status"), rs.getString("result_content"), rs.getString("description"), rs.getLong("row_version"), dateTime(rs, "updated_at"));
    private static final RowMapper<ReleaseDrillRound> RELEASE_DRILL_ROUND = (rs, n) -> new ReleaseDrillRound(rs.getLong("id"), rs.getLong("project_id"), rs.getInt("round_no"), rs.getString("round_name"), dateTime(rs, "planned_at"), DrillStatus.valueOf(rs.getString("status")), rs.getString("result_content"), rs.getLong("release_plan_id"), rs.getString("plan_name"), rs.getLong("environment_id"), rs.getString("environment_name"), rs.getLong("row_version"), dateTime(rs, "updated_at"), List.of());
    private static final RowMapper<Timeline> TIMELINE = (rs, n) -> new Timeline(rs.getLong("id"), rs.getLong("project_id"), TimelineType.valueOf(rs.getString("timeline_type")), rs.getString("timeline_name"), rs.getString("description"), rs.getLong("row_version"), dateTime(rs, "updated_at"), List.of());
    private static final RowMapper<TimelineItem> TIMELINE_ITEM = (rs, n) -> new TimelineItem(rs.getLong("id"), rs.getLong("project_id"), rs.getInt("seq_no"), rs.getString("item_name"), dateTime(rs, "planned_start"), dateTime(rs, "planned_end"), (Long) rs.getObject("owner_id"), rs.getString("owner_name"), rs.getString("status"), rs.getString("description"), rs.getLong("row_version"), dateTime(rs, "updated_at"));
    private static final RowMapper<Issue> ISSUE = (rs, n) -> new Issue(rs.getLong("id"), rs.getLong("project_id"), rs.getString("issue_no"), rs.getString("issue_title"), IssuePriority.valueOf(rs.getString("priority")), IssueStatus.valueOf(rs.getString("issue_status")), dateTime(rs, "discovered_at"), (Long) rs.getObject("owner_id"), rs.getString("owner_name"), rs.getString("issue_description"), rs.getString("analysis_content"), rs.getString("action_content"), rs.getString("follow_up_content"), dateTime(rs, "closed_at"), (Long) rs.getObject("drill_round_id"), rs.getString("drill_round_name"), rs.getLong("row_version"), dateTime(rs, "updated_at"));
    private static final RowMapper<Group> GROUP = (rs, n) -> new Group(rs.getLong("id"), rs.getLong("project_id"), rs.getString("group_name"), rs.getString("description"), rs.getLong("row_version"), dateTime(rs, "updated_at"), List.of());
    private static final RowMapper<GroupMember> GROUP_MEMBER = (rs, n) -> new GroupMember(rs.getLong("id"), rs.getLong("group_id"), rs.getLong("project_member_id"), rs.getLong("user_id"), rs.getString("member_name"), dateTime(rs, "created_at"));
}
