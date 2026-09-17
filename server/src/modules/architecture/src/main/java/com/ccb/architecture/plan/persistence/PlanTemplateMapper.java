package com.ccb.architecture.plan.persistence;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

@Mapper
public interface PlanTemplateMapper {
    int insertTemplate(Map<String, Object> params); int updateTemplate(Map<String, Object> params);
    int updateTemplateStatus(Map<String, Object> params); int updateTemplateLatestVersion(Map<String, Object> params);
    Map<String, Object> findTemplate(Map<String, Object> params); Map<String, Object> lockTemplate(Map<String, Object> params);
    List<Map<String, Object>> searchTemplates(Map<String, Object> params); Long countTemplates(Map<String, Object> params);
    int insertStage(Map<String, Object> params); int updateStage(Map<String, Object> params); int deleteStage(Map<String, Object> params);
    List<Map<String, Object>> findStages(Map<String, Object> params); Long stageIdOfTaskTemplate(Map<String, Object> params);
    Map<String, Object> findStageRef(Map<String, Object> params); Long findStageId(Map<String, Object> params);
    int insertTaskTemplate(Map<String, Object> params); int updateTaskTemplate(Map<String, Object> params);
    int updateTaskTemplateStatus(Map<String, Object> params); int updateTaskTemplateLatestVersion(Map<String, Object> params);
    int deleteTaskTemplate(Map<String, Object> params); List<Map<String, Object>> findTaskTemplates(Map<String, Object> params);
    Map<String, Object> findTaskTemplate(Map<String, Object> params); Map<String, Object> taskTemplateVersionMeta(Map<String, Object> params);
    String taskTemplateCheckItemsJson(Map<String, Object> params); Integer latestTaskTemplateVersion(Map<String, Object> params);
    int insertTaskTemplateVersion(Map<String, Object> params); int insertTemplateVersion(Map<String, Object> params);
    List<Map<String, Object>> findTemplateVersions(Map<String, Object> params); Map<String, Object> findTemplateVersion(Map<String, Object> params);
    int insertStageDependency(Map<String, Object> params); List<Map<String, Object>> findStageDependencies(Map<String, Object> params);
    int deleteStageDependencies(Map<String, Object> params); int insertTaskTemplateDependency(Map<String, Object> params);
    List<Map<String, Object>> findTaskTemplateDependencies(Map<String, Object> params); int deleteTaskTemplateDependencies(Map<String, Object> params);
    int insertActivity(Map<String, Object> params);
}
