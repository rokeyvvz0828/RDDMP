package com.ccb.system.formmetadata;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.ccb.system.service.SystemService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class BusinessFormMetadataService {
    private static final Set<String> FIELD_KINDS = Set.of("builtin", "extension");
    private static final Set<String> INPUT_TYPES = Set.of("text", "textarea", "number", "date", "datetime", "select", "radio", "checkbox", "boolean", "person", "organization", "user", "attachment", "rich_text", "json");
    private static final Set<String> VALUE_TYPES = Set.of("string", "text", "code", "integer", "decimal", "date", "datetime", "boolean", "reference", "json");
    private static final Set<String> SOURCE_TYPES = Set.of("none", "static", "dict", "user", "organization", "role", "attachment", "api");
    private static final Set<String> ACTIONS = Set.of("create", "edit", "submit", "approve", "view");
    private static final Set<String> CONDITION_TYPES = Set.of("status", "role", "expression");

    private final BusinessFormMetadataRepository repository;
    private final ObjectMapper objectMapper;
    private final SystemService systemService;

    public BusinessFormMetadataService(BusinessFormMetadataRepository repository, ObjectMapper objectMapper, SystemService systemService) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.systemService = systemService;
    }

    public List<Map<String, Object>> listScopes(String keyword, AuthUser user) {
        systemService.requireAction("form-metadata", "read", user);
        String like = keyword == null || keyword.isBlank() ? "%" : "%" + keyword.trim() + "%";
        return repository.listScopes(params("tenantId", user.tenantId(), "like", like));
    }

    public Map<String, Object> schema(long scopeId, AuthUser user) {
        systemService.requireAction("form-metadata", "read", user);
        Map<String, Object> scope = findScope(scopeId, user.tenantId());
        List<Map<String, Object>> sections = repository.sections(params("tenantId", user.tenantId(), "scopeId", scopeId));
        List<Map<String, Object>> fields = repository.fields(params("tenantId", user.tenantId(), "scopeId", scopeId));
        for (Map<String, Object> field : fields) {
            field.put("rules", repository.rules(params("tenantId", user.tenantId(), "fieldId", field.get("id"))));
            field.put("options", repository.options(params("tenantId", user.tenantId(), "fieldId", field.get("id"))));
        }
        List<Map<String, Object>> revisions = repository.revisions(params("tenantId", user.tenantId(), "scopeId", scopeId));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scope", scope);
        result.put("sections", sections);
        result.put("fields", fields);
        result.put("revisions", revisions);
        return result;
    }

    @Transactional
    public Map<String, Object> createScope(Map<String, Object> input, AuthUser user) {
        systemService.requireAction("form-metadata", "create", user);
        String scopeKey = required(input, "scope_key");
        if (!scopeKey.matches("[A-Za-z0-9_.-]+")) throw bad("scope_key 只能包含字母、数字、点、下划线和短横线");
        String name = required(input, "scope_name");
        String moduleKey = required(input, "module_key");
        String entityType = required(input, "entity_type");
        String formKey = optional(input, "form_key", "default");
        String permissionPrefix = required(input, "permission_prefix");
        Integer duplicate = repository.scopeDuplicate(params("tenantId", user.tenantId(), "scopeKey", scopeKey));
        if (duplicate != null && duplicate > 0) throw bad("业务范围编码已存在");
        long id = nextId();
        repository.insertScope(params("id", id, "tenantId", user.tenantId(), "scopeKey", scopeKey, "scopeName", name, "moduleKey", moduleKey, "entityType", entityType, "formKey", formKey, "statusField", input.get("status_field"), "permissionPrefix", permissionPrefix, "operatorId", user.id()));
        systemService.auditOperation(user, "system:form-metadata:scope-create");
        return findScope(id, user.tenantId());
    }

    @Transactional
    public Map<String, Object> updateScope(long scopeId, Map<String, Object> input, AuthUser user) {
        systemService.requireAction("form-metadata", "update", user);
        findScope(scopeId, user.tenantId());
        Map<String, Object> values = camel(input, "scope_name", "module_key", "entity_type", "form_key", "status_field", "permission_prefix", "enabled");
        if (values.isEmpty()) throw bad("没有可更新的业务范围字段");
        values.putAll(params("scopeId", scopeId, "tenantId", user.tenantId(), "operatorId", user.id()));
        if (repository.updateScope(values) == 0) throw bad("记录不存在");
        systemService.auditOperation(user, "system:form-metadata:scope-update");
        return findScope(scopeId, user.tenantId());
    }

    @Transactional
    public Map<String, Object> saveSection(long scopeId, Long sectionId, Map<String, Object> input, AuthUser user) {
        systemService.requireAction("form-metadata", sectionId == null ? "create" : "update", user);
        findScope(scopeId, user.tenantId());
        String key = required(input, "section_key");
        String title = required(input, "title");
        String layout = optional(input, "layout_mode", "left");
        if (!Set.of("left", "right", "full").contains(layout)) throw bad("分区布局只能是 left、right 或 full");
        int sortNo = integer(input.get("sort_no"), 0);
        if (sectionId == null) {
            long id = nextId();
            repository.insertSection(params("id", id, "tenantId", user.tenantId(), "scopeId", scopeId, "sectionKey", key, "title", title, "layoutMode", layout, "showTitle", bool(input.get("show_title"), true), "collapsed", bool(input.get("collapsed"), false), "sortNo", sortNo, "operatorId", user.id()));
            sectionId = id;
        } else {
            ensureSection(sectionId, scopeId, user.tenantId());
            Map<String, Object> values = camel(input, "section_key", "title", "layout_mode", "show_title", "collapsed", "sort_no", "enabled");
            values.putAll(params("sectionId", sectionId, "tenantId", user.tenantId(), "operatorId", user.id()));
            if (repository.updateSection(values) == 0) throw bad("记录不存在");
        }
        systemService.auditOperation(user, "system:form-metadata:section-save");
        return repository.section(params("sectionId", sectionId, "tenantId", user.tenantId()));
    }

    @Transactional
    public void deleteSection(long scopeId, long sectionId, AuthUser user) {
        systemService.requireAction("form-metadata", "delete", user);
        ensureSection(sectionId, scopeId, user.tenantId());
        Integer fields = repository.fieldCountForSection(params("tenantId", user.tenantId(), "sectionId", sectionId));
        if (fields != null && fields > 0) throw bad("分区下仍有字段，请先移除字段后再删除分区");
        repository.deleteSection(params("operatorId", user.id(), "sectionId", sectionId, "tenantId", user.tenantId()));
        systemService.auditOperation(user, "system:form-metadata:section-delete");
    }

    @Transactional
    public Map<String, Object> saveField(long scopeId, Long fieldId, Map<String, Object> input, AuthUser user) {
        systemService.requireAction("form-metadata", fieldId == null ? "create" : "update", user);
        findScope(scopeId, user.tenantId());
        String fieldKey = required(input, "field_key");
        String label = required(input, "label");
        String kind = optional(input, "field_kind", "extension");
        String inputType = optional(input, "input_type", "text");
        String valueType = optional(input, "value_type", "string");
        String sourceType = optional(input, "source_type", "none");
        if (!FIELD_KINDS.contains(kind)) throw bad("字段类型只能是 builtin 或 extension");
        if (!INPUT_TYPES.contains(inputType)) throw bad("不支持的输入控件类型");
        if (!VALUE_TYPES.contains(valueType)) throw bad("不支持的值类型");
        if (!SOURCE_TYPES.contains(sourceType)) throw bad("不支持的选项来源");
        int span = integer(input.get("column_span"), 12);
        if (span < 1 || span > 24) throw bad("字段栅格宽度必须在 1 到 24 之间");
        Long sectionId = nullableLong(input.get("section_id"));
        if (sectionId != null) ensureSection(sectionId, scopeId, user.tenantId());
        Map<String, Object> values = camel(input, "section_id", "field_key", "label", "field_kind", "input_type", "value_type", "source_type", "source_key", "component_key", "native_column", "multiple", "column_span", "visible", "list_visible", "filterable", "sortable", "dashboard_dimension", "placeholder", "help_text", "default_value_json", "sort_no", "enabled");
        if (fieldId == null) {
            Integer duplicate = repository.fieldDuplicate(params("tenantId", user.tenantId(), "scopeId", scopeId, "fieldKey", fieldKey));
            if (duplicate != null && duplicate > 0) throw bad("字段编码在当前业务范围内已存在");
            fieldId = nextId();
            values.putAll(params("id", fieldId, "tenantId", user.tenantId(), "scopeId", scopeId, "operatorId", user.id()));
            repository.insertField(values);
        } else {
            ensureField(fieldId, scopeId, user.tenantId());
            values.putAll(params("fieldId", fieldId, "tenantId", user.tenantId(), "operatorId", user.id()));
            if (repository.updateField(values) == 0) throw bad("记录不存在");
        }
        if (input.containsKey("rules")) replaceRules(fieldId, user.tenantId(), input.get("rules"), user.id());
        if (input.containsKey("options")) replaceOptions(fieldId, user.tenantId(), input.get("options"), user.id());
        systemService.auditOperation(user, "system:form-metadata:field-save");
        return field(scopeId, fieldId, user.tenantId());
    }

    @Transactional
    public void deleteField(long scopeId, long fieldId, AuthUser user) {
        systemService.requireAction("form-metadata", "delete", user);
        ensureField(fieldId, scopeId, user.tenantId());
        repository.deleteField(params("operatorId", user.id(), "fieldId", fieldId, "tenantId", user.tenantId()));
        repository.deleteRules(params("operatorId", user.id(), "fieldId", fieldId, "tenantId", user.tenantId()));
        repository.deleteOptions(params("operatorId", user.id(), "fieldId", fieldId, "tenantId", user.tenantId()));
        systemService.auditOperation(user, "system:form-metadata:field-delete");
    }

    @Transactional
    public Map<String, Object> publish(long scopeId, String summary, AuthUser user) {
        systemService.requireAction("form-metadata", "update", user);
        Map<String, Object> snapshot = schema(scopeId, user);
        String json;
        try { json = objectMapper.writeValueAsString(snapshot); }
        catch (JsonProcessingException exception) { throw bad("配置快照生成失败"); }
        Integer revisionNo = repository.nextRevisionNo(params("tenantId", user.tenantId(), "scopeId", scopeId));
        long revisionId = nextId();
        repository.archivePublished(params("tenantId", user.tenantId(), "scopeId", scopeId));
        repository.insertRevision(params("id", revisionId, "tenantId", user.tenantId(), "scopeId", scopeId, "revisionNo", revisionNo, "snapshotJson", json, "summary", summary, "operatorId", user.id()));
        repository.publishScope(params("revisionId", revisionId, "operatorId", user.id(), "scopeId", scopeId, "tenantId", user.tenantId()));
        systemService.auditOperation(user, "system:form-metadata:publish");
        return Map.of("revisionId", revisionId, "revisionNo", revisionNo);
    }

    private Map<String, Object> findScope(long id, long tenantId) {
        Map<String, Object> scope = repository.scope(params("scopeId", id, "tenantId", tenantId));
        if (scope == null) throw bad("业务范围不存在");
        return scope;
    }

    private Map<String, Object> field(long scopeId, long fieldId, long tenantId) {
        Map<String, Object> field = repository.field(params("fieldId", fieldId, "scopeId", scopeId, "tenantId", tenantId));
        field.put("rules", repository.rules(params("tenantId", tenantId, "fieldId", fieldId)));
        field.put("options", repository.options(params("tenantId", tenantId, "fieldId", fieldId)));
        return field;
    }

    private void replaceRules(long fieldId, long tenantId, Object raw, long operatorId) {
        if (!(raw instanceof List<?> rules)) throw bad("规则必须是数组");
        repository.deleteRules(params("operatorId", operatorId, "tenantId", tenantId, "fieldId", fieldId));
        for (Object item : rules) {
            if (!(item instanceof Map<?, ?> rule)) throw bad("规则格式无效");
            String action = text(rule.get("action_code"), "action_code");
            String conditionType = optional(rule, "condition_type", "status");
            String conditionKey = text(rule.get("condition_key"), "condition_key");
            if (!ACTIONS.contains(action) || !CONDITION_TYPES.contains(conditionType)) throw bad("规则动作或条件类型无效");
            repository.insertRule(params("id", nextId(), "tenantId", tenantId, "fieldId", fieldId, "actionCode", action, "conditionType", conditionType, "conditionKey", conditionKey, "required", bool(rule.get("required"), false), "editable", bool(rule.get("editable"), true), "visible", bool(rule.get("visible"), true), "validationJson", json(rule.get("validation_json")), "operatorId", operatorId));
        }
    }

    private void replaceOptions(long fieldId, long tenantId, Object raw, long operatorId) {
        if (!(raw instanceof List<?> options)) throw bad("选项必须是数组");
        repository.deleteOptions(params("operatorId", operatorId, "tenantId", tenantId, "fieldId", fieldId));
        for (Object item : options) {
            if (!(item instanceof Map<?, ?> option)) throw bad("选项格式无效");
            String value = text(option.get("option_value"), "option_value");
            String label = text(option.get("option_label"), "option_label");
            repository.insertOption(params("id", nextId(), "tenantId", tenantId, "fieldId", fieldId, "optionValue", value, "optionLabel", label, "optionGroup", option.get("option_group"), "sortNo", integer(option.get("sort_no"), 0), "operatorId", operatorId));
        }
    }

    private void ensureSection(long sectionId, long scopeId, long tenantId) {
        Integer count = repository.sectionCount(params("sectionId", sectionId, "scopeId", scopeId, "tenantId", tenantId));
        if (count == null || count == 0) throw bad("分区不存在或不属于当前业务范围");
    }

    private void ensureField(long fieldId, long scopeId, long tenantId) {
        Integer count = repository.fieldCount(params("fieldId", fieldId, "scopeId", scopeId, "tenantId", tenantId));
        if (count == null || count == 0) throw bad("字段不存在或不属于当前业务范围");
    }

    private Map<String, Object> params(Object... pairs) { Map<String, Object> result = new LinkedHashMap<>(); for (int index = 0; index < pairs.length; index += 2) result.put((String) pairs[index], pairs[index + 1]); return result; }
    private Map<String, Object> camel(Map<String, Object> input, String... keys) { Map<String, Object> result = new LinkedHashMap<>(); for (String key : keys) if (input.containsKey(key)) result.put(toCamel(key), input.get(key)); return result; }
    private String toCamel(String value) { StringBuilder result = new StringBuilder(); boolean upper = false; for (char character : value.toCharArray()) { if (character == '_') upper = true; else { result.append(upper ? Character.toUpperCase(character) : character); upper = false; } } return result.toString(); }
    private String required(Map<String, ?> input, String key) { String value = input.get(key) == null ? "" : String.valueOf(input.get(key)).trim(); if (value.isBlank()) throw bad(key + " 不能为空"); return value; }
    private String text(Object value, String key) { String result = value == null ? "" : String.valueOf(value).trim(); if (result.isBlank()) throw bad(key + " 不能为空"); return result; }
    private String optional(Map<?, ?> input, String key, String fallback) { return input.get(key) == null || String.valueOf(input.get(key)).isBlank() ? fallback : String.valueOf(input.get(key)); }
    private int integer(Object value, int fallback) { if (value == null) return fallback; try { return Integer.parseInt(String.valueOf(value)); } catch (NumberFormatException exception) { throw bad("数字字段格式无效"); } }
    private boolean bool(Object value, boolean fallback) { if (value == null) return fallback; if (value instanceof Boolean bool) return bool; return "1".equals(String.valueOf(value)) || "true".equalsIgnoreCase(String.valueOf(value)); }
    private Long nullableLong(Object value) { if (value == null || String.valueOf(value).isBlank()) return null; try { return Long.valueOf(String.valueOf(value)); } catch (NumberFormatException exception) { throw bad("关联分区编号无效"); } }
    private String json(Object value) { if (value == null) return null; if (value instanceof String string) return string; try { return objectMapper.writeValueAsString(value); } catch (JsonProcessingException exception) { throw bad("JSON 配置格式无效"); } }
    private BusinessException bad(String message) { return new BusinessException(ErrorCode.BAD_REQUEST, message); }
    private long nextId() { return System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000); }
}
