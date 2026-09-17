package com.ccb.boot.workflow;

import com.ccb.security.model.AuthUser;
import com.ccb.boot.persistence.BootWorkflowRepository;
import com.ccb.workflow.integration.WorkflowDefinitionPublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Component
@Profile("local")
@ConditionalOnProperty(prefix = "ccb.workflow.seeded-definition-publisher", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class LocalSeededWorkflowPublisher implements ApplicationRunner {
    private final BootWorkflowRepository repository;
    private final WorkflowDefinitionPublisher workflows;
    private final long tenantId;
    private final long operatorUserId;
    private final List<String> definitionCodes;

    public LocalSeededWorkflowPublisher(BootWorkflowRepository repository, WorkflowDefinitionPublisher workflows,
            @Value("${ccb.workflow.seeded-definition-publisher.tenant-id:1}") long tenantId,
            @Value("${ccb.workflow.seeded-definition-publisher.operator-user-id:1}") long operatorUserId,
            @Value("${ccb.workflow.seeded-definition-publisher.definition-codes:architecture.subsystem.change,architecture.resource-request}") String definitionCodes) {
        this.repository = repository;
        this.workflows = workflows;
        this.tenantId = tenantId;
        this.operatorUserId = operatorUserId;
        this.definitionCodes = parseCodes(definitionCodes);
    }

    @Override
    public void run(ApplicationArguments args) {
        AuthUser operator = loadOperator();
        Map<String, Map<String, Object>> definitions = loadDefinitions();
        for (String code : definitionCodes) {
            Map<String, Object> definition = definitions.get(code);
            if (definition == null) throw new IllegalStateException("本地固定流程不存在: " + code);
            String status = String.valueOf(definition.get("status"));
            if ("PUBLISHED".equals(status)) continue;
            if (!"DRAFT".equals(status)) {
                throw new IllegalStateException("本地固定流程状态不允许发布: " + code + " (" + status + ")");
            }
            workflows.publish(((Number) definition.get("id")).longValue(), operator);
        }
    }

    private AuthUser loadOperator() {
        Map<String, Object> row = repository.activeOperator(Map.of("tenantId", tenantId, "operatorUserId", operatorUserId));
        if (row == null) {
            throw new IllegalStateException("本地固定流程发布操作用户不存在或未启用: " + operatorUserId);
        }
        return new AuthUser(((Number) row.get("id")).longValue(), tenantId,
                String.valueOf(row.get("username")), "", String.valueOf(row.get("display_name")),
                ((Number) row.get("org_id")).longValue(), true);
    }

    private Map<String, Map<String, Object>> loadDefinitions() {
        Map<String, Map<String, Object>> result = new java.util.LinkedHashMap<>();
        for (Map<String, Object> row : repository.seededDefinitions(Map.of("tenantId", tenantId, "codes", definitionCodes))) {
            result.put(String.valueOf(row.get("code")), row);
        }
        return result;
    }

    private static List<String> parseCodes(String value) {
        List<String> codes = Arrays.stream(value.split(",")).map(String::trim)
                .filter(code -> !code.isEmpty()).distinct().toList();
        if (codes.isEmpty()) throw new IllegalArgumentException("本地固定流程编码不能为空");
        return codes;
    }
}
