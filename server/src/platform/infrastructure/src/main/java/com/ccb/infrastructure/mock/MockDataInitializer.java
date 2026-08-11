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
        JsonNode database = root.get("database");
        if (database != null && !database.isNull()) {
            if (!database.isArray()) throw new IllegalStateException("mock database must be an array");
            for (JsonNode tableNode : database) rowCount += syncTable(tableNode);
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

    private int syncTable(JsonNode tableNode) {
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
        int count = 0;
        for (JsonNode row : rows) {
            if (!row.isObject()) throw new IllegalStateException("mock row must be an object for table: " + table);
            count += upsert(table, allowed, keys, row);
        }
        return count;
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
        result.put("sys_notification", set("id", "tenant_id", "event_id", "business_type", "business_key", "title", "content", "notification_level", "source_name", "action_path", "created_by", "created_at"));
        result.put("sys_user_notification", set("notification_id", "tenant_id", "user_id", "is_read", "read_at", "created_at"));
        result.put("ai_provider", set("id", "tenant_id", "provider_code", "provider_name", "endpoint", "status", "deleted", "created_at"));
        result.put("ai_model", set("id", "tenant_id", "provider_id", "model_code", "model_name", "capabilities", "credential_secret", "status", "deleted", "created_at"));
        result.put("ai_route", set("id", "tenant_id", "capability", "model_id", "priority", "status", "deleted", "created_at"));
        result.put("wf_definition", set("id", "tenant_id", "code", "name", "status", "current_version", "deleted", "created_at", "updated_at"));
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
        return Collections.unmodifiableMap(result);
    }

    private static Set<String> set(String... values) {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(values)));
    }
}
