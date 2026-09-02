package com.ccb.release.operations.web;

import com.ccb.common.api.ApiResponse;
import com.ccb.common.api.PageQuery;
import com.ccb.common.trace.TraceId;
import com.ccb.release.operations.model.ReleaseOperationsModels.DrillPlan;
import com.ccb.release.operations.model.ReleaseOperationsModels.DrillPlanRequest;
import com.ccb.release.operations.model.ReleaseOperationsModels.DrillRound;
import com.ccb.release.operations.model.ReleaseOperationsModels.DrillRoundRequest;
import com.ccb.release.operations.model.ReleaseOperationsModels.Group;
import com.ccb.release.operations.model.ReleaseOperationsModels.GroupMember;
import com.ccb.release.operations.model.ReleaseOperationsModels.GroupRequest;
import com.ccb.release.operations.model.ReleaseOperationsModels.Issue;
import com.ccb.release.operations.model.ReleaseOperationsModels.IssueRequest;
import com.ccb.release.operations.model.ReleaseOperationsModels.MemberOption;
import com.ccb.release.operations.model.ReleaseOperationsModels.Timeline;
import com.ccb.release.operations.model.ReleaseOperationsModels.TimelineItem;
import com.ccb.release.operations.model.ReleaseOperationsModels.TimelineItemRequest;
import com.ccb.release.operations.model.ReleaseOperationsModels.TimelineRequest;
import com.ccb.release.operations.model.ReleaseOperationsModels.TimelineType;
import com.ccb.release.operations.service.ReleaseOperationsService;
import com.ccb.security.model.AuthUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/release/operations")
@PreAuthorize("hasAnyAuthority('release-operations:access','system:admin')")
public class ReleaseOperationsController {
    private final ReleaseOperationsService service;

    public ReleaseOperationsController(ReleaseOperationsService service) {
        this.service = service;
    }

    @GetMapping("/drill-plan")
    @PreAuthorize("hasAnyAuthority('release-operations:drill:view','system:admin')")
    public ApiResponse<DrillPlan> drillPlan(@RequestParam long projectId, @AuthenticationPrincipal AuthUser actor) {
        return ok(service.drillPlan(projectId, actor));
    }

    @PutMapping("/drill-plan")
    @PreAuthorize("hasAnyAuthority('release-operations:drill:manage','system:admin')")
    public ApiResponse<DrillPlan> saveDrillPlan(@RequestParam long projectId, @RequestBody DrillPlanRequest request, @AuthenticationPrincipal AuthUser actor) {
        return ok(service.saveDrillPlan(projectId, request, actor));
    }

    @PostMapping("/drill-plan/rounds")
    @PreAuthorize("hasAnyAuthority('release-operations:drill:manage','system:admin')")
    public ApiResponse<DrillRound> createDrillRound(@RequestParam long projectId, @RequestBody DrillRoundRequest request, @AuthenticationPrincipal AuthUser actor) {
        return ok(service.saveDrillRound(projectId, null, request, actor));
    }

    @PutMapping("/drill-plan/rounds/{roundId}")
    @PreAuthorize("hasAnyAuthority('release-operations:drill:manage','system:admin')")
    public ApiResponse<DrillRound> updateDrillRound(@RequestParam long projectId, @PathVariable long roundId, @RequestBody DrillRoundRequest request, @AuthenticationPrincipal AuthUser actor) {
        return ok(service.saveDrillRound(projectId, roundId, request, actor));
    }

    @DeleteMapping("/drill-plan/rounds/{roundId}")
    @PreAuthorize("hasAnyAuthority('release-operations:drill:manage','system:admin')")
    public ApiResponse<Void> deleteDrillRound(@RequestParam long projectId, @PathVariable long roundId, @RequestParam long rowVersion, @AuthenticationPrincipal AuthUser actor) {
        service.deleteDrillRound(projectId, roundId, rowVersion, actor);
        return ok(null);
    }

