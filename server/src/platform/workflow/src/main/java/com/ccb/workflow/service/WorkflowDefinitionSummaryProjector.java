package com.ccb.workflow.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

@Service
public class WorkflowDefinitionSummaryProjector {
    private final WorkflowDefinitionSummaryRepository repository;
    private final ObjectMapper objectMapper;

    public WorkflowDefinitionSummaryProjector(WorkflowDefinitionSummaryRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public void refresh(long definitionId, long tenantId, String scopeType, int versionNo, String definitionJson) {
        repository.refresh(definitionId, tenantId, versionNo, requiresConfiguration(scopeType, definitionJson));
    }

    boolean requiresConfiguration(String scopeType, String definitionJson) {
        if (!"PROJECT".equals(scopeType) || definitionJson == null || definitionJson.isBlank()) return false;
        try {
            JsonNode nodes = objectMapper.readTree(definitionJson).path("nodes");
            if (!nodes.isArray()) return false;
            for (JsonNode node : nodes) {
                JsonNode config = node.path("config");
                if ("APPROVAL".equals(node.path("type").asText()) && "TEMPLATE_PLACEHOLDER".equals(config.path("assigneeType").asText())) return true;
                if ("CC".equals(node.path("type").asText()) && config.path("templatePlaceholder").asBoolean(false)) return true;
            }
        } catch (Exception ignored) { return false; }
        return false;
    }
}
