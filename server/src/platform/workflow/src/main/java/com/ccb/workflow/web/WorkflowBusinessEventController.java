package com.ccb.workflow.web;

import com.ccb.common.api.ApiResponse;
import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.common.trace.TraceId;
import com.ccb.security.model.AuthUser;
import com.ccb.workflow.service.WorkflowBusinessEventQueryService;
import com.ccb.workflow.service.WorkflowLifecycleDispatcher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/workflows/events")
@PreAuthorize("hasAnyAuthority('system:admin','workflow:event:manage')")
public class WorkflowBusinessEventController {
    private final WorkflowBusinessEventQueryService queryService;
    private final WorkflowLifecycleDispatcher dispatcher;

    public WorkflowBusinessEventController(WorkflowBusinessEventQueryService queryService, WorkflowLifecycleDispatcher dispatcher) {
        this.queryService = queryService;
        this.dispatcher = dispatcher;
    }

    @GetMapping("/deliveries")
    public ApiResponse<PageResult<Map<String, Object>>> deliveries(@RequestParam(defaultValue = "1") long page,
                                                                   @RequestParam(defaultValue = "20") long size,
                                                                   @RequestParam(required = false) String status,
                                                                   @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(queryService.deliveries(new PageQuery(page, size), status, user), TraceId.getOrCreate());
    }

    @PostMapping("/{eventId}/subscribers/{subscriberKey}/retry")
    public ApiResponse<Void> retry(@PathVariable String eventId, @PathVariable String subscriberKey,
                                   @AuthenticationPrincipal AuthUser user) {
        dispatcher.retry(eventId, subscriberKey, user.tenantId());
        return ApiResponse.success(null, TraceId.getOrCreate());
    }
}
