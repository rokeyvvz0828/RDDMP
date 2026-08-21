package com.ccb.infrastructure.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
@Profile("local")
@ConditionalOnProperty(prefix = "ccb.mock-data", name = "enabled", havingValue = "true")
public class MockDataInitializer implements ApplicationRunner {
    private static final long STATE_ID = 920000000000001L;
    private static final Pattern SQL_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Map<String, Set<String>> ALLOWED_COLUMNS = allowedColumns();

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;
    private final MockDataProperties properties;

    public MockDataInitializer(JdbcTemplate jdbc, ObjectMapper objectMapper, ResourceLoader resourceLoader,
                               MockDataProperties properties) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        JsonNode root = readRoot();
        String datasetKey = requiredText(root, "datasetKey");
        String datasetVersion = requiredText(root, "datasetVersion");
        String checksum = sha256(root);
        int rowCount = 0;
        Set<Long> validatedArchitectureTenants = new HashSet<>();
        JsonNode database = root.get("database");
        if (database != null && !database.isNull()) {
            if (!database.isArray()) throw new IllegalStateException("mock database must be an array");
            for (JsonNode tableNode : database) rowCount += syncTable(tableNode, validatedArchitectureTenants);
        }
        jdbc.update("""
                INSERT INTO sys_mock_dataset_state (id, dataset_key, dataset_version, content_sha256, applied_at)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                ON DUPLICATE KEY UPDATE dataset_version = VALUES(dataset_version),
                    content_sha256 = VALUES(content_sha256), applied_at = CURRENT_TIMESTAMP
                """, STATE_ID, datasetKey, datasetVersion, checksum);
        System.out.printf("Mock data synchronized: dataset=%s version=%s rows=%d checksum=%s%n",
                datasetKey, datasetVersion, rowCount, checksum);
    }

    private JsonNode readRoot() {
        Resource resource = resourceLoader.getResource(properties.getResource());
        try (InputStream input = resource.getInputStream()) {
            return objectMapper.readTree(input);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read mock data resource: " + properties.getResource(), exception);
        }
    }

    private int syncTable(JsonNode tableNode, Set<Long> validatedArchitectureTenants) {
        if (!tableNode.isObject()) throw new IllegalStateException("mock table definition must be an object");
        String table = requiredText(tableNode, "table");
        Set<String> allowed = ALLOWED_COLUMNS.get(table);
        if (allowed == null) throw new IllegalStateException("mock table is not allowlisted: " + table);
        List<String> keys = textArray(tableNode, "keyColumns");
        if (keys.isEmpty() || !allowed.containsAll(keys)) {
            throw new IllegalStateException("mock key columns are invalid for table: " + table);
        }
        JsonNode rows = tableNode.get("rows");
        if (rows == null || !rows.isArray()) throw new IllegalStateException("mock rows must be an array for table: " + table);
        validateArchitectureRows(table, rows, validatedArchitectureTenants);
        int count = 0;
        for (JsonNode row : rows) {
            if (!row.isObject()) throw new IllegalStateException("mock row must be an object for table: " + table);
            count += upsert(table, allowed, keys, row);
        }
        return count;
    }

    private void validateArchitectureRows(String table, JsonNode rows, Set<Long> validatedTenants) {
        if (!"arch_logical_subsystem".equals(table) && !"arch_physical_subsystem".equals(table)) return;
        for (JsonNode row : rows) {
            if (!row.isObject()) throw new IllegalStateException("mock row must be an object for table: " + table);
            long tenantId = requiredPositiveLong(row, "tenant_id", table);
            if (validatedTenants.add(tenantId)) requireTenantRoot(tenantId);
            if ("arch_logical_subsystem".equals(table)) validateLogicalRow(row, tenantId);
            else validatePhysicalRow(row, tenantId);
        }
    }

    private void validateLogicalRow(JsonNode row, long tenantId) {
        requireActiveOrganization(tenantId, requiredPositiveLong(row, "business_org_id", "arch_logical_subsystem"), "事业群组织");
        requireActiveUser(tenantId, requiredPositiveLong(row, "contact_user_id", "arch_logical_subsystem"), "联系人");
        requireOptionalParameter(row, tenantId, "deployment_platform_code", "ARCH_DEPLOYMENT_PLATFORM");
        requireOptionalParameter(row, tenantId, "system_type_code", "ARCH_SYSTEM_TYPE");
        requireOptionalParameter(row, tenantId, "system_ownership_code", "ARCH_SYSTEM_OWNERSHIP");
    }

    private void validatePhysicalRow(JsonNode row, long tenantId) {
        long logicalId = requiredPositiveLong(row, "logical_subsystem_id", "arch_physical_subsystem");
        requireReference("SELECT COUNT(*) FROM arch_logical_subsystem WHERE tenant_id = ? AND id = ? AND deleted = 0",
                tenantId, logicalId, "所属逻辑子系统");
        long teamId = requiredPositiveLong(row, "responsible_team_org_id", "arch_physical_subsystem");
        String teamName = requireActiveOrganization(tenantId, teamId, "负责团队");
        String snapshot = requiredText(row, "responsible_team_name_snapshot");
        if (!teamName.equals(snapshot)) {
            throw new IllegalStateException("mock 负责团队名称快照与当前组织不一致: tenant=" + tenantId + ", org=" + teamId);
        }
        Long ownerId = optionalPositiveLong(row, "owner_user_id", "arch_physical_subsystem");
        if (ownerId != null) requireActiveUser(tenantId, ownerId, "负责人");
        requireOptionalParameter(row, tenantId, "runtime_code", "ARCH_RUNTIME");
        requireOptionalParameter(row, tenantId, "system_level_code", "ARCH_SYSTEM_LEVEL");
        requireOptionalParameter(row, tenantId, "development_framework_code", "ARCH_DEVELOPMENT_FRAMEWORK");
    }

    private void requireTenantRoot(long tenantId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_org WHERE tenant_id = ? AND parent_id = 0 AND status = 1 AND deleted = 0",
                Long.class, tenantId);
        if (count == null || count < 1) throw new IllegalStateException("mock tenant 不存在活动根组织: " + tenantId);
    }

    private String requireActiveOrganization(long tenantId, long organizationId, String label) {
        String name = jdbc.queryForObject(
                "SELECT MAX(org_name) FROM sys_org WHERE tenant_id = ? AND id = ? AND status = 1 AND deleted = 0",
                String.class, tenantId, organizationId);
        if (name == null || name.isBlank()) {
            throw new IllegalStateException("mock " + label + "不是当前租户活动组织: tenant=" + tenantId + ", org=" + organizationId);
        }
        return name;
    }

    private void requireActiveUser(long tenantId, long userId, String label) {
        requireReference("SELECT COUNT(*) FROM sys_user WHERE tenant_id = ? AND id = ? AND status = 1 AND deleted = 0",
                tenantId, userId, label);
    }

    private void requireReference(String sql, long tenantId, long referenceId, String label) {
        Long count = jdbc.queryForObject(sql, Long.class, tenantId, referenceId);
        if (count == null || count != 1) {
            throw new IllegalStateException("mock " + label + "不是当前租户活动引用: tenant=" + tenantId + ", id=" + referenceId);
        }
    }

    private void requireOptionalParameter(JsonNode row, long tenantId, String field, String categoryCode) {
        JsonNode value = row.get(field);
        if (value == null || value.isNull()) return;
        if (!value.isTextual() || value.textValue().isBlank()) {
            throw new IllegalStateException("mock parameter code must be nonblank text: " + field);
        }
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM sys_config c
                JOIN sys_dict_type t ON t.id = c.category_id AND t.tenant_id = c.tenant_id
                WHERE c.tenant_id = ? AND t.dict_code = ? AND c.config_key = ?
                  AND t.status = 1 AND t.deleted = 0 AND c.status = 1 AND c.deleted = 0
                """, Long.class, tenantId, categoryCode, value.textValue());
        if (count == null || count != 1) {
            throw new IllegalStateException("mock parameter 不是当前租户分类选项: " + categoryCode + "/" + value.textValue());
        }
    }

    private long requiredPositiveLong(JsonNode row, String field, String table) {
        JsonNode value = row.get(field);
        if (value == null || !value.isIntegralNumber() || value.longValue() <= 0) {
            throw new IllegalStateException("mock " + table + " requires explicit positive " + field);
        }
        return value.longValue();
    }

    private Long optionalPositiveLong(JsonNode row, String field, String table) {
        JsonNode value = row.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isIntegralNumber() || value.longValue() <= 0) {
            throw new IllegalStateException("mock " + table + " requires positive " + field + " when present");
        }
        return value.longValue();
    }

    private int upsert(String table, Set<String> allowed, List<String> keys, JsonNode row) {
        LinkedHashSet<String> columns = new LinkedHashSet<>(keys);
        Iterator<String> names = row.fieldNames();
        while (names.hasNext()) {
            String column = names.next();
            if (!SQL_IDENTIFIER.matcher(column).matches() || !allowed.contains(column)) {
                throw new IllegalStateException("mock column is not allowlisted: " + table + "." + column);
            }
            columns.add(column);
        }
        List<Object> values = new ArrayList<>();
        for (String column : columns) {
            JsonNode value = row.get(column);
            if (value == null) throw new IllegalStateException("mock key value is missing: " + table + "." + column);
            if (value.isNull()) {
                if (keys.contains(column)) throw new IllegalStateException("mock key value is null: " + table + "." + column);
                values.add(null);
            } else values.add(jdbcValue(value));
        }
        String columnSql = columns.stream().map(this::quote).reduce((left, right) -> left + ", " + right).orElseThrow();
        String placeholderSql = String.join(", ", Collections.nCopies(columns.size(), "?"));
        String updateSql = columns.stream().filter(column -> !keys.contains(column))
                .map(column -> quote(column) + " = VALUES(" + quote(column) + ")")
                .reduce((left, right) -> left + ", " + right)
                .orElseGet(() -> quote(keys.get(0)) + " = " + quote(keys.get(0)));
        return jdbc.update("INSERT INTO " + quote(table) + " (" + columnSql + ") VALUES (" + placeholderSql
                + ") ON DUPLICATE KEY UPDATE " + updateSql, values.toArray());
    }

    private Object jdbcValue(JsonNode value) {
        if (value.isTextual()) return value.textValue();
        if (value.isBoolean()) return value.booleanValue();
        if (value.isIntegralNumber()) return value.longValue();
        if (value.isFloatingPointNumber()) return value.doubleValue();
        try { return objectMapper.writeValueAsString(value); }
        catch (IOException exception) { throw new IllegalStateException("Unable to serialize mock JSON value", exception); }
    }

    private String quote(String identifier) {
        if (!SQL_IDENTIFIER.matcher(identifier).matches()) throw new IllegalStateException("Invalid SQL identifier: " + identifier);
        return "`" + identifier + "`";
    }

    private String requiredText(JsonNode object, String field) {
        JsonNode value = object.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw new IllegalStateException("mock data requires text field: " + field);
        }
        return value.textValue();
    }

    private List<String> textArray(JsonNode object, String field) {
        JsonNode value = object.get(field);
        if (value == null || !value.isArray()) throw new IllegalStateException("mock data requires array field: " + field);
        List<String> result = new ArrayList<>();
        for (JsonNode item : value) {
            if (!item.isTextual() || item.textValue().isBlank()) throw new IllegalStateException("mock array item must be text: " + field);
            result.add(item.textValue());
        }
        return result;
    }

    private String sha256(JsonNode root) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(objectMapper.writeValueAsBytes(root));
            StringBuilder result = new StringBuilder(64);
            for (byte item : digest) result.append(String.format("%02x", item));
            return result.toString();
        } catch (NoSuchAlgorithmException | IOException exception) {
            throw new IllegalStateException("Unable to calculate mock data checksum", exception);
        }
    }

    private static Map<String, Set<String>> allowedColumns() {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        result.put("sys_org", set("id", "tenant_id", "parent_id", "org_code", "org_name", "leader_id", "sort_no", "status", "created_at", "updated_at", "deleted"));
        result.put("sys_user", set("id", "tenant_id", "username", "password_hash", "display_name", "mobile_phone", "org_id", "avatar_object_key", "status", "last_login_at", "created_at", "updated_at", "deleted"));
        result.put("sys_role", set("id", "tenant_id", "role_code", "role_name", "status", "created_at", "updated_at", "deleted"));
        result.put("sys_user_role", set("user_id", "role_id", "tenant_id"));
        result.put("sys_role_menu", set("role_id", "menu_id", "tenant_id"));
        result.put("sys_role_permission", set("role_id", "permission_id", "tenant_id"));
        result.put("sys_dict_type", set("id", "tenant_id", "dict_code", "dict_name", "status", "created_at", "updated_at", "deleted"));
        result.put("sys_dict_item", set("id", "tenant_id", "dict_type_id", "item_value", "item_label", "sort_no", "status", "created_at", "updated_at", "deleted"));
        result.put("sys_config", set("id", "tenant_id", "category_id", "config_key", "config_value", "config_type", "status", "remark", "created_at", "updated_at", "deleted"));
        result.put("pm_project", set("id", "tenant_id", "project_code", "project_name", "description", "status", "plan_number_rule", "child_plan_number_rule", "risk_number_rule", "next_plan_sequence", "next_risk_sequence", "owner_id", "planned_start_date", "planned_end_date", "actual_end_date", "created_by", "created_at", "updated_at", "deleted"));
        result.put("pm_project_plan_group", set("id", "tenant_id", "project_id", "phase", "group_name", "color_key", "description", "sort_no", "created_at", "updated_at", "deleted"));
        result.put("pm_project_plan", set("id", "tenant_id", "project_id", "group_id", "parent_id", "plan_name", "plan_code", "next_child_plan_sequence", "description", "owner_id", "planned_start_date", "planned_end_date", "progress", "status", "phase", "sort_no", "created_at", "updated_at", "deleted"));
        result.put("pm_project_role", set("id", "tenant_id", "project_id", "role_code", "role_name", "description", "created_at", "updated_at", "deleted"));
        result.put("pm_project_member", set("id", "tenant_id", "project_id", "user_id", "org_id", "status", "joined_at", "created_at", "updated_at", "deleted"));
        result.put("pm_project_member_role", set("tenant_id", "member_id", "role_id"));
        result.put("pm_project_plan_org", set("plan_id", "org_id", "party_type", "tenant_id", "created_at"));
        result.put("pm_project_risk", set("id", "tenant_id", "project_id", "risk_code", "occurred_date", "project_phase", "urgency", "report_level", "current_status", "proposer_org_id", "proposer_subsystem", "proposer_contact_name", "proposer_contact_phone", "involved_org_id", "involved_subsystem", "problem_description", "expected_resolution_date", "suggested_solution", "current_handler_name", "current_handler_phone", "progress_description", "attention_level", "problem_nature", "problem_domain", "pmo_contact", "escalation_level", "current_problem_level", "planned_resolution_date", "actual_resolution_date", "resolution_solution", "created_by", "created_at", "updated_at", "deleted"));
        result.put("pm_project_risk_comment", set("id", "tenant_id", "project_id", "risk_id", "user_id", "comment_text", "created_at", "updated_at", "deleted"));
        result.put("pm_project_org", set("id", "tenant_id", "project_id", "parent_id", "org_code", "org_name", "sort_no", "status", "created_at", "updated_at", "deleted"));
        result.put("sys_attachment", set("id", "tenant_id", "business_type", "business_id", "file_name", "content_type", "file_size", "object_key", "uploader_id", "created_at", "updated_at", "deleted"));
        result.put("sys_notification", set("id", "tenant_id", "event_id", "module_code", "module_name", "business_type", "business_key", "title", "content", "notification_level", "source_name", "action_path", "created_by", "created_at"));
        result.put("sys_user_notification", set("notification_id", "tenant_id", "user_id", "is_read", "read_at", "created_at"));
        result.put("ai_provider", set("id", "tenant_id", "provider_code", "provider_name", "endpoint", "status", "deleted", "created_at"));
        result.put("ai_model", set("id", "tenant_id", "provider_id", "model_code", "model_name", "capabilities", "credential_secret", "status", "deleted", "created_at"));
        result.put("ai_route", set("id", "tenant_id", "capability", "model_id", "priority", "status", "deleted", "created_at"));
        result.put("wf_definition", set("id", "tenant_id", "code", "name", "status", "current_version", "model_schema_version", "deleted", "created_at", "updated_at"));
        result.put("wf_version", set("id", "tenant_id", "definition_id", "version_no", "definition_json", "model_schema_version", "status", "created_at"));
        result.put("wf_instance", set("id", "tenant_id", "definition_id", "version_no", "business_key", "status", "deleted", "starter_id", "variables_json", "created_at"));
        result.put("wf_task", set("id", "tenant_id", "instance_id", "task_key", "node_id", "task_type", "task_group_key", "parent_task_id", "assignee_type", "assignee_name", "assignee_id", "status", "comment", "completed_at", "created_at"));
        result.put("wf_task_action", set("id", "tenant_id", "instance_id", "task_id", "action_code", "operator_id", "target_user_id", "comment", "payload_json", "created_at"));
        result.put("wf_audit_event", set("id", "tenant_id", "definition_id", "version_no", "instance_id", "task_id", "event_type", "operator_id", "reason", "payload_json", "created_at"));
        result.put("biz_form_scope", set("id", "tenant_id", "scope_key", "scope_name", "module_key", "entity_type", "form_key", "status_field", "permission_prefix", "published_revision_id", "enabled", "deleted", "created_by", "updated_by", "created_at", "updated_at"));
        result.put("biz_form_section", set("id", "tenant_id", "scope_id", "section_key", "title", "layout_mode", "show_title", "collapsed", "sort_no", "is_builtin", "enabled", "deleted", "created_by", "updated_by", "created_at", "updated_at"));
        result.put("biz_form_field_definition", set("id", "tenant_id", "scope_id", "section_id", "field_key", "label", "field_kind", "input_type", "value_type", "source_type", "source_key", "component_key", "native_column", "multiple", "column_span", "visible", "list_visible", "filterable", "sortable", "dashboard_dimension", "placeholder", "help_text", "default_value_json", "sort_no", "is_builtin", "enabled", "deleted", "created_by", "updated_by", "created_at", "updated_at"));
        result.put("biz_form_field_rule", set("id", "tenant_id", "field_definition_id", "action_code", "condition_type", "condition_key", "required", "editable", "visible", "validation_json", "enabled", "deleted", "created_by", "updated_by", "created_at", "updated_at"));
        result.put("biz_form_field_option", set("id", "tenant_id", "field_definition_id", "option_value", "option_label", "option_group", "sort_no", "enabled", "deleted", "created_by", "updated_by", "created_at", "updated_at"));
        result.put("biz_form_field_value", set("id", "tenant_id", "scope_id", "field_definition_id", "entity_type", "entity_id", "ordinal", "value_text", "value_code", "value_number", "value_date", "value_datetime", "value_boolean", "value_ref_type", "value_ref_id", "value_json", "value_label_snapshot", "created_at", "updated_at"));
        result.put("biz_form_config_revision", set("id", "tenant_id", "scope_id", "revision_no", "revision_status", "snapshot_json", "change_summary", "created_by", "published_by", "created_at", "published_at"));
        result.put("req_system", set("id", "tenant_id", "system_code", "system_name", "english_name", "conglomerate", "status", "logical_subsystem_code", "logical_subsystem_name", "business_component_code", "business_component_name", "business_domain", "product_view", "launch_point", "category", "introduction", "disaster_level", "source_type", "created_by", "deleted"));
        result.put("req_project", set("id", "tenant_id", "project_code", "project_name", "project_type", "start_time", "status", "description", "created_by", "deleted"));
        result.put("req_project_member", set("id", "tenant_id", "project_id", "user_id", "member_role", "created_by", "deleted"));
        result.put("req_difference", set("id", "tenant_id", "project_id", "seq_no", "business_conglomerate", "business_section", "business_group", "requirement_no", "category", "name", "system_id", "jinke_practice", "difference_type", "monshang_practice", "difference_desc", "monshang_dept", "monshang_analyst", "jinke_analyst", "adapt_mode", "handle_status", "coord_group", "solution", "is_special", "decision_level", "decision_conclusion", "monshang_confirm_dept", "jinke_confirmer", "review_status", "review_comment", "reviewed_by", "reviewed_at", "dev_status", "test_status", "baseline_id", "source", "created_by", "deleted"));
        result.put("req_baseline", set("id", "tenant_id", "project_id", "baseline_no", "baseline_name", "status", "difference_count", "remark", "created_by", "deleted"));
        result.put("req_baseline_item", set("id", "tenant_id", "baseline_id", "difference_id", "snapshot_json", "deleted"));
        result.put("req_legacy_requirement", set("id", "tenant_id", "legacy_doc_name", "requirement_no", "requirement_name", "content_summary", "propose_dept", "proposer", "monshang_ba", "monshang_architect", "expected_launch_date", "regulator", "regulation_doc_no", "regulation_desc", "regulation_launch_date", "requirement_received_date", "requirement_type", "regulation_category", "business_group", "sub_group", "jinke_contact", "need_jinke_arch_decision", "jinke_architect", "unified_managed", "ba_review_date", "workload_date", "finance_project_date", "soft_doc_name", "owner_conglomerate", "owner_system", "owner_contact", "involve_cooperation", "coord_conglomerate", "coord_system", "soft_submit_date", "soft_review_date", "planned_launch_date", "actual_launch_date", "launch_mode", "requirement_status", "remark", "change_involved", "change_info", "change_review_conclusion", "change_conclusion_status", "change_remark", "not_project_developed", "current_stage", "propose_stage_status", "docking_stage_status", "workload_stage_status", "project_stage_status", "soft_stage_status", "launch_stage_status", "source", "created_by", "deleted"));
        result.put("req_stage_log", set("id", "tenant_id", "requirement_id", "from_stage", "to_stage", "from_status", "to_status", "operator_id", "operator_name", "comment", "deleted"));
        result.put("req_change_log", set("id", "tenant_id", "biz_type", "biz_id", "field_name", "old_value", "new_value", "change_type", "operator_id", "operator_name", "source", "trace_id", "deleted"));
        result.put("req_import_batch", set("id", "tenant_id", "biz_type", "project_id", "file_name", "template_type", "total_rows", "success_rows", "error_rows", "errors_json", "status", "operator_id", "operator_name", "deleted"));
        result.put("req_attachment", set("id", "tenant_id", "biz_type", "biz_id", "file_name", "file_size", "content_type", "preview_id", "preview_url", "operator_id", "deleted"));
        result.put("req_business_group_member", set("id", "tenant_id", "business_group", "user_id", "created_by", "deleted"));
        result.put("arch_logical_subsystem", set("id", "tenant_id", "code", "short_name", "name", "business_org_id", "deployment_platform_code", "system_type_code", "system_ownership_code", "contact_user_id", "description", "remark", "deleted", "created_by", "updated_by", "created_at", "updated_at"));
        result.put("arch_physical_subsystem", set("id", "tenant_id", "code", "short_name", "name", "logical_subsystem_id", "business_group_name", "responsible_team_org_id", "responsible_team_name_snapshot", "runtime_code", "system_level_code", "development_framework_code", "owner_user_id", "description", "remark", "deleted", "created_by", "updated_by", "created_at", "updated_at"));
        return Collections.unmodifiableMap(result);
    }

    private static Set<String> set(String... values) {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(values)));
    }
}
