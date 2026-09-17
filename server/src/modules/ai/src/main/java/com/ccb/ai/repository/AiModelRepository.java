package com.ccb.ai.repository;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class AiModelRepository {
    private final AiModelMapper mapper;

    public AiModelRepository(AiModelMapper mapper) { this.mapper = mapper; }
    public List<Map<String, Object>> providers(long tenantId) { return mapper.providers(tenantId); }
    public void insertProvider(long id, long tenantId, Object code, Object name, Object endpoint) { mapper.insertProvider(id, tenantId, code, name, endpoint); }
    public Map<String, Object> provider(long id, long tenantId) { return mapper.provider(id, tenantId); }
    public List<Map<String, Object>> models(long tenantId) { return mapper.models(tenantId); }
    public List<Map<String, Object>> routes(long tenantId) { return mapper.routes(tenantId); }
    public int providerCount(long id, long tenantId) { return mapper.providerCount(id, tenantId); }
    public void insertModel(long id, long tenantId, long providerId, Object code, Object name, Object capabilities, Object credentialSecret) { mapper.insertModel(id, tenantId, providerId, code, name, capabilities, credentialSecret); }
    public Map<String, Object> model(long id, long tenantId) { return mapper.model(id, tenantId); }
    public int modelCount(long id, long tenantId) { return mapper.modelCount(id, tenantId); }
    public void insertRoute(long id, long tenantId, Object capability, long modelId, Object priority) { mapper.insertRoute(id, tenantId, capability, modelId, priority); }
    public Map<String, Object> route(long id, long tenantId) { return mapper.route(id, tenantId); }
    public List<Map<String, Object>> activeRoutes(long tenantId, String capability) { return mapper.activeRoutes(tenantId, capability); }
    public void insertExecution(long id, long tenantId, long operatorId, String capability, Object modelId, String inputSummary) { mapper.insertExecution(id, tenantId, operatorId, capability, modelId, inputSummary); }
}
