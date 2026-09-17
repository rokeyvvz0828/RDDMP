package com.ccb.architecture.plan.persistence;

import com.ccb.architecture.plan.model.PlanTemplateModels.CheckItemDraft;
import com.ccb.architecture.plan.model.PlanTemplateModels.Dimension;
import com.ccb.architecture.plan.model.PlanTemplateModels.PlanTemplate;
import com.ccb.architecture.plan.model.PlanTemplateModels.StageDraft;
import com.ccb.architecture.plan.model.PlanTemplateModels.TaskTemplateDraft;
import com.ccb.architecture.plan.model.PlanTemplateModels.TemplateStatus;
import com.ccb.architecture.plan.model.PlanTemplateModels.TemplateVersion;
import com.ccb.architecture.plan.service.PlanTemplateService.StageRef;
import com.ccb.architecture.plan.service.PlanTemplateService.TaskTemplateVersionMeta;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Template persistence boundary. SQL is owned by {@link PlanTemplateMapper}. */
@Repository
public class PlanTemplateStore {
    private final PlanTemplateMapper mapper;
    private final ObjectMapper objectMapper;

    public PlanTemplateStore(PlanTemplateMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    public void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("模板数据操作必须在事务内执行");
        }
    }

    public void insertTemplate(long tenantId, PlanTemplate template) {
        requireTransaction();
        mapper.insertTemplate(p("id", template.id(), "tenantId", tenantId, "name", template.name(), "description", template.description(), "status", template.status().name(), "latestVersionNo", template.latestVersionNo(), "rowVersion", template.rowVersion(), "createdBy", template.createdBy(), "updatedBy", template.updatedBy()));
    }
    public void updateTemplate(long tenantId, long id, String name, String description, long rowVersion, long updatedBy) {
        requireTransaction();
        if (mapper.updateTemplate(p("tenantId", tenantId, "id", id, "name", name, "description", description, "rowVersion", rowVersion, "updatedBy", updatedBy)) != 1) throw new IllegalStateException("模板已被并发修改，请刷新后重试");
    }
    public void updateTemplateStatus(long tenantId, long id, TemplateStatus status, long updatedBy) { requireTransaction(); mapper.updateTemplateStatus(p("tenantId", tenantId, "id", id, "status", status.name(), "updatedBy", updatedBy)); }
    public void updateTemplateLatestVersion(long tenantId, long id, int latestVersionNo, long updatedBy) { requireTransaction(); mapper.updateTemplateLatestVersion(p("tenantId", tenantId, "id", id, "latestVersionNo", latestVersionNo, "updatedBy", updatedBy)); }
    public Optional<PlanTemplate> findTemplate(long tenantId, long id) { return Optional.ofNullable(mapper.findTemplate(p("tenantId", tenantId, "id", id))).map(this::template); }
    public Optional<PlanTemplate> lockTemplate(long tenantId, long id) { requireTransaction(); return Optional.ofNullable(mapper.lockTemplate(p("tenantId", tenantId, "id", id))).map(this::template); }
    public List<PlanTemplate> searchTemplates(long tenantId, String keyword, TemplateStatus status, int limit, int offset) { if (limit <= 0 || offset < 0) throw new IllegalArgumentException("分页参数无效"); return mapper.searchTemplates(p("tenantId", tenantId, "keyword", blankToNull(keyword), "status", status == null ? null : status.name(), "limit", limit, "offset", offset)).stream().map(this::template).toList(); }
    public long countTemplates(long tenantId, String keyword, TemplateStatus status) { Long value = mapper.countTemplates(p("tenantId", tenantId, "keyword", blankToNull(keyword), "status", status == null ? null : status.name())); return value == null ? 0 : value; }

    public void insertStage(long tenantId, long stageId, long templateId, String name, int sortNo, Integer startOffsetDays, Integer durationDays, long createdBy) { requireTransaction(); mapper.insertStage(p("tenantId", tenantId, "stageId", stageId, "templateId", templateId, "name", name, "sortNo", sortNo, "startOffsetDays", startOffsetDays, "durationDays", durationDays, "createdBy", createdBy)); }
    public void updateStage(long tenantId, long stageId, String name, int sortNo, Integer startOffsetDays, Integer durationDays, long updatedBy) { requireTransaction(); mapper.updateStage(p("tenantId", tenantId, "stageId", stageId, "name", name, "sortNo", sortNo, "startOffsetDays", startOffsetDays, "durationDays", durationDays, "updatedBy", updatedBy)); }
    public void deleteStage(long tenantId, long stageId) { requireTransaction(); mapper.deleteStage(p("tenantId", tenantId, "stageId", stageId)); }
    public List<StageDraft> findStages(long tenantId, long templateId) { return mapper.findStages(p("tenantId", tenantId, "templateId", templateId)).stream().map(row -> new StageDraft(longValue(row, "id"), string(row, "name"), integer(row, "sort_no"), nullableInteger(row, "start_offset_days"), nullableInteger(row, "duration_days"), new ArrayList<>())).toList(); }
    public Long stageIdOfTaskTemplate(long tenantId, long taskTemplateId) { return mapper.stageIdOfTaskTemplate(p("tenantId", tenantId, "taskTemplateId", taskTemplateId)); }
    public Optional<StageRef> findStageRef(long tenantId, long stageId) { return Optional.ofNullable(mapper.findStageRef(p("tenantId", tenantId, "stageId", stageId))).map(row -> new StageRef(longValue(row, "id"), longValue(row, "template_id"), string(row, "name"), integer(row, "sort_no"), nullableInteger(row, "start_offset_days"), nullableInteger(row, "duration_days"))); }
    public Optional<Long> findStageId(long tenantId, long stageId) { return Optional.ofNullable(mapper.findStageId(p("tenantId", tenantId, "stageId", stageId))); }

    public void insertTaskTemplate(long tenantId, TaskTemplateDraft task, long templateId, long stageId, String checkItemsJson, long createdBy) { requireTransaction(); mapper.insertTaskTemplate(p("id", task.id(), "tenantId", tenantId, "templateId", templateId, "stageId", stageId, "name", task.name(), "dimension", task.dimension().name(), "checkItemsJson", checkItemsJson, "status", task.status().name(), "createdBy", createdBy)); }
    public void updateTaskTemplate(long tenantId, TaskTemplateDraft task, String checkItemsJson, long updatedBy) { requireTransaction(); if (mapper.updateTaskTemplate(p("tenantId", tenantId, "id", task.id(), "name", task.name(), "dimension", task.dimension().name(), "checkItemsJson", checkItemsJson, "rowVersion", task.rowVersion(), "updatedBy", updatedBy)) != 1) throw new IllegalStateException("任务模板已被并发修改，请刷新后重试"); }
    public void updateTaskTemplateStatus(long tenantId, long taskId, TemplateStatus status, long updatedBy) { requireTransaction(); mapper.updateTaskTemplateStatus(p("tenantId", tenantId, "taskId", taskId, "status", status.name(), "updatedBy", updatedBy)); }
    public void updateTaskTemplateLatestVersion(long tenantId, long taskId, int latestVersionNo, long updatedBy) { requireTransaction(); mapper.updateTaskTemplateLatestVersion(p("tenantId", tenantId, "taskId", taskId, "latestVersionNo", latestVersionNo, "updatedBy", updatedBy)); }
    public void deleteTaskTemplate(long tenantId, long taskId) { requireTransaction(); mapper.deleteTaskTemplate(p("tenantId", tenantId, "taskId", taskId)); }
    public List<TaskTemplateDraft> findTaskTemplates(long tenantId, Long templateId, Long stageId) { return mapper.findTaskTemplates(p("tenantId", tenantId, "templateId", templateId, "stageId", stageId)).stream().map(this::task).toList(); }
    public Optional<TaskTemplateDraft> findTaskTemplate(long tenantId, long taskId) { return Optional.ofNullable(mapper.findTaskTemplate(p("tenantId", tenantId, "taskId", taskId))).map(this::task); }
    public Optional<TaskTemplateVersionMeta> taskTemplateVersionMeta(long tenantId, long taskTemplateId, int versionNo) { return Optional.ofNullable(mapper.taskTemplateVersionMeta(p("tenantId", tenantId, "taskTemplateId", taskTemplateId, "versionNo", versionNo))).map(row -> new TaskTemplateVersionMeta(string(row, "name"), Dimension.valueOf(string(row, "dimension")), string(row, "check_items_json"))); }
    public String taskTemplateCheckItemsJson(long tenantId, long taskId) { return mapper.taskTemplateCheckItemsJson(p("tenantId", tenantId, "taskId", taskId)); }
    public int latestTaskTemplateVersion(long tenantId, long taskId) { Integer value = mapper.latestTaskTemplateVersion(p("tenantId", tenantId, "taskId", taskId)); return value == null ? 0 : value; }
    public void insertTaskTemplateVersion(long tenantId, long versionId, long taskTemplateId, int versionNo, String name, Dimension dimension, String checkItemsJson, String note, long publishedBy) { requireTransaction(); mapper.insertTaskTemplateVersion(p("tenantId", tenantId, "versionId", versionId, "taskTemplateId", taskTemplateId, "versionNo", versionNo, "name", name, "dimension", dimension.name(), "checkItemsJson", checkItemsJson, "note", note, "publishedBy", publishedBy)); }
    public void insertTemplateVersion(long tenantId, long versionId, long templateId, int versionNo, String contentJson, String note, long publishedBy) { requireTransaction(); mapper.insertTemplateVersion(p("tenantId", tenantId, "versionId", versionId, "templateId", templateId, "versionNo", versionNo, "contentJson", contentJson, "note", note, "publishedBy", publishedBy)); }
    public List<TemplateVersion> findTemplateVersions(long tenantId, long templateId) { return mapper.findTemplateVersions(p("tenantId", tenantId, "templateId", templateId)).stream().map(this::version).toList(); }
    public Optional<TemplateVersion> findTemplateVersion(long tenantId, long templateId, int versionNo) { return Optional.ofNullable(mapper.findTemplateVersion(p("tenantId", tenantId, "templateId", templateId, "versionNo", versionNo))).map(this::version); }

    public void insertStageDependency(long tenantId, long id, long templateId, long stageId, long predecessorStageId, long createdBy) { requireTransaction(); mapper.insertStageDependency(p("tenantId", tenantId, "id", id, "templateId", templateId, "stageId", stageId, "predecessorStageId", predecessorStageId, "createdBy", createdBy)); }
    public List<Long[]> findStageDependencies(long tenantId, long templateId) { return mapper.findStageDependencies(p("tenantId", tenantId, "templateId", templateId)).stream().map(row -> new Long[]{longValue(row, "stage_id"), longValue(row, "predecessor_stage_id")}).toList(); }
    public void deleteStageDependencies(long tenantId, long stageId) { requireTransaction(); mapper.deleteStageDependencies(p("tenantId", tenantId, "stageId", stageId)); }
    public void insertTaskTemplateDependency(long tenantId, long id, long templateId, long stageId, long taskTemplateId, long predecessorTaskTemplateId, long createdBy) { requireTransaction(); mapper.insertTaskTemplateDependency(p("tenantId", tenantId, "id", id, "templateId", templateId, "stageId", stageId, "taskTemplateId", taskTemplateId, "predecessorTaskTemplateId", predecessorTaskTemplateId, "createdBy", createdBy)); }
    public List<Long[]> findTaskTemplateDependencies(long tenantId, Long templateId, Long stageId) { return mapper.findTaskTemplateDependencies(p("tenantId", tenantId, "templateId", templateId, "stageId", stageId)).stream().map(row -> new Long[]{longValue(row, "task_template_id"), longValue(row, "predecessor_task_template_id")}).toList(); }
    public void deleteTaskTemplateDependencies(long tenantId, Long templateId, Long stageId, Long taskTemplateId) { requireTransaction(); mapper.deleteTaskTemplateDependencies(p("tenantId", tenantId, "templateId", templateId, "stageId", stageId, "taskTemplateId", taskTemplateId)); }
    public void insertActivity(long tenantId, long id, String scopeType, long scopeId, String objectType, Long objectId, String action, long operatorUserId, String reason, String beforeJson, String afterJson) { requireTransaction(); mapper.insertActivity(p("tenantId", tenantId, "id", id, "scopeType", scopeType, "scopeId", scopeId, "objectType", objectType, "objectId", objectId, "action", action, "operatorUserId", operatorUserId, "reason", reason, "beforeJson", beforeJson, "afterJson", afterJson)); }

    private PlanTemplate template(Map<String, Object> row) { return new PlanTemplate(longValue(row, "id"), string(row, "name"), string(row, "description"), TemplateStatus.valueOf(string(row, "status")), integer(row, "latest_version_no"), longValue(row, "row_version"), longValue(row, "created_by"), longValue(row, "updated_by")); }
    private TaskTemplateDraft task(Map<String, Object> row) { return new TaskTemplateDraft(longValue(row, "id"), longValue(row, "template_id"), string(row, "name"), Dimension.valueOf(string(row, "dimension")), readCheckItems(string(row, "check_items_json")), TemplateStatus.valueOf(string(row, "status")), integer(row, "latest_version_no"), longValue(row, "row_version")); }
    private TemplateVersion version(Map<String, Object> row) { return new TemplateVersion(longValue(row, "id"), longValue(row, "template_id"), integer(row, "version_no"), string(row, "content_json"), string(row, "note"), longValue(row, "published_by"), dateTime(value(row, "published_at"))); }
    private List<CheckItemDraft> readCheckItems(String json) { if (json == null || json.isBlank()) return List.of(); try { return objectMapper.readValue(json, new TypeReference<List<CheckItemDraft>>() {}); } catch (Exception exception) { throw new IllegalStateException("检查项快照格式错误", exception); } }
    private static Map<String, Object> p(Object... values) { Map<String, Object> params = new HashMap<>(); for (int i = 0; i < values.length; i += 2) params.put((String) values[i], values[i + 1]); return params; }
    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static Object value(Map<String, Object> row, String name) { Object value = row.get(name); return value == null ? row.get(toCamel(name)) : value; }
    private static String string(Map<String, Object> row, String name) { Object value = value(row, name); return value == null ? null : String.valueOf(value); }
    private static long longValue(Map<String, Object> row, String name) { return ((Number) value(row, name)).longValue(); }
    private static int integer(Map<String, Object> row, String name) { return ((Number) value(row, name)).intValue(); }
    private static Integer nullableInteger(Map<String, Object> row, String name) { Object value = value(row, name); return value == null ? null : ((Number) value).intValue(); }
    private static LocalDateTime dateTime(Object value) { return value instanceof Timestamp timestamp ? timestamp.toLocalDateTime() : (LocalDateTime) value; }
    private static String toCamel(String value) { StringBuilder result = new StringBuilder(); boolean upper = false; for (char character : value.toCharArray()) { if (character == '_') upper = true; else { result.append(upper ? Character.toUpperCase(character) : character); upper = false; } } return result.toString(); }
}
