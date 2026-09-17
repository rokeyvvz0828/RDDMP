package com.ccb.ai.service;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.ccb.ai.repository.AiModelRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class AiModelService {
    private final AiModelRepository repository;

    public AiModelService(AiModelRepository repository) { this.repository = repository; }

    public List<Map<String, Object>> providers(AuthUser user) {
        return repository.providers(user.tenantId());
    }

    @Transactional
    public Map<String, Object> createProvider(Map<String, Object> body, AuthUser user) {
        long id = nextId();
        repository.insertProvider(id, user.tenantId(), body.get("providerCode"), body.get("providerName"), body.getOrDefault("endpoint", ""));
        return repository.provider(id, user.tenantId());
    }

    public List<Map<String, Object>> models(AuthUser user) {
        return repository.models(user.tenantId());
    }

    public List<Map<String, Object>> routes(AuthUser user) {
        return repository.routes(user.tenantId());
    }

    @Transactional
    public Map<String, Object> createModel(Map<String, Object> body, AuthUser user) {
        long providerId = Long.parseLong(String.valueOf(body.get("providerId")));
        int providerCount = repository.providerCount(providerId, user.tenantId());
        if (providerCount == 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "AI provider not found");
        long id = nextId();
        repository.insertModel(id, user.tenantId(), providerId, body.get("modelCode"), body.get("modelName"), body.getOrDefault("capabilities", ""), body.getOrDefault("credentialSecret", ""));
        return repository.model(id, user.tenantId());
    }

    @Transactional
    public Map<String, Object> createRoute(Map<String, Object> body, AuthUser user) {
        long modelId = Long.parseLong(String.valueOf(body.get("modelId")));
        int modelCount = repository.modelCount(modelId, user.tenantId());
        if (modelCount == 0) throw new BusinessException(ErrorCode.BAD_REQUEST, "AI model not found");
        long id = nextId();
        repository.insertRoute(id, user.tenantId(), body.get("capability"), modelId, body.getOrDefault("priority", 100));
        return repository.route(id, user.tenantId());
    }

    @Transactional
    public Map<String, Object> execute(String capability, String input, AuthUser user) {
        List<Map<String, Object>> routes = repository.activeRoutes(user.tenantId(), capability);
        if (routes.isEmpty()) throw new BusinessException(ErrorCode.BAD_REQUEST, "No AI model route for capability");
        Map<String, Object> route = routes.get(0);
        long executionId = nextId();
        repository.insertExecution(executionId, user.tenantId(), user.id(), capability, route.get("model_id"), input == null ? "" : input.substring(0, Math.min(input.length(), 500)));
        return Map.of("executionId", executionId, "capability", capability, "modelCode", route.get("model_code"), "status", "ACCEPTED");
    }

    private long nextId() { return System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000); }
}
