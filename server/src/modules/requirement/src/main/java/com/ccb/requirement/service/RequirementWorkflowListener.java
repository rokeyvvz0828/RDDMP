package com.ccb.requirement.service;

import com.ccb.workflow.integration.WorkflowLifecycleConsumer;
import com.ccb.workflow.integration.WorkflowLifecycleEvent;
import com.ccb.workflow.integration.WorkflowLifecycleEventType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import com.ccb.requirement.support.RequirementIds;

/**
 * 监听平台审批流终态事件，按 businessKey 回写需求管理平台的业务字段。
 * <p>businessKey 约定：
 * <ul>
 *   <li>差异评审：req-diff:{differenceId}      → APPROVED 改"已评审"，REJECTED/RETURNED 改"已退回"</li>
 *   <li>交付件评审：req-deliverable:{WORKLOAD|SOFT}:{deliverableId} → 同口径回写工作量表/软需文档评审状态</li>
 * </ul>
 * <p>存量需求阶段推进不接入审批流，直接状态流转并在 req_stage_log 留痕，不再由本监听器回写。
 * <p>事件由工作流生命周期投递器持久化分发；消费异常必须向上抛出，以进入平台重试和失败告警流程。
 */
@Component
public class RequirementWorkflowListener implements WorkflowLifecycleConsumer {
    private static final String DIFF_PREFIX = "req-diff:";
    private static final String DELIVERABLE_PREFIX = "req-deliverable:";

    private final JdbcTemplate jdbc;
    private final RequirementChangeLogService changeLog;

    public RequirementWorkflowListener(JdbcTemplate jdbc, RequirementChangeLogService changeLog) {
        this.jdbc = jdbc;
        this.changeLog = changeLog;
    }

    @Override
    public String subscriberKey() {
        return "requirement.diff-review.lifecycle";
    }

    @Override
    public boolean supports(String businessType) {
        return "requirement_diff_review".equals(businessType)
                || "requirement_deliverable_review".equals(businessType);
    }

    @Override
    @Transactional
    public void consume(WorkflowLifecycleEvent event) {
        if (event.eventType() != WorkflowLifecycleEventType.APPROVED
                && event.eventType() != WorkflowLifecycleEventType.REJECTED
                && event.eventType() != WorkflowLifecycleEventType.RETURNED) {
            return;
        }
        String businessKey = event.context().businessKey();
        if (businessKey == null || businessKey.isBlank()) return;
        if (businessKey.startsWith(DIFF_PREFIX)) {
            handleDiffReview(event, parseLong(businessKey.substring(DIFF_PREFIX.length())));
            return;
        }
        if (businessKey.startsWith(DELIVERABLE_PREFIX)) {
            handleDeliverableReview(event, businessKey.substring(DELIVERABLE_PREFIX.length()));
        }
    }

    /** 存量交付件（工作量表/软需文档）评审回写：通过→已评审，退回/驳回→已退回，并保留评审记录。 */
    private void handleDeliverableReview(WorkflowLifecycleEvent event, String key) {
        int separator = key.indexOf(':');
        if (separator <= 0) {
            return;
        }
        String type = key.substring(0, separator).trim();
        if (!"WORKLOAD".equals(type) && !"SOFT".equals(type)) {
            return;
        }
        long deliverableId = parseLong(key.substring(separator + 1));
        if (deliverableId <= 0) {
            return;
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT review_status, review_report_name, review_report_attachment_id FROM req_legacy_deliverable"
                        + " WHERE tenant_id = ? AND id = ? AND deliverable_type = ? AND deleted = 0",
                event.tenantId(), deliverableId, type);
        if (rows.isEmpty()) {
            return;
        }
        String current = String.valueOf(rows.get(0).get("review_status"));
        if (!"评审中".equals(current)) {
            return;  // 幂等：可能已被手工处理
        }
        String bizType = "WORKLOAD".equals(type) ? "LEGACY_WORKLOAD" : "LEGACY_SOFT";
        String newStatus = event.eventType() == WorkflowLifecycleEventType.APPROVED ? "已评审" : "已退回";
        long operatorId = event.operatorId();
        long recordId = writeDeliverableReviewRecord(event, bizType, deliverableId, newStatus, operatorId,
                contentValue(rows.get(0).get("review_report_name")),
                attachmentRef(rows.get(0).get("review_report_attachment_id")));
        jdbc.update("UPDATE req_legacy_deliverable SET review_status = ?, review_record_id = ?"
                        + " WHERE tenant_id = ? AND id = ?",
                newStatus, recordId, event.tenantId(), deliverableId);
        changeLog.record(bizType, deliverableId, "REVIEW_RESULT", "review_status", current, newStatus,
                new com.ccb.security.model.AuthUser(operatorId, event.tenantId(), "system", "", "审批系统", 0L, true),
                "WORKFLOW");
    }

