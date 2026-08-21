package com.ccb.requirement.service;

import com.ccb.requirement.support.RequirementEnums;
import com.ccb.requirement.support.RequirementIds;
import com.ccb.requirement.support.RequirementSql;
import com.ccb.workflow.event.WorkflowInstanceCompletedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 监听平台审批流终态事件，按 businessKey 回写需求管理平台的业务字段。
 * <p>businessKey 约定：
 * <ul>
 *   <li>差异评审：req-diff:{differenceId}      → APPROVED 改"已评审"，REJECTED 改"已退回"</li>
 *   <li>阶段推进：req-legacy:{reqId}:{stage}:{action}   → APPROVED 执行 START/COMPLETE/BACK 状态流转，REJECTED 一律回退到"未开始"</li>
 * </ul>
 * <p>事件由 WorkflowService 在 instance 到 APPROVED/REJECTED 时发布；本监听器同步执行于事务内，
 * 业务异常已被 caller 捕获；非预期异常被 catch 不影响流程实例已落库的状态。
 */
@Component
public class RequirementWorkflowListener {

    private static final String LEGACY_PREFIX = "req-legacy:";
    private static final String DIFF_PREFIX = "req-diff:";

    private final JdbcTemplate jdbc;
    private final RequirementChangeLogService changeLog;

    public RequirementWorkflowListener(JdbcTemplate jdbc, RequirementChangeLogService changeLog) {
        this.jdbc = jdbc;
        this.changeLog = changeLog;
    }

    @EventListener
    @Transactional
    public void onWorkflowCompleted(WorkflowInstanceCompletedEvent event) {
        String businessKey = event.businessKey();
        if (businessKey == null || businessKey.isBlank()) return;
        try {
            if (businessKey.startsWith(DIFF_PREFIX)) {
                handleDiffReview(event, parseLong(businessKey.substring(DIFF_PREFIX.length())));
            } else if (businessKey.startsWith(LEGACY_PREFIX)) {
                handleLegacyStageTransition(event, businessKey.substring(LEGACY_PREFIX.length()));
            }
        } catch (Exception ignored) {
            // 监听器异常不应使 workflow 事务回滚（避免审批已通过但状态显示未变更）
            // 业务表的"审批中"状态会保留，运维可手动介入；如需严格事务可在调用方关闭此 catch
        }
    }

