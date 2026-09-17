package com.ccb.requirement.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ccb.requirement.support.RequirementIds;
import com.ccb.requirement.support.RequirementValues;

/** 系统清单主数据：两域共用，供涉及系统/主责系统选择与校验。 */
@Service
public class RequirementSystemService {
    private static final List<String> SYSTEM_FIELDS = List.of(
            "system_code", "system_name", "english_name", "conglomerate", "status",
            "logical_subsystem_code", "logical_subsystem_name", "business_component_code",
            "business_component_name", "business_domain", "product_view", "launch_point",
            "category", "introduction", "disaster_level", "source_type");

    private final RequirementSystemRepository repository;
    private final RequirementChangeLogService changeLog;

    public RequirementSystemService(RequirementSystemRepository repository, RequirementChangeLogService changeLog) {
        this.repository = repository;
        this.changeLog = changeLog;
    }

    public List<Map<String, Object>> list(AuthUser user) {
        return repository.list(user.tenantId());
    }

    public Map<String, Object> get(long id, AuthUser user) {
        Map<String, Object> row = repository.find(user.tenantId(), id);
        if (row == null || row.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "系统不存在");
        }
        return row;
    }

    @Transactional
    public Map<String, Object> create(Map<String, Object> body, AuthUser user) {
        String systemCode = RequirementValues.requireText(body, "system_code", "系统编号不能为空");
        String systemName = RequirementValues.requireText(body, "system_name", "系统名称不能为空");
        if (repository.existsByCode(user.tenantId(), systemCode)) {
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
        repository.insert(values);
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
        changes.put("id", id);
        changes.put("tenant_id", user.tenantId());
        repository.update(changes);
        Map<String, Object> after = get(id, user);
        changeLog.recordFields("SYSTEM", id, "UPDATE", before, after, user, "ONLINE");
        return after;
    }

    @Transactional
    public void delete(long id, AuthUser user) {
        Map<String, Object> row = get(id, user);
        repository.softDelete(user.tenantId(), id, user.id());
        changeLog.record("SYSTEM", id, "DELETE", "deleted", "0", "1", user, "ONLINE");
    }

    public long resolveSystemId(String systemCode, AuthUser user) {
        if (systemCode == null || systemCode.isBlank()) {
            return 0L;
        }
        return repository.findIdByCode(user.tenantId(), systemCode);
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