    /** 交付件评审记录：评审人/时间/结论/意见取自工作流任务动作。 */
    private long writeDeliverableReviewRecord(WorkflowLifecycleEvent event, String bizType, long deliverableId,
                                              String newStatus, long operatorId,
                                              String reportDocName, String reportPreviewId) {
        List<Map<String, Object>> actions = jdbc.queryForList("""
                SELECT a.operator_id, u.display_name AS operator_name, a.comment, a.created_at
                FROM wf_task_action a
                LEFT JOIN sys_user u ON u.id = a.operator_id AND u.tenant_id = a.tenant_id
                WHERE a.tenant_id = ? AND a.instance_id = ?
                ORDER BY a.id DESC LIMIT 1
                """, event.tenantId(), event.instanceId());
        String operatorName = "";
        String comment = null;
        long reviewerId = operatorId;
        if (!actions.isEmpty()) {
            Map<String, Object> action = actions.get(0);
            reviewerId = ((Number) action.get("operator_id")).longValue();
            operatorName = action.get("operator_name") == null ? "" : String.valueOf(action.get("operator_name"));
            comment = action.get("comment") == null ? null : String.valueOf(action.get("comment"));
        }
        long recordId = RequirementIds.next();
        jdbc.update("""
                INSERT INTO req_review_record (id, tenant_id, biz_type, biz_id, reviewer_id, reviewer_name,
                    review_time, conclusion, comment, report_doc_name, report_preview_id, created_by, deleted)
                VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?, ?, ?, ?, ?, 0)
                """, recordId, event.tenantId(), bizType, deliverableId, reviewerId, operatorName,
                "已评审".equals(newStatus) ? "通过" : "退回", comment, reportDocName, reportPreviewId, operatorId);
        return recordId;
    }

    private void handleDiffReview(WorkflowLifecycleEvent event, long diffId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT r.review_status, d.review_report_name, d.review_report_attachment_id FROM req_requirement r"
                        + " LEFT JOIN req_difference_detail d ON d.requirement_id = r.id AND d.tenant_id = r.tenant_id"
                        + " WHERE r.tenant_id = ? AND r.id = ? AND r.deleted = 0"
                        + " AND r.requirement_kind = 'NEW_PROJECT_DIFF'",
                event.tenantId(), diffId);
        if (rows.isEmpty()) return;
        Map<String, Object> diff = rows.get(0);
        String current = String.valueOf(diff.get("review_status"));
        if (!"评审中".equals(current)) return;  // 幂等：可能已被手工处理
        String newStatus = event.eventType() == WorkflowLifecycleEventType.APPROVED ? "已评审" : "已退回";
        long operatorId = submitterId(event);
        jdbc.update("UPDATE req_requirement SET review_status = ?, updated_by = ? WHERE tenant_id = ? AND id = ?",
                newStatus, operatorId, event.tenantId(), diffId);
        jdbc.update("UPDATE req_difference_detail SET review_comment = NULL WHERE tenant_id = ? AND requirement_id = ?",
                event.tenantId(), diffId);
        writeDiffReviewRecord(event, diffId, newStatus, contentValue(diff.get("review_report_name")),
                attachmentRef(diff.get("review_report_attachment_id")));
        // 锁定/解锁差异：已评审 → 不可修改；已退回 → 可再编辑
        changeLog.record("NEW_PROJECT_DIFF", diffId, "REVIEW_RESULT", "review_status", current, newStatus,
                new com.ccb.security.model.AuthUser(operatorId, event.tenantId(), "system", "", "审批系统", 0L, true),
                "WORKFLOW");
    }

    /** 差异评审完成时回写评审记录（评审人/时间/结论/意见/评审报告文档名称）。 */
    private void writeDiffReviewRecord(WorkflowLifecycleEvent event, long diffId, String newStatus,
                                       String reportDocName, String reportPreviewId) {
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
                    review_time, conclusion, comment, report_doc_name, report_preview_id, created_by, deleted)
                VALUES (?, ?, 'DIFFERENCE', ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                """, recordId, event.tenantId(), diffId,
                ((Number) action.get("operator_id")).longValue(),
                action.get("operator_name") == null ? "" : String.valueOf(action.get("operator_name")),
                action.get("created_at"),
                "已评审".equals(newStatus) ? "通过" : "退回",
                action.get("comment") == null ? null : String.valueOf(action.get("comment")),
                reportDocName, reportPreviewId, event.tenantId());
    }

    /** 文本列取值：去空后回写，超长由数据库列长度兜底。 */
    private static String contentValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /** 评审报告附件引用：统一按 VARCHAR(64) 的 report_preview_id 列存放附件 ID。 */
    private static String attachmentRef(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text.substring(0, Math.min(text.length(), 64));
    }

    private long submitterId(WorkflowLifecycleEvent event) {
        return event.operatorId();
    }

    private long parseLong(String text) {
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }
}
