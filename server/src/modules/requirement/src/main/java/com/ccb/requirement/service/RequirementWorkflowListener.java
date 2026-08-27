package com.ccb.requirement.service;

import com.ccb.workflow.event.WorkflowInstanceCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.ccb.requirement.support.RequirementIds;

/**
 * 监听平台审批流终态事件，按 businessKey 回写需求管理平台的业务字段。
 * <p>businessKey 约定：
 * <ul>
 *   <li>差异评审：req-diff:{differenceId}      → APPROVED 改"已评审"，REJECTED/RETURNED 改"已退回"</li>
 * </ul>
 * <p>存量需求阶段推进不接入审批流，直接状态流转并在 req_stage_log 留痕，不再由本监听器回写。
 * <p>事件由 WorkflowService 在 instance 到 APPROVED/REJECTED/RETURNED/TERMINATED 时发布；本监听器同步执行于事务内。
 * 异常默认保留原 trace 并记录 warn，避免将审批通过的结果静默丢失。
 */
@Component
public class RequirementWorkflowListener {

    private static final Logger log = LoggerFactory.getLogger(RequirementWorkflowListener.class);

    private static final String DIFF_PREFIX = "req-diff:";
    private static final Set<String> APPROVAL_STATUSES = Set.of("APPROVE", "APPROVED");
    private static final Set<String> REJECT_STATUSES = Set.of("REJECT", "REJECTED");
    private static final Set<String> RETURN_STATUSES = Set.of("RETURN", "RETURNED");

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
            }
        } catch (Exception e) {
            // 记录监听器异常，避免审批通过/驳回的结果静默丢失；不将异常再抛出影响 workflow 事务已落库的状态
            log.warn("Requirement workflow callback failed for businessKey={}, status={}, instanceId={}, tenantId={}: {}",
                    businessKey, event.status(), event.instanceId(), event.tenantId(), e.getMessage(), e);
        }
    }

    private void handleDiffReview(WorkflowInstanceCompletedEvent event, long diffId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT review_status, review_report_name FROM req_difference WHERE tenant_id = ? AND id = ? AND deleted = 0",
                event.tenantId(), diffId);
        if (rows.isEmpty()) return;
        Map<String, Object> diff = rows.get(0);
        String current = String.valueOf(diff.get("review_status"));
        if (!"评审中".equals(current)) return;  // 幂等：可能已被手工处理
        String rawStatus = normalize(event.status());
        String newStatus = APPROVAL_STATUSES.contains(rawStatus) ? "已评审" : "已退回";
        long operatorId = submitterId(event);
        jdbc.update("UPDATE req_difference SET review_status = ?, review_comment = ?, updated_by = ? WHERE tenant_id = ? AND id = ?",
                newStatus, null, operatorId, event.tenantId(), diffId);
        writeDiffReviewRecord(event, diffId, newStatus,
                diff.get("review_report_name") == null ? null : String.valueOf(diff.get("review_report_name")));
        // 锁定/解锁差异：已评审 → 不可修改；已退回 → 可再编辑
        changeLog.record("NEW_PROJECT_DIFF", diffId, "REVIEW_RESULT", "review_status", current, newStatus,
                new com.ccb.security.model.AuthUser(operatorId, event.tenantId(), "system", "", "审批系统", 0L, true),
                "WORKFLOW");
    }

    /** 差异评审完成时回写评审记录（评审人/时间/结论/意见/评审报告文档名称）。 */
    private void writeDiffReviewRecord(WorkflowInstanceCompletedEvent event, long diffId, String newStatus,
                                       String reportDocName) {
        List<Map<String, Object>> actions = jdbc.queryForList("""
                SELECT a.operator_id, u.display_name AS operator_name, a.comment, a.created_at
                FROM wf_task_action a
                LEFT JOIN sys_user u ON u.id = a.operator_id AND u.tenant_id = a.tenant_id
                WHERE a.tenant_id = ? AND a.instance_id = ?
                ORDER BY a.id DESC LIMIT 1
                """, event.tenantId(), event.instanceId());
        if (actions.isEmpty()) {
            return;
        }
        Map<String, Object> action = actions.get(0);
        long recordId = RequirementIds.next();
        jdbc.update("""
                INSERT INTO req_review_record (id, tenant_id, biz_type, biz_id, reviewer_id, reviewer_name,
                    review_time, conclusion, comment, report_doc_name, created_by, deleted)
                VALUES (?, ?, 'DIFFERENCE', ?, ?, ?, ?, ?, ?, ?, ?, 0)
                """, recordId, event.tenantId(), diffId,
                ((Number) action.get("operator_id")).longValue(),
                action.get("operator_name") == null ? "" : String.valueOf(action.get("operator_name")),
                action.get("created_at"),
                "已评审".equals(newStatus) ? "通过" : "退回",
                action.get("comment") == null ? null : String.valueOf(action.get("comment")),
                reportDocName, event.tenantId());
    }

    /** 归一化 workflow event.status：兼容动作常量 APPROVE/REJECT/RETURN 与终态常量 APPROVED/REJECTED/RETURNED。 */
    private String normalize(String status) {
        if (status == null) return "";
        String upper = status.trim().toUpperCase(Locale.ROOT);
        return switch (upper) {
            case "APPROVE" -> "APPROVED";
            case "REJECT" -> "REJECTED";
            case "RETURN" -> "RETURNED";
            default -> upper;
        };
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
