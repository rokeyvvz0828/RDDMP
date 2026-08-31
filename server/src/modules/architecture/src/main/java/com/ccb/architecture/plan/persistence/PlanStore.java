package com.ccb.architecture.plan.persistence;

import com.ccb.architecture.plan.model.PlanModels.Block;
import com.ccb.architecture.plan.model.PlanModels.CancelSuggestion;
import com.ccb.architecture.plan.model.PlanModels.CheckItem;
import com.ccb.architecture.plan.model.PlanModels.CheckItemStatus;
import com.ccb.architecture.plan.model.PlanModels.Dependency;
import com.ccb.architecture.plan.model.PlanModels.EventType;
import com.ccb.architecture.plan.model.PlanModels.Plan;
import com.ccb.architecture.plan.model.PlanModels.PlanEvent;
import com.ccb.architecture.plan.model.PlanModels.PlanStatus;
import com.ccb.architecture.plan.model.PlanModels.PlanTarget;
import com.ccb.architecture.plan.model.PlanModels.Stage;
import com.ccb.architecture.plan.model.PlanModels.TargetType;
import com.ccb.architecture.plan.model.PlanModels.Task;
import com.ccb.architecture.plan.model.PlanModels.TaskStatus;
import com.ccb.architecture.plan.model.PlanModels.TaskWorkOrder;
import com.ccb.architecture.plan.model.PlanModels.WorkOrderSource;
import com.ccb.architecture.plan.model.PlanModels.WorkOrderType;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 搭建计划数据访问边界（REQ-20260830-056）。同一模块内只读 arch_ 表与公共架构表。 */
@Repository
public class PlanStore {

    private static final String PLAN_COLUMNS = """
            p.id, p.tenant_id, p.plan_no, p.name, p.environment_id, p.status, p.template_id,
            p.template_version_no, p.plan_owner_user_id, p.planned_start, p.planned_end,
            p.actual_start, p.actual_end, p.cancelled, p.cancel_reason, p.cancelled_by,
            p.cancelled_at, p.row_version
            """;

    private static final RowMapper<Plan> PLAN_MAPPER = (rs, rowNum) -> new Plan(
            rs.getLong("id"), rs.getString("plan_no"), rs.getString("name"),
            rs.getLong("environment_id"), PlanStatus.valueOf(rs.getString("status")),
            rs.getLong("template_id"), rs.getInt("template_version_no"),
            rs.getLong("plan_owner_user_id"), toLocalDateTime(rs.getTimestamp("planned_start")),
            toLocalDateTime(rs.getTimestamp("planned_end")),
            toLocalDateTime(rs.getTimestamp("actual_start")),
            toLocalDateTime(rs.getTimestamp("actual_end")),
            rs.getBoolean("cancelled"), rs.getString("cancel_reason"),
            nullableLong(rs, "cancelled_by"), toLocalDateTime(rs.getTimestamp("cancelled_at")),
            rs.getLong("row_version"));

    private static final RowMapper<Stage> STAGE_MAPPER = (rs, rowNum) -> new Stage(
            rs.getLong("id"), rs.getLong("plan_id"), rs.getInt("stage_no"), rs.getString("name"),
            rs.getInt("sort_no"), rs.getLong("owner_user_id"),
            toLocalDateTime(rs.getTimestamp("planned_start")),
            toLocalDateTime(rs.getTimestamp("planned_end")),
            toLocalDateTime(rs.getTimestamp("actual_start")),
            toLocalDateTime(rs.getTimestamp("actual_end")),
            PlanStatus.valueOf(rs.getString("status")), rs.getBoolean("cancelled"),
            rs.getString("cancel_reason"), nullableLong(rs, "cancelled_by"),
            toLocalDateTime(rs.getTimestamp("cancelled_at")), rs.getString("snapshot_json"));

    private static final RowMapper<Task> TASK_MAPPER = (rs, rowNum) -> new Task(
            rs.getLong("id"), rs.getLong("plan_id"), rs.getLong("stage_id"), rs.getInt("task_no"),
            rs.getString("name"), nullableTargetType(rs.getString("target_type")),
            nullableLong(rs, "target_id"), rs.getString("target_no"), rs.getString("target_name"),
            nullableLong(rs, "task_template_id"),
            nullableInt(rs, "task_template_version_no"), rs.getString("dimension"),
            rs.getString("snapshot_json"), rs.getLong("owner_user_id"),
            toLocalDateTime(rs.getTimestamp("planned_start")),
            toLocalDateTime(rs.getTimestamp("planned_end")),
            toLocalDateTime(rs.getTimestamp("actual_start")),
            toLocalDateTime(rs.getTimestamp("actual_end")),
            TaskStatus.valueOf(rs.getString("status")), rs.getBoolean("waived_all"),
            rs.getBoolean("cancelled"), rs.getString("cancel_reason"),
            nullableLong(rs, "cancelled_by"), toLocalDateTime(rs.getTimestamp("cancelled_at")),
            rs.getLong("row_version"));

    private static final RowMapper<CheckItem> CHECK_ITEM_MAPPER = (rs, rowNum) -> new CheckItem(
            rs.getLong("id"), rs.getLong("task_id"), rs.getInt("check_no"), rs.getString("name"),
            rs.getInt("sort_no"), rs.getString("guide"),
            CheckItemStatus.valueOf(rs.getString("status")), rs.getString("remark"),
            nullableLong(rs, "completed_by"), toLocalDateTime(rs.getTimestamp("completed_at")),
            rs.getBoolean("cancelled"), rs.getString("cancel_reason"),
            nullableLong(rs, "cancelled_by"), toLocalDateTime(rs.getTimestamp("cancelled_at")),
            rs.getLong("row_version"), rs.getLong("created_by"));

    private static final RowMapper<PlanTarget> TARGET_MAPPER = (rs, rowNum) -> new PlanTarget(
            rs.getLong("id"), rs.getLong("plan_id"), TargetType.valueOf(rs.getString("target_type")),
            rs.getLong("target_id"), rs.getString("target_no"), rs.getString("target_name"),
            "REMOVED".equals(rs.getString("status")), rs.getString("removed_reason"));

    private final JdbcTemplate jdbc;

