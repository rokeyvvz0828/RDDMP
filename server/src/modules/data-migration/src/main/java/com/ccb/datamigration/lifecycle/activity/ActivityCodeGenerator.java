package com.ccb.datamigration.lifecycle.activity;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 活动编码自动生成：ACT-PRJ-### / ACT-CMP-### / ACT-TPC-###，全局唯一、不可修改（基线 9.2.1）。 */
@Component
public class ActivityCodeGenerator {
    private final JdbcTemplate jdbc;

    public ActivityCodeGenerator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 按活动类型与颗粒度生成编码前缀。 */
    public static String prefixFor(String activityType, String granularity) {
        if ("TOPIC".equals(activityType)) {
            return "ACT-TPC";
        }
        return "PROJECT".equals(granularity) ? "ACT-PRJ" : "ACT-CMP";
    }

    /** 计算下一候选编码（无副作用）；唯一性由 uk_activity_code 兜底，服务层捕获冲突重试。 */
    public String candidate(long tenantId, String activityType, String granularity) {
        String prefix = prefixFor(activityType, granularity);
        Long maxSeq = jdbc.queryForObject(
                "SELECT MAX(CAST(SUBSTRING_INDEX(activity_code, '-', -1) AS UNSIGNED)) FROM activity "
                        + "WHERE tenant_id = ? AND activity_code LIKE ? AND deleted = 0",
                Long.class, tenantId, prefix + "-%");
        long next = (maxSeq == null ? 0 : maxSeq) + 1;
        return prefix + "-" + String.format("%03d", next);
    }
}
