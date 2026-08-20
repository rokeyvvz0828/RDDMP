package com.ccb.release.integration;

import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.release.application.model.ReleaseApplicationModels.Application;
import com.ccb.release.application.model.ReleaseApplicationModels.Status;
import com.ccb.release.application.persistence.ReleaseApplicationStore;
import com.ccb.release.application.service.ReleaseSubmissionService;
import com.ccb.release.integration.ReleaseWorkflowStore.RoundSnapshot;
import com.ccb.release.production.service.ReleaseProductionService;
import com.ccb.security.model.AuthUser;
import com.ccb.workflow.integration.WorkflowBusinessContext;
import com.ccb.workflow.integration.WorkflowLifecycleConsumer;
import com.ccb.workflow.integration.WorkflowLifecycleEvent;
import com.ccb.workflow.integration.WorkflowLifecycleEventType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;

@Component
public class ReleaseWorkflowLifecycleConsumer implements WorkflowLifecycleConsumer {
    public static final String SUBSCRIBER_KEY = "release.application.lifecycle.v1";

    private final ReleaseApplicationStore applications;
    private final ReleaseWorkflowStore workflowStore;
    private final ReleaseProductionService productionService;

    public ReleaseWorkflowLifecycleConsumer(ReleaseApplicationStore applications, ReleaseWorkflowStore workflowStore,
                                            ReleaseProductionService productionService) {
        this.applications = applications;
        this.workflowStore = workflowStore;
        this.productionService = productionService;
    }

    @Override
    public String subscriberKey() {
        return SUBSCRIBER_KEY;
    }

    @Override
    public boolean supports(String businessType) {
        return ReleaseSubmissionService.BUSINESS_TYPE.equals(businessType);
    }

    @Override
    @Transactional
    public void consume(WorkflowLifecycleEvent event) {
        validateEvent(event);
        WorkflowBusinessContext context = event.context();
        Application application = applications.findByCodeForUpdate(context.businessKey(), event.tenantId())
                .orElseThrow(() -> conflict("工作流事件关联的版本申请不存在"));
        if (!workflowStore.beginReceipt(event, application.id(), SUBSCRIBER_KEY)) return;

        RoundSnapshot round = workflowStore.findRoundByInstanceForUpdate(event.tenantId(), event.instanceId())
                .orElseThrow(() -> conflict("工作流事件关联的审批轮次不存在"));
        if (!matches(application, round, event)
                || !workflowStore.isLatestRound(event.tenantId(), application.id(), round.roundNo())) {
            workflowStore.completeReceipt(event.tenantId(), event.eventId(), SUBSCRIBER_KEY, "IGNORED");
            return;
        }

        switch (event.eventType()) {
            case STARTED -> consumeStarted(event, application, round);
            case APPROVED -> consumeApproved(event, application, round);
            case RETURNED, REJECTED -> consumeReturned(event, application, round);
            case TERMINATED -> consumeTerminated(event, application, round);
        }
    }

    private void consumeStarted(WorkflowLifecycleEvent event, Application application, RoundSnapshot round) {
        if (application.status() != Status.IN_REVIEW || !"IN_REVIEW".equals(round.roundStatus())) {
            ignored(event);
            return;
        }
        processed(event);
    }

    private void consumeApproved(WorkflowLifecycleEvent event, Application application, RoundSnapshot round) {
        if (application.status() != Status.IN_REVIEW || !"IN_REVIEW".equals(round.roundStatus())) {
            ignored(event);
            return;
        }
        long assignedWindowId;
        if (application.emergency()) {
            assignedWindowId = workflowStore.findReceivingWindow(application.tenantId(), application.projectId(),
                            event.occurredAt())
                    .orElseThrow(() -> conflict("应急版本制品准出时没有可承接的投产窗口"));
        } else if (application.windowId() != null) {
            assignedWindowId = application.windowId();
        } else {
            throw conflict("非应急版本缺少投产窗口");
        }
        if (!workflowStore.markApproved(application, assignedWindowId, event.occurredAt(), event.operatorId())
                || !workflowStore.completeRound(application.tenantId(), round.id(), "IN_REVIEW", "APPROVED",
                event.occurredAt())) {
            throw conflict("版本申请审批状态已变化");
        }
        applications.appendEvent(nextId(), application.tenantId(), application.id(), "WORKFLOW_APPROVED",
                Status.IN_REVIEW, Status.RELEASED, null,
                payload(event, Map.of("assignedWindowId", assignedWindowId)), event.operatorId(), "工作流审批");
        productionService.refreshReleasedCandidates(application.id(), workflowOperator(event));
        processed(event);
    }

