package com.ccb.testmanagement.analytics;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestAnalyticsMetricCatalogTest {
    @Test
    void keepsInProgressSeparateFromExecutedAndUsesEffectiveCasesAsDenominator() {
        Map<String, Object> metrics = TestAnalyticsMetricCatalog.executionMetrics(10, 2, 3, 1, 1, 2);

        assertEquals(5L, metrics.get("execution_total"));
        assertEquals(2L, metrics.get("execution_in_progress"));
        assertEquals(3L, metrics.get("execution_unexecuted"));
        assertEquals(50D, metrics.get("execution_rate"));
        assertEquals(30D, metrics.get("case_success_rate"));
        assertEquals(60D, metrics.get("executed_case_success_rate"));
    }

    @Test
    void exposesAllFixedChineseReportsAndCharts() {
        assertEquals(26, TestAnalyticsMetricCatalog.FIXED_REPORTS.size());
        assertEquals(16, TestAnalyticsMetricCatalog.FIXED_CHARTS.size());
        assertTrue(TestAnalyticsMetricCatalog.FIXED_REPORTS.stream().allMatch(item -> item.get("name").chars().anyMatch(character -> Character.UnicodeScript.of(character) == Character.UnicodeScript.HAN)));
    }
}
