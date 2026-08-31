package com.ccb.architecture.network.integration;

import com.ccb.architecture.network.model.NetworkAccessModels.ApplicationStatus;
import com.ccb.architecture.network.model.NetworkAccessModels.NetworkAccessApplication;
import com.ccb.architecture.network.model.NetworkAccessModels.WorkflowReceiptStart;
import com.ccb.architecture.network.model.NetworkAccessModels.WorkflowReceiptStatus;
import com.ccb.architecture.network.model.NetworkAccessModels.WorkflowRound;
import com.ccb.architecture.network.model.NetworkAccessModels.WorkflowRoundStatus;
import com.ccb.architecture.network.persistence.NetworkAccessStore;
import com.ccb.architecture.network.service.NetworkAccessApplicationSubmissionService;
import com.ccb.architecture.network.service.NetworkAccessService;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.workflow.integration.WorkflowBusinessContext;
import com.ccb.workflow.integration.WorkflowLifecycleConsumer;
import com.ccb.workflow.integration.WorkflowLifecycleEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;

/** 网络访问申请的可信工作流生命周期消费者。 */
@Component
public class NetworkAccessWorkflowLifecycleConsumer implements WorkflowLifecycleConsumer {
    public static final String SUBSCRIBER_KEY = "architecture.network-access.application.lifecycle.v1";

    private final NetworkAccessStore store;
    private final NetworkAccessService access;
    private final LongSupplier idSupplier;

    @Autowired
    public NetworkAccessWorkflowLifecycleConsumer(NetworkAccessStore store,
                                                  NetworkAccessService access) {
        this(store, access, () -> System.currentTimeMillis() * 1_000 + ThreadLocalRandom.current().nextInt(1_000));
    }

    NetworkAccessWorkflowLifecycleConsumer(NetworkAccessStore store,
                                           NetworkAccessService access,
                                           LongSupplier idSupplier) {
        this.store = Objects.requireNonNull(store, "网络访问存储不能为空");
        this.access = Objects.requireNonNull(access, "网络访问服务不能为空");
        this.idSupplier = Objects.requireNonNull(idSupplier, "标识生成器不能为空");
    }

    @Override
    public String subscriberKey() {
        return SUBSCRIBER_KEY;
    }

    @Override
    public boolean supports(String businessType) {
        return NetworkAccessApplicationSubmissionService.BUSINESS_TYPE.equals(businessType);
    }

    @Override
    @Transactional
    public void consume(WorkflowLifecycleEvent event) {
        long applicationId = validateAndApplicationId(event);
        NetworkAccessApplication application = store.lockApplication(event.tenantId(), applicationId)
                .orElseThrow(() -> conflict("工作流事件关联的网络访问申请不存在"));
        if (!store.beginReceipt(new WorkflowReceiptStart(nextId(), event.tenantId(), event.eventId(),
                SUBSCRIBER_KEY, applicationId, event.context().businessRound(), event.instanceId(),
                event.eventType().name()))) {
            return;
        }

        WorkflowRound round = store.lockWorkflowRoundByInstance(event.tenantId(), event.instanceId())
                .orElseThrow(() -> conflict("工作流事件关联的网络访问审批轮次不存在"));
        if (!matches(application, round, event)
                || !store.isLatestWorkflowRound(event.tenantId(), application.id(), round.roundNo())) {
            ignored(event, "事件不匹配当前实例、轮次或摘要");
            return;
        }

        switch (event.eventType()) {
            case STARTED -> consumeStarted(event, application, round);
            case APPROVED -> consumeApproved(event, application, round);
            case RETURNED -> consumeReviewOutcome(event, application, round,
                    ApplicationStatus.RETURNED, WorkflowRoundStatus.RETURNED);
            case REJECTED -> consumeReviewOutcome(event, application, round,
                    ApplicationStatus.REJECTED, WorkflowRoundStatus.REJECTED);
            case TERMINATED -> consumeTerminated(event, application, round);
        }
    }

    private void consumeStarted(WorkflowLifecycleEvent event, NetworkAccessApplication application,
                                WorkflowRound round) {
        if (!isActiveReview(application, round)) {
            ignored(event, "STARTED 事件对应的网络访问申请或轮次已完成");
            return;
        }
        processed(event, "已确认当前审批轮次启动");
    }

    private void consumeApproved(WorkflowLifecycleEvent event, NetworkAccessApplication application,
                                 WorkflowRound round) {
        if (!isActiveReview(application, round) || application.cancellationRequested()) {
            ignored(event, "APPROVED 事件对应的网络访问申请已变化或正在取消");
            return;
        }
        access.applyApprovalInCurrentTransaction(event.tenantId(), application.id(),
                application.rowVersion(), event.operatorId());
        completeRound(event, application, round, WorkflowRoundStatus.APPROVED);
        processed(event, "已批准并完成网络访问申请");
    }

