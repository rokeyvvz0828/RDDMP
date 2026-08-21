package com.ccb.requirement.service;

import com.ccb.common.trace.TraceId;
import com.ccb.security.model.AuthUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.ccb.requirement.support.RequirementIds;

/** 统一改动记录：新增、修改、状态流转均写入 req_change_log。 */
@Service
public class RequirementChangeLogService {
    private static final int MAX_VALUE_LENGTH = 1000;
    private static final Set<String> EXCLUDED_FIELDS = Set.of(
            "id", "tenant_id", "deleted", "created_at", "updated_at", "created_by", "updated_by");

    private final JdbcTemplate jdbc;

    public RequirementChangeLogService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void record(String bizType, long bizId, String changeType, String field,
                       String oldValue, String newValue, AuthUser user, String source) {
        long id = RequirementIds.next();
        jdbc.update("""
                INSERT INTO req_change_log
                    (id, tenant_id, biz_type, biz_id, field_name, old_value, new_value, change_type,
                     operator_id, operator_name, source, trace_id, deleted)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                """, id, user.tenantId(), bizType, bizId, field,
                truncate(oldValue), truncate(newValue), changeType,
                user.id(), user.displayName(), source, TraceId.getOrCreate());
    }

    public void recordFields(String bizType, long bizId, String changeType,
                             Map<String, Object> oldValues, Map<String, Object> newValues,
                             AuthUser user, String source) {
        for (Map.Entry<String, Object> entry : newValues.entrySet()) {
            String field = entry.getKey();
            if (EXCLUDED_FIELDS.contains(field)) {
                continue;
            }
            Object oldValue = oldValues.get(field);
            Object newValue = entry.getValue();
            if (Objects.equals(oldValue, newValue)) {
                continue;
            }
            record(bizType, bizId, changeType, field, asText(oldValue), asText(newValue), user, source);
        }
    }

    public void recordCreate(String bizType, long bizId, Map<String, Object> values, AuthUser user, String source) {
        recordFields(bizType, bizId, "CREATE", Map.of(), values, user, source);
    }

    public List<Map<String, Object>> list(String bizType, long bizId, AuthUser user) {
        return jdbc.queryForList("""
                SELECT field_name, old_value, new_value, change_type, operator_id, operator_name,
                       source, trace_id, created_at
                FROM req_change_log
                WHERE tenant_id = ? AND biz_type = ? AND biz_id = ? AND deleted = 0
                ORDER BY created_at DESC, id DESC
                """, user.tenantId(), bizType, bizId);
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= MAX_VALUE_LENGTH ? value : value.substring(0, MAX_VALUE_LENGTH);
    }

    private static String asText(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return text.length() <= MAX_VALUE_LENGTH ? text : text.substring(0, MAX_VALUE_LENGTH);
    }

    public Map<String, Object> toChangeLogView(List<Map<String, Object>> rows) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", rows);
        return result;
    }
}