    @GetMapping("/timelines/{timelineType}")
    @PreAuthorize("hasAnyAuthority('release-operations:timeline:view','release-operations:rollback-timeline:view','system:admin')")
    public ApiResponse<Timeline> timeline(@RequestParam long projectId, @PathVariable String timelineType, @AuthenticationPrincipal AuthUser actor) {
        return ok(service.timeline(projectId, type(timelineType), actor));
    }

    @PutMapping("/timelines/{timelineType}")
    @PreAuthorize("hasAnyAuthority('release-operations:timeline:manage','release-operations:rollback-timeline:manage','system:admin')")
    public ApiResponse<Timeline> saveTimeline(@RequestParam long projectId, @PathVariable String timelineType, @RequestBody TimelineRequest request, @AuthenticationPrincipal AuthUser actor) {
        return ok(service.saveTimeline(projectId, type(timelineType), request, actor));
    }

    @PostMapping("/timelines/{timelineType}/items")
    @PreAuthorize("hasAnyAuthority('release-operations:timeline:manage','release-operations:rollback-timeline:manage','system:admin')")
    public ApiResponse<TimelineItem> createTimelineItem(@RequestParam long projectId, @PathVariable String timelineType, @RequestBody TimelineItemRequest request, @AuthenticationPrincipal AuthUser actor) {
        return ok(service.saveTimelineItem(projectId, type(timelineType), null, request, actor));
    }

    @PutMapping("/timelines/{timelineType}/items/{itemId}")
    @PreAuthorize("hasAnyAuthority('release-operations:timeline:manage','release-operations:rollback-timeline:manage','system:admin')")
    public ApiResponse<TimelineItem> updateTimelineItem(@RequestParam long projectId, @PathVariable String timelineType, @PathVariable long itemId, @RequestBody TimelineItemRequest request, @AuthenticationPrincipal AuthUser actor) {
        return ok(service.saveTimelineItem(projectId, type(timelineType), itemId, request, actor));
    }

    @DeleteMapping("/timelines/{timelineType}/items/{itemId}")
    @PreAuthorize("hasAnyAuthority('release-operations:timeline:manage','release-operations:rollback-timeline:manage','system:admin')")
    public ApiResponse<Void> deleteTimelineItem(@RequestParam long projectId, @PathVariable String timelineType, @PathVariable long itemId, @RequestParam long rowVersion, @AuthenticationPrincipal AuthUser actor) {
        service.deleteTimelineItem(projectId, type(timelineType), itemId, rowVersion, actor);
        return ok(null);
    }

