package com.ccb.datamigration.lifecycle.web;

import com.ccb.common.api.ApiResponse;
import com.ccb.common.trace.TraceId;
import com.ccb.common.api.PageResult;
import com.ccb.datamigration.lifecycle.risk.RiskService;
import com.ccb.datamigration.lifecycle.risk.model.RiskStrategyLibView;
import com.ccb.datamigration.lifecycle.risk.model.RiskView;
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

/** 风险管理 + 风险策略库接口（基线第 14 章，T9）：台账/防控闭环/对账同步/策略匹配与一键复用。 */
@RestController
@RequestMapping("/api/data-migration-lifecycle")
@PreAuthorize("hasAnyAuthority('data-migration-lifecycle:access','data-migration:access','system:admin')")
public class RiskController {
    private final RiskService riskService;

    public RiskController(RiskService riskService) {
        this.riskService = riskService;
    }

    @GetMapping("/risks")
    public ApiResponse<PageResult<RiskView>> pageRisks(
            @RequestParam(required = false) String riskStatus,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false) String riskCode,
            @RequestParam(required = false) String riskSource,
            @RequestParam(required = false) Long componentId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @AuthenticationPrincipal AuthUser user) {
        return ok(riskService.listRisks(user, riskStatus, riskLevel, riskCode, componentId,
                riskSource, page, size));
    }

    @PostMapping("/risks")
    public ApiResponse<RiskView> create(@RequestBody Map<String, Object> body, @AuthenticationPrincipal AuthUser user) {
        return ok(riskService.createRisk(user, body));
    }

    @PutMapping("/risks/{riskId}")
    public ApiResponse<RiskView> update(@PathVariable long riskId, @RequestBody Map<String, Object> body,
                                        @AuthenticationPrincipal AuthUser user) {
        return ok(riskService.updateRisk(user, riskId, body, true));
    }

    @PostMapping("/risk/reconcile")
    public ApiResponse<Map<String, Object>> reconcile(@AuthenticationPrincipal AuthUser user) {
        return ok(riskService.reconcile(user));
    }

    @PostMapping("/risks/{riskId}/prevent")
    public ApiResponse<RiskView> prevent(@PathVariable long riskId, @RequestBody Map<String, Object> body,
                                         @AuthenticationPrincipal AuthUser user) {
        return ok(riskService.prevent(user, riskId,
                str(body, "preventProgress"), str(body, "record")));
    }

    @PostMapping("/risks/{riskId}/close")
    public ApiResponse<Map<String, Object>> close(@PathVariable long riskId, @RequestBody Map<String, Object> body,
                                                  @AuthenticationPrincipal AuthUser user) {
        return ok(riskService.close(user, riskId, str(body, "finalStatus")));
    }

    @PostMapping("/risks/{riskId}/cancel")
    public ApiResponse<Void> cancel(@PathVariable long riskId, @RequestBody Map<String, Object> body,
                                    @AuthenticationPrincipal AuthUser user) {
        riskService.cancel(user, riskId, str(body, "reason"));
        return ok(null);
    }

    @GetMapping("/risk/strategy/match")
    public ApiResponse<List<RiskStrategyLibView>> match(@RequestParam(required = false) String riskTitle,
                                                        @RequestParam(required = false) String riskLevel,
                                                        @RequestParam(required = false) String probability,
                                                        @AuthenticationPrincipal AuthUser user) {
        return ok(riskService.matchStrategies(user, riskTitle, riskLevel, probability));
    }

    @GetMapping("/risk/strategies")
    public ApiResponse<List<RiskStrategyLibView>> strategies(@RequestParam(required = false) String keyword,
                                                             @RequestParam(required = false) String status,
                                                             @AuthenticationPrincipal AuthUser user) {
        return ok(riskService.listStrategies(user, keyword, status));
    }

    @PostMapping("/risk/strategies")
    public ApiResponse<RiskStrategyLibView> createStrategy(@RequestBody Map<String, Object> body,
                                                           @AuthenticationPrincipal AuthUser user) {
        return ok(riskService.createStrategy(user, body));
    }

    @PostMapping("/risk/strategies/{strategyId}/reuse")
    public ApiResponse<Map<String, Object>> reuse(@PathVariable long strategyId, @RequestBody Map<String, Object> body,
                                                  @AuthenticationPrincipal AuthUser user) {
        return ok(riskService.reuseStrategy(user, strategyId, lng(body.get("riskId"))));
    }

    @PostMapping("/risk/strategies/{strategyId}/offline")
    public ApiResponse<RiskStrategyLibView> offline(@PathVariable long strategyId, @AuthenticationPrincipal AuthUser user) {
        return ok(riskService.offStrategy(user, strategyId));
    }

    private static String str(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static Long lng(Object value) {
        if (value == null) {
            return null;
        }
        return value instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(value));
    }

    /** 统一成功响应助手（补齐 TraceId）。 */
    private static <T> ApiResponse<T> ok(T data) {
        return ApiResponse.success(data, TraceId.getOrCreate());
    }
}