    private void consumeReviewOutcome(WorkflowLifecycleEvent event, NetworkAccessApplication application,
                                      WorkflowRound round, ApplicationStatus outcome,
                                      WorkflowRoundStatus roundStatus) {
        if (!isActiveReview(application, round) || application.cancellationRequested()) {
            ignored(event, event.eventType() + " 事件对应的网络访问申请已变化或正在取消");
            return;
        }
        access.applyReviewOutcomeInCurrentTransaction(event.tenantId(), application.id(),
                application.rowVersion(), event.operatorId(), outcome);
        completeRound(event, application, round, roundStatus);
        processed(event, outcome == ApplicationStatus.RETURNED ? "已退回申请人修改" : "已拒绝并结束办理");
    }

    private void consumeTerminated(WorkflowLifecycleEvent event, NetworkAccessApplication application,
                                   WorkflowRound round) {
        if (!isActiveReview(application, round) || !application.cancellationRequested()) {
            ignored(event, "TERMINATED 事件没有匹配的取消请求");
            return;
        }
        access.applyCancellationConfirmationInCurrentTransaction(event.tenantId(), application.id(),
                application.rowVersion(), event.operatorId());
        completeRound(event, application, round, WorkflowRoundStatus.TERMINATED);
        processed(event, "已确认工作流终止并取消网络访问申请");
    }

    private boolean isActiveReview(NetworkAccessApplication application, WorkflowRound round) {
        return application.status() == ApplicationStatus.IN_REVIEW && round.status() == WorkflowRoundStatus.STARTED;
    }

    private void completeRound(WorkflowLifecycleEvent event, NetworkAccessApplication application,
                               WorkflowRound round, WorkflowRoundStatus nextStatus) {
        if (!store.completeStartedWorkflowRound(event.tenantId(), application.id(), round.roundNo(),
                nextStatus, event.occurredAt())) {
            throw conflict("审批轮次状态已变化");
        }
    }

    private boolean matches(NetworkAccessApplication application, WorkflowRound round, WorkflowLifecycleEvent event) {
        WorkflowBusinessContext context = event.context();
        return application.id() == round.applicationId()
                && application.tenantId() == event.tenantId()
                && application.currentBusinessRound() == context.businessRound()
                && Objects.equals(application.currentWorkflowDefinitionId(), round.workflowDefinitionId())
                && Objects.equals(application.currentWorkflowVersionId(), round.workflowVersionId())
                && Objects.equals(application.currentWorkflowInstanceId(), event.instanceId())
                && Objects.equals(round.workflowInstanceId(), event.instanceId())
                && round.roundNo() == context.businessRound()
                && equalsDigest(application.currentPayloadDigest(), context.dataDigest())
                && equalsDigest(round.payloadDigest(), context.dataDigest())
                && String.valueOf(application.id()).equals(context.businessKey());
    }

    private long validateAndApplicationId(WorkflowLifecycleEvent event) {
        if (event == null || event.context() == null || event.eventType() == null || event.occurredAt() == null
                || event.eventId() == null || event.eventId().isBlank() || event.tenantId() <= 0
                || event.instanceId() <= 0 || event.operatorId() <= 0
                || !supports(event.context().businessType())
                || !NetworkAccessApplicationSubmissionService.MODULE_CODE.equals(event.context().moduleCode())
                || event.context().businessRound() <= 0
                || event.context().dataDigest() == null
                || event.context().dataDigest().length() != 64) {
            throw conflict("工作流生命周期事件无效");
        }
        try {
            long applicationId = Long.parseLong(event.context().businessKey());
            if (applicationId <= 0) {
                throw new NumberFormatException("非正数");
            }
            return applicationId;
        } catch (NumberFormatException exception) {
            throw conflict("工作流事件业务键不是有效的网络访问申请编号");
        }
    }

    private boolean equalsDigest(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    private void processed(WorkflowLifecycleEvent event, String detail) {
        if (!store.completeReceipt(event.tenantId(), event.eventId(), SUBSCRIBER_KEY,
                WorkflowReceiptStatus.PROCESSED, detail)) {
            throw conflict("工作流事件回执状态已变化");
        }
    }

    private void ignored(WorkflowLifecycleEvent event, String detail) {
        if (!store.completeReceipt(event.tenantId(), event.eventId(), SUBSCRIBER_KEY,
                WorkflowReceiptStatus.IGNORED, detail)) {
            throw conflict("工作流事件回执状态已变化");
        }
    }

    private long nextId() {
        long value = idSupplier.getAsLong();
        if (value <= 0) {
            throw new IllegalStateException("工作流回执标识生成器返回无效值");
        }
        return value;
    }

    private BusinessException conflict(String message) {
        return new BusinessException(ErrorCode.CONFLICT, message);
    }
}
