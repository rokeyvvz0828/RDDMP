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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 搭建计划模板数据访问边界（REQ-20260830-056）。 */
@Repository
public class PlanTemplateStore {

    private static final String TEMPLATE_COLUMNS = """
            t.id, t.tenant_id, t.name, t.description, t.status, t.latest_version_no, t.row_version,
            t.created_by, t.updated_by
            """;

    private static final RowMapper<PlanTemplate> TEMPLATE_MAPPER = (rs, rowNum) -> new PlanTemplate(
            rs.getLong("id"), rs.getString("name"),
            rs.getString("description"), TemplateStatus.valueOf(rs.getString("status")),
            rs.getInt("latest_version_no"), rs.getLong("row_version"),
            rs.getLong("created_by"), rs.getLong("updated_by"));

    private static final RowMapper<StageDraft> STAGE_MAPPER = (rs, rowNum) -> new StageDraft(
            rs.getLong("id"), rs.getString("name"), rs.getInt("sort_no"),
            nullableInt(rs, "start_offset_days"), nullableInt(rs, "duration_days"), new ArrayList<>());

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public PlanTemplateStore(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("模板数据操作必须在事务内执行");
        }
    }

    public void insertTemplate(long tenantId, PlanTemplate template) {
        requireTransaction();
        jdbc.update("""
                INSERT INTO arch_plan_template
                    (id, tenant_id, name, description, status, latest_version_no, row_version,
                     created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, template.id(), tenantId, template.name(), template.description(),
                template.status().name(), template.latestVersionNo(), template.rowVersion(),
                template.createdBy(), template.updatedBy());
    }

    public void updateTemplate(long tenantId, long id, String name, String description, long rowVersion,
                               long updatedBy) {
        requireTransaction();
        int updated = jdbc.update("""
                UPDATE arch_plan_template
                SET name = ?, description = ?, row_version = row_version + 1, updated_by = ?
                WHERE tenant_id = ? AND id = ? AND row_version = ?
                """, name, description, updatedBy, tenantId, id, rowVersion);
        if (updated != 1) {
            throw new IllegalStateException("模板已被并发修改，请刷新后重试");
        }
    }

    public void updateTemplateStatus(long tenantId, long id, TemplateStatus status, long updatedBy) {
        requireTransaction();
        jdbc.update("""
                UPDATE arch_plan_template
                SET status = ?, row_version = row_version + 1, updated_by = ?
                WHERE tenant_id = ? AND id = ?
                """, status.name(), updatedBy, tenantId, id);
    }

    public void updateTemplateLatestVersion(long tenantId, long id, int latestVersionNo, long updatedBy) {
        requireTransaction();
        jdbc.update("""
                UPDATE arch_plan_template
                SET latest_version_no = ?, row_version = row_version + 1, updated_by = ?
                WHERE tenant_id = ? AND id = ?
                """, latestVersionNo, updatedBy, tenantId, id);
    }

    public Optional<PlanTemplate> findTemplate(long tenantId, long id) {
        return list(jdbc.query("""
                SELECT %s FROM arch_plan_template t
                WHERE t.tenant_id = ? AND t.id = ?
                """.formatted(TEMPLATE_COLUMNS), TEMPLATE_MAPPER, tenantId, id)).stream().findFirst();
    }

    public Optional<PlanTemplate> lockTemplate(long tenantId, long id) {
        requireTransaction();
        return list(jdbc.query("""
                SELECT %s FROM arch_plan_template t
                WHERE t.tenant_id = ? AND t.id = ? FOR UPDATE
                """.formatted(TEMPLATE_COLUMNS), TEMPLATE_MAPPER, tenantId, id)).stream().findFirst();
    }

    public List<PlanTemplate> searchTemplates(long tenantId, String keyword, TemplateStatus status,
                                              int limit, int offset) {
        if (limit <= 0 || offset < 0) {
            throw new IllegalArgumentException("分页参数无效");
        }
        StringBuilder where = new StringBuilder("WHERE t.tenant_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND t.name LIKE ?");
            args.add("%" + keyword.trim() + "%");
        }
        if (status != null) {
            where.append(" AND t.status = ?");
            args.add(status.name());
        }
        where.append(" ORDER BY t.updated_at DESC, t.id DESC LIMIT ? OFFSET ?");
        args.add(limit);
        args.add(offset);
        return jdbc.query("SELECT " + TEMPLATE_COLUMNS + " FROM arch_plan_template t " + where,
                TEMPLATE_MAPPER, args.toArray());
    }

    public long countTemplates(long tenantId, String keyword, TemplateStatus status) {
        StringBuilder where = new StringBuilder("WHERE tenant_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND name LIKE ?");
            args.add("%" + keyword.trim() + "%");
        }
        if (status != null) {
            where.append(" AND status = ?");
            args.add(status.name());
        }
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM arch_plan_template " + where, Long.class,
                args.toArray());
        return count == null ? 0 : count;
    }

    public void insertStage(long tenantId, long stageId, long templateId, String name, int sortNo,
                            Integer startOffsetDays, Integer durationDays, long createdBy) {
        requireTransaction();
        jdbc.update("""
                INSERT INTO arch_plan_template_stage
                    (id, tenant_id, template_id, name, sort_no, start_offset_days, duration_days,
                     created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, stageId, tenantId, templateId, name, sortNo, startOffsetDays, durationDays,
                createdBy, createdBy);
    }

    public void updateStage(long tenantId, long stageId, String name, int sortNo, Integer startOffsetDays,
                            Integer durationDays, long updatedBy) {
        requireTransaction();
        jdbc.update("""
                UPDATE arch_plan_template_stage
                SET name = ?, sort_no = ?, start_offset_days = ?, duration_days = ?, updated_by = ?
                WHERE tenant_id = ? AND id = ?
                """, name, sortNo, startOffsetDays, durationDays, updatedBy, tenantId, stageId);
    }

    public void deleteStage(long tenantId, long stageId) {
        requireTransaction();
        jdbc.update("DELETE FROM arch_plan_template_stage WHERE tenant_id = ? AND id = ?", tenantId, stageId);
    }

    public List<StageDraft> findStages(long tenantId, long templateId) {
        return list(jdbc.query("""
                SELECT id, tenant_id, name, sort_no, start_offset_days, duration_days
                FROM arch_plan_template_stage
                WHERE tenant_id = ? AND template_id = ?
                ORDER BY sort_no ASC, id ASC
                """, STAGE_MAPPER, tenantId, templateId));
    }

    public Long stageIdOfTaskTemplate(long tenantId, long taskTemplateId) {
        return jdbc.query("""
                SELECT stage_id FROM arch_task_template WHERE tenant_id = ? AND id = ?
                """, (rs, rowNum) -> rs.getLong("stage_id"), tenantId, taskTemplateId)
                .stream().findFirst().orElse(null);
    }

    public Optional<StageRef> findStageRef(long tenantId, long stageId) {
        return jdbc.query("""
                SELECT id, template_id, name, sort_no, start_offset_days, duration_days
                FROM arch_plan_template_stage
                WHERE tenant_id = ? AND id = ?
                """, (rs, rowNum) -> new StageRef(rs.getLong("id"), rs.getLong("template_id"),
                rs.getString("name"), rs.getInt("sort_no"),
                nullableInt(rs, "start_offset_days"), nullableInt(rs, "duration_days")),
                tenantId, stageId).stream().findFirst();
    }

    public Optional<Long> findStageId(long tenantId, long stageId) {
        return jdbc.query("""
                SELECT id FROM arch_plan_template_stage WHERE tenant_id = ? AND id = ?
                """, (rs, rowNum) -> rs.getLong("id"), tenantId, stageId).stream().findFirst();
    }

    public void insertTaskTemplate(long tenantId, TaskTemplateDraft task, long templateId, long stageId,
                                   String checkItemsJson, long createdBy) {
        requireTransaction();
        jdbc.update("""
                INSERT INTO arch_task_template
                    (id, tenant_id, template_id, stage_id, name, dimension, description,
                     check_items_json, status, latest_version_no, row_version, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, NULL, ?, ?, 0, 0, ?, ?)
                """, task.id(), tenantId, templateId, stageId, task.name(), task.dimension().name(),
                checkItemsJson, task.status().name(), createdBy, createdBy);
    }

    public void updateTaskTemplate(long tenantId, TaskTemplateDraft task, String checkItemsJson,
                                   long updatedBy) {
        requireTransaction();
        int updated = jdbc.update("""
                UPDATE arch_task_template
                SET name = ?, dimension = ?, check_items_json = ?, row_version = row_version + 1,
                    updated_by = ?
                WHERE tenant_id = ? AND id = ? AND row_version = ?
                """, task.name(), task.dimension().name(), checkItemsJson, updatedBy, tenantId,
                task.id(), task.rowVersion());
        if (updated != 1) {
            throw new IllegalStateException("任务模板已被并发修改，请刷新后重试");
        }
    }

    public void updateTaskTemplateStatus(long tenantId, long taskId, TemplateStatus status, long updatedBy) {
        requireTransaction();
        jdbc.update("""
                UPDATE arch_task_template SET status = ?, row_version = row_version + 1, updated_by = ?
                WHERE tenant_id = ? AND id = ?
                """, status.name(), updatedBy, tenantId, taskId);
    }

    public void updateTaskTemplateLatestVersion(long tenantId, long taskId, int latestVersionNo,
                                                long updatedBy) {
        requireTransaction();
        jdbc.update("""
                UPDATE arch_task_template SET latest_version_no = ?, row_version = row_version + 1,
                    updated_by = ?
                WHERE tenant_id = ? AND id = ?
                """, latestVersionNo, updatedBy, tenantId, taskId);
    }

    public void deleteTaskTemplate(long tenantId, long taskId) {
        requireTransaction();
        jdbc.update("DELETE FROM arch_task_template WHERE tenant_id = ? AND id = ?", tenantId, taskId);
    }

    public List<TaskTemplateDraft> findTaskTemplates(long tenantId, Long templateId, Long stageId) {
        StringBuilder where = new StringBuilder("WHERE tenant_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (templateId != null) {
            where.append(" AND template_id = ?");
            args.add(templateId);
        }
        if (stageId != null) {
            where.append(" AND stage_id = ?");
            args.add(stageId);
        }
        where.append(" ORDER BY id ASC");
        return jdbc.query("""
                SELECT id, template_id, stage_id, name, dimension, check_items_json, status, latest_version_no,
                       row_version
                FROM arch_task_template
                """ + where, (rs, rowNum) -> new TaskTemplateDraft(rs.getLong("id"),
                rs.getLong("template_id"), rs.getString("name"),
                Dimension.valueOf(rs.getString("dimension")), readCheckItems(rs.getString("check_items_json")),
                TemplateStatus.valueOf(rs.getString("status")), rs.getInt("latest_version_no"),
                rs.getLong("row_version")),
                args.toArray());
    }

    public Optional<TaskTemplateDraft> findTaskTemplate(long tenantId, long taskId) {
        return jdbc.query("""
                SELECT id, template_id, stage_id, name, dimension, check_items_json, status, latest_version_no,
                       row_version
                FROM arch_task_template
                WHERE tenant_id = ? AND id = ?
                """, (rs, rowNum) -> new TaskTemplateDraft(rs.getLong("id"),
                rs.getLong("template_id"), rs.getString("name"),
                Dimension.valueOf(rs.getString("dimension")), readCheckItems(rs.getString("check_items_json")),
                TemplateStatus.valueOf(rs.getString("status")), rs.getInt("latest_version_no"),
                rs.getLong("row_version")),
                tenantId, taskId).stream().findFirst();
    }

    public Optional<TaskTemplateVersionMeta> taskTemplateVersionMeta(long tenantId, long taskTemplateId,
                                                                     int versionNo) {
        return jdbc.query("""
                SELECT name, dimension, check_items_json FROM arch_task_template_version
                WHERE tenant_id = ? AND task_template_id = ? AND version_no = ?
                """, (rs, rowNum) -> new TaskTemplateVersionMeta(rs.getString("name"),
                Dimension.valueOf(rs.getString("dimension")), rs.getString("check_items_json")),
                tenantId, taskTemplateId, versionNo).stream().findFirst();
    }

    public String taskTemplateCheckItemsJson(long tenantId, long taskId) {
        return jdbc.query("""
                SELECT check_items_json FROM arch_task_template WHERE tenant_id = ? AND id = ?
                """, (rs, rowNum) -> rs.getString("check_items_json"), tenantId, taskId).stream()
                .findFirst().orElse(null);
    }

    public int latestTaskTemplateVersion(long tenantId, long taskId) {
        Integer value = jdbc.queryForObject("""
                SELECT MAX(version_no) FROM arch_task_template_version
                WHERE tenant_id = ? AND task_template_id = ?
                """, Integer.class, tenantId, taskId);
        return value == null ? 0 : value;
    }

    public void insertTaskTemplateVersion(long tenantId, long versionId, long taskTemplateId, int versionNo,
                                          String name, Dimension dimension, String checkItemsJson,
                                          String note, long publishedBy) {
        requireTransaction();
        jdbc.update("""
                INSERT INTO arch_task_template_version
                    (id, tenant_id, task_template_id, version_no, name, dimension, check_items_json, note,
                     published_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, versionId, tenantId, taskTemplateId, versionNo, name, dimension.name(), checkItemsJson,
                note, publishedBy);
    }

    public void insertTemplateVersion(long tenantId, long versionId, long templateId, int versionNo,
                                      String contentJson, String note, long publishedBy) {
        requireTransaction();
        jdbc.update("""
                INSERT INTO arch_plan_template_version
                    (id, tenant_id, template_id, version_no, content_json, note, published_by)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, versionId, tenantId, templateId, versionNo, contentJson, note, publishedBy);
    }

    public List<TemplateVersion> findTemplateVersions(long tenantId, long templateId) {
        return jdbc.query("""
                SELECT id, template_id, version_no, content_json, note, published_by, published_at
                FROM arch_plan_template_version
                WHERE tenant_id = ? AND template_id = ?
                ORDER BY version_no DESC
                """, (rs, rowNum) -> new TemplateVersion(rs.getLong("id"), rs.getLong("template_id"),
                rs.getInt("version_no"), rs.getString("content_json"), rs.getString("note"),
                rs.getLong("published_by"), toLocalDateTime(rs.getTimestamp("published_at"))),
                tenantId, templateId);
    }

    public Optional<TemplateVersion> findTemplateVersion(long tenantId, long templateId, int versionNo) {
        return jdbc.query("""
                SELECT id, template_id, version_no, content_json, note, published_by, published_at
                FROM arch_plan_template_version
                WHERE tenant_id = ? AND template_id = ? AND version_no = ?
                """, (rs, rowNum) -> new TemplateVersion(rs.getLong("id"), rs.getLong("template_id"),
                rs.getInt("version_no"), rs.getString("content_json"), rs.getString("note"),
                rs.getLong("published_by"), toLocalDateTime(rs.getTimestamp("published_at"))),
                tenantId, templateId, versionNo).stream().findFirst();
    }

    public void insertStageDependency(long tenantId, long id, long templateId, long stageId,
                                      long predecessorStageId, long createdBy) {
        requireTransaction();
        jdbc.update("""
                INSERT INTO arch_plan_template_stage_dependency
                    (id, tenant_id, template_id, stage_id, predecessor_stage_id, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, id, tenantId, templateId, stageId, predecessorStageId, createdBy, createdBy);
    }

    public List<Long[]> findStageDependencies(long tenantId, long templateId) {
        return jdbc.query("""
                SELECT stage_id, predecessor_stage_id FROM arch_plan_template_stage_dependency
                WHERE tenant_id = ? AND template_id = ?
                ORDER BY id ASC
                """, (rs, rowNum) -> new Long[]{rs.getLong("stage_id"),
                rs.getLong("predecessor_stage_id")}, tenantId, templateId);
    }

    /** 删除指定环节作为「后续环节」的依赖记录（重设前置时先清空；删除环节时由外键级联清理）。 */
    public void deleteStageDependencies(long tenantId, long stageId) {
        requireTransaction();
        jdbc.update("""
                DELETE FROM arch_plan_template_stage_dependency
                WHERE tenant_id = ? AND stage_id = ?
                """, tenantId, stageId);
    }

    public void insertTaskTemplateDependency(long tenantId, long id, long templateId, long stageId,
                                             long taskTemplateId, long predecessorTaskTemplateId,
                                             long createdBy) {
        requireTransaction();
        jdbc.update("""
                INSERT INTO arch_task_template_dependency
                    (id, tenant_id, template_id, stage_id, task_template_id, predecessor_task_template_id,
                     created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, id, tenantId, templateId, stageId, taskTemplateId, predecessorTaskTemplateId,
                createdBy, createdBy);
    }

    public List<Long[]> findTaskTemplateDependencies(long tenantId, Long templateId, Long stageId) {
        StringBuilder where = new StringBuilder("WHERE tenant_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (templateId != null) {
            where.append(" AND template_id = ?");
            args.add(templateId);
        }
        if (stageId != null) {
            where.append(" AND stage_id = ?");
            args.add(stageId);
        }
        where.append(" ORDER BY id ASC");
        return jdbc.query("""
                SELECT task_template_id, predecessor_task_template_id FROM arch_task_template_dependency
                """ + where, (rs, rowNum) -> new Long[]{rs.getLong("task_template_id"),
                rs.getLong("predecessor_task_template_id")}, args.toArray());
    }

    /** 删除指定任务模板作为「后续任务」的依赖记录（重设前置时先清空；删除任务时由外键级联清理）。 */
    public void deleteTaskTemplateDependencies(long tenantId, Long templateId, Long stageId,
                                               Long taskTemplateId) {
        requireTransaction();
        StringBuilder where = new StringBuilder("WHERE tenant_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (templateId != null) {
            where.append(" AND template_id = ?");
            args.add(templateId);
        }
        if (stageId != null) {
            where.append(" AND stage_id = ?");
            args.add(stageId);
        }
        if (taskTemplateId != null) {
            where.append(" AND task_template_id = ?");
            args.add(taskTemplateId);
        }
        jdbc.update("DELETE FROM arch_task_template_dependency " + where, args.toArray());
    }

    public void insertActivity(long tenantId, long id, String scopeType, long scopeId, String objectType,
                               Long objectId, String action, long operatorUserId, String reason,
                               String beforeJson, String afterJson) {
        requireTransaction();
        jdbc.update("""
                INSERT INTO arch_setup_plan_activity
                    (id, tenant_id, scope_type, scope_id, object_type, object_id, action,
                     operator_user_id, reason, before_json, after_json)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, tenantId, scopeType, scopeId, objectType, objectId, action, operatorUserId,
                reason, beforeJson, afterJson);
    }

    private List<CheckItemDraft> readCheckItems(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<CheckItemDraft>>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException("检查项快照格式错误", e);
        }
    }

    private static Integer nullableInt(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static java.time.LocalDateTime toLocalDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    private static <T> List<T> list(List<T> values) {
        return values == null ? List.of() : values;
    }
}
