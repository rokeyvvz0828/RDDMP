package com.ccb.release.reporting.persistence;

import com.ccb.common.api.PageQuery;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReleaseAnalyticsStoreTest {
    @Test
    void keepsDistinctSortColumnsInsideAProjectedSubquery() {
        CapturingJdbcTemplate jdbc = new CapturingJdbcTemplate();
        ReleaseAnalyticsStore store = new ReleaseAnalyticsStore(jdbc);

        store.drilldown(1L, "PRJ-1", 10L, "productionResult", "SUCCEEDED", new PageQuery(1, 20));

        assertTrue(jdbc.listSql.contains("SELECT DISTINCT"));
        assertTrue(jdbc.listSql.contains("a.updated_at AS sort_updated_at"));
        assertTrue(jdbc.listSql.contains(") drilldown_rows ORDER BY sort_updated_at DESC, sort_id DESC"));
        assertFalse(jdbc.listSql.startsWith("SELECT DISTINCT"));
    }

    @Test
    void summaryCountsDeliveryUnitsAndFileMediaSeparatelyByItemKey() {
        CapturingJdbcTemplate jdbc = new CapturingJdbcTemplate();
        ReleaseAnalyticsStore store = new ReleaseAnalyticsStore(jdbc);

        store.summary(1L, "PRJ-1", 10L);

        assertTrue(jdbc.countSql.stream().anyMatch(sql -> sql.contains("d.item_type = 'DELIVERY_UNIT'")
                && sql.contains("d.item_key")));
        assertTrue(jdbc.countSql.stream().anyMatch(sql -> sql.contains("d.item_type = 'FILE_MEDIA'")
                && sql.contains("d.item_key")));
    }

    private static final class CapturingJdbcTemplate extends JdbcTemplate {
        private String listSql = "";
        private final java.util.ArrayList<String> countSql = new java.util.ArrayList<>();

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            countSql.add(sql);
            return requiredType.cast(0L);
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            listSql = sql;
            return List.of();
        }
    }
}
