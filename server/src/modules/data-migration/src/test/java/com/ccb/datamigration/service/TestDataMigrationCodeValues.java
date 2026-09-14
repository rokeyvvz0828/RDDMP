package com.ccb.datamigration.service;

import com.ccb.common.api.PageQuery;
import com.ccb.common.api.PageResult;
import com.ccb.security.model.AuthUser;
import com.ccb.system.capability.SystemParameterReference;
import com.ccb.system.capability.SystemReferenceQuery;
import com.ccb.system.capability.SystemUserReference;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 测试用参数管理码值：默认返回 V183 初始化出的数据迁移业务码值。 */
public final class TestDataMigrationCodeValues {
    private static final Map<String, List<SystemParameterReference>> OPTIONS = new LinkedHashMap<>();

    static {
        put("DM_TOPIC_GRANULARITY", "PROJECT", "项目级", "SYSTEM", "系统级");
        put("DM_PLAN_GRANULARITY", "PROJECT", "项目级", "SYSTEM", "系统级");
        put("DM_PLAN_TYPE", "BUSINESS", "业务迁移方案", "DATA", "数据迁移方案");
        put("DM_ISSUE_GRANULARITY", "PROJECT", "项目级", "COMPONENT", "组件级", "TABLE", "表级", "FIELD", "字段级");
        put("DM_ISSUE_SOURCE", "MIGRATION_CHECK", "数迁检核", "SIT_FEEDBACK", "SIT测试反馈", "UAT_FEEDBACK", "UAT测试反馈",
                "DATA_LINE_FEEDBACK", "数据线反馈", "EXPERT_FEEDBACK", "事业群专家反馈", "RISK_IDENTIFICATION", "风险识别",
                "MIGRATION_RELEASE", "数迁投产过程");
        put("DM_DEFECT_TYPE", "REQUIREMENT", "需求问题", "DESIGN", "设计问题", "CODING", "编码问题",
                "DATA_QUALITY", "数据质量问题", "CLEANUP", "清理补录问题", "BUSINESS", "业务问题",
                "UNDERSTANDING", "理解问题", "PERFORMANCE", "性能问题", "MASKING", "脱敏问题", "OTHER", "其他问题");
        put("DM_ISSUE_FREQUENCY", "CLASSIC", "经典问题", "HIGH_FREQ", "高频重复", "LOW_FREQ", "低频偶发", "SINGLE_CASE", "单次个案");
        put("DM_MEETING_GRANULARITY", "PROJECT", "项目级", "COMPONENT", "组件级", "TABLE", "表级", "FIELD", "字段级");
        put("DM_MEETING_SOURCE", "MEETING_MINUTES", "会议纪要", "ISSUE_EXTRACT", "问题提取");
        put("DM_REPORT_PERIOD", "DAILY", "日报", "WEEKLY", "周报", "BIWEEKLY", "双周报", "MONTHLY", "月报", "IRREGULAR", "不定期汇报");
        put("DM_TARGET_TABLE_CATEGORY", "TARGET", "目标表结构", "INTERMEDIATE", "中间表结构");
        put("DM_RELEASE_DRILL_GRANULARITY", "PROJECT", "项目级", "COMPONENT", "组件级");
        put("DM_RELEASE_DRILL_TYPE", "RELEASE_PLAN", "投产方案", "EMERGENCY_PLAN", "应急方案", "RELEASE_SUMMARY", "投产总结",
                "BRIEFING_NOTICE", "宣讲通知", "SCHEDULE_TIMELINE", "调度时序", "CRITICAL_PATH", "关键路径",
                "MILESTONE_REPORT_INSTRUCTION", "里程碑汇报指令", "DRILL_ENV_INFO", "数迁环境信息");
    }

    private TestDataMigrationCodeValues() {
    }

    public static DataMigrationCodeValueService service() {
        return new DataMigrationCodeValueService(new SystemReferenceQuery() {
            @Override
            public PageResult<SystemUserReference> searchActiveUsers(AuthUser actor, PageQuery page, String keyword) {
                return new PageResult<>(List.of(), 0L, 1, 20);
            }

            @Override
            public Optional<SystemUserReference> findUser(AuthUser actor, long userId, boolean activeOnly) {
                return Optional.empty();
            }

            @Override
            public List<SystemParameterReference> activeParameters(AuthUser actor, String categoryCode) {
                return OPTIONS.getOrDefault(categoryCode, List.of());
            }
        });
    }

    private static void put(String category, String... values) {
        java.util.ArrayList<SystemParameterReference> options = new java.util.ArrayList<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            options.add(new SystemParameterReference(values[index], values[index + 1]));
        }
        OPTIONS.put(category, List.copyOf(options));
    }
}
