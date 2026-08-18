package com.ccb.requirement.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ccb.requirement.support.RequirementIds;
import com.ccb.requirement.support.RequirementSql;
import com.ccb.requirement.support.RequirementValues;

/** 系统清单主数据：两域共用，供涉及系统/主责系统选择与校验。 */
@Service
public class RequirementSystemService {
    private static final List<String> SYSTEM_FIELDS = List.of(
            "system_code", "system_name", "english_name", "conglomerate", "status",
            "logical_subsystem_code", "logical_subsystem_name", "business_component_code",
            "business_component_name", "business_domain", "product_view", "launch_point",
            "category", "introduction", "disaster_level", "source_type");

    private final JdbcTemplate jdbc;
    private final RequirementChangeLogService changeLog;

    public RequirementSystemService(JdbcTemplate jdbc, RequirementChangeLogService changeLog) {
        this.jdbc = jdbc;
        this.changeLog = changeLog;
    }

    public List<Map<String, Object>> list(AuthUser user) {
        return jdbc.queryForList("""
                SELECT id, system_code, system_name, english_name, conglomerate, status,
                       logical_subsystem_code, logical_subsystem_name, business_component_code,
                       business_component_name, business_domain, product_view, launch_point,
                       category, introduction, disaster_level, source_type, created_at, updated_at
                FROM req_system WHERE tenant_id = ? AND deleted = 0 ORDER BY system_code
                """, user.tenantId());
    }

    public Map<String, Object> get(long id, AuthUser user) {
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT id, system_code, system_name, english_name, conglomerate, status,
                       logical_subsystem_code, logical_subsystem_name, business_component_code,
                       business_component_name, business_domain, product_view, launch_point,
                       category, introduction, disaster_level, source_type, created_at, updated_at
                FROM req_system WHERE tenant_id = ? AND id = ? AND deleted = 0
                """, user.tenantId(), id);
        if (row == null || row.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "系统不存在");
        }
        return row;
    }

    @Transactional
    public Map<String, Object> create(Map<String, Object> body, AuthUser user) {
        String systemCode = RequirementValues.requireText(body, "system_code", "系统编号不能为空");
        String systemName = RequirementValues.requireText(body, "system_name", "系统名称不能为空");
        Integer duplicate = jdbc.queryForObject(
                "SELECT COUNT(*) FROM req_system WHERE tenant_id = ? AND system_code = ? AND deleted = 0",
                Integer.class, user.tenantId(), systemCode);
        if (duplicate != null && duplicate > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "系统编号已存在：" + systemCode);
        }
        RequirementValues.requireOption("systemStatuses", RequirementValues.text(body, "status"));
        long id = RequirementIds.next();
        Map<String, Object> values = normalized(body);
        values.put("id", id);
        values.put("tenant_id", user.tenantId());
        values.putIfAbsent("status", "启用");
        values.put("created_by", user.id());
        values.put("deleted", 0);
        RequirementSql.insert(jdbc, "req_system", values);
        changeLog.recordCreate("SYSTEM", id, values, user, "ONLINE");
        return get(id, user);
    }

    @Transactional
    public Map<String, Object> update(long id, Map<String, Object> body, AuthUser user) {
        Map<String, Object> before = get(id, user);
        RequirementValues.requireOption("systemStatuses", RequirementValues.text(body, "status"));
        Map<String, Object> changes = normalized(body);
        changes.remove("system_code");
        if (changes.isEmpty()) {
            return before;
        }
        changes.put("updated_by", user.id());
        RequirementSql.update(jdbc, "req_system", id, user.tenantId(), changes);
        Map<String, Object> after = get(id, user);
        changeLog.recordFields("SYSTEM", id, "UPDATE", before, after, user, "ONLINE");
        return after;
    }

    @Transactional
    public void delete(long id, AuthUser user) {
        Map<String, Object> row = get(id, user);
        jdbc.update("UPDATE req_system SET deleted = 1, updated_by = ? WHERE tenant_id = ? AND id = ?",
                user.id(), user.tenantId(), id);
        changeLog.record("SYSTEM", id, "DELETE", "deleted", "0", "1", user, "ONLINE");
    }

    public long resolveSystemId(String systemCode, AuthUser user) {
        if (systemCode == null || systemCode.isBlank()) {
            return 0L;
        }
        List<Long> ids = jdbc.queryForList(
                "SELECT id FROM req_system WHERE tenant_id = ? AND system_code = ? AND deleted = 0 LIMIT 1",
                Long.class, user.tenantId(), systemCode);
        return ids.isEmpty() ? 0L : ids.get(0);
    }

    private Map<String, Object> normalized(Map<String, Object> body) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (String field : SYSTEM_FIELDS) {
            Object value = body.get(field);
            if (value != null) {
                values.put(field, value);
            }
        }
        return values;
    }
}