    private void consumeReturned(WorkflowLifecycleEvent event, Application application, RoundSnapshot round) {
        if (application.status() != Status.IN_REVIEW || !"IN_REVIEW".equals(round.roundStatus())) {
            ignored(event);
            return;
        }
        String roundStatus = event.eventType() == WorkflowLifecycleEventType.REJECTED ? "REJECTED" : "RETURNED";
        if (!workflowStore.markReturned(application, event.operatorId())
                || !workflowStore.completeRound(application.tenantId(), round.id(), "IN_REVIEW", roundStatus,
                event.occurredAt())) {
            throw conflict("版本申请退回状态已变化");
        }
        applications.appendEvent(nextId(), application.tenantId(), application.id(), "WORKFLOW_" + roundStatus,
                Status.IN_REVIEW, Status.RETURNED, null, payload(event, Map.of()), event.operatorId(), "工作流审批");
        processed(event);
    }

    private void consumeTerminated(WorkflowLifecycleEvent event, Application application, RoundSnapshot round) {
        if (application.status() != Status.IN_REVIEW) {
            ignored(event);
            return;
        }
        Status target;
        String expectedRoundStatus;
        if ("CANCEL_REQUESTED".equals(round.roundStatus())) {
            target = Status.CANCELLED;
            expectedRoundStatus = "CANCEL_REQUESTED";
        } else if ("WITHDRAW_REQUESTED".equals(round.roundStatus())) {
            target = Status.WITHDRAWN;
            expectedRoundStatus = "WITHDRAW_REQUESTED";
        } else if ("IN_REVIEW".equals(round.roundStatus())) {
            target = Status.RETURNED;
            expectedRoundStatus = "IN_REVIEW";
        } else {
            ignored(event);
            return;
        }
        boolean applicationChanged = switch (target) {
            case CANCELLED -> workflowStore.markCancelled(application, event.operatorId());
            case WITHDRAWN -> workflowStore.markWithdrawn(application, event.operatorId());
            default -> workflowStore.markReturned(application, event.operatorId());
        };
        if (!applicationChanged || !workflowStore.completeRound(application.tenantId(), round.id(), expectedRoundStatus,
                "TERMINATED", event.occurredAt())) {
            throw conflict("版本申请流程终止状态已变化");
        }
        applications.appendEvent(nextId(), application.tenantId(), application.id(), "WORKFLOW_TERMINATED",
                Status.IN_REVIEW, target, null, payload(event, Map.of("source", expectedRoundStatus)),
                event.operatorId(), "工作流审批");
        processed(event);
    }

    private boolean matches(Application application, RoundSnapshot round, WorkflowLifecycleEvent event) {
        WorkflowBusinessContext context = event.context();
        return round.applicationId() == application.id()
                && round.roundNo() == context.businessRound()
                && Objects.equals(round.workflowInstanceId(), event.instanceId())
                && round.dataDigest().equalsIgnoreCase(context.dataDigest())
                && application.tenantId() == event.tenantId()
                && application.applicationCode().equals(context.businessKey());
    }

    private void validateEvent(WorkflowLifecycleEvent event) {
        if (event == null || event.context() == null || event.eventType() == null || event.occurredAt() == null
                || event.eventId() == null || event.eventId().isBlank() || event.instanceId() <= 0
                || !supports(event.context().businessType())) {
            throw conflict("工作流生命周期事件无效");
        }
    }

    private void processed(WorkflowLifecycleEvent event) {
        workflowStore.completeReceipt(event.tenantId(), event.eventId(), SUBSCRIBER_KEY, "PROCESSED");
    }

    private void ignored(WorkflowLifecycleEvent event) {
        workflowStore.completeReceipt(event.tenantId(), event.eventId(), SUBSCRIBER_KEY, "IGNORED");
    }

    private String payload(WorkflowLifecycleEvent event, Map<String, Object> values) {
        Map<String, Object> payload = new java.util.LinkedHashMap<>(values);
        payload.put("workflowEventId", event.eventId());
        payload.put("workflowInstanceId", event.instanceId());
        payload.put("roundNo", event.context().businessRound());
        payload.put("occurredAt", event.occurredAt().toString());
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(payload);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "工作流事件审计序列化失败");
        }
    }

    private AuthUser workflowOperator(WorkflowLifecycleEvent event) {
        return new AuthUser(event.operatorId(), event.tenantId(), "workflow", "", "工作流审批", 0, true);
    }

    private BusinessException conflict(String message) {
        return new BusinessException(ErrorCode.CONFLICT, message);
    }

    private long nextId() {
        return System.currentTimeMillis() * 1000 + java.util.concurrent.ThreadLocalRandom.current().nextInt(1000);
    }
}
