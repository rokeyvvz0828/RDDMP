package com.ccb.release.operations.service;

import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.release.operations.model.ReleaseOperationsModels.DrillPlan;
import com.ccb.release.operations.model.ReleaseOperationsModels.DrillPlanRequest;
import com.ccb.release.operations.model.ReleaseOperationsModels.DrillRound;
import com.ccb.release.operations.model.ReleaseOperationsModels.DrillRoundRequest;
import com.ccb.release.operations.model.ReleaseOperationsModels.DrillStatus;
import com.ccb.release.operations.model.ReleaseOperationsModels.Group;
import com.ccb.release.operations.model.ReleaseOperationsModels.GroupMember;
import com.ccb.release.operations.model.ReleaseOperationsModels.GroupRequest;
import com.ccb.release.operations.model.ReleaseOperationsModels.Issue;
import com.ccb.release.operations.model.ReleaseOperationsModels.IssuePriority;
import com.ccb.release.operations.model.ReleaseOperationsModels.IssueRequest;
import com.ccb.release.operations.model.ReleaseOperationsModels.IssueStatus;
import com.ccb.release.operations.model.ReleaseOperationsModels.MemberOption;
import com.ccb.release.operations.model.ReleaseOperationsModels.Timeline;
import com.ccb.release.operations.model.ReleaseOperationsModels.TimelineItem;
import com.ccb.release.operations.model.ReleaseOperationsModels.TimelineItemRequest;
import com.ccb.release.operations.model.ReleaseOperationsModels.TimelineRequest;
import com.ccb.release.operations.model.ReleaseOperationsModels.TimelineType;
import com.ccb.release.operations.persistence.ReleaseOperationsStore;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.ProjectMemberReference;
import com.ccb.system.capability.ProjectMemberReferenceQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class ReleaseOperationsService {
    private final ReleaseOperationsStore store;
    private final ProjectMemberReferenceQuery projectMembers;

    public ReleaseOperationsService(ReleaseOperationsStore store, ProjectMemberReferenceQuery projectMembers) {
        this.store = store;
        this.projectMembers = projectMembers;
    }

    public DrillPlan drillPlan(long projectId, AuthUser actor) {
        requireProjectMember(projectId, actor);
        return store.findDrillPlan(actor.tenantId(), projectId)
                .map(plan -> withRounds(plan, actor)).orElse(null);
    }

    @Transactional
    public DrillPlan saveDrillPlan(long projectId, DrillPlanRequest request, AuthUser actor) {
        requireProjectMember(projectId, actor);
        if (request == null) throw badRequest("演练方案不能为空");
        String scenario = optional(request.scenarioContent(), 4000);
        String environment = optional(request.environmentContent(), 4000);
        DrillPlan current = store.findDrillPlan(actor.tenantId(), projectId).orElse(null);
        if (current == null) {
            if (request.rowVersion() != 0) throw conflict("演练方案已发生变化，请刷新后重试");
            store.insertDrillPlan(new DrillPlan(nextId(), actor.tenantId(), projectId, scenario, environment, 0, null, List.of()), actor.id());
        } else if (!store.updateDrillPlan(current.id(), actor.tenantId(), request.rowVersion(), scenario, environment, actor.id())) {
            throw conflict("演练方案已被其他人修改，请刷新后重试");
        }
        return drillPlan(projectId, actor);
    }

    @Transactional
    public DrillRound saveDrillRound(long projectId, Long roundId, DrillRoundRequest request, AuthUser actor) {
        requireProjectMember(projectId, actor);
        if (request == null) throw badRequest("演练轮次不能为空");
        DrillStatus status = enumValue(request.status(), DrillStatus.class, "演练状态");
        String name = required(request.roundName(), "轮次名称", 128);
        DrillRound current = roundId == null ? null : store.findDrillRound(roundId, actor.tenantId(), projectId).orElseThrow(() -> badRequest("演练轮次不存在"));
        if (current == null) {
            DrillPlan plan = store.findDrillPlan(actor.tenantId(), projectId).orElseThrow(() -> badRequest("请先保存演练方案"));
            int roundNo = store.nextRoundNo(actor.tenantId(), projectId);
            DrillRound value = new DrillRound(nextId(), projectId, roundNo, name, request.plannedAt(), status, optional(request.resultContent(), 4000), 0, null);
            store.insertDrillRound(value, actor.tenantId(), plan.id(), actor.id());
            return value;
        }
        if (current.rowVersion() != request.rowVersion()) throw conflict("演练轮次已被其他人修改，请刷新后重试");
        DrillRound value = new DrillRound(current.id(), projectId, current.roundNo(), name, request.plannedAt(), status, optional(request.resultContent(), 4000), current.rowVersion(), null);
        if (!store.updateDrillRound(value, actor.tenantId(), request.rowVersion(), actor.id())) throw conflict("演练轮次已被其他人修改，请刷新后重试");
        return value;
    }

    @Transactional
    public void deleteDrillRound(long projectId, long roundId, long rowVersion, AuthUser actor) {
        requireProjectMember(projectId, actor);
        if (!store.deleteDrillRound(roundId, actor.tenantId(), projectId, rowVersion, actor.id())) throw conflict("演练轮次已被其他人修改或不存在");
    }

    public Timeline timeline(long projectId, TimelineType type, AuthUser actor) {
        requireProjectMember(projectId, actor);
        return store.findTimeline(actor.tenantId(), projectId, type).map(value -> withItems(value, actor)).orElse(null);
    }

    @Transactional
    public Timeline saveTimeline(long projectId, TimelineType type, TimelineRequest request, AuthUser actor) {
        requireProjectMember(projectId, actor);
        if (request == null) throw badRequest("时序信息不能为空");
        Timeline current = store.findTimeline(actor.tenantId(), projectId, type).orElse(null);
        String name = required(request.timelineName(), "时序名称", 128);
        String description = optional(request.description(), 2000);
        if (current == null) {
            if (request.rowVersion() != 0) throw conflict("时序已发生变化，请刷新后重试");
            store.insertTimeline(new Timeline(nextId(), projectId, type, name, description, 0, null, List.of()), actor.tenantId(), actor.id());
        } else if (!store.updateTimeline(new Timeline(current.id(), projectId, type, name, description, current.rowVersion(), null, List.of()), actor.tenantId(), request.rowVersion(), actor.id())) {
            throw conflict("时序已被其他人修改，请刷新后重试");
        }
        return timeline(projectId, type, actor);
    }

    @Transactional
    public TimelineItem saveTimelineItem(long projectId, TimelineType type, Long itemId, TimelineItemRequest request, AuthUser actor) {
        requireProjectMember(projectId, actor);
        if (request == null) throw badRequest("时序明细不能为空");
        Timeline timeline = store.findTimeline(actor.tenantId(), projectId, type).orElseThrow(() -> badRequest("请先保存时序信息"));
        String name = required(request.itemName(), "时序节点名称", 128);
        int seqNo = request.seqNo() == null || request.seqNo() < 1 ? store.findTimelineItems(actor.tenantId(), projectId, timeline.id()).size() + 1 : request.seqNo();
        if (request.plannedStart() != null && request.plannedEnd() != null && request.plannedEnd().isBefore(request.plannedStart())) throw badRequest("计划结束时间不能早于开始时间");
        ProjectMemberReference owner = request.ownerId() == null ? null : findUserMember(projectId, request.ownerId(), actor);
        String status = optional(request.status(), 24);
        if (status == null) status = "PENDING";
        TimelineItem current = itemId == null ? null : store.findTimelineItem(itemId, actor.tenantId(), projectId, timeline.id()).orElseThrow(() -> badRequest("时序明细不存在"));
        TimelineItem value = new TimelineItem(current == null ? nextId() : current.id(), projectId, seqNo, name, request.plannedStart(), request.plannedEnd(), owner == null ? null : owner.userId(), owner == null ? null : owner.displayName(), status, optional(request.description(), 2000), current == null ? 0 : current.rowVersion(), null);
        if (current == null) store.insertTimelineItem(value, actor.tenantId(), timeline.id(), actor.id());
        else if (!store.updateTimelineItem(value, actor.tenantId(), timeline.id(), request.rowVersion(), actor.id())) throw conflict("时序明细已被其他人修改，请刷新后重试");
        return value;
    }

    @Transactional
    public void deleteTimelineItem(long projectId, TimelineType type, long itemId, long rowVersion, AuthUser actor) {
        requireProjectMember(projectId, actor);
        Timeline timeline = store.findTimeline(actor.tenantId(), projectId, type).orElseThrow(() -> badRequest("时序不存在"));
        if (!store.deleteTimelineItem(itemId, actor.tenantId(), projectId, timeline.id(), rowVersion, actor.id())) throw conflict("时序明细已被其他人修改或不存在");
    }

    public PageResult<Issue> issues(long projectId, String keyword, String priority, String status, PageQuery page, AuthUser actor) {
        requireProjectMember(projectId, actor);
        return store.findIssues(actor.tenantId(), projectId, keyword, enumName(priority, IssuePriority.class, "问题优先级"), enumName(status, IssueStatus.class, "问题状态"), page);
    }

    @Transactional
    public Issue saveIssue(long projectId, Long issueId, IssueRequest request, AuthUser actor) {
        requireProjectMember(projectId, actor);
        if (request == null) throw badRequest("问题信息不能为空");
        String no = required(request.issueNo(), "问题编号", 64);
        String title = required(request.issueTitle(), "问题标题", 256);
        IssuePriority priority = enumValue(request.priority(), IssuePriority.class, "问题优先级");
        IssueStatus status = enumValue(request.issueStatus(), IssueStatus.class, "问题状态");
        ProjectMemberReference owner = request.ownerId() == null ? null : findUserMember(projectId, request.ownerId(), actor);
        LocalDateTime closedAt = status == IssueStatus.CLOSED ? request.closedAt() : null;
        if (status == IssueStatus.CLOSED && closedAt == null) throw badRequest("问题关闭时必须填写关闭时间");
        Issue current = issueId == null ? null : store.findIssue(issueId, actor.tenantId(), projectId).orElseThrow(() -> badRequest("问题不存在"));
        Issue value = new Issue(current == null ? nextId() : current.id(), projectId, no, title, priority, status, request.discoveredAt(), owner == null ? null : owner.userId(), owner == null ? null : owner.displayName(), optional(request.issueDescription(), 4000), optional(request.analysisContent(), 4000), optional(request.actionContent(), 4000), optional(request.followUpContent(), 4000), closedAt, current == null ? 0 : current.rowVersion(), null);
        if (current == null) store.insertIssue(value, actor.tenantId(), actor.id());
        else if (!store.updateIssue(value, actor.tenantId(), request.rowVersion(), actor.id())) throw conflict("问题已被其他人修改，请刷新后重试");
        return value;
    }

    @Transactional
    public void deleteIssue(long projectId, long issueId, long rowVersion, AuthUser actor) {
        requireProjectMember(projectId, actor);
        if (!store.deleteIssue(issueId, actor.tenantId(), projectId, rowVersion, actor.id())) throw conflict("问题已被其他人修改或不存在");
    }

    public List<Group> groups(long projectId, AuthUser actor) {
        requireProjectMember(projectId, actor);
        return store.findGroups(actor.tenantId(), projectId).stream().map(value -> withMembers(value, actor)).toList();
    }

    @Transactional
    public Group saveGroup(long projectId, Long groupId, GroupRequest request, AuthUser actor) {
        requireProjectMember(projectId, actor);
        if (request == null) throw badRequest("投产组信息不能为空");
        String name = required(request.groupName(), "投产组名称", 128);
        String description = optional(request.description(), 1000);
        Group current = groupId == null ? null : store.findGroup(groupId, actor.tenantId(), projectId).orElseThrow(() -> badRequest("投产组不存在"));
        Group value = new Group(current == null ? nextId() : current.id(), projectId, name, description, current == null ? 0 : current.rowVersion(), null, List.of());
        if (current == null) store.insertGroup(value, actor.tenantId(), actor.id());
        else if (!store.updateGroup(value, actor.tenantId(), request.rowVersion(), actor.id())) throw conflict("投产组已被其他人修改，请刷新后重试");
        return store.findGroup(value.id(), actor.tenantId(), projectId).map(item -> withMembers(item, actor)).orElseThrow();
    }

    @Transactional
    public void deleteGroup(long projectId, long groupId, long rowVersion, AuthUser actor) {
        requireProjectMember(projectId, actor);
        if (!store.deleteGroup(groupId, actor.tenantId(), projectId, rowVersion, actor.id())) throw conflict("投产组已被其他人修改或不存在");
    }

    public List<GroupMember> groupMembers(long projectId, long groupId, AuthUser actor) {
        requireProjectMember(projectId, actor);
        ensureGroup(projectId, groupId, actor);
        return store.findGroupMembers(actor.tenantId(), projectId, groupId);
    }

    @Transactional
    public GroupMember addGroupMember(long projectId, long groupId, long projectMemberId, AuthUser actor) {
        requireProjectMember(projectId, actor);
        ensureGroup(projectId, groupId, actor);
        ProjectMemberReference member = projectMembers.findActiveMember(actor, projectId, projectMemberId).orElseThrow(() -> forbidden("只能添加当前项目有效成员"));
        if (store.groupMemberExists(actor.tenantId(), projectId, groupId, projectMemberId)) throw badRequest("该成员已经在投产组中");
        GroupMember value = new GroupMember(nextId(), groupId, member.id(), member.userId(), member.displayName(), null);
        store.insertGroupMember(value, actor.tenantId(), projectId, actor.id());
        return value;
    }

    @Transactional
    public void deleteGroupMember(long projectId, long groupId, long projectMemberId, AuthUser actor) {
        requireProjectMember(projectId, actor);
        ensureGroup(projectId, groupId, actor);
        if (!store.deleteGroupMember(actor.tenantId(), projectId, groupId, projectMemberId)) throw badRequest("投产组成员不存在");
    }

    public List<MemberOption> memberOptions(long projectId, AuthUser actor) {
        requireProjectMember(projectId, actor);
        return projectMembers.findActiveMembers(actor, projectId).stream().map(value -> new MemberOption(value.id(), value.userId(), value.displayName(), value.username())).toList();
    }

    private DrillPlan withRounds(DrillPlan plan, AuthUser actor) { return new DrillPlan(plan.id(), plan.tenantId(), plan.projectId(), plan.scenarioContent(), plan.environmentContent(), plan.rowVersion(), plan.updatedAt(), store.findDrillRounds(actor.tenantId(), plan.projectId())); }
    private Timeline withItems(Timeline value, AuthUser actor) { return new Timeline(value.id(), value.projectId(), value.timelineType(), value.timelineName(), value.description(), value.rowVersion(), value.updatedAt(), store.findTimelineItems(actor.tenantId(), value.projectId(), value.id())); }
    private Group withMembers(Group value, AuthUser actor) { return new Group(value.id(), value.projectId(), value.groupName(), value.description(), value.rowVersion(), value.updatedAt(), store.findGroupMembers(actor.tenantId(), value.projectId(), value.id())); }
    private void ensureGroup(long projectId, long groupId, AuthUser actor) { store.findGroup(groupId, actor.tenantId(), projectId).orElseThrow(() -> badRequest("投产组不存在")); }

    private void requireProjectMember(long projectId, AuthUser actor) {
        Objects.requireNonNull(actor, "认证主体不能为空");
        if (projectId <= 0) throw badRequest("项目编号无效");
        boolean allowed = projectMembers.findActiveMembers(actor, projectId).stream().anyMatch(value -> value.userId() == actor.id());
        if (!allowed) throw forbidden("没有该项目的操作权限");
    }

    private ProjectMemberReference findUserMember(long projectId, long userId, AuthUser actor) {
        return projectMembers.findActiveMembers(actor, projectId).stream().filter(value -> value.userId() == userId).findFirst().orElseThrow(() -> forbidden("负责人必须是当前项目有效成员"));
    }

    private static <E extends Enum<E>> E enumValue(String value, Class<E> type, String label) { String normalized = required(value, label, 24).toUpperCase(Locale.ROOT); try { return Enum.valueOf(type, normalized); } catch (IllegalArgumentException exception) { throw badRequest(label + "无效"); } }
    private static <E extends Enum<E>> String enumName(String value, Class<E> type, String label) { return value == null || value.isBlank() ? null : enumValue(value, type, label).name(); }
    private static String required(String value, String label, int max) { String normalized = value == null || value.isBlank() ? null : value.trim(); if (normalized == null) throw badRequest(label + "不能为空"); if (normalized.length() > max) throw badRequest(label + "长度不能超过 " + max); return normalized; }
    private static String optional(String value, int max) { if (value == null || value.isBlank()) return null; String normalized = value.trim(); if (normalized.length() > max) throw badRequest("文本长度不能超过 " + max); return normalized; }
    private static BusinessException badRequest(String message) { return new BusinessException(ErrorCode.BAD_REQUEST, message); }
    private static BusinessException forbidden(String message) { return new BusinessException(ErrorCode.FORBIDDEN, message); }
    private static BusinessException conflict(String message) { return new BusinessException(ErrorCode.CONFLICT, message); }
    private static long nextId() { return System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000); }
}
