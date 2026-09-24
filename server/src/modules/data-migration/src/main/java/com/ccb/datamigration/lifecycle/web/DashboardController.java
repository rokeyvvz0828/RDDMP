package com.ccb.datamigration.lifecycle.web;

import com.ccb.common.api.ApiResponse;
import com.ccb.common.trace.TraceId;
import com.ccb.datamigration.lifecycle.dashboard.DashboardService;
import com.ccb.datamigration.lifecycle.dashboard.model.ActivityStatusView;
import com.ccb.datamigration.lifecycle.dashboard.model.IssueStatusView;
import com.ccb.datamigration.lifecycle.dashboard.model.OrderStatusView;
import com.ccb.datamigration.lifecycle.dashboard.model.RiskStatusView;
import com.ccb.datamigration.lifecycle.dashboard.model.StageActivityOrderView;
import com.ccb.datamigration.lifecycle.dashboard.model.TopicProgressOverview;
import com.ccb.security.model.AuthUser;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 数据看板接口（基线第 16 章，T10）：六大维度统计（权限前置过滤）+ 时效对账闸门，全部只读无编辑入口。 */
@RestController
@RequestMapping("/api/data-migration-lifecycle/dashboard")
@PreAuthorize("hasAnyAuthority('data-migration-lifecycle:dashboard','data-migration:access','system:admin')")
public class DashboardController {
    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /** 维度 1：全生命周期阶段-活动-工单层级统计（仅普通活动）。 */
    @GetMapping("/stage-activity")
    public ApiResponse<List<StageActivityOrderView>> stageActivity(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(dashboardService.stageActivity(user), TraceId.getOrCreate());
    }

    /** 维度 2：活动进展状态（活动类型标签区分普通/专题）。 */
    @GetMapping("/activity-status")
    public ApiResponse<ActivityStatusView> activityStatus(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(dashboardService.activityStatus(user), TraceId.getOrCreate());
    }

    /** 维度 3：专题进度 + 专题运行状态（独立于生命周期阶段）。 */
    @GetMapping("/topic-progress")
    public ApiResponse<TopicProgressOverview> topicProgress(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(dashboardService.topicProgress(user), TraceId.getOrCreate());
    }

    /** 维度 4：问题状态分项。 */
    @GetMapping("/issue-status")
    public ApiResponse<IssueStatusView> issueStatus(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(dashboardService.issueStatus(user), TraceId.getOrCreate());
    }

    /** 维度 5：风险状态分项 + 等级分层。 */
    @GetMapping("/risk-status")
    public ApiResponse<RiskStatusView> riskStatus(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(dashboardService.riskStatus(user), TraceId.getOrCreate());
    }

    /** 维度 6：工单进度状态 + 时效分桶（叠加维度，仅「在办工单」参与；先时效对账后出数）。 */
    @GetMapping("/order-status")
    public ApiResponse<OrderStatusView> orderStatus(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.success(dashboardService.orderStatus(user), TraceId.getOrCreate());
    }
}
