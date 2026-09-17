package com.ccb.ai.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

@Mapper
public interface AiModelMapper {
    List<Map<String, Object>> providers(@Param("tenantId") long tenantId);
    int insertProvider(@Param("id") long id, @Param("tenantId") long tenantId, @Param("providerCode") Object providerCode, @Param("providerName") Object providerName, @Param("endpoint") Object endpoint);
    Map<String, Object> provider(@Param("id") long id, @Param("tenantId") long tenantId);
    List<Map<String, Object>> models(@Param("tenantId") long tenantId);
    List<Map<String, Object>> routes(@Param("tenantId") long tenantId);
    int providerCount(@Param("id") long id, @Param("tenantId") long tenantId);
    int insertModel(@Param("id") long id, @Param("tenantId") long tenantId, @Param("providerId") long providerId, @Param("modelCode") Object modelCode, @Param("modelName") Object modelName, @Param("capabilities") Object capabilities, @Param("credentialSecret") Object credentialSecret);
    Map<String, Object> model(@Param("id") long id, @Param("tenantId") long tenantId);
    int modelCount(@Param("id") long id, @Param("tenantId") long tenantId);
    int insertRoute(@Param("id") long id, @Param("tenantId") long tenantId, @Param("capability") Object capability, @Param("modelId") long modelId, @Param("priority") Object priority);
    Map<String, Object> route(@Param("id") long id, @Param("tenantId") long tenantId);
    List<Map<String, Object>> activeRoutes(@Param("tenantId") long tenantId, @Param("capability") String capability);
    int insertExecution(@Param("id") long id, @Param("tenantId") long tenantId, @Param("operatorId") long operatorId, @Param("capability") String capability, @Param("modelId") Object modelId, @Param("inputSummary") String inputSummary);
}

@Configuration
@MapperScan(basePackageClasses = AiModelMapper.class)
class AiModelMapperConfiguration {
}
