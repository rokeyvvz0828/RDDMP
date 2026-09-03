package com.ccb.testmanagement.analytics;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/** 统一维护测试报告和分析统计的中文固定目录，禁止由页面拼接任意指标或图表。 */
public final class TestAnalyticsMetricCatalog {
    private TestAnalyticsMetricCatalog() { }

    public static final List<String> EXECUTED_STATUSES = List.of("SUCCESS", "FAILED", "BLOCKED");
    public static final List<Map<String, String>> FIXED_REPORTS = List.of(
            item("RPT-001", "项目测试概览"), item("RPT-002", "机构测试概览"), item("RPT-003", "系统测试概览"), item("RPT-004", "测试范围覆盖情况"),
            item("RPT-005", "案例执行进度"), item("RPT-006", "案例执行结果"), item("RPT-007", "案例阻塞情况"), item("RPT-008", "缺陷总体情况"),
            item("RPT-009", "缺陷严重程度分布"), item("RPT-010", "缺陷状态分布"), item("RPT-011", "缺陷修复情况"), item("RPT-012", "缺陷逾期情况"),
            item("RPT-013", "轮次测试对比"), item("RPT-014", "周期测试对比"), item("RPT-015", "责任团队质量情况"), item("RPT-016", "测试人员工作量"),
            item("RPT-017", "执行人员工作量"), item("RPT-018", "缺陷处理人员工作量"), item("RPT-019", "系统质量排名"), item("RPT-020", "机构质量排名"),
            item("RPT-021", "质量阈值达标情况"), item("RPT-022", "风险指标预警"), item("RPT-023", "无效案例情况"), item("RPT-024", "执行中案例情况"),
            item("RPT-025", "测试趋势分析"), item("RPT-026", "测试数据明细")
    );
    public static final List<Map<String, String>> FIXED_CHARTS = List.of(
            item("CHT-001", "案例执行状态分布"), item("CHT-002", "案例执行趋势"), item("CHT-003", "案例成功率趋势"), item("CHT-004", "缺陷状态分布"),
            item("CHT-005", "缺陷严重程度分布"), item("CHT-006", "缺陷修复趋势"), item("CHT-007", "缺陷密度对比"), item("CHT-008", "阻塞案例趋势"),
            item("CHT-009", "系统执行率对比"), item("CHT-010", "系统成功率对比"), item("CHT-011", "机构执行率对比"), item("CHT-012", "机构成功率对比"),
            item("CHT-013", "责任团队缺陷分布"), item("CHT-014", "测试人员工作量"), item("CHT-015", "质量阈值达标情况"), item("CHT-016", "测试质量综合趋势")
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