    private void handleDiffReview(WorkflowInstanceCompletedEvent event, long diffId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT review_status FROM req_difference WHERE tenant_id = ? AND id = ? AND deleted = 0",
                event.tenantId(), diffId);
        if (rows.isEmpty()) return;
        String current = String.valueOf(rows.get(0).get("review_status"));
        if (!"评审中".equals(current)) return;  // 幂等：可能已被手工处理
        String newStatus = "APPROVED".equals(event.status()) ? "已评审" : "已退回";
        long operatorId = submitterId(event);
        jdbc.update("UPDATE req_difference SET review_status = ?, review_comment = ?, updated_by = ? WHERE tenant_id = ? AND id = ?",
                newStatus, null, operatorId, event.tenantId(), diffId);
        // 锁定/解锁差异：已评审 → 不可修改；已退回 → 可再编辑
        changeLog.record("NEW_PROJECT_DIFF", diffId, "REVIEW_RESULT", "review_status", current, newStatus,
                new com.ccb.security.model.AuthUser(operatorId, event.tenantId(), "system", "", "审批系统", 0L, true),
                "WORKFLOW");
    }

    private void handleLegacyStageTransition(WorkflowInstanceCompletedEvent event, String payload) {
        // payload = {reqId}:{stage}:{action}
        String[] parts = payload.split(":");
        if (parts.length != 3) return;
        long reqId = parseLong(parts[0]);
        String stage = parts[1];
        String action = parts[2].toUpperCase();
        String column = RequirementEnums.LEGACY_STAGE_COLUMNS.get(stage);
        if (column == null) return;

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT " + RequirementSql.quote(column) + " AS s, current_stage, requirement_status, workflow_instance_id FROM req_legacy_requirement WHERE tenant_id = ? AND id = ? AND deleted = 0",
                event.tenantId(), reqId);
        if (rows.isEmpty()) return;
        Map<String, Object> row = rows.get(0);
        String currentStatus = String.valueOf(row.get("s"));
        if (!"审批中".equals(currentStatus)) return;  // 幂等保护
        String oldStage = String.valueOf(row.get("current_stage"));
        String oldRequirementStatus = row.get("requirement_status") == null ? null : String.valueOf(row.get("requirement_status"));

        // 发起审批前的原阶段状态（中文，存于 variables.fromStatus），REJECTED 时按此回退，避免一律退"未开始"丢失工作
        String fromStatus = event.variables() != null && event.variables().has("fromStatus")
                ? event.variables().path("fromStatus").asText(currentStatus) : currentStatus;
        String instanceId = row.get("workflow_instance_id") == null ? null : String.valueOf(row.get("workflow_instance_id"));

        String newStatus;
        String approvalResult;
        if ("APPROVED".equals(event.status())) {
            newStatus = switch (action) {
                case "START" -> "进行中";
                case "COMPLETE" -> "已完成";
                case "BACK" -> "未开始";
                default -> null;
            };
            approvalResult = "APPROVED";
        } else if ("REJECTED".equals(event.status())) {
            // 驳回保持发起前原状态：未开始阶段驳回 → 未开始；进行中阶段驳回 → 进行中
            newStatus = fromStatus;
            approvalResult = "REJECTED";
        } else {
            return;
        }
        if (newStatus == null) return;

        // 二维联动：APPROVED + START/COMPLETE/BACK 按 (stage, action) 映射覆盖 requirement_status
        // REJECTED 不联动 requirement_status，避免驳回把已通过的高水位状态抹掉
        String newRequirementStatus = oldRequirementStatus;
        boolean linkageRequired = "APPROVED".equals(event.status());
        if (linkageRequired) {
            String mapped = RequirementEnums.LEGACY_STAGE_ACTION_TO_REQ_STATUS.get(stage + ":" + action);
            if (mapped != null) {
                newRequirementStatus = mapped;
            }
        }

        long operatorId = submitterId(event);
        if (newRequirementStatus != null && !newRequirementStatus.equals(oldRequirementStatus)) {
            jdbc.update("UPDATE req_legacy_requirement SET " + RequirementSql.quote(column)
                            + " = ?, current_stage = ?, requirement_status = ?, updated_by = ? WHERE tenant_id = ? AND id = ?",
                    newStatus, stage, newRequirementStatus, operatorId, event.tenantId(), reqId);
            changeLog.record("LEGACY_REQUIREMENT", reqId, "STAGE_TRANSITION", "requirement_status",
                    oldRequirementStatus, newRequirementStatus,
                    new com.ccb.security.model.AuthUser(operatorId, event.tenantId(), "system", "", "审批系统", 0L, true),
                    "WORKFLOW");
        } else {
            jdbc.update("UPDATE req_legacy_requirement SET " + RequirementSql.quote(column)
                            + " = ?, current_stage = ?, updated_by = ? WHERE tenant_id = ? AND id = ?",
                    newStatus, stage, operatorId, event.tenantId(), reqId);
        }
        long logId = RequirementIds.next();
        jdbc.update("""
                INSERT INTO req_stage_log (id, tenant_id, requirement_id, from_stage, to_stage, from_status, to_status, operator_id, operator_name, comment, approval_result, workflow_instance_id, deleted)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                """, logId, event.tenantId(), reqId, oldStage, stage, currentStatus, newStatus,
                operatorId, "审批系统", "审批流回写：" + event.status(), approvalResult, instanceId);
        changeLog.record("LEGACY_REQUIREMENT", reqId, "STAGE_TRANSITION", column, currentStatus, newStatus,
                new com.ccb.security.model.AuthUser(operatorId, event.tenantId(), "system", "", "审批系统", 0L, true),
                "WORKFLOW");
    }

    private long submitterId(WorkflowInstanceCompletedEvent event) {
        if (event.variables() != null && event.variables().has("submitterId")) {
            return event.variables().path("submitterId").asLong(0L);
        }
        return 0L;
    }

    private long parseLong(String text) {
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }
}
