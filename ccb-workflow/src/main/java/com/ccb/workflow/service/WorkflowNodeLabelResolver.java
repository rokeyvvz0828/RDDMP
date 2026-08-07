package com.ccb.workflow.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Resolves persisted node ids to the user-facing labels from the instance version. */
@Component
public class WorkflowNodeLabelResolver {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public WorkflowNodeLabelResolver(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> decorateTasks(List<Map<String, Object>> rows, long tenantId) {
        Map<Long, Map<String, String>> labelCache = new HashMap<>();
        for (Map<String, Object> row : rows) {
            long instanceId = number(row.get("instance_id"));
            String nodeId = text(row.get("node_id"), text(row.get("task_key"), ""));
            String nodeName = nodeLabel(instanceId, tenantId, nodeId, labelCache);
            if (nodeName.isBlank()) {
                nodeName = "CC".equalsIgnoreCase(text(row.get("task_type"), "")) ? "抄送" : "未命名节点";
            }
            row.put("node_name", nodeName);
        }
        return rows;
    }

    public String labelsForInstance(long instanceId, long tenantId, String nodeIds) {
        if (nodeIds == null || nodeIds.isBlank()) return "";
        Map<Long, Map<String, String>> cache = new HashMap<>();
        return List.of(nodeIds.split(",\\s*"))
                .stream()
                .map(nodeId -> nodeLabel(instanceId, tenantId, nodeId, cache))
                .filter(label -> !label.isBlank())
                .distinct()
                .reduce((left, right) -> left + "、" + right)
                .orElse("");
    }

    private String nodeLabel(long instanceId, long tenantId, String nodeId, Map<Long, Map<String, String>> cache) {
        if (nodeId == null || nodeId.isBlank()) return "";
        Map<String, String> labels = cache.computeIfAbsent(instanceId, ignored -> loadLabels(instanceId, tenantId));
        return labels.getOrDefault(nodeId, "");
    }

    private Map<String, String> loadLabels(long instanceId, long tenantId) {
        List<String> definitions = jdbc.query("""
                SELECT CAST(v.definition_json AS CHAR)
                FROM wf_instance i
                JOIN wf_version v ON v.definition_id = i.definition_id
                    AND v.tenant_id = i.tenant_id AND v.version_no = i.version_no
                WHERE i.id = ? AND i.tenant_id = ?
                """, (rs, rowNum) -> rs.getString(1), instanceId, tenantId);
        if (definitions.isEmpty()) return Map.of();
        try {
            JsonNode root = objectMapper.readTree(definitions.get(0));
            Map<String, String> labels = new HashMap<>();
            JsonNode nodes = root.path("nodes");
            if (nodes.isArray()) {
                for (JsonNode node : nodes) {
                    String id = node.path("id").asText("");
                    String label = node.path("label").asText("");
                    if (!id.isBlank() && !label.isBlank()) labels.put(id, label);
                }
            }
            return labels;
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private long number(Object value) {
        if (value instanceof Number number) return number.longValue();
        try { return Long.parseLong(String.valueOf(value)); } catch (Exception ignored) { return 0; }
    }

    private String text(Object value, String fallback) {
        String text = value == null ? "" : String.valueOf(value).trim();
        return text.isBlank() ? fallback : text;
    }
}
