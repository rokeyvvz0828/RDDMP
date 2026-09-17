package com.ccb.requirement.service;

import com.ccb.workflow.integration.WorkflowLifecycleConsumer;
import com.ccb.workflow.integration.WorkflowLifecycleEvent;
import com.ccb.workflow.integration.WorkflowLifecycleEventType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import com.ccb.requirement.support.RequirementIds;

/**
 * 监听平台审批流终态事件，按 businessKey 回写需求管理平台的业务字段。
 * <p>businessKey 约定：
 * <ul>
 *   <li>差异评审：req-diff:{differenceId}      → APPROVED 改"已评审"，REJECTED/RETURNED 改"已退回"</li>
 * </ul>
 * <p>存量需求阶段推进不接入审批流，直接状态流转并在 req_stage_log 留痕，不再由本监听器回写。
 * <p>事件由工作流生命周期投递器持久化分发；消费异常必须向上抛出，以进入平台重试和失败告警流程。
 */
@Component
public class RequirementWorkflowListener implements WorkflowLifecycleConsumer {
    private static final String DIFF_PREFIX = "req-diff:";

    private final RequirementWorkflowRepository repository;
    private final RequirementChangeLogService changeLog;

    public RequirementWorkflowListener(RequirementWorkflowRepository repository, RequirementChangeLogService changeLog) {
        this.repository = repository;
        this.changeLog = changeLog;
    }

    @Override
    public String subscriberKey() {
        return "requirement.diff-review.lifecycle";
    }

    @Override
    public boolean supports(String businessType) {
        return "requirement_diff_review".equals(businessType);
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
        }
    }

    private void handleDiffReview(WorkflowLifecycleEvent event, long diffId) {
        Map<String, Object> diff = repository.findActiveDifference(event.tenantId(), diffId);
        if (diff == null) return;
        String current = String.valueOf(diff.get("review_status"));
        if (!"评审中".equals(current)) return;  // 幂等：可能已被手工处理
        String newStatus = event.eventType() == WorkflowLifecycleEventType.APPROVED ? "已评审" : "已退回";
        long operatorId = submitterId(event);
        repository.updateReviewStatus(event.tenantId(), diffId, newStatus, operatorId);
        writeDiffReviewRecord(event, diffId, newStatus,
                diff.get("review_report_name") == null ? null : String.valueOf(diff.get("review_report_name")));
        // 锁定/解锁差异：已评审 → 不可修改；已退回 → 可再编辑
        changeLog.record("NEW_PROJECT_DIFF", diffId, "REVIEW_RESULT", "review_status", current, newStatus,
                new com.ccb.security.model.AuthUser(operatorId, event.tenantId(), "system", "", "审批系统", 0L, true),
                "WORKFLOW");
    }

    /** 差异评审完成时回写评审记录（评审人/时间/结论/意见/评审报告文档名称）。 */
    private void writeDiffReviewRecord(WorkflowLifecycleEvent event, long diffId, String newStatus,
                                       String reportDocName) {
        Map<String, Object> action = repository.findLatestWorkflowAction(event.tenantId(), event.instanceId());
        if (action == null) {
            return;
        }
        long recordId = RequirementIds.next();
        repository.insertReviewRecord(recordId, event.tenantId(), diffId,
                ((Number) action.get("operator_id")).longValue(),
                action.get("operator_name") == null ? "" : String.valueOf(action.get("operator_name")),
                (java.time.LocalDateTime) action.get("created_at"),
                "已评审".equals(newStatus) ? "通过" : "退回",
                action.get("comment") == null ? null : String.valueOf(action.get("comment")), reportDocName, event.tenantId());
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
