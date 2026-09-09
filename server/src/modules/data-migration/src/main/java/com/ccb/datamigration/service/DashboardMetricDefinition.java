package com.ccb.datamigration.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Static whitelist shared by dashboard metric counts and drilldown queries. */
public enum DashboardMetricDefinition {
    OVERALL_PLAN("dm_plan", "id", "doc_code", "doc_name", "granularity", "system_code", "PROJECT"),
    COMPONENT_PLAN("dm_plan", "id", "doc_code", "doc_name", "granularity", "system_code", "SYSTEM"),
    PROJECT_TOPIC("dm_topic", "id", "doc_code", "doc_name", "granularity", null, "PROJECT"),
    COMPONENT_TOPIC("dm_topic", "id", "doc_code", "doc_name", "granularity", null, "SYSTEM"),
    REPORT("dm_report", "id", "doc_code", "doc_name", null, null, null),
    MEETING("dm_meeting", "meeting_id", "meeting_code", "meeting_title", "granularity", null, null),
    ISSUE("dm_issue", "id", "issue_code", "issue_name", "granularity", "system_code", null),
    RELEASE_DRILL("dm_release_drill", "id", "doc_code", "doc_name", "granularity", "system_code", null),
    MAPPING_DOC("dm_mapping_doc", "id", "doc_code", "doc_name", null, "system_code", null),
    RULE("dm_rule", "id", "rule_code", "COALESCE(NULLIF(rule_code_desc, ''), rule_code)", null, "system_code", null),
    PARAMETER("dm_parameter", "id", "parameter_name", "parameter_name", null, "system_code", null),
    DEPENDENCY("dm_dependency", "id", "doc_code", "doc_name", null, "system_code", null),
    SCRIPT("dm_script", "id", "doc_code", "doc_name", null, "system_code", null);

    private static final Map<String, DashboardMetricDefinition> BY_CODE;

    static {
        Map<String, DashboardMetricDefinition> definitions = new LinkedHashMap<>();
        Arrays.stream(values()).forEach(definition -> definitions.put(definition.code(), definition));
        BY_CODE = Collections.unmodifiableMap(definitions);
    }

    private final String tableName;
    private final String idColumn;
    private final String codeExpression;
    private final String nameExpression;
    private final String granularityExpression;
    private final String systemCodeExpression;
    private final String granularity;

    DashboardMetricDefinition(String tableName, String idColumn, String codeExpression, String nameExpression,
                              String granularityExpression, String systemCodeExpression, String granularity) {
        this.tableName = tableName;
        this.idColumn = idColumn;
        this.codeExpression = codeExpression;
        this.nameExpression = nameExpression;
        this.granularityExpression = granularityExpression;
        this.systemCodeExpression = systemCodeExpression;
        this.granularity = granularity;
    }

    public String code() {
        return name();
    }

    public String tableName() {
        return tableName;
    }

    public String idColumn() {
        return idColumn;
    }

    public String granularity() {
        return granularity;
    }

    public String countSql() {
        return "SELECT COUNT(*) FROM " + tableName + whereClause();
    }

    public String drilldownSql() {
        return "SELECT " + idColumn + " AS id, " + codeExpression + " AS code, "
                + nameExpression + " AS name, " + nullableExpression(granularityExpression) + " AS granularity, "
                + nullableExpression(systemCodeExpression) + " AS systemCode, updated_at AS updatedAt FROM "
                + tableName + whereClause() + " ORDER BY updated_at DESC, " + idColumn + " DESC LIMIT ? OFFSET ?";
    }

    public List<Object> scopeArguments(long tenantId, long projectId) {
        return granularity == null
                ? List.of(tenantId, projectId)
                : List.of(tenantId, projectId, granularity);
    }

    public static Set<String> codes() {
        return BY_CODE.keySet();
    }

    public static DashboardMetricDefinition require(String rawCode) {
        String code = rawCode == null ? "" : rawCode.trim().toUpperCase(Locale.ROOT);
        DashboardMetricDefinition definition = BY_CODE.get(code);
        if (definition == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的看板指标码");
        }
        return definition;
    }

    private String whereClause() {
        return " WHERE tenant_id = ? AND project_id = ? AND deleted = 0"
                + (granularity == null ? "" : " AND granularity = ?");
    }

    private String nullableExpression(String expression) {
        return expression == null ? "NULL" : expression;
    }
}
