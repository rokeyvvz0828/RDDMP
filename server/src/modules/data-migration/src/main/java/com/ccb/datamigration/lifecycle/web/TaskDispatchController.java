package com.ccb.datamigration.lifecycle.web;

import com.ccb.common.api.ApiResponse;
import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.common.trace.TraceId;
import com.ccb.datamigration.lifecycle.task.TaskDispatchService;
import com.ccb.datamigration.lifecycle.task.model.TaskView;
import com.ccb.security.model.AuthUser;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 任务发布接口（基线第 10 章）：任务台账、单发/批量下发、专题聚合边维护、工单级快照固化。 */
@RestController
@RequestMapping("/api/data-migration-lifecycle")
@PreAuthorize("hasAnyAuthority('data-migration-lifecycle:access','data-migration:access','system:admin')")
public class TaskDispatchController {
    private final TaskDispatchService dispatchService;

    public TaskDispatchController(TaskDispatchService dispatchService) {
        this.dispatchService = dispatchService;
    }

    @GetMapping("/tasks")
    public ApiResponse<PageResult<TaskView>> pageTasks(
            @RequestParam(required = false) String taskCode,
            @RequestParam(required = false) String taskName,
            @RequestParam(required = false) String granularity,
            @RequestParam(required = false) String activityType,
            @RequestParam(required = false) String taskStatus,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long componentId,
            @RequestParam(required = false) String createdFrom,
            @RequestParam(required = false) String createdTo,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(dispatchService.pageTasks(user, taskCode, taskName, granularity, activityType,
                taskStatus, projectId, componentId, createdFrom, createdTo, new PageQuery(page, size)),
                TraceId.getOrCreate());
    }

    @GetMapping("/tasks/{id}")
    public ApiResponse<Map<String, Object>> taskDetail(@PathVariable long id, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(dispatchService.detail(user, id), TraceId.getOrCreate());
    }

    @PostMapping("/tasks/dispatch")
    public ApiResponse<Map<String, Object>> dispatch(@RequestBody Map<String, Object> body,
                                                     @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(dispatchService.dispatch(user, body), TraceId.getOrCreate());
    }

    @GetMapping("/activities/{id}/aggregate-edges")
    public ApiResponse<List<Map<String, Object>>> aggregateEdges(@PathVariable long id,
                                                                 @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(dispatchService.topicAggregateEdges(user, id), TraceId.getOrCreate());
    }

    @PutMapping("/activities/{id}/aggregate-edges")
    public ApiResponse<Void> saveAggregateEdges(@PathVariable long id,
                                                @RequestBody Map<String, Object> body,
                                                @AuthenticationPrincipal AuthUser user) {
        dispatchService.saveTopicAggregateEdges(user, id, edgeList(body.get("edges")));
        return ApiResponse.success(null, TraceId.getOrCreate());
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> edgeList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().filter(Map.class::isInstance).map(item -> (Map<String, Object>) item).toList();
    }
}
