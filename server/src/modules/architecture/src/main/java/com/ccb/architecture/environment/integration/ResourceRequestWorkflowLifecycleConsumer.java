package com.ccb.architecture.environment.integration;

import com.ccb.architecture.environment.model.EnvironmentResourceModels.RequestStatus;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.ResourceRequest;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.WorkflowReceiptStart;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.WorkflowReceiptStatus;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.WorkflowRound;
import com.ccb.architecture.environment.model.EnvironmentResourceModels.WorkflowRoundStatus;
import com.ccb.architecture.environment.persistence.EnvironmentResourceStore;
import com.ccb.architecture.environment.service.EnvironmentResourceService;
import com.ccb.architecture.environment.service.ResourceRequestSubmissionService;
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

/**
 * 资源申请可信工作流生命周期消费者（REQ-20260824-052）。
 *
 * <p>批准只将申请置为 APPROVED，表示申请态批准；实际资源分配、机器/IP 与环境部署
 * 实例仍由后续搭建任务承接。</p>
 */
@Component
public class ResourceRequestWorkflowLifecycleConsumer implements WorkflowLifecycleConsumer {
    public static final String SUBSCRIBER_KEY = "architecture.resource-request.lifecycle.v1";

    private final EnvironmentResourceStore store;
    private final EnvironmentResourceService changes;
    private final LongSupplier idSupplier;

    @Autowired
    public ResourceRequestWorkflowLifecycleConsumer(EnvironmentResourceStore store,
                                                   EnvironmentResourceService changes) {
        this(store, changes,
                () -> System.currentTimeMillis() * 1_000 + ThreadLocalRandom.current().nextInt(1_000));
    }

    ResourceRequestWorkflowLifecycleConsumer(EnvironmentResourceStore store,
                                             EnvironmentResourceService changes,
                                             LongSupplier idSupplier) {
        this.store = Objects.requireNonNull(store, "环境资源存储不能为空");
        this.changes = Objects.requireNonNull(changes, "环境资源服务不能为空");
        this.idSupplier = Objects.requireNonNull(idSupplier, "标识生成器不能为空");
    }

    @Override
    public String subscriberKey() {
        return SUBSCRIBER_KEY;
    }

    @Override
    public boolean supports(String businessType) {
        return ResourceRequestSubmissionService.BUSINESS_TYPE.equals(businessType);
    }

    @Override
    @Transactional
    public void consume(WorkflowLifecycleEvent event) {
        long requestId = validateAndRequestId(event);
        ResourceRequest request = store.lockRequest(event.tenantId(), requestId)
                .orElseThrow(() -> conflict("工作流事件关联的资源申请不存在"));
        if (!store.beginReceipt(new WorkflowReceiptStart(nextId(), event.tenantId(), event.eventId(),
                SUBSCRIBER_KEY, requestId, event.context().businessRound(), event.instanceId(),
                event.eventType().name()))) {
            return;
        }

        WorkflowRound round = store.lockWorkflowRoundByInstance(event.tenantId(), event.instanceId())
                .orElseThrow(() -> conflict("工作流事件关联的审批轮次不存在"));
        if (!matches(request, round, event)
                || !store.isLatestWorkflowRound(event.tenantId(), request.id(), round.roundNo())) {
            ignored(event, "事件不匹配当前实例、轮次或摘要");
            return;
        }

        switch (event.eventType()) {
            case STARTED -> consumeStarted(event, request, round);
            case APPROVED -> consumeApproved(event, request, round);
            case RETURNED -> consumeReviewOutcome(event, request, round,
                    RequestStatus.RETURNED, WorkflowRoundStatus.RETURNED);
            case REJECTED -> consumeReviewOutcome(event, request, round,
                    RequestStatus.REJECTED, WorkflowRoundStatus.REJECTED);
            case TERMINATED -> consumeTerminated(event, request, round);
        }
    }

    private void consumeStarted(WorkflowLifecycleEvent event, ResourceRequest request, WorkflowRound round) {
        if (!isActiveReview(request, round)) {
            ignored(event, "STARTED 事件对应的资源申请或轮次已完成");
            return;
        }
        processed(event, "已确认当前审批轮次启动");
    }

