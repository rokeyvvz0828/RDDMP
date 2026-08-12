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
import java.util.concurrent.ThreadLocalRandom;

@Service
public class BusinessFormViewService {
    private static final Set<String> VIEW_TYPES = Set.of("form", "detail", "list", "approval", "wizard");
    private static final Set<String> VIEW_ROLES = Set.of("list", "detail", "create", "edit", "approval");
    private static final Set<String> FORM_MODES = Set.of("single", "wizard", "none");
    private static final Set<String> BLOCK_TYPES = Set.of("form", "list", "wizard", "detail", "workflow", "chart", "attachment", "timeline");
    private static final Set<String> FIXED_POSITIONS = Set.of("left", "right", "none");
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final SystemService systemService;

    public BusinessFormViewService(JdbcTemplate jdbc, ObjectMapper objectMapper, SystemService systemService) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.systemService = systemService;
    }

    public List<Map<String, Object>> listViews(long scopeId, AuthUser user) {
        systemService.requireAction("form-metadata", "read", user);
        ensureScope(scopeId, user.tenantId());
        List<Map<String, Object>> views = jdbc.queryForList("SELECT id, scope_id, view_key, view_name, view_type, view_group_key, view_role, form_mode, layout_json, published_revision_id, enabled FROM biz_form_view WHERE tenant_id = ? AND scope_id = ? AND deleted = 0 ORDER BY FIELD(view_role, 'list', 'detail', 'create', 'edit', 'approval'), form_mode, view_key", user.tenantId(), scopeId);
        views.forEach(this::normalizeView);
        return views;
    }

    public Map<String, Object> schema(long scopeId, long viewId, AuthUser user) {
        systemService.requireAction("form-metadata", "read", user);
        Map<String, Object> view = ensureView(viewId, scopeId, user.tenantId());
        List<Map<String, Object>> fields = jdbc.queryForList("""
                SELECT vf.id, vf.view_id, vf.field_definition_id, vf.view_section_key, vf.sort_no, vf.visible,
                       vf.editable, vf.required, vf.column_span, vf.column_width, vf.fixed_position,
                       vf.filter_operator, vf.display_format_json, vf.mobile_visible, vf.enabled,
                       f.field_key, f.label, f.input_type, f.value_type, f.form_available, f.source_type, f.source_key
                FROM biz_form_view_field vf
                JOIN biz_form_field_definition f ON f.id = vf.field_definition_id AND f.tenant_id = vf.tenant_id AND f.deleted = 0
                WHERE vf.tenant_id = ? AND vf.view_id = ? AND vf.deleted = 0
                ORDER BY vf.sort_no, vf.id
                """, user.tenantId(), viewId);
        List<Map<String, Object>> steps = jdbc.queryForList("SELECT id, view_id, step_key, title, description, validation_mode, sort_no, enabled FROM biz_form_view_step WHERE tenant_id = ? AND view_id = ? AND deleted = 0 ORDER BY sort_no, id", user.tenantId(), viewId);
        for (Map<String, Object> step : steps) {
            step.put("fields", jdbc.queryForList("""
                    SELECT sf.id, sf.step_id, sf.field_definition_id, sf.sort_no, sf.enabled, f.field_key, f.label, f.input_type
                    FROM biz_form_view_step_field sf
                    JOIN biz_form_field_definition f ON f.id = sf.field_definition_id AND f.tenant_id = sf.tenant_id AND f.deleted = 0
                    WHERE sf.tenant_id = ? AND sf.step_id = ? AND sf.deleted = 0 ORDER BY sf.sort_no, sf.id
                    """, user.tenantId(), step.get("id")));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("view", view);
        result.put("fields", fields);
        result.put("steps", steps);
        result.put("revisions", jdbc.queryForList("SELECT id, revision_no, revision_status, change_summary, created_at, published_at FROM biz_form_view_revision WHERE tenant_id = ? AND view_id = ? ORDER BY revision_no DESC", user.tenantId(), viewId));
        return result;
    }

    @Transactional
    public Map<String, Object> saveView(long scopeId, Long viewId, Map<String, Object> input, AuthUser user) {
        systemService.requireAction("form-metadata", viewId == null ? "create" : "update", user);
        ensureScope(scopeId, user.tenantId());
        String viewKey = required(input, "view_key");
        if (!viewKey.matches("[A-Za-z0-9_.-]+")) throw bad("view_key 只能包含字母、数字、点、下划线和短横线");
        String viewName = required(input, "view_name");
        Map<String, Object> existingView = viewId == null ? null : ensureView(viewId, scopeId, user.tenantId());
        String legacyViewType = optional(input, "view_type", existingView == null ? "form" : String.valueOf(existingView.get("view_type")));
        if (!VIEW_TYPES.contains(legacyViewType)) throw bad("视图类型不受支持");
        Map<String, Object> scope = ensureScope(scopeId, user.tenantId());
        String viewRole = optional(input, "view_role", existingView == null ? roleForLegacyType(legacyViewType) : String.valueOf(existingView.get("view_role")));
        String formMode = optional(input, "form_mode", existingView == null ? modeForLegacyType(legacyViewType) : String.valueOf(existingView.get("form_mode")));
        validateViewRoleAndMode(viewRole, formMode);
        String viewType = legacyTypeFor(viewRole, formMode);
        String viewGroupKey = optional(input, "view_group_key", existingView == null ? String.valueOf(scope.get("scope_key")) : String.valueOf(existingView.get("view_group_key")));
        if (!viewGroupKey.matches("[A-Za-z0-9_.-]+")) throw bad("view_group_key 只能包含字母、数字、点、下划线和短横线");
        Map<String, Object> values = new LinkedHashMap<>();
        copy(values, input, "view_key", "view_name", "layout_json", "enabled");
        values.put("view_type", viewType); values.put("view_group_key", viewGroupKey); values.put("view_role", viewRole); values.put("form_mode", formMode);
        if (viewId == null) {
            Integer duplicate = jdbc.queryForObject("SELECT COUNT(*) FROM biz_form_view WHERE tenant_id = ? AND scope_id = ? AND view_key = ? AND deleted = 0", Integer.class, user.tenantId(), scopeId, viewKey);
            if (duplicate != null && duplicate > 0) throw bad("视图编码已存在");
            viewId = nextId();
            values.put("id", viewId); values.put("tenant_id", user.tenantId()); values.put("scope_id", scopeId); values.put("created_by", user.id()); values.put("updated_by", user.id());
            insert("biz_form_view", values);
        } else {
            ensureView(viewId, scopeId, user.tenantId()); values.put("updated_by", user.id()); update("biz_form_view", "id", viewId, user.tenantId(), values);
        }
        systemService.auditOperation(user, "system:form-metadata:view-save");
        return ensureView(viewId, scopeId, user.tenantId());
    }

    @Transactional
    public Map<String, Object> saveViewField(long scopeId, long viewId, Long viewFieldId, Map<String, Object> input, AuthUser user) {
        systemService.requireAction("form-metadata", viewFieldId == null ? "create" : "update", user);
        Map<String, Object> view = ensureView(viewId, scopeId, user.tenantId());
        long fieldId = requiredLong(input, "field_definition_id");
        Map<String, Object> field = ensureField(fieldId, scopeId, user.tenantId());
        if ("list".equals(view.get("view_role")) && !bool(field.get("form_available"), true)) throw bad("列表字段必须先纳入业务表单字段集合");
        String fixed = optional(input, "fixed_position", "none");
        if (!FIXED_POSITIONS.contains(fixed)) throw bad("固定列位置无效");
        int span = integer(input.get("column_span"), 12);
        if (span < 1 || span > 24) throw bad("视图栅格宽度必须在 1 到 24 之间");
        Map<String, Object> values = new LinkedHashMap<>();
        copy(values, input, "field_definition_id", "view_section_key", "sort_no", "visible", "editable", "required", "column_span", "column_width", "fixed_position", "filter_operator", "display_format_json", "mobile_visible", "enabled");
        values.put("fixed_position", fixed);
        if (viewFieldId == null) {
            Integer duplicate = jdbc.queryForObject("SELECT COUNT(*) FROM biz_form_view_field WHERE tenant_id = ? AND view_id = ? AND field_definition_id = ? AND deleted = 0", Integer.class, user.tenantId(), viewId, fieldId);
            if (duplicate != null && duplicate > 0) throw bad("该字段已经加入当前视图");
            viewFieldId = nextId(); values.put("id", viewFieldId); values.put("tenant_id", user.tenantId()); values.put("view_id", viewId); values.put("created_by", user.id()); values.put("updated_by", user.id()); insert("biz_form_view_field", values);
        } else {
            ensureViewField(viewFieldId, viewId, user.tenantId()); values.put("updated_by", user.id()); update("biz_form_view_field", "id", viewFieldId, user.tenantId(), values);
        }
        systemService.auditOperation(user, "system:form-metadata:view-field-save");
        return Map.of("id", viewFieldId, "view_id", viewId, "field_definition_id", fieldId, "field_key", field.get("field_key"));
    }

    @Transactional
    public void deleteViewField(long scopeId, long viewId, long viewFieldId, AuthUser user) {
        systemService.requireAction("form-metadata", "delete", user); ensureView(viewId, scopeId, user.tenantId()); ensureViewField(viewFieldId, viewId, user.tenantId());
        jdbc.update("UPDATE biz_form_view_field SET deleted = 1, updated_at = CURRENT_TIMESTAMP, updated_by = ? WHERE id = ? AND tenant_id = ?", user.id(), viewFieldId, user.tenantId());
        systemService.auditOperation(user, "system:form-metadata:view-field-delete");
    }

    @Transactional
    public Map<String, Object> saveStep(long scopeId, long viewId, Long stepId, Map<String, Object> input, AuthUser user) {
        systemService.requireAction("form-metadata", stepId == null ? "create" : "update", user);
        Map<String, Object> view = ensureView(viewId, scopeId, user.tenantId()); if (!"wizard".equals(view.get("form_mode")) || !("create".equals(view.get("view_role")) || "edit".equals(view.get("view_role")))) throw bad("只有新建或编辑的分步页面可以维护步骤");
        String key = required(input, "step_key"); String title = required(input, "title");
        Map<String, Object> values = new LinkedHashMap<>(); copy(values, input, "step_key", "title", "description", "validation_mode", "sort_no", "enabled");
        if (stepId == null) { Integer duplicate = jdbc.queryForObject("SELECT COUNT(*) FROM biz_form_view_step WHERE tenant_id = ? AND view_id = ? AND step_key = ? AND deleted = 0", Integer.class, user.tenantId(), viewId, key); if (duplicate != null && duplicate > 0) throw bad("步骤编码已存在"); stepId = nextId(); values.put("id", stepId); values.put("tenant_id", user.tenantId()); values.put("view_id", viewId); values.put("created_by", user.id()); values.put("updated_by", user.id()); insert("biz_form_view_step", values); }
        else { ensureStep(stepId, viewId, user.tenantId()); values.put("updated_by", user.id()); update("biz_form_view_step", "id", stepId, user.tenantId(), values); }
        systemService.auditOperation(user, "system:form-metadata:view-step-save"); return Map.of("id", stepId, "view_id", viewId, "step_key", key, "title", title);
    }

    @Transactional
    public Map<String, Object> saveStepField(long scopeId, long viewId, long stepId, Long stepFieldId, Map<String, Object> input, AuthUser user) {
        systemService.requireAction("form-metadata", stepFieldId == null ? "create" : "update", user); ensureView(viewId, scopeId, user.tenantId()); ensureStep(stepId, viewId, user.tenantId());
        long fieldId = requiredLong(input, "field_definition_id"); Map<String, Object> field = ensureField(fieldId, scopeId, user.tenantId()); if (!bool(field.get("form_available"), true)) throw bad("步骤字段必须属于业务表单字段集合");
        Map<String, Object> values = new LinkedHashMap<>(); copy(values, input, "field_definition_id", "sort_no", "enabled");
        if (stepFieldId == null) { Integer duplicate = jdbc.queryForObject("SELECT COUNT(*) FROM biz_form_view_step_field WHERE tenant_id = ? AND step_id = ? AND field_definition_id = ? AND deleted = 0", Integer.class, user.tenantId(), stepId, fieldId); if (duplicate != null && duplicate > 0) throw bad("该字段已经加入当前步骤"); stepFieldId = nextId(); values.put("id", stepFieldId); values.put("tenant_id", user.tenantId()); values.put("step_id", stepId); values.put("created_by", user.id()); values.put("updated_by", user.id()); insert("biz_form_view_step_field", values); }
        else { ensureStepField(stepFieldId, stepId, user.tenantId()); values.put("updated_by", user.id()); update("biz_form_view_step_field", "id", stepFieldId, user.tenantId(), values); }
        systemService.auditOperation(user, "system:form-metadata:view-step-field-save"); return Map.of("id", stepFieldId, "step_id", stepId, "field_definition_id", fieldId, "field_key", field.get("field_key"));
    }

    @Transactional
    public Map<String, Object> publish(long scopeId, long viewId, String summary, AuthUser user) {
        systemService.requireAction("form-metadata", "update", user); Map<String, Object> snapshot = schema(scopeId, viewId, user); String json;
        try { json = objectMapper.writeValueAsString(snapshot); } catch (JsonProcessingException exception) { throw bad("视图配置快照生成失败"); }
        Integer revisionNo = jdbc.queryForObject("SELECT COALESCE(MAX(revision_no), 0) + 1 FROM biz_form_view_revision WHERE tenant_id = ? AND view_id = ?", Integer.class, user.tenantId(), viewId); long revisionId = nextId();
        jdbc.update("UPDATE biz_form_view_revision SET revision_status = 'archived' WHERE tenant_id = ? AND view_id = ? AND revision_status = 'published'", user.tenantId(), viewId);
        jdbc.update("INSERT INTO biz_form_view_revision (id, tenant_id, view_id, revision_no, revision_status, snapshot_json, change_summary, created_by, published_by, published_at) VALUES (?, ?, ?, ?, 'published', ?, ?, ?, ?, CURRENT_TIMESTAMP)", revisionId, user.tenantId(), viewId, revisionNo, json, summary, user.id(), user.id());
        jdbc.update("UPDATE biz_form_view SET published_revision_id = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND tenant_id = ? AND deleted = 0", revisionId, user.id(), viewId, user.tenantId());
        systemService.auditOperation(user, "system:form-metadata:view-publish"); return Map.of("revisionId", revisionId, "revisionNo", revisionNo);
    }

    public List<Map<String, Object>> listPages(String moduleKey, AuthUser user) {
        systemService.requireAction("form-metadata", "read", user);
        String like = moduleKey == null || moduleKey.isBlank() ? "%" : moduleKey.trim();
        return jdbc.queryForList("SELECT id, module_key, page_key, page_name, layout_json, enabled FROM biz_form_page WHERE tenant_id = ? AND module_key LIKE ? AND deleted = 0 ORDER BY module_key, page_key", user.tenantId(), like);
    }

    public Map<String, Object> pageSchema(long pageId, AuthUser user) {
        systemService.requireAction("form-metadata", "read", user);
        Map<String, Object> page = ensurePage(pageId, user.tenantId());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("page", page);
        result.put("blocks", jdbc.queryForList("SELECT id, page_id, block_key, block_type, module_key, ref_key, title, sort_no, grid_span, permission_code, config_json, enabled FROM biz_form_page_block WHERE tenant_id = ? AND page_id = ? AND deleted = 0 ORDER BY sort_no, id", user.tenantId(), pageId));
        return result;
    }

    @Transactional
    public Map<String, Object> savePage(Long pageId, Map<String, Object> input, AuthUser user) {
        systemService.requireAction("form-metadata", pageId == null ? "create" : "update", user);
        String moduleKey = required(input, "module_key"); String pageKey = required(input, "page_key"); String pageName = required(input, "page_name");
        if (!pageKey.matches("[A-Za-z0-9_.-]+")) throw bad("page_key 只能包含字母、数字、点、下划线和短横线");
        Map<String, Object> values = new LinkedHashMap<>(); copy(values, input, "module_key", "page_key", "page_name", "layout_json", "enabled");
        if (pageId == null) {
            Integer duplicate = jdbc.queryForObject("SELECT COUNT(*) FROM biz_form_page WHERE tenant_id = ? AND page_key = ? AND deleted = 0", Integer.class, user.tenantId(), pageKey);
            if (duplicate != null && duplicate > 0) throw bad("页面编码已存在");
            pageId = nextId(); values.put("id", pageId); values.put("tenant_id", user.tenantId()); values.put("created_by", user.id()); values.put("updated_by", user.id()); insert("biz_form_page", values);
        } else { ensurePage(pageId, user.tenantId()); values.put("updated_by", user.id()); update("biz_form_page", "id", pageId, user.tenantId(), values); }
        systemService.auditOperation(user, "system:form-metadata:page-save"); return ensurePage(pageId, user.tenantId());
    }

    @Transactional
    public Map<String, Object> saveBlock(long pageId, Long blockId, Map<String, Object> input, AuthUser user) {
        systemService.requireAction("form-metadata", blockId == null ? "create" : "update", user); ensurePage(pageId, user.tenantId());
        String blockKey = required(input, "block_key"); String blockType = required(input, "block_type"); String moduleKey = required(input, "module_key"); String refKey = required(input, "ref_key");
        if (!BLOCK_TYPES.contains(blockType)) throw bad("Block 类型不受支持"); int gridSpan = integer(input.get("grid_span"), 24); if (gridSpan < 1 || gridSpan > 24) throw bad("Block 栅格宽度必须在 1 到 24 之间");
        Map<String, Object> values = new LinkedHashMap<>(); copy(values, input, "block_key", "block_type", "module_key", "ref_key", "title", "sort_no", "grid_span", "permission_code", "config_json", "enabled");
        if (blockId == null) {
            Integer duplicate = jdbc.queryForObject("SELECT COUNT(*) FROM biz_form_page_block WHERE tenant_id = ? AND page_id = ? AND block_key = ? AND deleted = 0", Integer.class, user.tenantId(), pageId, blockKey);
            if (duplicate != null && duplicate > 0) throw bad("页面 Block 编码已存在");
            blockId = nextId(); values.put("id", blockId); values.put("tenant_id", user.tenantId()); values.put("page_id", pageId); values.put("created_by", user.id()); values.put("updated_by", user.id()); insert("biz_form_page_block", values);
        } else { ensureBlock(blockId, pageId, user.tenantId()); values.put("updated_by", user.id()); update("biz_form_page_block", "id", blockId, user.tenantId(), values); }
        systemService.auditOperation(user, "system:form-metadata:page-block-save"); return Map.of("id", blockId, "page_id", pageId, "block_key", blockKey, "block_type", blockType, "module_key", moduleKey, "ref_key", refKey);
    }

    @Transactional
    public void deleteBlock(long pageId, long blockId, AuthUser user) {
        systemService.requireAction("form-metadata", "delete", user); ensurePage(pageId, user.tenantId()); ensureBlock(blockId, pageId, user.tenantId());
        jdbc.update("UPDATE biz_form_page_block SET deleted = 1, updated_at = CURRENT_TIMESTAMP, updated_by = ? WHERE id = ? AND tenant_id = ?", user.id(), blockId, user.tenantId());
        systemService.auditOperation(user, "system:form-metadata:page-block-delete");
    }

    private Map<String, Object> ensureScope(long scopeId, long tenantId) { try { return jdbc.queryForMap("SELECT id, scope_key, scope_name, module_key, entity_type FROM biz_form_scope WHERE id = ? AND tenant_id = ? AND deleted = 0", scopeId, tenantId); } catch (EmptyResultDataAccessException exception) { throw bad("业务范围不存在"); } }
    private Map<String, Object> ensureView(long viewId, long scopeId, long tenantId) { try { Map<String, Object> view = jdbc.queryForMap("SELECT id, scope_id, view_key, view_name, view_type, view_group_key, view_role, form_mode, layout_json, published_revision_id, enabled FROM biz_form_view WHERE id = ? AND scope_id = ? AND tenant_id = ? AND deleted = 0", viewId, scopeId, tenantId); normalizeView(view); return view; } catch (EmptyResultDataAccessException exception) { throw bad("视图不存在或不属于当前业务范围"); } }
    private Map<String, Object> ensureField(long fieldId, long scopeId, long tenantId) { try { return jdbc.queryForMap("SELECT id, field_key, label, form_available, enabled FROM biz_form_field_definition WHERE id = ? AND scope_id = ? AND tenant_id = ? AND deleted = 0", fieldId, scopeId, tenantId); } catch (EmptyResultDataAccessException exception) { throw bad("字段不存在或不属于当前业务范围"); } }
    private void ensureViewField(long id, long viewId, long tenantId) { Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM biz_form_view_field WHERE id = ? AND view_id = ? AND tenant_id = ? AND deleted = 0", Integer.class, id, viewId, tenantId); if (count == null || count == 0) throw bad("视图字段不存在"); }
    private void ensureStep(long id, long viewId, long tenantId) { Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM biz_form_view_step WHERE id = ? AND view_id = ? AND tenant_id = ? AND deleted = 0", Integer.class, id, viewId, tenantId); if (count == null || count == 0) throw bad("步骤不存在"); }
    private void ensureStepField(long id, long stepId, long tenantId) { Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM biz_form_view_step_field WHERE id = ? AND step_id = ? AND tenant_id = ? AND deleted = 0", Integer.class, id, stepId, tenantId); if (count == null || count == 0) throw bad("步骤字段不存在"); }
    private Map<String, Object> ensurePage(long pageId, long tenantId) { try { return jdbc.queryForMap("SELECT id, module_key, page_key, page_name, layout_json, enabled FROM biz_form_page WHERE id = ? AND tenant_id = ? AND deleted = 0", pageId, tenantId); } catch (EmptyResultDataAccessException exception) { throw bad("页面不存在"); } }
    private void ensureBlock(long id, long pageId, long tenantId) { Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM biz_form_page_block WHERE id = ? AND page_id = ? AND tenant_id = ? AND deleted = 0", Integer.class, id, pageId, tenantId); if (count == null || count == 0) throw bad("页面 Block 不存在"); }
    private void normalizeView(Map<String, Object> view) { String legacy = String.valueOf(view.getOrDefault("view_type", "form")); String role = optional(view, "view_role", roleForLegacyType(legacy)); String mode = optional(view, "form_mode", modeForLegacyType(legacy)); view.put("view_role", role); view.put("form_mode", mode); view.putIfAbsent("view_group_key", ""); }
    private String roleForLegacyType(String viewType) { return switch (viewType) { case "list" -> "list"; case "detail" -> "detail"; case "approval" -> "approval"; default -> "create"; }; }
    private String modeForLegacyType(String viewType) { return "wizard".equals(viewType) ? "wizard" : ("form".equals(viewType) ? "single" : "none"); }
    private String legacyTypeFor(String role, String mode) { return switch (role) { case "create", "edit" -> "wizard".equals(mode) ? "wizard" : "form"; default -> role; }; }
    private void validateViewRoleAndMode(String role, String mode) { if (!VIEW_ROLES.contains(role)) throw bad("页面角色不受支持"); if (!FORM_MODES.contains(mode)) throw bad("表单模式不受支持"); if (("list".equals(role) || "detail".equals(role) || "approval".equals(role)) && !"none".equals(mode)) throw bad("列表、详情和审批页面不能配置表单模式"); if (("create".equals(role) || "edit".equals(role)) && !Set.of("single", "wizard").contains(mode)) throw bad("新建和编辑页面必须选择单页表单或分步表单"); }
    private String required(Map<String, ?> input, String key) { String value = input.get(key) == null ? "" : String.valueOf(input.get(key)).trim(); if (value.isBlank()) throw bad(key + " 不能为空"); return value; }
    private long requiredLong(Map<String, ?> input, String key) { try { return Long.parseLong(required(input, key)); } catch (NumberFormatException exception) { throw bad(key + " 必须是数字"); } }
    private String optional(Map<?, ?> input, String key, String fallback) { return input.get(key) == null || String.valueOf(input.get(key)).isBlank() ? fallback : String.valueOf(input.get(key)); }
    private int integer(Object value, int fallback) { if (value == null) return fallback; try { return Integer.parseInt(String.valueOf(value)); } catch (NumberFormatException exception) { throw bad("数字字段格式无效"); } }
    private boolean bool(Object value, boolean fallback) { if (value == null) return fallback; if (value instanceof Boolean b) return b; return "1".equals(String.valueOf(value)) || "true".equalsIgnoreCase(String.valueOf(value)); }
    private void copy(Map<String, Object> target, Map<String, Object> input, String... keys) { for (String key : keys) if (input.containsKey(key)) target.put(key, input.get(key)); }
    private void update(String table, String idColumn, long id, long tenantId, Map<String, Object> fields) { List<Object> args = new ArrayList<>(fields.values()); String assignments = fields.keySet().stream().map(key -> key + " = ?").reduce((a, b) -> a + ", " + b).orElseThrow(); args.add(id); args.add(tenantId); if (jdbc.update("UPDATE " + table + " SET " + assignments + " WHERE " + idColumn + " = ? AND tenant_id = ? AND deleted = 0", args.toArray()) == 0) throw bad("记录不存在"); }
    private void insert(String table, Map<String, Object> fields) { String columns = String.join(", ", fields.keySet()); String placeholders = fields.keySet().stream().map(key -> "?").reduce((a, b) -> a + ", " + b).orElseThrow(); jdbc.update("INSERT INTO " + table + " (" + columns + ") VALUES (" + placeholders + ")", fields.values().toArray()); }
    private BusinessException bad(String message) { return new BusinessException(ErrorCode.BAD_REQUEST, message); }
    private long nextId() { return System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000); }
}
