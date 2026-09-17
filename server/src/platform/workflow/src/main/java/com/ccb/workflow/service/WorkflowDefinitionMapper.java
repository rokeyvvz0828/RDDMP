package com.ccb.workflow.service;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

@Mapper
public interface WorkflowDefinitionMapper {
    List<Map<String, Object>> selectDefinitionSummaries(@Param("tenantId") long tenantId,
                                                         @Param("requestedScope") String requestedScope,
                                                         @Param("projectId") Long projectId,
                                                         @Param("offset") long offset,
                                                         @Param("limit") long limit);

    long countDefinitionSummaries(@Param("tenantId") long tenantId,
                                  @Param("requestedScope") String requestedScope,
                                  @Param("projectId") Long projectId);
    Map<String, Object> detail(@Param("definitionId") long definitionId, @Param("tenantId") long tenantId);
    Map<String, Object> status(@Param("definitionId") long definitionId, @Param("tenantId") long tenantId);
    List<Map<String, Object>> versions(@Param("definitionId") long definitionId, @Param("tenantId") long tenantId);
    Map<String, Object> version(@Param("definitionId") long definitionId, @Param("tenantId") long tenantId, @Param("versionNo") int versionNo);
    List<Map<String, Object>> events(@Param("definitionId") long definitionId, @Param("tenantId") long tenantId);
    Map<String, Object> published(@Param("definitionId") long definitionId, @Param("tenantId") long tenantId);
    Map<String, Object> enterprise(@Param("definitionId") long definitionId, @Param("tenantId") long tenantId);
    int updateDraft(Map<String, Object> params);
    int updateDraftVersion(Map<String, Object> params);
    int softDelete(Map<String, Object> params);
    int archive(Map<String, Object> params);
    int restore(Map<String, Object> params);
    int insertDefinition(Map<String, Object> params);
    int insertVersion(Map<String, Object> params);
    Map<String, Object> definitionSummary(@org.apache.ibatis.annotations.Param("definitionId") long definitionId, @org.apache.ibatis.annotations.Param("tenantId") long tenantId);
    Map<String, Object> latestVersion(@org.apache.ibatis.annotations.Param("definitionId") long definitionId, @org.apache.ibatis.annotations.Param("tenantId") long tenantId);
    int publishVersion(Map<String, Object> params);
    int publishDefinition(Map<String, Object> params);
    Map<String, Object> publishedVersion(@org.apache.ibatis.annotations.Param("definitionId") long definitionId, @org.apache.ibatis.annotations.Param("tenantId") long tenantId);
    int countPublishedVersions(@org.apache.ibatis.annotations.Param("definitionId") long definitionId, @org.apache.ibatis.annotations.Param("tenantId") long tenantId);
    int countInstances(@org.apache.ibatis.annotations.Param("definitionId") long definitionId, @org.apache.ibatis.annotations.Param("tenantId") long tenantId);
    Long nextVersion(@org.apache.ibatis.annotations.Param("definitionId") long definitionId, @org.apache.ibatis.annotations.Param("tenantId") long tenantId);
    int insertDraftVersion(Map<String, Object> params);
    int unpublish(Map<String, Object> params);
}

@Configuration
@MapperScan(basePackageClasses = WorkflowDefinitionMapper.class)
class WorkflowDefinitionMapperConfiguration {
}