    private void consumeApproved(WorkflowLifecycleEvent event, ResourceRequest request, WorkflowRound round) {
        if (!isActiveReview(request, round) || request.cancellationRequested()) {
            ignored(event, "APPROVED 事件对应的资源申请已变化或正在取消");
            return;
        }
        changes.applyApprovalInCurrentTransaction(event.tenantId(), request.id(),
                request.rowVersion(), event.operatorId());
        completeRound(event, request, round, WorkflowRoundStatus.APPROVED);
        processed(event, "已批准资源申请，实际资源分配待后续搭建任务接入");
    }

    private void consumeReviewOutcome(WorkflowLifecycleEvent event, ResourceRequest request,
                                      WorkflowRound round, RequestStatus outcome,
                                      WorkflowRoundStatus roundStatus) {
        if (!isActiveReview(request, round) || request.cancellationRequested()) {
            ignored(event, event.eventType() + " 事件对应的资源申请已变化或正在取消");
            return;
        }
        changes.applyReviewOutcomeInCurrentTransaction(event.tenantId(), request.id(),
                request.rowVersion(), event.operatorId(), outcome);
        completeRound(event, request, round, roundStatus);
        processed(event, outcome == RequestStatus.RETURNED
                ? "已退回申请人修改" : "已拒绝并结束资源申请");
    }

    private void consumeTerminated(WorkflowLifecycleEvent event, ResourceRequest request,
                                   WorkflowRound round) {
        if (!isActiveReview(request, round) || !request.cancellationRequested()) {
            ignored(event, "TERMINATED 事件没有匹配的取消请求");
            return;
        }
        changes.applyCancellationConfirmationInCurrentTransaction(event.tenantId(), request.id(),
                request.rowVersion(), event.operatorId());
        completeRound(event, request, round, WorkflowRoundStatus.TERMINATED);
        processed(event, "已确认工作流终止并取消资源申请");
    }

    private boolean isActiveReview(ResourceRequest request, WorkflowRound round) {
        return request.status() == RequestStatus.IN_REVIEW
                && round.status() == WorkflowRoundStatus.STARTED;
    }

    private void completeRound(WorkflowLifecycleEvent event, ResourceRequest request,
                               WorkflowRound round, WorkflowRoundStatus nextStatus) {
        if (!store.completeStartedWorkflowRound(event.tenantId(), request.id(), round.roundNo(),
                nextStatus, event.occurredAt())) {
            throw conflict("审批轮次状态已变化");
        }
    }

    private boolean matches(ResourceRequest request, WorkflowRound round, WorkflowLifecycleEvent event) {
        WorkflowBusinessContext context = event.context();
        return request.id() == round.requestId()
                && request.tenantId() == event.tenantId()
                && request.currentBusinessRound() == context.businessRound()
                && Objects.equals(request.currentWorkflowDefinitionId(), round.workflowDefinitionId())
                && Objects.equals(request.currentWorkflowVersionId(), round.workflowVersionId())
                && Objects.equals(request.currentWorkflowInstanceId(), event.instanceId())
                && Objects.equals(round.workflowInstanceId(), event.instanceId())
                && round.roundNo() == context.businessRound()
                && equalsDigest(request.currentPayloadDigest(), context.dataDigest())
                && equalsDigest(round.payloadDigest(), context.dataDigest())
                && String.valueOf(request.id()).equals(context.businessKey());
    }

    private long validateAndRequestId(WorkflowLifecycleEvent event) {
        if (event == null || event.context() == null || event.eventType() == null || event.occurredAt() == null
                || event.eventId() == null || event.eventId().isBlank() || event.tenantId() <= 0
                || event.instanceId() <= 0 || event.operatorId() <= 0
                || !supports(event.context().businessType())
                || !ResourceRequestSubmissionService.MODULE_CODE.equals(event.context().moduleCode())
                || event.context().businessRound() <= 0
                || event.context().dataDigest() == null
                || event.context().dataDigest().length() != 64) {
            throw conflict("工作流生命周期事件无效");
        }
        try {
            long requestId = Long.parseLong(event.context().businessKey());
            if (requestId <= 0) {
                throw new NumberFormatException("非正数");
            }
            return requestId;
        } catch (NumberFormatException exception) {
            throw conflict("工作流事件业务键不是有效的资源申请编号");
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
