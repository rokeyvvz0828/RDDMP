package com.ccb.testmanagement.analytics;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/** 统一维护测试报告和分析统计的中文固定目录，禁止由页面拼接任意指标或图表。 */
public final class TestAnalyticsMetricCatalog {
    private TestAnalyticsMetricCatalog() { }

    public static final List<String> EXECUTED_STATUSES = List.of("SUCCESS", "FAILED", "BLOCKED");
    public static final List<Map<String, String>> FIXED_REPORTS = List.of(
            item("RPT-001", "测试执行进度汇总表"), item("RPT-002", "按周期各系统执行统计表"), item("RPT-003", "按轮次各系统执行统计表"), item("RPT-004", "按机构各周期执行统计表"),
            item("RPT-005", "按机构各轮次执行统计表"), item("RPT-006", "测试案例执行明细表"), item("RPT-007", "测试缺陷明细表"), item("RPT-008", "缺陷状态分布统计表"),
            item("RPT-009", "缺陷严重程度分布统计表"), item("RPT-010", "按系统缺陷密度统计表"), item("RPT-011", "范围覆盖分析表"), item("RPT-012", "测试人员工作量统计表"),
            item("RPT-013", "缺陷生命周期统计表"), item("RPT-014", "按案例类型执行统计表"), item("RPT-015", "按优先级执行统计表"), item("RPT-016", "核算相关功能测试统计表"),
            item("RPT-017", "缺陷处理效率统计表"), item("RPT-018", "测试轮次对比分析表"), item("RPT-019", "系统间测试质量对比表"), item("RPT-020", "测试日报表（按子系统）"),
            item("RPT-021", "测试周报表（按子系统）"), item("RPT-022", "测试月报表（按子系统）"), item("RPT-023", "项目测试日报汇总表"), item("RPT-024", "机构测试质量汇总表"),
            item("RPT-025", "轮次内按日进度跟踪表"), item("RPT-026", "缺陷闭环统计表")
    );
    public static final List<Map<String, String>> FIXED_CHARTS = List.of(
            item("CHT-001", "案例执行状态分布"), item("CHT-002", "缺陷状态分布"), item("CHT-003", "缺陷严重程度分布"), item("CHT-004", "执行率、案例成功率、已执行案例成功率趋势"),
            item("CHT-005", "系统测试质量对比"), item("CHT-006", "范围执行覆盖率"), item("CHT-007", "系统×周期执行状态分布"), item("CHT-008", "缺陷严重程度与状态分布"),
            item("CHT-009", "测试人员工作量对比"), item("CHT-010", "缺陷提出与关闭趋势"), item("CHT-011", "案例类型分布"), item("CHT-012", "系统质量雷达图"),
            item("CHT-013", "日执行量与累计执行率"), item("CHT-014", "缺陷状态随时间变化"), item("CHT-015", "系统日执行强度热力图"), item("CHT-016", "轮次执行状态对比")
    );
    public static final List<Map<String, String>> QUALITY_METRICS = List.of(
            metric("execution_rate", "执行率", "AT_LEAST"), metric("case_success_rate", "案例成功率", "AT_LEAST"),
            metric("executed_case_success_rate", "已执行案例成功率", "AT_LEAST"), metric("defect_density", "缺陷密度", "AT_MOST"),
            metric("defect_repair_rate", "缺陷修复率", "AT_LEAST"), metric("severe_defect_count", "严重缺陷数", "AT_MOST"), metric("blocked_case_count", "阻塞案例数", "AT_MOST")
    );

    /** 执行中单列展示；已执行只包含成功、失败和阻塞，所有比率以有效案例为主分母。 */
    public static Map<String, Object> executionMetrics(long effectiveCases, long invalidCases, long success, long failed, long blocked, long inProgress) {
        long executed = success + failed + blocked;
        long unexecuted = Math.max(0, effectiveCases - executed - inProgress);
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("effective_case_total", effectiveCases); metrics.put("invalid_case_total", invalidCases); metrics.put("execution_total", executed);
        metrics.put("execution_unexecuted", unexecuted); metrics.put("execution_in_progress", inProgress); metrics.put("execution_success", success); metrics.put("execution_failed", failed); metrics.put("execution_blocked", blocked);
        metrics.put("execution_rate", rate(executed, effectiveCases)); metrics.put("case_success_rate", rate(success, effectiveCases)); metrics.put("executed_case_success_rate", rate(success, executed));
        return metrics;
    }

    private static Map<String, String> item(String code, String name) { return Map.of("code", code, "name", name); }
    private static Map<String, String> metric(String code, String name, String direction) { return Map.of("code", code, "name", name, "direction", direction); }
    private static double rate(long value, long base) { return base == 0 ? 0D : Math.round(value * 10000D / base) / 100D; }
}