    public PlanStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("计划数据操作必须在事务内执行");
        }
    }

    // ---------- 计划 ----------

    public void insertPlan(long tenantId, Plan plan) {
        requireTransaction();
        jdbc.update("""
                INSERT INTO arch_setup_plan
                    (id, tenant_id, plan_no, name, environment_id, status, template_id,
                     template_version_no, plan_owner_user_id, planned_start, planned_end, row_version,
                     created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, plan.id(), tenantId, plan.planNo(), plan.name(), plan.environmentId(),
                plan.status().name(), plan.templateId(), plan.templateVersionNo(),
                plan.planOwnerUserId(), toTimestamp(plan.plannedStart()), toTimestamp(plan.plannedEnd()),
                plan.rowVersion(), plan.planOwnerUserId(), plan.planOwnerUserId());
    }

    public Optional<Plan> findPlan(long tenantId, long planId) {
        return jdbc.query("SELECT " + PLAN_COLUMNS
                        + " FROM arch_setup_plan p WHERE p.tenant_id = ? AND p.id = ?",
                PLAN_MAPPER, tenantId, planId).stream().findFirst();
    }

    public Optional<Plan> lockPlan(long tenantId, long planId) {
        requireTransaction();
        return jdbc.query("SELECT " + PLAN_COLUMNS
                        + " FROM arch_setup_plan p WHERE p.tenant_id = ? AND p.id = ? FOR UPDATE",
                PLAN_MAPPER, tenantId, planId).stream().findFirst();
    }

    public void updatePlanStatus(long tenantId, long planId, PlanStatus status, boolean cancelled,
                                 String cancelReason, Long cancelledBy, LocalDateTime cancelledAt) {
        requireTransaction();
        jdbc.update("""
                UPDATE arch_setup_plan SET status = ?, cancelled = ?, cancel_reason = ?, cancelled_by = ?,
                    cancelled_at = ?, updated_by = ?
                WHERE tenant_id = ? AND id = ?
                """, status.name(), cancelled ? 1 : 0, cancelReason, cancelledBy, toTimestamp(cancelledAt),
                cancelledBy == null ? 0 : cancelledBy, tenantId, planId);
    }

    public void updatePlanSchedule(long tenantId, long planId, LocalDateTime plannedStart,
                                   LocalDateTime plannedEnd) {
        requireTransaction();
        jdbc.update("""
                UPDATE arch_setup_plan SET planned_start = ?, planned_end = ?,
                    row_version = row_version + 1
                WHERE tenant_id = ? AND id = ?
                """, toTimestamp(plannedStart), toTimestamp(plannedEnd), tenantId, planId);
    }

    public void updatePlanActual(long tenantId, long planId, LocalDateTime actualStart,
                                 LocalDateTime actualEnd) {
        requireTransaction();
        jdbc.update("""
                UPDATE arch_setup_plan SET actual_start = ?, actual_end = ?
                WHERE tenant_id = ? AND id = ?
                """, toTimestamp(actualStart), toTimestamp(actualEnd), tenantId, planId);
    }

    // ---------- 目标快照 ----------

    public void insertTarget(long tenantId, PlanTarget target, String addedReason) {
        requireTransaction();
        jdbc.update("""
                INSERT INTO arch_plan_target
                    (id, tenant_id, plan_id, target_type, target_id, target_no, target_name,
                     target_snapshot_json, status, added_reason, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, NULL, 'ACTIVE', ?, ?, ?)
                """, target.id(), tenantId, target.planId(), target.targetType().name(),
                target.targetId(), target.targetNo(), target.targetName(), addedReason,
                target.id(), target.id());
    }

    public List<PlanTarget> findTargets(long tenantId, long planId, boolean removedIncluded) {
        String condition = removedIncluded ? "" : " AND status = 'ACTIVE'";
        return jdbc.query("""
                SELECT id, plan_id, target_type, target_id, target_no, target_name, status, removed_reason
                FROM arch_plan_target WHERE tenant_id = ? AND plan_id = ? AND status IN ('ACTIVE', 'REMOVED')
                """ + condition + " ORDER BY target_type ASC, target_id ASC",
                TARGET_MAPPER, tenantId, planId);
    }

    public List<PlanTarget> findActiveTargets(long tenantId, long planId) {
        return findTargets(tenantId, planId, false);
    }

    public Optional<PlanTarget> findTarget(long tenantId, long planId, TargetType targetType,
                                           long targetId) {
        return jdbc.query("""
                SELECT id, plan_id, target_type, target_id, target_no, target_name, status, removed_reason
                FROM arch_plan_target
                WHERE tenant_id = ? AND plan_id = ? AND target_type = ? AND target_id = ?
                """, TARGET_MAPPER, tenantId, planId, targetType.name(), targetId).stream().findFirst();
    }

    public void removeTarget(long tenantId, long targetId, String reason, long removedBy) {
        requireTransaction();
        jdbc.update("""
                UPDATE arch_plan_target SET status = 'REMOVED', removed_reason = ?, removed_by = ?,
                    updated_by = ?
                WHERE tenant_id = ? AND id = ?
                """, reason, removedBy, removedBy, tenantId, targetId);
    }

    // ---------- 环节 ----------

    public void insertStage(long tenantId, Stage stage) {
        requireTransaction();
        jdbc.update("""
                INSERT INTO arch_plan_stage
                    (id, tenant_id, plan_id, stage_no, name, sort_no, owner_user_id, planned_start,
                     planned_end, status, snapshot_json, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, stage.id(), tenantId, stage.planId(), stage.stageNo(), stage.name(), stage.sortNo(),
                stage.ownerUserId(), toTimestamp(stage.plannedStart()), toTimestamp(stage.plannedEnd()),
                stage.status().name(), stage.snapshotJson(), stage.ownerUserId(), stage.ownerUserId());
    }

    public List<Stage> findStages(long tenantId, long planId) {
        return jdbc.query("""
                SELECT id, plan_id, stage_no, name, sort_no, owner_user_id, planned_start, planned_end,
                       actual_start, actual_end, status, cancelled, cancel_reason, cancelled_by,
                       cancelled_at, snapshot_json
                FROM arch_plan_stage WHERE tenant_id = ? AND plan_id = ?
                ORDER BY sort_no ASC, id ASC
                """, STAGE_MAPPER, tenantId, planId);
    }

    public Optional<Stage> findStage(long tenantId, long stageId) {
        return jdbc.query("""
                SELECT id, plan_id, stage_no, name, sort_no, owner_user_id, planned_start, planned_end,
                       actual_start, actual_end, status, cancelled, cancel_reason, cancelled_by,
                       cancelled_at, snapshot_json
                FROM arch_plan_stage WHERE tenant_id = ? AND id = ?
                """, STAGE_MAPPER, tenantId, stageId).stream().findFirst();
    }

    public void updateStageStatus(long tenantId, long stageId, PlanStatus status, boolean cancelled,
                                  String cancelReason, Long cancelledBy, LocalDateTime cancelledAt) {
        requireTransaction();
        jdbc.update("""
                UPDATE arch_plan_stage SET status = ?, cancelled = ?, cancel_reason = ?, cancelled_by = ?,
                    cancelled_at = ?
                WHERE tenant_id = ? AND id = ?
                """, status.name(), cancelled ? 1 : 0, cancelReason, cancelledBy,
                toTimestamp(cancelledAt), tenantId, stageId);
    }

    public void updateStageSchedule(long tenantId, long stageId, LocalDateTime plannedStart,
                                    LocalDateTime plannedEnd) {
        requireTransaction();
        jdbc.update("""
                UPDATE arch_plan_stage SET planned_start = ?, planned_end = ?
                WHERE tenant_id = ? AND id = ?
                """, toTimestamp(plannedStart), toTimestamp(plannedEnd), tenantId, stageId);
    }

    public void updateStageActual(long tenantId, long stageId, LocalDateTime actualStart,
                                  LocalDateTime actualEnd) {
        requireTransaction();
        jdbc.update("""
                UPDATE arch_plan_stage SET actual_start = ?, actual_end = ?
                WHERE tenant_id = ? AND id = ?
                """, toTimestamp(actualStart), toTimestamp(actualEnd), tenantId, stageId);
    }

    // ---------- 任务 ----------

    public void insertTask(long tenantId, Task task) {
        requireTransaction();
        jdbc.update("""
                INSERT INTO arch_plan_task
                    (id, tenant_id, plan_id, stage_id, task_no, name, target_type, target_id, target_no,
                     target_name, task_template_id, task_template_version_no, dimension, snapshot_json,
                     owner_user_id, planned_start, planned_end, status, row_version, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, task.id(), tenantId, task.planId(), task.stageId(), task.taskNo(), task.name(),
                task.targetType().name(), task.targetId(), task.targetNo(), task.targetName(),
                task.taskTemplateId(), task.taskTemplateVersionNo(), task.dimension(), task.snapshotJson(),
                task.ownerUserId(), toTimestamp(task.plannedStart()), toTimestamp(task.plannedEnd()),
                task.status().name(), task.rowVersion(), task.ownerUserId(), task.ownerUserId());
    }

    public Optional<Task> findTask(long tenantId, long taskId) {
        return jdbc.query(taskSelect("WHERE task.tenant_id = ? AND task.id = ?"), TASK_MAPPER,
                tenantId, taskId).stream().findFirst();
    }

    public Optional<Task> lockTask(long tenantId, long taskId) {
        requireTransaction();
        return jdbc.query(taskSelect("WHERE task.tenant_id = ? AND task.id = ? FOR UPDATE"),
                TASK_MAPPER, tenantId, taskId).stream().findFirst();
    }

    public List<Task> findTasks(long tenantId, Long planId, Long stageId) {
        StringBuilder where = new StringBuilder("WHERE task.tenant_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (planId != null) {
            where.append(" AND task.plan_id = ?");
            args.add(planId);
        }
        if (stageId != null) {
            where.append(" AND task.stage_id = ?");
            args.add(stageId);
        }
        where.append(" ORDER BY task.id ASC");
        return jdbc.query(taskSelect(where.toString()), TASK_MAPPER, args.toArray());
    }

    public void updateTaskExecution(long tenantId, long taskId, TaskStatus status,
                                    LocalDateTime actualStart, LocalDateTime actualEnd,
                                    boolean waivedAll, long updatedBy) {
        requireTransaction();
        jdbc.update("""
                UPDATE arch_plan_task SET status = ?, actual_start = ?, actual_end = ?, waived_all = ?,
                    row_version = row_version + 1, updated_by = ?
                WHERE tenant_id = ? AND id = ?
                """, status.name(), toTimestamp(actualStart), toTimestamp(actualEnd), waivedAll ? 1 : 0,
                updatedBy, tenantId, taskId);
    }

    public void updateTaskCancel(long tenantId, long taskId, boolean cancelled, String cancelReason,
                                 Long cancelledBy, LocalDateTime cancelledAt) {
        requireTransaction();
        jdbc.update("""
                UPDATE arch_plan_task SET cancelled = ?, cancel_reason = ?, cancelled_by = ?,
                    cancelled_at = ?, row_version = row_version + 1
                WHERE tenant_id = ? AND id = ?
                """, cancelled ? 1 : 0, cancelReason, cancelledBy, toTimestamp(cancelledAt),
                tenantId, taskId);
    }

    public void updateTaskSchedule(long tenantId, long taskId, LocalDateTime plannedStart,
                                   LocalDateTime plannedEnd) {
        requireTransaction();
        jdbc.update("""
                UPDATE arch_plan_task SET planned_start = ?, planned_end = ?
                WHERE tenant_id = ? AND id = ?
                """, toTimestamp(plannedStart), toTimestamp(plannedEnd), tenantId, taskId);
    }

    public void updateTaskOwner(long tenantId, long taskId, long ownerUserId, long updatedBy) {
        requireTransaction();
        jdbc.update("""
                UPDATE arch_plan_task SET owner_user_id = ?, updated_by = ?
                WHERE tenant_id = ? AND id = ?
                """, ownerUserId, updatedBy, tenantId, taskId);
    }

    public void deleteTask(long tenantId, long taskId) {
        requireTransaction();
        jdbc.update("DELETE FROM arch_plan_task WHERE tenant_id = ? AND id = ?", tenantId, taskId);
    }

    // ---------- 检查项 ----------

    public void insertCheckItem(long tenantId, CheckItem item) {
        requireTransaction();
        jdbc.update("""
                INSERT INTO arch_plan_check_item
                    (id, tenant_id, task_id, check_no, name, guide, sort_no, status, row_version,
                     created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, item.id(), tenantId, item.taskId(), item.checkNo(), item.name(), item.guide(),
                item.sortNo(), item.status().name(), item.rowVersion(), item.createdBy(),
                item.createdBy());
    }

    public List<CheckItem> findCheckItems(long tenantId, long taskId) {
        return jdbc.query("""
                SELECT id, task_id, check_no, name, guide, sort_no, status, remark, completed_by,
                       completed_at, cancelled, cancel_reason, cancelled_by, cancelled_at, row_version,
                       created_by
                FROM arch_plan_check_item WHERE tenant_id = ? AND task_id = ?
                ORDER BY sort_no ASC, id ASC
                """, CHECK_ITEM_MAPPER, tenantId, taskId);
    }

    public Optional<CheckItem> findCheckItem(long tenantId, long checkItemId) {
        return jdbc.query("""
                SELECT id, task_id, check_no, name, guide, sort_no, status, remark, completed_by,
                       completed_at, cancelled, cancel_reason, cancelled_by, cancelled_at, row_version,
                       created_by
                FROM arch_plan_check_item WHERE tenant_id = ? AND id = ?
                """, CHECK_ITEM_MAPPER, tenantId, checkItemId).stream().findFirst();
    }

    public void updateCheckItemCompletion(long tenantId, long checkItemId, CheckItemStatus status,
                                          String remark, Long completedBy, LocalDateTime completedAt,
                                          long updatedBy) {
        requireTransaction();
        jdbc.update("""
                UPDATE arch_plan_check_item SET status = ?, remark = ?, completed_by = ?, completed_at = ?,
                    row_version = row_version + 1, updated_by = ?
                WHERE tenant_id = ? AND id = ?
                """, status.name(), remark, completedBy, toTimestamp(completedAt), updatedBy,
                tenantId, checkItemId);
    }

    public void updateCheckItemCancel(long tenantId, long checkItemId, boolean cancelled,
                                      String cancelReason, Long cancelledBy, LocalDateTime cancelledAt) {
        requireTransaction();
        jdbc.update("""
                UPDATE arch_plan_check_item SET cancelled = ?, cancel_reason = ?, cancelled_by = ?,
                    cancelled_at = ?, status = ?, row_version = row_version + 1
                WHERE tenant_id = ? AND id = ?
                """, cancelled ? 1 : 0, cancelReason, cancelledBy, toTimestamp(cancelledAt),
                cancelled ? "CANCELLED" : "PENDING", tenantId, checkItemId);
    }

    public void deleteCheckItem(long tenantId, long checkItemId) {
        requireTransaction();
        jdbc.update("DELETE FROM arch_plan_check_item WHERE tenant_id = ? AND id = ?", tenantId, checkItemId);
    }

    // ---------- 参与人 ----------

    public void insertParticipant(long tenantId, long id, long taskId, long userId, long createdBy) {
        requireTransaction();
        jdbc.update("""
                INSERT INTO arch_plan_task_participant (id, tenant_id, task_id, user_id, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?)
                """, id, tenantId, taskId, userId, createdBy, createdBy);
    }

    public List<Long> findParticipantUserIds(long tenantId, long taskId) {
        return jdbc.query("""
                SELECT user_id FROM arch_plan_task_participant WHERE tenant_id = ? AND task_id = ?
                ORDER BY id ASC
                """, (rs, rowNum) -> rs.getLong("user_id"), tenantId, taskId);
    }

    public void deleteParticipants(long tenantId, long taskId) {
        requireTransaction();
        jdbc.update("DELETE FROM arch_plan_task_participant WHERE tenant_id = ? AND task_id = ?",
                tenantId, taskId);
    }

    // ---------- 依赖 ----------

    public void insertDependency(long tenantId, long id, long taskId, long predecessorId, long createdBy) {
        requireTransaction();
        jdbc.update("""
                INSERT INTO arch_plan_task_dependency
                    (id, tenant_id, task_id, predecessor_id, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?)
                """, id, tenantId, taskId, predecessorId, createdBy, createdBy);
    }

    public List<Dependency> findDependencies(long tenantId, long taskId, boolean removedIncluded) {
        String condition = removedIncluded ? "" : " AND status = 'ACTIVE'";
        return jdbc.query("""
                SELECT id, task_id, predecessor_id, status, removed_reason
                FROM arch_plan_task_dependency
                WHERE tenant_id = ? AND task_id = ? AND status IN ('ACTIVE', 'REMOVED')
                """ + condition + " ORDER BY id ASC", (rs, rowNum) -> new Dependency(
                rs.getLong("id"), rs.getLong("task_id"), rs.getLong("predecessor_id"),
                "REMOVED".equals(rs.getString("status")), rs.getString("removed_reason")),
                tenantId, taskId);
    }

    public Optional<Dependency> findDependencyById(long tenantId, long dependencyId) {
        return jdbc.query("""
                SELECT id, task_id, predecessor_id, status, removed_reason
                FROM arch_plan_task_dependency
                WHERE tenant_id = ? AND id = ?
                """, (rs, rowNum) -> new Dependency(rs.getLong("id"), rs.getLong("task_id"),
                rs.getLong("predecessor_id"), "REMOVED".equals(rs.getString("status")),
                rs.getString("removed_reason")), tenantId, dependencyId).stream().findFirst();
    }

    public void removeDependency(long tenantId, long id, String reason, long removedBy) {
        requireTransaction();
        jdbc.update("""
                UPDATE arch_plan_task_dependency SET status = 'REMOVED', removed_reason = ?,
                    removed_by = ?, updated_by = ?
                WHERE tenant_id = ? AND id = ?
                """, reason, removedBy, removedBy, tenantId, id);
    }

    public void deleteDependenciesByTask(long tenantId, long taskId) {
        requireTransaction();
        jdbc.update("""
                DELETE FROM arch_plan_task_dependency WHERE tenant_id = ? AND (task_id = ? OR predecessor_id = ?)
                """, tenantId, taskId, taskId);
    }

    // ---------- 阻塞 ----------

    public void insertBlock(long tenantId, Block block) {
        requireTransaction();
        jdbc.update("""
                INSERT INTO arch_plan_block
                    (id, tenant_id, task_id, description, impact, owner_user_id, expected_resolve_at, status,
                     created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'OPEN', ?, ?)
                """, block.id(), tenantId, block.taskId(), block.description(), block.impact(),
                block.ownerUserId(), toTimestamp(block.expectedResolveAt()), block.createdBy(),
                block.createdBy());
    }

    public List<Block> findBlocks(long tenantId, long taskId) {
        return jdbc.query("""
                SELECT id, task_id, description, impact, owner_user_id, expected_resolve_at, status,
                       resolved_note, resolved_by, resolved_at, created_by
                FROM arch_plan_block WHERE tenant_id = ? AND task_id = ?
                ORDER BY id ASC
                """, BLOCK_MAPPER, tenantId, taskId);
    }

    public Optional<Block> findBlock(long tenantId, long blockId) {
        return jdbc.query("""
                SELECT id, task_id, description, impact, owner_user_id, expected_resolve_at, status,
                       resolved_note, resolved_by, resolved_at, created_by
                FROM arch_plan_block WHERE tenant_id = ? AND id = ?
                """, BLOCK_MAPPER, tenantId, blockId).stream().findFirst();
    }

    public void updateBlock(long tenantId, long blockId, String description, String impact,
                            long ownerUserId, LocalDateTime expectedResolveAt) {
        requireTransaction();
        jdbc.update("""
                UPDATE arch_plan_block SET description = ?, impact = ?, owner_user_id = ?,
                    expected_resolve_at = ?
                WHERE tenant_id = ? AND id = ?
                """, description, impact, ownerUserId, toTimestamp(expectedResolveAt), tenantId, blockId);
    }

    public void resolveBlock(long tenantId, long blockId, String note, long resolvedBy) {
        requireTransaction();
        jdbc.update("""
                UPDATE arch_plan_block SET status = 'RESOLVED', resolved_note = ?, resolved_by = ?
                WHERE tenant_id = ? AND id = ?
                """, note, resolvedBy, tenantId, blockId);
    }

    public void deleteBlocksByTask(long tenantId, long taskId) {
        requireTransaction();
        jdbc.update("DELETE FROM arch_plan_block WHERE tenant_id = ? AND task_id = ?", tenantId, taskId);
    }

    // ---------- 取消建议 ----------

    public void insertCancelSuggestion(long tenantId, CancelSuggestion suggestion) {
        requireTransaction();
        jdbc.update("""
                INSERT INTO arch_plan_check_item_cancel_suggestion
                    (id, tenant_id, check_item_id, reason, submitter_user_id, status, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, 'PENDING', ?, ?)
                """, suggestion.id(), tenantId, suggestion.checkItemId(), suggestion.reason(),
                suggestion.submitterUserId(), suggestion.submitterUserId(), suggestion.submitterUserId());
    }

    public List<CancelSuggestion> findPendingSuggestions(long tenantId, long checkItemId) {
        String condition = checkItemId > 0 ? " AND check_item_id = ?" : "";
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (checkItemId > 0) {
            args.add(checkItemId);
        }
        return jdbc.query("""
                SELECT id, check_item_id, reason, submitter_user_id, status, handled_by_user_id,
                       handled_at, handler_note
                FROM arch_plan_check_item_cancel_suggestion
                WHERE tenant_id = ? AND status = 'PENDING'
                """ + condition + " ORDER BY id ASC", SUGGESTION_MAPPER, args.toArray());
    }

    public Optional<CancelSuggestion> findSuggestion(long tenantId, long suggestionId) {
        return jdbc.query("""
                SELECT id, check_item_id, reason, submitter_user_id, status, handled_by_user_id,
                       handled_at, handler_note
                FROM arch_plan_check_item_cancel_suggestion
                WHERE tenant_id = ? AND id = ?
                """, SUGGESTION_MAPPER, tenantId, suggestionId).stream().findFirst();
    }

    public void handleSuggestion(long tenantId, long suggestionId, String status, Long handledBy,
                                 String handlerNote) {
        requireTransaction();
        jdbc.update("""
                UPDATE arch_plan_check_item_cancel_suggestion SET status = ?, handled_by_user_id = ?,
                    handled_at = NOW(), handler_note = ?
                WHERE tenant_id = ? AND id = ?
                """, status, handledBy, handlerNote, tenantId, suggestionId);
    }

    // ---------- 事件 ----------

    public void insertEvent(long tenantId, PlanEvent event) {
        requireTransaction();
        jdbc.update("""
                INSERT INTO arch_plan_event
                    (id, tenant_id, plan_id, object_type, object_id, event_type, occurred_at,
                     operator_user_id, reason, correct_of_event_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, event.id(), tenantId, event.planId(), event.objectType(), event.objectId(),
                event.eventType().name(), toTimestamp(event.occurredAt()), event.operatorUserId(),
                event.reason(), event.correctOfEventId());
    }

    public List<PlanEvent> findEvents(long tenantId, long planId, String objectType, long objectId) {
        return jdbc.query("""
                SELECT id, plan_id, object_type, object_id, event_type, occurred_at, operator_user_id,
                       reason, correct_of_event_id
                FROM arch_plan_event
                WHERE tenant_id = ? AND plan_id = ? AND object_type = ? AND object_id = ?
                ORDER BY occurred_at ASC, id ASC
                """, EVENT_MAPPER, tenantId, planId, objectType, objectId);
    }

    public Optional<PlanEvent> findEvent(long tenantId, long eventId) {
        return jdbc.query("""
                SELECT id, plan_id, object_type, object_id, event_type, occurred_at, operator_user_id,
                       reason, correct_of_event_id
                FROM arch_plan_event WHERE tenant_id = ? AND id = ?
                """, EVENT_MAPPER, tenantId, eventId).stream().findFirst();
    }

    // ---------- 工单关联 ----------

    public void insertWorkOrder(long tenantId, TaskWorkOrder workOrder) {
        requireTransaction();
        jdbc.update("""
                INSERT INTO arch_plan_work_order
                    (id, tenant_id, plan_id, task_id, work_order_type, work_order_id, source,
                     created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, workOrder.id(), tenantId, workOrder.planId(), workOrder.taskId(),
                workOrder.workOrderType().name(), workOrder.workOrderId(), workOrder.source().name(),
                workOrder.id(), workOrder.id());
    }

    public List<TaskWorkOrder> findWorkOrders(long tenantId, long taskId) {
        return jdbc.query("""
                SELECT id, task_id, plan_id, work_order_type, work_order_id, source, status
                FROM arch_plan_work_order WHERE tenant_id = ? AND task_id = ? AND status = 'ACTIVE'
                ORDER BY id ASC
                """, (rs, rowNum) -> new TaskWorkOrder(rs.getLong("id"), rs.getLong("task_id"),
                rs.getLong("plan_id"), WorkOrderType.valueOf(rs.getString("work_order_type")),
                rs.getLong("work_order_id"),
                WorkOrderSource.valueOf(rs.getString("source")), false), tenantId, taskId);
    }

    public Optional<TaskWorkOrder> findWorkOrder(long tenantId, long workOrderId) {
        return jdbc.query("""
                SELECT id, task_id, plan_id, work_order_type, work_order_id, source, status
                FROM arch_plan_work_order WHERE tenant_id = ? AND id = ?
                """, (rs, rowNum) -> new TaskWorkOrder(rs.getLong("id"), rs.getLong("task_id"),
                rs.getLong("plan_id"), WorkOrderType.valueOf(rs.getString("work_order_type")),
                rs.getLong("work_order_id"), WorkOrderSource.valueOf(rs.getString("source")),
                "REMOVED".equals(rs.getString("status"))), tenantId, workOrderId).stream().findFirst();
    }

    public void removeWorkOrder(long tenantId, long workOrderId, String reason, long removedBy) {
        requireTransaction();
        jdbc.update("""
                UPDATE arch_plan_work_order SET status = 'REMOVED', removed_reason = ?, removed_by = ?,
                    updated_by = ?
                WHERE tenant_id = ? AND id = ?
                """, reason, removedBy, removedBy, tenantId, workOrderId);
    }

    public List<Long> openResourceRequestIds(long tenantId, List<Long> workOrderIds) {
        if (workOrderIds.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(workOrderIds.size(), "?"));
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        args.addAll(workOrderIds);
        return jdbc.query("""
                SELECT id FROM arch_resource_request
                WHERE tenant_id = ? AND id IN (%s)
                  AND status IN ('DRAFT', 'IN_REVIEW', 'RETURNED', 'APPROVED')
                """.formatted(placeholders), (rs, rowNum) -> rs.getLong("id"), args.toArray());
    }

    public List<long[]> resourceRequestRefs(long tenantId, List<Long> workOrderIds) {
        if (workOrderIds.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(workOrderIds.size(), "?"));
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        args.addAll(workOrderIds);
        return jdbc.query("""
                SELECT id, environment_id FROM arch_resource_request
                WHERE tenant_id = ? AND id IN (%s)
                """.formatted(placeholders), (rs, rowNum) -> new long[]{rs.getLong("id"),
                rs.getLong("environment_id")}, args.toArray());
    }

    public boolean networkWorkOrderRefs(long tenantId, List<Long> workOrderIds) {
        if (workOrderIds.isEmpty()) {
            return false;
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(workOrderIds.size(), "?"));
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        args.addAll(workOrderIds);
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM arch_network_work_order
                WHERE tenant_id = ? AND id IN (%s)
                """.formatted(placeholders), Long.class, args.toArray());
        return count != null && count == workOrderIds.size();
    }

    public List<Long> openNetworkWorkOrderIds(long tenantId, List<Long> workOrderIds) {
        if (workOrderIds.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(workOrderIds.size(), "?"));
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        args.addAll(workOrderIds);
        return jdbc.query("""
                SELECT id FROM arch_network_work_order
                WHERE tenant_id = ? AND id IN (%s)
                  AND status IN ('DRAFT', 'IN_REVIEW', 'RETURNED')
                """.formatted(placeholders), (rs, rowNum) -> rs.getLong("id"), args.toArray());
    }

    // ---------- 查询与统计 ----------

    public record PlanListRow(Plan plan, String environmentCode, String environmentName, long taskCount,
                              long totalCheckItems, long completedCheckItems, long cancelledCheckItems,
                              long openBlocks) {
    }

    public List<PlanListRow> searchPlans(long tenantId, Long environmentId, PlanStatus status,
                                         Long ownerUserId, boolean hasBlocked, boolean hasOverdue,
                                         boolean hasWaived, String keyword, TargetType targetType,
                                         Long targetId, int limit, int offset) {
        if (limit <= 0 || offset < 0) {
            throw new IllegalArgumentException("分页参数无效");
        }
        StringBuilder where = new StringBuilder("WHERE p.tenant_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (environmentId != null) {
            where.append(" AND p.environment_id = ?");
            args.add(environmentId);
        }
        if (status != null) {
            where.append(" AND p.status = ?");
            args.add(status.name());
        }
        if (ownerUserId != null) {
            where.append(" AND p.plan_owner_user_id = ?");
            args.add(ownerUserId);
        }
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (p.name LIKE ? OR p.plan_no LIKE ?)");
            args.add("%" + keyword.trim() + "%");
            args.add("%" + keyword.trim() + "%");
        }
        if (hasBlocked) {
            where.append("""
                     AND EXISTS (SELECT 1 FROM arch_plan_block b
                                JOIN arch_plan_task bt ON bt.tenant_id = b.tenant_id AND bt.id = b.task_id
                                WHERE b.tenant_id = p.tenant_id AND bt.plan_id = p.id
                                  AND b.status = 'OPEN' AND bt.cancelled = 0)
                    """);
        }
        if (hasOverdue) {
            where.append("""
                     AND EXISTS (SELECT 1 FROM arch_plan_task ot
                                WHERE ot.tenant_id = p.tenant_id AND ot.plan_id = p.id
                                  AND ot.cancelled = 0 AND ot.status <> 'COMPLETED'
                                  AND ot.planned_end IS NOT NULL AND ot.planned_end < NOW())
                    """);
        }
        if (hasWaived) {
            where.append("""
                     AND EXISTS (SELECT 1 FROM arch_plan_check_item wci
                                JOIN arch_plan_task wct ON wct.tenant_id = wci.tenant_id AND wct.id = wci.task_id
                                WHERE wct.tenant_id = p.tenant_id AND wct.plan_id = p.id
                                  AND (wci.cancelled = 1 OR wct.cancelled = 1 OR wct.waived_all = 1))
                    """);
        }
        if (targetType != null && targetId != null) {
            where.append(" AND EXISTS (SELECT 1 FROM arch_plan_target pt"
                    + " WHERE pt.tenant_id = p.tenant_id AND pt.plan_id = p.id"
                    + " AND pt.target_type = ? AND pt.target_id = ? AND pt.status = 'ACTIVE')");
            args.add(targetType.name());
            args.add(targetId);
        }
        where.append(" ORDER BY p.updated_at DESC, p.id DESC LIMIT ? OFFSET ?");
        args.add(limit);
        args.add(offset);
        String columnsPart = "SELECT " + PLAN_COLUMNS
                + ", env.code AS environment_code, env.name AS environment_name,";
        return jdbc.query(columnsPart + """
                  (SELECT COUNT(*) FROM arch_plan_task t
                    WHERE t.tenant_id = p.tenant_id AND t.plan_id = p.id AND t.cancelled = 0) AS task_count,
                  (SELECT COUNT(*) FROM arch_plan_check_item ci
                    JOIN arch_plan_task ct ON ct.tenant_id = ci.tenant_id AND ct.id = ci.task_id
                    WHERE ci.tenant_id = p.tenant_id AND ct.plan_id = p.id
                      AND ct.cancelled = 0 AND ci.cancelled = 0) AS total_check_items,
                  (SELECT COUNT(*) FROM arch_plan_check_item ci
                    JOIN arch_plan_task ct ON ct.tenant_id = ci.tenant_id AND ct.id = ci.task_id
                    WHERE ci.tenant_id = p.tenant_id AND ct.plan_id = p.id
                      AND ct.cancelled = 0 AND ci.cancelled = 0 AND ci.status = 'COMPLETED')
                    AS completed_check_items,
                  (SELECT COUNT(*) FROM arch_plan_check_item ci
                    JOIN arch_plan_task ct ON ct.tenant_id = ci.tenant_id AND ct.id = ci.task_id
                    WHERE ci.tenant_id = p.tenant_id AND ct.plan_id = p.id
                      AND ct.cancelled = 0 AND ci.cancelled = 1) AS cancelled_check_items,
                  (SELECT COUNT(*) FROM arch_plan_block b
                    JOIN arch_plan_task bt ON bt.tenant_id = b.tenant_id AND bt.id = b.task_id
                    WHERE b.tenant_id = p.tenant_id AND bt.plan_id = p.id
                      AND b.status = 'OPEN' AND bt.cancelled = 0) AS open_blocks
                  FROM arch_setup_plan p
                  JOIN arch_environment env ON env.tenant_id = p.tenant_id AND env.id = p.environment_id
                """ + where, (rs, rowNum) -> new PlanListRow(PLAN_MAPPER.mapRow(rs, rowNum),
                rs.getString("environment_code"), rs.getString("environment_name"),
                rs.getLong("task_count"), rs.getLong("total_check_items"),
                rs.getLong("completed_check_items"), rs.getLong("cancelled_check_items"),
                rs.getLong("open_blocks")), args.toArray());
    }

    public long countPlans(long tenantId, Long environmentId, PlanStatus status, Long ownerUserId,
                           boolean hasBlocked, boolean hasOverdue, boolean hasWaived, String keyword,
                           TargetType targetType, Long targetId) {
        StringBuilder where = new StringBuilder("WHERE p.tenant_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (environmentId != null) {
            where.append(" AND p.environment_id = ?");
            args.add(environmentId);
        }
        if (status != null) {
            where.append(" AND p.status = ?");
            args.add(status.name());
        }
        if (ownerUserId != null) {
            where.append(" AND p.plan_owner_user_id = ?");
            args.add(ownerUserId);
        }
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (p.name LIKE ? OR p.plan_no LIKE ?)");
            args.add("%" + keyword.trim() + "%");
            args.add("%" + keyword.trim() + "%");
        }
        if (hasBlocked) {
            where.append("""
                     AND EXISTS (SELECT 1 FROM arch_plan_block b
                                JOIN arch_plan_task bt ON bt.tenant_id = b.tenant_id AND bt.id = b.task_id
                                WHERE b.tenant_id = p.tenant_id AND bt.plan_id = p.id
                                  AND b.status = 'OPEN' AND bt.cancelled = 0)
                    """);
        }
        if (hasOverdue) {
            where.append("""
                     AND EXISTS (SELECT 1 FROM arch_plan_task ot
                                WHERE ot.tenant_id = p.tenant_id AND ot.plan_id = p.id
                                  AND ot.cancelled = 0 AND ot.status <> 'COMPLETED'
                                  AND ot.planned_end IS NOT NULL AND ot.planned_end < NOW())
                    """);
        }
        if (hasWaived) {
            where.append("""
                     AND EXISTS (SELECT 1 FROM arch_plan_check_item wci
                                JOIN arch_plan_task wct ON wct.tenant_id = wci.tenant_id AND wct.id = wci.task_id
                                WHERE wct.tenant_id = p.tenant_id AND wct.plan_id = p.id
                                  AND (wci.cancelled = 1 OR wct.cancelled = 1 OR wct.waived_all = 1))
                    """);
        }
        if (targetType != null && targetId != null) {
            where.append(" AND EXISTS (SELECT 1 FROM arch_plan_target pt"
                    + " WHERE pt.tenant_id = p.tenant_id AND pt.plan_id = p.id"
                    + " AND pt.target_type = ? AND pt.target_id = ? AND pt.status = 'ACTIVE')");
            args.add(targetType.name());
            args.add(targetId);
        }
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM arch_setup_plan p " + where, Long.class,
                args.toArray());
        return count == null ? 0 : count;
    }

    public Optional<Long> findPlanIdByTask(long tenantId, long taskId) {
        return jdbc.query("""
                SELECT plan_id FROM arch_plan_task WHERE tenant_id = ? AND id = ?
                """, (rs, rowNum) -> rs.getLong("plan_id"), tenantId, taskId).stream().findFirst();
    }

    public record TargetRef(long id, String code, String name, String status) {
    }

    public record AlertPlan(long tenantId, long planId, String planNo, long planOwnerUserId) {
    }

    public Optional<EnvironmentRef> envReference(long tenantId, long environmentId) {
        return jdbc.query("""
                SELECT id, code, name, status FROM arch_environment
                WHERE tenant_id = ? AND id = ?
                """, (rs, rowNum) -> new EnvironmentRef(rs.getLong("id"), rs.getString("code"),
                rs.getString("name"), rs.getString("status")), tenantId, environmentId)
                .stream().findFirst();
    }

    public record EnvironmentRef(long id, String code, String name, String status) {
    }

    public List<TargetRef> listPhysicalSubsystemRefs(long tenantId, List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        args.addAll(ids);
        return jdbc.query("""
                SELECT id, code, name, status FROM arch_physical_subsystem
                WHERE tenant_id = ? AND id IN (%s) AND deleted = 0
                """.formatted(placeholders), (rs, rowNum) -> new TargetRef(rs.getLong("id"),
                rs.getString("code"), rs.getString("name"), rs.getString("status")), args.toArray());
    }

    public List<TargetRef> listDeploymentUnitRefs(long tenantId, List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        args.addAll(ids);
        return jdbc.query("""
                SELECT id, code, name, status FROM arch_deployment_unit
                WHERE tenant_id = ? AND id IN (%s)
                """.formatted(placeholders), (rs, rowNum) -> new TargetRef(rs.getLong("id"),
                rs.getString("code"), rs.getString("name"), rs.getString("status")), args.toArray());
    }

    public Map<Long, String> currentTargetNames(long tenantId, TargetType targetType, List<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<TargetRef> refs = targetType == TargetType.PHYSICAL_SUBSYSTEM
                ? listPhysicalSubsystemRefs(tenantId, ids) : listDeploymentUnitRefs(tenantId, ids);
        Map<Long, String> result = new java.util.HashMap<>();
        for (TargetRef ref : refs) {
            result.put(ref.id(), ref.name());
        }
        return result;
    }

    public List<AlertPlan> planIdsNeedingAlert() {
        return jdbc.query("""
                SELECT tenant_id, id, plan_no, plan_owner_user_id FROM arch_setup_plan
                WHERE status IN ('NOT_STARTED', 'IN_PROGRESS')
                """, (rs, rowNum) -> new AlertPlan(rs.getLong("tenant_id"), rs.getLong("id"),
                rs.getString("plan_no"), rs.getLong("plan_owner_user_id")));
    }

    public long countOverdueTasks(long tenantId, long planId, LocalDateTime now) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM arch_plan_task
                WHERE tenant_id = ? AND plan_id = ? AND cancelled = 0
                  AND status <> 'COMPLETED' AND planned_end IS NOT NULL AND planned_end < ?
                """, Long.class, tenantId, planId, toTimestamp(now));
        return count == null ? 0 : count;
    }

    public void insertStageDependency(long tenantId, long id, long planId, long stageId,
                                      long predecessorStageId, long createdBy) {
        requireTransaction();
        jdbc.update("""
                INSERT INTO arch_plan_stage_dependency
                    (id, tenant_id, plan_id, stage_id, predecessor_stage_id, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, id, tenantId, planId, stageId, predecessorStageId, createdBy, createdBy);
    }

    public List<Long[]> findStageDependencies(long tenantId, long planId) {
        return jdbc.query("""
                SELECT stage_id, predecessor_stage_id FROM arch_plan_stage_dependency
                WHERE tenant_id = ? AND plan_id = ?
                ORDER BY id ASC
                """, (rs, rowNum) -> new Long[]{rs.getLong("stage_id"),
                rs.getLong("predecessor_stage_id")}, tenantId, planId);
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

    private static String taskSelect(String where) {
        return """
                SELECT task.id, task.tenant_id, task.plan_id, task.stage_id, task.task_no, task.name,
                       task.target_type, task.target_id, task.target_no, task.target_name,
                       task.task_template_id, task.task_template_version_no, task.dimension,
                       task.snapshot_json, task.owner_user_id, task.planned_start, task.planned_end,
                       task.actual_start, task.actual_end, task.status, task.waived_all, task.cancelled,
                       task.cancel_reason, task.cancelled_by, task.cancelled_at, task.row_version
                FROM arch_plan_task task
                """ + where;
    }

    private static final RowMapper<Block> BLOCK_MAPPER = (rs, rowNum) -> new Block(
            rs.getLong("id"), rs.getLong("task_id"), rs.getString("description"), rs.getString("impact"),
            rs.getLong("owner_user_id"), toLocalDateTime(rs.getTimestamp("expected_resolve_at")),
            "RESOLVED".equals(rs.getString("status")), rs.getString("resolved_note"),
            nullableLong(rs, "resolved_by"), toLocalDateTime(rs.getTimestamp("resolved_at")),
            rs.getLong("created_by"));

    private static final RowMapper<CancelSuggestion> SUGGESTION_MAPPER = (rs, rowNum) ->
            new CancelSuggestion(rs.getLong("id"), rs.getLong("check_item_id"), rs.getString("reason"),
                    rs.getLong("submitter_user_id"), rs.getString("status"),
                    nullableLong(rs, "handled_by_user_id"),
                    toLocalDateTime(rs.getTimestamp("handled_at")), rs.getString("handler_note"));

    private static final RowMapper<PlanEvent> EVENT_MAPPER = (rs, rowNum) -> new PlanEvent(
            rs.getLong("id"), rs.getLong("plan_id"), rs.getString("object_type"), rs.getLong("object_id"),
            EventType.valueOf(rs.getString("event_type")),
            toLocalDateTime(rs.getTimestamp("occurred_at")), rs.getLong("operator_user_id"),
            rs.getString("reason"), nullableLong(rs, "correct_of_event_id"));

    private static Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Integer nullableInt(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static TargetType nullableTargetType(String value) {
        return value == null ? null : TargetType.valueOf(value);
    }

    private static LocalDateTime toLocalDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    private static Timestamp toTimestamp(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }
}
