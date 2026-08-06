package com.ccb.ai.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class AiModelService {
    private final JdbcTemplate jdbc;

    public AiModelService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Map<String, Object>> providers(AuthUser user) {
        return jdbc.queryForList("SELECT id, provider_code, provider_name, endpoint, status, created_at FROM ai_provider WHERE tenant_id = ? AND deleted = 0 ORDER BY id DESC", user.tenantId());
    }

    @Transactional
    public Map<String, Object> createProvider(Map<String, Object> body, AuthUser user) {
        long id = nextId();
        jdbc.update("INSERT INTO ai_provider (id, tenant_id, provider_code, provider_name, endpoint, status, deleted) VALUES (?, ?, ?, ?, ?, 1, 0)",
                id, user.tenantId(), body.get("providerCode"), body.get("providerName"), body.getOrDefault("endpoint", ""));
        return jdbc.queryForMap("SELECT id, provider_code, provider_name, endpoint, status, created_at FROM ai_provider WHERE id = ? AND tenant_id = ?", id, user.tenantId());
    }

    public List<Map<String, Object>> models(AuthUser user) {
        return jdbc.queryForList("SELECT id, provider_id, model_code, model_name, capabilities, status, created_at FROM ai_model WHERE tenant_id = ? AND deleted = 0 ORDER BY id DESC", user.tenantId());
    }

    public List<Map<String, Object>> routes(AuthUser user) {
        return jdbc.queryForList("SELECT id, capability, model_id, priority, status FROM ai_route WHERE tenant_id = ? AND deleted = 0 ORDER BY priority, id", user.tenantId());
    }

    @Transactional
    public Map<String, Object> createModel(Map<String, Object> body, AuthUser user) {
        long providerId = Long.parseLong(String.valueOf(body.get("providerId")));
        Integer providerCount = jdbc.queryForObject("SELECT COUNT(*) FROM ai_provider WHERE id = ? AND tenant_id = ? AND deleted = 0", Integer.class, providerId, user.tenantId());
        if (providerCount == null || providerCount == 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "AI provider not found");
        long id = nextId();
        jdbc.update("INSERT INTO ai_model (id, tenant_id, provider_id, model_code, model_name, capabilities, credential_secret, status, deleted) VALUES (?, ?, ?, ?, ?, ?, ?, 1, 0)",
                id, user.tenantId(), providerId, body.get("modelCode"), body.get("modelName"), body.getOrDefault("capabilities", ""), body.getOrDefault("credentialSecret", ""));
        return jdbc.queryForMap("SELECT id, provider_id, model_code, model_name, capabilities, status FROM ai_model WHERE id = ? AND tenant_id = ?", id, user.tenantId());
    }

    @Transactional
    public Map<String, Object> createRoute(Map<String, Object> body, AuthUser user) {
        long modelId = Long.parseLong(String.valueOf(body.get("modelId")));
        Integer modelCount = jdbc.queryForObject("SELECT COUNT(*) FROM ai_model WHERE id = ? AND tenant_id = ? AND deleted = 0", Integer.class, modelId, user.tenantId());
        if (modelCount == null || modelCount == 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "AI model not found");
        long id = nextId();
        jdbc.update("INSERT INTO ai_route (id, tenant_id, capability, model_id, priority, status, deleted) VALUES (?, ?, ?, ?, ?, 1, 0)",
                id, user.tenantId(), body.get("capability"), modelId, body.getOrDefault("priority", 100));
        return jdbc.queryForMap("SELECT id, capability, model_id, priority, status FROM ai_route WHERE id = ? AND tenant_id = ?", id, user.tenantId());
    }

    @Transactional
    public Map<String, Object> execute(String capability, String input, AuthUser user) {
        List<Map<String, Object>> routes = jdbc.queryForList("SELECT r.id, r.model_id, m.model_code FROM ai_route r JOIN ai_model m ON m.id = r.model_id AND m.tenant_id = r.tenant_id WHERE r.tenant_id = ? AND r.capability = ? AND r.status = 1 AND m.status = 1 ORDER BY r.priority, r.id LIMIT 1", user.tenantId(), capability);
        if (routes.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "No AI model route for capability");
        Map<String, Object> route = routes.get(0);
        long executionId = nextId();
        jdbc.update("INSERT INTO ai_execution (id, tenant_id, operator_id, capability, model_id, input_summary, status) VALUES (?, ?, ?, ?, ?, ?, 'ACCEPTED')",
                executionId, user.tenantId(), user.id(), capability, route.get("model_id"), input == null ? "" : input.substring(0, Math.min(input.length(), 500)));
        return Map.of("executionId", executionId, "capability", capability, "modelCode", route.get("model_code"), "status", "ACCEPTED");
    }

    private long nextId() { return System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000); }
}