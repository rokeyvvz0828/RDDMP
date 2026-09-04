package com.ccb.boot.workflow;

import com.ccb.security.model.AuthUser;
import com.ccb.workflow.service.WorkflowService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@Profile("local")
@ConditionalOnProperty(prefix = "ccb.workflow.seeded-definition-publisher", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class LocalSeededWorkflowPublisher implements ApplicationRunner {
    private static final String DEFINITION_SQL = """
            SELECT id, code, status FROM wf_definition
            WHERE tenant_id = ? AND deleted = 0 AND code IN (%s)
            """;
    private static final String OPERATOR_SQL = """
            SELECT id, username, display_name, org_id FROM sys_user
            WHERE tenant_id = ? AND id = ? AND deleted = 0 AND status = 1
            """;

    private final JdbcTemplate jdbc;
    private final WorkflowService workflows;
    private final long tenantId;
    private final long operatorUserId;
    private final List<String> definitionCodes;

    public LocalSeededWorkflowPublisher(JdbcTemplate jdbc, WorkflowService workflows,
            @Value("${ccb.workflow.seeded-definition-publisher.tenant-id:1}") long tenantId,
            @Value("${ccb.workflow.seeded-definition-publisher.operator-user-id:1}") long operatorUserId,
            @Value("${ccb.workflow.seeded-definition-publisher.definition-codes:architecture.subsystem.change,architecture.resource-request}") String definitionCodes) {
        this.jdbc = jdbc;
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
        List<Map<String, Object>> rows = jdbc.queryForList(OPERATOR_SQL, tenantId, operatorUserId);
        if (rows.size() != 1) {
            throw new IllegalStateException("本地固定流程发布操作用户不存在或未启用: " + operatorUserId);
        }
        Map<String, Object> row = rows.get(0);
        return new AuthUser(((Number) row.get("id")).longValue(), tenantId,
                String.valueOf(row.get("username")), "", String.valueOf(row.get("display_name")),
                ((Number) row.get("org_id")).longValue(), true);
    }

    private Map<String, Map<String, Object>> loadDefinitions() {
        String placeholders = String.join(",", definitionCodes.stream().map(code -> "?").toList());
        Object[] args = new Object[definitionCodes.size() + 1];
        args[0] = tenantId;
        for (int index = 0; index < definitionCodes.size(); index++) args[index + 1] = definitionCodes.get(index);
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (Map<String, Object> row : jdbc.queryForList(DEFINITION_SQL.formatted(placeholders), args)) {
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
