package com.ccb.requirement.support;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** 动态列 SQL 小工具：字段名全部来自本模块受控映射，标识符先做白名单校验。 */
public final class RequirementSql {
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private RequirementSql() {
    }

    public static void insert(JdbcTemplate jdbc, String table, Map<String, Object> values) {
        List<String> columns = new ArrayList<>(values.keySet());
        String columnSql = String.join(", ", columns.stream().map(RequirementSql::quote).toList());
        String placeholderSql = String.join(", ", columns.stream().map(column -> "?").toList());
        List<Object> params = columns.stream().map(values::get).toList();
        jdbc.update("INSERT INTO " + quote(table) + " (" + columnSql + ") VALUES (" + placeholderSql + ")", params.toArray());
    }

    public static void update(JdbcTemplate jdbc, String table, long id, long tenantId, Map<String, Object> values) {
        updateBy(jdbc, table, "id", id, tenantId, values);
    }

    /** 按任意业务主键列更新（详情表等以 requirement_id 为主键的表使用）。 */
    public static void updateBy(JdbcTemplate jdbc, String table, String keyColumn, Object keyValue,
                                long tenantId, Map<String, Object> values) {
        List<String> setClauses = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (keyColumn.equals(entry.getKey()) || "tenant_id".equals(entry.getKey())) {
                continue;
            }
            setClauses.add(quote(entry.getKey()) + " = ?");
            params.add(entry.getValue());
        }
        if (setClauses.isEmpty()) {
            return;
        }
        params.add(tenantId);
        params.add(keyValue);
        jdbc.update("UPDATE " + quote(table) + " SET " + String.join(", ", setClauses)
                + " WHERE tenant_id = ? AND " + quote(keyColumn) + " = ?", params.toArray());
    }

    public static String quote(String identifier) {
        if (!IDENTIFIER.matcher(identifier).matches()) {
            throw new IllegalArgumentException("Invalid SQL identifier: " + identifier);
        }
        return "`" + identifier + "`";
    }
}
