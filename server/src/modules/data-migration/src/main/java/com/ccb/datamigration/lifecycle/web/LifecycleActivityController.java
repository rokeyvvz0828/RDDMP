package com.ccb.datamigration.lifecycle.web;

import com.ccb.common.api.ApiResponse;
import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.common.trace.TraceId;
import com.ccb.datamigration.lifecycle.activity.ActivityService;
import com.ccb.datamigration.lifecycle.activity.ActivityTemplateService;
import com.ccb.datamigration.lifecycle.activity.ActivityTopologyService;
import com.ccb.datamigration.lifecycle.activity.model.ActivityView;
import com.ccb.datamigration.lifecycle.activity.model.ProcessView;
import com.ccb.datamigration.lifecycle.activity.model.TopologyInput;
import com.ccb.datamigration.lifecycle.activity.model.TopologyView;
import com.ccb.security.model.AuthUser;
import java.util.List;
import java.util.Map;
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

/** 活动管理接口（基线第 9 章）：活动 CRUD / 状态管控 / 工序拓扑 / 发布快照 / 专题聚合 / 模板导入导出（JSON）。 */
@RestController
@RequestMapping("/api/data-migration-lifecycle/activities")
@PreAuthorize("hasAnyAuthority('data-migration-lifecycle:access','data-migration:access','system:admin')")
public class LifecycleActivityController {
    private final ActivityService activityService;
    private final ActivityTopologyService topologyService;
    private final ActivityTemplateService templateService;

    public LifecycleActivityController(ActivityService activityService, ActivityTopologyService topologyService,
                                       ActivityTemplateService templateService) {
        this.activityService = activityService;
        this.topologyService = topologyService;
        this.templateService = templateService;
    }

    @GetMapping
    public ApiResponse<PageResult<ActivityView>> pageActivities(
            @RequestParam(required = false) String activityCode,
            @RequestParam(required = false) String activityName,
            @RequestParam(required = false) Long lifecycleStageId,
            @RequestParam(required = false) String granularity,
            @RequestParam(required = false) String activityStatus,
            @RequestParam(required = false) String createdFrom,
            @RequestParam(required = false) String createdTo,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(activityService.pageActivities(user, activityCode, activityName, lifecycleStageId,
                granularity, activityStatus, createdFrom, createdTo, new PageQuery(page, size)), TraceId.getOrCreate());
    }

    @PostMapping
    public ApiResponse<ActivityView> createActivity(@RequestBody Map<String, Object> body,
                                                    @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(activityService.createActivity(user, body), TraceId.getOrCreate());
    }

    @GetMapping("/{id}")
    public ApiResponse<ActivityView> activityDetail(@PathVariable long id, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(activityService.detail(user, id), TraceId.getOrCreate());
    }

    @PutMapping("/{id}")
    public ApiResponse<ActivityView> updateActivity(@PathVariable long id, @RequestBody Map<String, Object> body,
                                                    @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(activityService.updateActivity(user, id, body), TraceId.getOrCreate());
    }

    @PutMapping("/{id}/status")
    public ApiResponse<ActivityView> setStatus(@PathVariable long id, @RequestBody Map<String, Object> body,
                                               @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(activityService.setStatus(user, id, String.valueOf(body.get("status"))),
                TraceId.getOrCreate());
    }

    @PostMapping("/{id}/obsolete")
    public ApiResponse<ActivityView> obsolete(@PathVariable long id, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(activityService.obsoleteActivity(user, id), TraceId.getOrCreate());
    }

    @GetMapping("/{id}/topic-candidates")
    public ApiResponse<List<Map<String, Object>>> topicCandidates(@PathVariable long id,
                                                                  @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(activityService.topicCandidates(user, id), TraceId.getOrCreate());
    }

    @GetMapping("/{id}/topic-members")
    public ApiResponse<List<Map<String, Object>>> topicMembers(@PathVariable long id,
                                                               @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(activityService.topicMembers(user, id), TraceId.getOrCreate());
    }

    @PostMapping("/{id}/topic-members")
    public ApiResponse<Void> addTopicMember(@PathVariable long id, @RequestBody Map<String, Object> body,
                                            @AuthenticationPrincipal AuthUser user) {
        activityService.addTopicMember(user, id, Long.parseLong(String.valueOf(body.get("memberActivityId"))));
        return ApiResponse.success(null, TraceId.getOrCreate());
    }

    @DeleteMapping("/{id}/topic-members/{memberId}")
    public ApiResponse<Void> removeTopicMember(@PathVariable long id, @PathVariable long memberId,
                                               @AuthenticationPrincipal AuthUser user) {
        activityService.removeTopicMember(user, id, memberId);
        return ApiResponse.success(null, TraceId.getOrCreate());
    }

    @GetMapping("/{id}/processes")
    public ApiResponse<List<ProcessView>> processes(@PathVariable long id, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(topologyService.processes(user, id), TraceId.getOrCreate());
    }

    @PutMapping("/{id}/processes")
    public ApiResponse<List<ProcessView>> saveProcesses(@PathVariable long id,
                                                        @RequestBody List<com.ccb.datamigration.lifecycle.activity.model.ProcessInput> inputs,
                                                        @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(topologyService.saveProcesses(user, id, inputs), TraceId.getOrCreate());
    }

    @GetMapping("/{id}/topology")
    public ApiResponse<TopologyView> topology(@PathVariable long id, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(topologyService.topology(user, id), TraceId.getOrCreate());
    }

    @PutMapping("/{id}/topology")
    public ApiResponse<TopologyView> saveTopology(@PathVariable long id, @RequestBody TopologyInput input,
                                                  @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(topologyService.saveTopology(user, id, input), TraceId.getOrCreate());
    }

    @PostMapping("/{id}/publish")
    public ApiResponse<Map<String, Object>> publish(@PathVariable long id, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(topologyService.publishVersion(user, id), TraceId.getOrCreate());
    }

    @GetMapping("/{id}/publish-status")
    public ApiResponse<Map<String, Object>> publishStatus(@PathVariable long id, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(topologyService.publishStatus(user, id), TraceId.getOrCreate());
    }

    @GetMapping("/{id}/snapshot")
    public ApiResponse<Map<String, Object>> snapshotForTask(@PathVariable long id, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(topologyService.snapshotForTask(user, id), TraceId.getOrCreate());
    }

    @PostMapping("/{id}/template/export")
    public ApiResponse<Map<String, Object>> exportTemplate(@PathVariable long id, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(templateService.exportTemplate(user, id), TraceId.getOrCreate());
    }

    @PostMapping("/template/import")
    public ApiResponse<Map<String, Object>> importTemplate(@RequestBody Map<String, Object> body,
                                                           @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(templateService.importTemplate(user, String.valueOf(body.get("packageJson"))),
                TraceId.getOrCreate());
    }
}
