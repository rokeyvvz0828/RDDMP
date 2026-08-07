package com.ccb.system.formmetadata;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.ccb.system.service.SystemService;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
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

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final SystemService systemService;

    public BusinessFormMetadataService(JdbcTemplate jdbc, ObjectMapper objectMapper, SystemService systemService) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.systemService = systemService;
    }

    public List<Map<String, Object>> listScopes(String keyword, AuthUser user) {
        systemService.requireAction("form-metadata", "read", user);
        String like = keyword == null || keyword.isBlank() ? "%" : "%" + keyword.trim() + "%";
        return jdbc.queryForList("""
                SELECT s.id, s.scope_key, s.scope_name, s.module_key, s.entity_type, s.form_key,
                       s.status_field, s.permission_prefix, s.published_revision_id, s.enabled,
                       COUNT(DISTINCT sec.id) section_count, COUNT(DISTINCT f.id) field_count
                FROM biz_form_scope s
                LEFT JOIN biz_form_section sec ON sec.scope_id = s.id AND sec.tenant_id = s.tenant_id AND sec.deleted = 0
                LEFT JOIN biz_form_field_definition f ON f.scope_id = s.id AND f.tenant_id = s.tenant_id AND f.deleted = 0
                WHERE s.tenant_id = ? AND s.deleted = 0 AND (s.scope_key LIKE ? OR s.scope_name LIKE ? OR s.module_key LIKE ?)
                GROUP BY s.id, s.scope_key, s.scope_name, s.module_key, s.entity_type, s.form_key,
                         s.status_field, s.permission_prefix, s.published_revision_id, s.enabled
                ORDER BY s.module_key, s.scope_key
                """, user.tenantId(), like, like, like);
    }

    public Map<String, Object> schema(long scopeId, AuthUser user) {
        systemService.requireAction("form-metadata", "read", user);
        Map<String, Object> scope = findScope(scopeId, user.tenantId());
        List<Map<String, Object>> sections = jdbc.queryForList("SELECT id, scope_id, section_key, title, layout_mode, show_title, collapsed, sort_no, is_builtin, enabled FROM biz_form_section WHERE tenant_id = ? AND scope_id = ? AND deleted = 0 ORDER BY sort_no, id", user.tenantId(), scopeId);
        List<Map<String, Object>> fields = jdbc.queryForList("SELECT id, scope_id, section_id, field_key, label, field_kind, input_type, value_type, source_type, source_key, component_key, native_column, multiple, column_span, visible, list_visible, filterable, sortable, dashboard_dimension, placeholder, help_text, default_value_json, sort_no, is_builtin, enabled FROM biz_form_field_definition WHERE tenant_id = ? AND scope_id = ? AND deleted = 0 ORDER BY sort_no, id", user.tenantId(), scopeId);
        for (Map<String, Object> field : fields) {
            field.put("rules", jdbc.queryForList("SELECT id, action_code, condition_type, condition_key, required, editable, visible, validation_json, enabled FROM biz_form_field_rule WHERE tenant_id = ? AND field_definition_id = ? AND deleted = 0 ORDER BY id", user.tenantId(), field.get("id")));
            field.put("options", jdbc.queryForList("SELECT id, option_value, option_label, option_group, sort_no, enabled FROM biz_form_field_option WHERE tenant_id = ? AND field_definition_id = ? AND deleted = 0 ORDER BY sort_no, id", user.tenantId(), field.get("id")));
        }
        List<Map<String, Object>> revisions = jdbc.queryForList("SELECT id, revision_no, revision_status, change_summary, created_by, published_by, created_at, published_at FROM biz_form_config_revision WHERE tenant_id = ? AND scope_id = ? ORDER BY revision_no DESC", user.tenantId(), scopeId);
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
        Integer duplicate = jdbc.queryForObject("SELECT COUNT(*) FROM biz_form_scope WHERE tenant_id = ? AND scope_key = ? AND deleted = 0", Integer.class, user.tenantId(), scopeKey);
        if (duplicate != null && duplicate > 0) throw bad("业务范围编码已存在");
        long id = nextId();
        jdbc.update("INSERT INTO biz_form_scope (id, tenant_id, scope_key, scope_name, module_key, entity_type, form_key, status_field, permission_prefix, enabled, created_by, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?)", id, user.tenantId(), scopeKey, name, moduleKey, entityType, formKey, input.get("status_field"), permissionPrefix, user.id(), user.id());
        systemService.auditOperation(user, "system:form-metadata:scope-create");
        return findScope(id, user.tenantId());
    }

    @Transactional
    public Map<String, Object> updateScope(long scopeId, Map<String, Object> input, AuthUser user) {
        systemService.requireAction("form-metadata", "update", user);
        findScope(scopeId, user.tenantId());
        Map<String, Object> values = new LinkedHashMap<>();
        copy(values, input, "scope_name", "module_key", "entity_type", "form_key", "status_field", "permission_prefix", "enabled");
        if (values.isEmpty()) throw bad("没有可更新的业务范围字段");
        values.put("updated_by", user.id());
        update("biz_form_scope", "id", scopeId, user.tenantId(), values);
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
            jdbc.update("INSERT INTO biz_form_section (id, tenant_id, scope_id, section_key, title, layout_mode, show_title, collapsed, sort_no, is_builtin, enabled, created_by, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 1, ?, ?)", id, user.tenantId(), scopeId, key, title, layout, bool(input.get("show_title"), true), bool(input.get("collapsed"), false), sortNo, user.id(), user.id());
            sectionId = id;
        } else {
            ensureSection(sectionId, scopeId, user.tenantId());
            Map<String, Object> values = new LinkedHashMap<>();
            copy(values, input, "section_key", "title", "layout_mode", "show_title", "collapsed", "sort_no", "enabled");
            values.put("updated_by", user.id());
            update("biz_form_section", "id", sectionId, user.tenantId(), values);
        }
        systemService.auditOperation(user, "system:form-metadata:section-save");
        return jdbc.queryForMap("SELECT id, scope_id, section_key, title, layout_mode, show_title, collapsed, sort_no, is_builtin, enabled FROM biz_form_section WHERE id = ? AND tenant_id = ? AND deleted = 0", sectionId, user.tenantId());
    }

    @Transactional
    public void deleteSection(long scopeId, long sectionId, AuthUser user) {
        systemService.requireAction("form-metadata", "delete", user);
        ensureSection(sectionId, scopeId, user.tenantId());
        Integer fields = jdbc.queryForObject("SELECT COUNT(*) FROM biz_form_field_definition WHERE tenant_id = ? AND section_id = ? AND deleted = 0", Integer.class, user.tenantId(), sectionId);
        if (fields != null && fields > 0) throw bad("分区下仍有字段，请先移除字段后再删除分区");
        jdbc.update("UPDATE biz_form_section SET deleted = 1, updated_at = CURRENT_TIMESTAMP, updated_by = ? WHERE id = ? AND tenant_id = ?", user.id(), sectionId, user.tenantId());
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
        Map<String, Object> values = new LinkedHashMap<>();
        copy(values, input, "section_id", "field_key", "label", "field_kind", "input_type", "value_type", "source_type", "source_key", "component_key", "native_column", "multiple", "column_span", "visible", "list_visible", "filterable", "sortable", "dashboard_dimension", "placeholder", "help_text", "default_value_json", "sort_no", "enabled");
        if (fieldId == null) {
            Integer duplicate = jdbc.queryForObject("SELECT COUNT(*) FROM biz_form_field_definition WHERE tenant_id = ? AND scope_id = ? AND field_key = ? AND deleted = 0", Integer.class, user.tenantId(), scopeId, fieldKey);
            if (duplicate != null && duplicate > 0) throw bad("字段编码在当前业务范围内已存在");
            fieldId = nextId();
            values.put("id", fieldId);
            values.put("tenant_id", user.tenantId());
            values.put("scope_id", scopeId);
            values.put("created_by", user.id());
            values.put("updated_by", user.id());
            insert("biz_form_field_definition", values);
        } else {
            ensureField(fieldId, scopeId, user.tenantId());
            values.put("updated_by", user.id());
            update("biz_form_field_definition", "id", fieldId, user.tenantId(), values);
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
        jdbc.update("UPDATE biz_form_field_definition SET deleted = 1, updated_at = CURRENT_TIMESTAMP, updated_by = ? WHERE id = ? AND tenant_id = ?", user.id(), fieldId, user.tenantId());
        jdbc.update("UPDATE biz_form_field_rule SET deleted = 1, updated_at = CURRENT_TIMESTAMP, updated_by = ? WHERE field_definition_id = ? AND tenant_id = ? AND deleted = 0", user.id(), fieldId, user.tenantId());
        jdbc.update("UPDATE biz_form_field_option SET deleted = 1, updated_at = CURRENT_TIMESTAMP, updated_by = ? WHERE field_definition_id = ? AND tenant_id = ? AND deleted = 0", user.id(), fieldId, user.tenantId());
        systemService.auditOperation(user, "system:form-metadata:field-delete");
    }

    @Transactional
    public Map<String, Object> publish(long scopeId, String summary, AuthUser user) {
        systemService.requireAction("form-metadata", "update", user);
        Map<String, Object> snapshot = schema(scopeId, user);
        String json;
        try { json = objectMapper.writeValueAsString(snapshot); }
        catch (JsonProcessingException exception) { throw bad("配置快照生成失败"); }
        Integer revisionNo = jdbc.queryForObject("SELECT COALESCE(MAX(revision_no), 0) + 1 FROM biz_form_config_revision WHERE tenant_id = ? AND scope_id = ?", Integer.class, user.tenantId(), scopeId);
        long revisionId = nextId();
        jdbc.update("UPDATE biz_form_config_revision SET revision_status = 'archived' WHERE tenant_id = ? AND scope_id = ? AND revision_status = 'published'", user.tenantId(), scopeId);
        jdbc.update("INSERT INTO biz_form_config_revision (id, tenant_id, scope_id, revision_no, revision_status, snapshot_json, change_summary, created_by, published_by, published_at) VALUES (?, ?, ?, ?, 'published', ?, ?, ?, ?, CURRENT_TIMESTAMP)", revisionId, user.tenantId(), scopeId, revisionNo, json, summary, user.id(), user.id());
        jdbc.update("UPDATE biz_form_scope SET published_revision_id = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND tenant_id = ? AND deleted = 0", revisionId, user.id(), scopeId, user.tenantId());
        systemService.auditOperation(user, "system:form-metadata:publish");
        return Map.of("revisionId", revisionId, "revisionNo", revisionNo);
    }

    private Map<String, Object> findScope(long id, long tenantId) {
        try { return jdbc.queryForMap("SELECT id, scope_key, scope_name, module_key, entity_type, form_key, status_field, permission_prefix, published_revision_id, enabled FROM biz_form_scope WHERE id = ? AND tenant_id = ? AND deleted = 0", id, tenantId); }
        catch (EmptyResultDataAccessException exception) { throw bad("业务范围不存在"); }
    }

    private Map<String, Object> field(long scopeId, long fieldId, long tenantId) {
        Map<String, Object> field = jdbc.queryForMap("SELECT id, scope_id, section_id, field_key, label, field_kind, input_type, value_type, source_type, source_key, component_key, native_column, multiple, column_span, visible, list_visible, filterable, sortable, dashboard_dimension, placeholder, help_text, default_value_json, sort_no, is_builtin, enabled FROM biz_form_field_definition WHERE id = ? AND scope_id = ? AND tenant_id = ? AND deleted = 0", fieldId, scopeId, tenantId);
        field.put("rules", jdbc.queryForList("SELECT id, action_code, condition_type, condition_key, required, editable, visible, validation_json, enabled FROM biz_form_field_rule WHERE tenant_id = ? AND field_definition_id = ? AND deleted = 0 ORDER BY id", tenantId, fieldId));
        field.put("options", jdbc.queryForList("SELECT id, option_value, option_label, option_group, sort_no, enabled FROM biz_form_field_option WHERE tenant_id = ? AND field_definition_id = ? AND deleted = 0 ORDER BY sort_no, id", tenantId, fieldId));
        return field;
    }

    private void replaceRules(long fieldId, long tenantId, Object raw, long operatorId) {
        if (!(raw instanceof List<?> rules)) throw bad("规则必须是数组");
        jdbc.update("UPDATE biz_form_field_rule SET deleted = 1, updated_at = CURRENT_TIMESTAMP, updated_by = ? WHERE tenant_id = ? AND field_definition_id = ? AND deleted = 0", operatorId, tenantId, fieldId);
        for (Object item : rules) {
            if (!(item instanceof Map<?, ?> rule)) throw bad("规则格式无效");
            String action = text(rule.get("action_code"), "action_code");
            String conditionType = optional(rule, "condition_type", "status");
            String conditionKey = text(rule.get("condition_key"), "condition_key");
            if (!ACTIONS.contains(action) || !CONDITION_TYPES.contains(conditionType)) throw bad("规则动作或条件类型无效");
            jdbc.update("INSERT INTO biz_form_field_rule (id, tenant_id, field_definition_id, action_code, condition_type, condition_key, required, editable, visible, validation_json, enabled, created_by, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?)", nextId(), tenantId, fieldId, action, conditionType, conditionKey, bool(rule.get("required"), false), bool(rule.get("editable"), true), bool(rule.get("visible"), true), json(rule.get("validation_json")), operatorId, operatorId);
        }
    }

    private void replaceOptions(long fieldId, long tenantId, Object raw, long operatorId) {
        if (!(raw instanceof List<?> options)) throw bad("选项必须是数组");
        jdbc.update("UPDATE biz_form_field_option SET deleted = 1, updated_at = CURRENT_TIMESTAMP, updated_by = ? WHERE tenant_id = ? AND field_definition_id = ? AND deleted = 0", operatorId, tenantId, fieldId);
        for (Object item : options) {
            if (!(item instanceof Map<?, ?> option)) throw bad("选项格式无效");
            String value = text(option.get("option_value"), "option_value");
            String label = text(option.get("option_label"), "option_label");
            jdbc.update("INSERT INTO biz_form_field_option (id, tenant_id, field_definition_id, option_value, option_label, option_group, sort_no, enabled, created_by, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, 1, ?, ?)", nextId(), tenantId, fieldId, value, label, option.get("option_group"), integer(option.get("sort_no"), 0), operatorId, operatorId);
        }
    }

    private void ensureSection(long sectionId, long scopeId, long tenantId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM biz_form_section WHERE id = ? AND scope_id = ? AND tenant_id = ? AND deleted = 0", Integer.class, sectionId, scopeId, tenantId);
        if (count == null || count == 0) throw bad("分区不存在或不属于当前业务范围");
    }

    private void ensureField(long fieldId, long scopeId, long tenantId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM biz_form_field_definition WHERE id = ? AND scope_id = ? AND tenant_id = ? AND deleted = 0", Integer.class, fieldId, scopeId, tenantId);
        if (count == null || count == 0) throw bad("字段不存在或不属于当前业务范围");
    }

    private void copy(Map<String, Object> target, Map<String, Object> input, String... keys) { for (String key : keys) if (input.containsKey(key)) target.put(key, input.get(key)); }
    private void update(String table, String idColumn, long id, long tenantId, Map<String, Object> fields) {
        List<Object> args = new ArrayList<>(fields.values());
        String assignments = fields.keySet().stream().map(key -> key + " = ?").reduce((a, b) -> a + ", " + b).orElseThrow();
        args.add(id); args.add(tenantId);
        if (jdbc.update("UPDATE " + table + " SET " + assignments + " WHERE " + idColumn + " = ? AND tenant_id = ? AND deleted = 0", args.toArray()) == 0) throw bad("记录不存在");
    }
    private void insert(String table, Map<String, Object> fields) { String columns = String.join(", ", fields.keySet()); String placeholders = fields.keySet().stream().map(key -> "?").reduce((a, b) -> a + ", " + b).orElseThrow(); jdbc.update("INSERT INTO " + table + " (" + columns + ") VALUES (" + placeholders + ")", fields.values().toArray()); }
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
