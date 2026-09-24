package com.ccb.datamigration.lifecycle.web;

import com.ccb.common.api.ApiResponse;
import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.common.trace.TraceId;
import com.ccb.datamigration.lifecycle.order.OrderStateMachineService;
import com.ccb.datamigration.lifecycle.order.model.OrderProcessView;
import com.ccb.datamigration.lifecycle.order.model.OrderView;
import com.ccb.security.model.AuthUser;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 工单流转引擎接口（基线第 11 章 + 18.2/18.3/18.4）：台账、工序流转面板、四类异常干预、审核结果驱动解锁。 */
@RestController
@RequestMapping("/api/data-migration-lifecycle/orders")
@PreAuthorize("hasAnyAuthority('data-migration-lifecycle:access','data-migration:access','system:admin')")
public class OrderController {
    private final OrderStateMachineService orderService;

    public OrderController(OrderStateMachineService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public ApiResponse<PageResult<OrderView>> pageOrders(
            @RequestParam(required = false) String orderCode,
            @RequestParam(required = false) String orderStatus,
            @RequestParam(required = false) String granularity,
            @RequestParam(required = false) String activityType,
            @RequestParam(required = false) String deadlineStatus,
            @RequestParam(required = false) Long componentId,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long currentExecutorId,
            @RequestParam(required = false) String createdFrom,
            @RequestParam(required = false) String createdTo,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(orderService.listOrders(user, orderCode, orderStatus, granularity, activityType,
                deadlineStatus, componentId, projectId, currentExecutorId, createdFrom, createdTo,
                new PageQuery(page, size)), TraceId.getOrCreate());
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> orderDetail(@PathVariable long id, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(orderService.orderDetail(user, id), TraceId.getOrCreate());
    }

    @GetMapping("/{id}/processes")
    public ApiResponse<List<OrderProcessView>> processes(@PathVariable long id, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(orderService.orderProcesses(user, id), TraceId.getOrCreate());
    }

    @PostMapping("/{id}/suspend")
    public ApiResponse<Map<String, Object>> suspend(@PathVariable long id, @RequestBody Map<String, Object> body,
                                                    @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(orderService.suspend(user, id, stringOrNull(body.get("reason"))), TraceId.getOrCreate());
    }

    @PostMapping("/{id}/resume")
    public ApiResponse<Map<String, Object>> resume(@PathVariable long id, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(orderService.resume(user, id), TraceId.getOrCreate());
    }

    @PostMapping("/{id}/transfer")
    public ApiResponse<Map<String, Object>> transfer(@PathVariable long id, @RequestBody Map<String, Object> body,
                                                     @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(orderService.transfer(user, id, longValue(body, "nextExecutorId"),
                stringOrNull(body.get("reason"))), TraceId.getOrCreate());
    }

    @PostMapping("/{id}/restart")
    public ApiResponse<Map<String, Object>> restart(@PathVariable long id, @RequestBody Map<String, Object> body,
                                                    @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(orderService.restart(user, id, intValue(body, "restartPointSeq", 0),
                stringOrNull(body.get("reason"))), TraceId.getOrCreate());
    }

    @PostMapping("/{id}/sla-adjust")
    public ApiResponse<Map<String, Object>> slaAdjust(@PathVariable long id, @RequestBody Map<String, Object> body,
                                                      @AuthenticationPrincipal AuthUser user) {
        String raw = stringOrNull(body.get("planFinishTime"));
        LocalDateTime plan = raw == null ? null : LocalDateTime.parse(raw);
        return ApiResponse.success(orderService.slaAdjust(user, id, plan, stringOrNull(body.get("reason"))),
                TraceId.getOrCreate());
    }

    @PostMapping("/{id}/sla-exempt")
    public ApiResponse<Map<String, Object>> slaExempt(@PathVariable long id, @RequestBody Map<String, Object> body,
                                                      @AuthenticationPrincipal AuthUser user) {
        boolean exempt = Boolean.parseBoolean(String.valueOf(body.getOrDefault("exempt", true)));
        return ApiResponse.success(orderService.slaExempt(user, id, exempt, stringOrNull(body.get("reason"))),
                TraceId.getOrCreate());
    }

    @PostMapping("/{id}/archive")
    public ApiResponse<Map<String, Object>> archive(@PathVariable long id, @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(orderService.archive(user, id), TraceId.getOrCreate());
    }

    /** 审核结果驱动解锁（T7 消费契约）；本批次供审核联动与自检使用。 */
    @PostMapping("/{id}/processes/{seq}/audit-result")
    public ApiResponse<Map<String, Object>> auditResult(@PathVariable long id, @PathVariable int seq,
                                                        @RequestBody Map<String, Object> body,
                                                        @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(orderService.applyAuditResult(user, id, seq,
                String.valueOf(body.get("auditResult"))), TraceId.getOrCreate());
    }

    private static String stringOrNull(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value);
        return s.isBlank() || "null".equals(s) ? null : s;
    }

    private static long longValue(Map<String, Object> body, String key) {
        Object raw = body.get(key);
        if (raw == null) {
            throw new IllegalArgumentException(key + " 不能为空");
        }
        return Long.parseLong(String.valueOf(raw));
    }

    private static int intValue(Map<String, Object> body, String key, int defaultValue) {
        Object raw = body.get(key);
        if (raw == null) {
            return defaultValue;
        }
        return Integer.parseInt(String.valueOf(raw));
    }
}