    @GetMapping("/issues")
    @PreAuthorize("hasAnyAuthority('release-operations:issue:view','system:admin')")
    public ApiResponse<com.ccb.common.api.PageResult<Issue>> issues(@RequestParam long projectId, @RequestParam(required = false) String keyword, @RequestParam(required = false) String priority, @RequestParam(required = false) String status, @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "20") long size, @AuthenticationPrincipal AuthUser actor) {
        return ok(service.issues(projectId, keyword, priority, status, new PageQuery(page, size), actor));
    }

    @PostMapping("/issues")
    @PreAuthorize("hasAnyAuthority('release-operations:issue:manage','system:admin')")
    public ApiResponse<Issue> createIssue(@RequestParam long projectId, @RequestBody IssueRequest request, @AuthenticationPrincipal AuthUser actor) {
        return ok(service.saveIssue(projectId, null, request, actor));
    }

    @PutMapping("/issues/{issueId}")
    @PreAuthorize("hasAnyAuthority('release-operations:issue:manage','system:admin')")
    public ApiResponse<Issue> updateIssue(@RequestParam long projectId, @PathVariable long issueId, @RequestBody IssueRequest request, @AuthenticationPrincipal AuthUser actor) {
        return ok(service.saveIssue(projectId, issueId, request, actor));
    }

    @DeleteMapping("/issues/{issueId}")
    @PreAuthorize("hasAnyAuthority('release-operations:issue:manage','system:admin')")
    public ApiResponse<Void> deleteIssue(@RequestParam long projectId, @PathVariable long issueId, @RequestParam long rowVersion, @AuthenticationPrincipal AuthUser actor) {
        service.deleteIssue(projectId, issueId, rowVersion, actor);
        return ok(null);
    }

    @GetMapping("/groups")
    @PreAuthorize("hasAnyAuthority('release-operations:organization:view','system:admin')")
    public ApiResponse<List<Group>> groups(@RequestParam long projectId, @AuthenticationPrincipal AuthUser actor) {
        return ok(service.groups(projectId, actor));
    }

    @PostMapping("/groups")
    @PreAuthorize("hasAnyAuthority('release-operations:organization:manage','system:admin')")
    public ApiResponse<Group> createGroup(@RequestParam long projectId, @RequestBody GroupRequest request, @AuthenticationPrincipal AuthUser actor) {
        return ok(service.saveGroup(projectId, null, request, actor));
    }

    @PutMapping("/groups/{groupId}")
    @PreAuthorize("hasAnyAuthority('release-operations:organization:manage','system:admin')")
    public ApiResponse<Group> updateGroup(@RequestParam long projectId, @PathVariable long groupId, @RequestBody GroupRequest request, @AuthenticationPrincipal AuthUser actor) {
        return ok(service.saveGroup(projectId, groupId, request, actor));
    }

    @DeleteMapping("/groups/{groupId}")
    @PreAuthorize("hasAnyAuthority('release-operations:organization:manage','system:admin')")
    public ApiResponse<Void> deleteGroup(@RequestParam long projectId, @PathVariable long groupId, @RequestParam long rowVersion, @AuthenticationPrincipal AuthUser actor) {
        service.deleteGroup(projectId, groupId, rowVersion, actor);
        return ok(null);
    }

    @GetMapping("/groups/{groupId}/members")
    @PreAuthorize("hasAnyAuthority('release-operations:organization:view','system:admin')")
    public ApiResponse<List<GroupMember>> groupMembers(@RequestParam long projectId, @PathVariable long groupId, @AuthenticationPrincipal AuthUser actor) {
        return ok(service.groupMembers(projectId, groupId, actor));
    }

    @PostMapping("/groups/{groupId}/members")
    @PreAuthorize("hasAnyAuthority('release-operations:organization:manage','system:admin')")
    public ApiResponse<GroupMember> addGroupMember(@RequestParam long projectId, @PathVariable long groupId, @RequestParam long projectMemberId, @AuthenticationPrincipal AuthUser actor) {
        return ok(service.addGroupMember(projectId, groupId, projectMemberId, actor));
    }

    @DeleteMapping("/groups/{groupId}/members/{projectMemberId}")
    @PreAuthorize("hasAnyAuthority('release-operations:organization:manage','system:admin')")
    public ApiResponse<Void> deleteGroupMember(@RequestParam long projectId, @PathVariable long groupId, @PathVariable long projectMemberId, @AuthenticationPrincipal AuthUser actor) {
        service.deleteGroupMember(projectId, groupId, projectMemberId, actor);
        return ok(null);
    }

    @GetMapping("/members")
    @PreAuthorize("hasAnyAuthority('release-operations:organization:view','system:admin')")
    public ApiResponse<List<MemberOption>> memberOptions(@RequestParam long projectId, @AuthenticationPrincipal AuthUser actor) {
        return ok(service.memberOptions(projectId, actor));
    }

    private static TimelineType type(String value) {
        try { return TimelineType.valueOf(value == null ? "" : value.trim().toUpperCase()); }
        catch (IllegalArgumentException exception) { throw new com.ccb.common.exception.BusinessException(com.ccb.common.exception.ErrorCode.BAD_REQUEST, "时序类型无效"); }
    }

    private static <T> ApiResponse<T> ok(T value) { return ApiResponse.success(value, TraceId.getOrCreate()); }
}
