package com.ccb.architecture.network.integration;

import com.ccb.architecture.network.model.NetworkWorkOrderModels.WorkOrder;
import com.ccb.architecture.network.model.NetworkWorkOrderModels.WorkOrderStatus;
import com.ccb.architecture.network.model.NetworkWorkOrderModels.WorkflowReceiptStart;
import com.ccb.architecture.network.model.NetworkWorkOrderModels.WorkflowReceiptStatus;
import com.ccb.architecture.network.model.NetworkWorkOrderModels.WorkflowRound;
import com.ccb.architecture.network.model.NetworkWorkOrderModels.WorkflowRoundStatus;
import com.ccb.architecture.network.persistence.NetworkWorkOrderStore;
import com.ccb.architecture.network.service.NetworkWorkOrderService;
import com.ccb.architecture.network.service.NetworkWorkOrderSubmissionService;
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
 * 网络专项工单的可信工作流生命周期消费者（REQ-20260823-050）。
 *
 * <p>事件只携带流程身份，不携带可编辑业务值；批准只把工单推进到 COMPLETED，
 * 不执行任何外部动作。回执、轮次和业务状态共享同一事务，失败时回执也回滚，
 * 从而允许平台安全重试。</p>
 */
@Component
public class NetworkWorkflowLifecycleConsumer implements WorkflowLifecycleConsumer {
    public static final String SUBSCRIBER_KEY = "architecture.network.work-order.lifecycle.v1";

    private final NetworkWorkOrderStore store;
    private final NetworkWorkOrderService changes;
    private final LongSupplier idSupplier;

    @Autowired
    public NetworkWorkflowLifecycleConsumer(NetworkWorkOrderStore store,
                                            NetworkWorkOrderService changes) {
        this(store, changes,
                () -> System.currentTimeMillis() * 1_000 + ThreadLocalRandom.current().nextInt(1_000));
    }

    NetworkWorkflowLifecycleConsumer(NetworkWorkOrderStore store,
                                     NetworkWorkOrderService changes,
                                     LongSupplier idSupplier) {
        this.store = Objects.requireNonNull(store, "工单存储不能为空");
        this.changes = Objects.requireNonNull(changes, "工单服务不能为空");
        this.idSupplier = Objects.requireNonNull(idSupplier, "标识生成器不能为空");
    }

    @Override
    public String subscriberKey() {
        return SUBSCRIBER_KEY;
    }

    @Override
    public boolean supports(String businessType) {
        return NetworkWorkOrderSubmissionService.BUSINESS_TYPE.equals(businessType);
    }

    @Override
    @Transactional
    public void consume(WorkflowLifecycleEvent event) {
        long workOrderId = validateAndWorkOrderId(event);
        WorkOrder workOrder = store.lockWorkOrder(event.tenantId(), workOrderId)
                .orElseThrow(() -> conflict("工作流事件关联的网络专项工单不存在"));
        if (!store.beginReceipt(new WorkflowReceiptStart(nextId(), event.tenantId(), event.eventId(),
                SUBSCRIBER_KEY, workOrderId, event.context().businessRound(), event.instanceId(),
                event.eventType().name()))) {
            return;
        }

        WorkflowRound round = store.lockWorkflowRoundByInstance(event.tenantId(), event.instanceId())
                .orElseThrow(() -> conflict("工作流事件关联的审批轮次不存在"));
        if (!matches(workOrder, round, event)
                || !store.isLatestWorkflowRound(event.tenantId(), workOrder.id(), round.roundNo())) {
            ignored(event, "事件不匹配当前实例、轮次或摘要");
            return;
        }

        switch (event.eventType()) {
            case STARTED -> consumeStarted(event, workOrder, round);
            case APPROVED -> consumeApproved(event, workOrder, round);
            case RETURNED -> consumeReviewOutcome(event, workOrder, round,
                    WorkOrderStatus.RETURNED, WorkflowRoundStatus.RETURNED);
            case REJECTED -> consumeReviewOutcome(event, workOrder, round,
                    WorkOrderStatus.REJECTED, WorkflowRoundStatus.REJECTED);
            case TERMINATED -> consumeTerminated(event, workOrder, round);
        }
    }

    private void consumeStarted(WorkflowLifecycleEvent event, WorkOrder workOrder, WorkflowRound round) {
        if (!isActiveReview(workOrder, round)) {
            ignored(event, "STARTED 事件对应的工单或轮次已完成");
            return;
        }
        processed(event, "已确认当前审批轮次启动");
    }

    private void consumeApproved(WorkflowLifecycleEvent event, WorkOrder workOrder, WorkflowRound round) {
        if (!isActiveReview(workOrder, round) || workOrder.cancellationRequested()) {
            ignored(event, "APPROVED 事件对应的工单已变化或正在取消");
            return;
        }
        changes.applyCompletionInCurrentTransaction(event.tenantId(), workOrder.id(),
                workOrder.rowVersion(), event.operatorId());
        completeRound(event, workOrder, round, WorkflowRoundStatus.APPROVED);
        processed(event, "已批准并完成网络专项工单");
    }

    private void consumeReviewOutcome(WorkflowLifecycleEvent event, WorkOrder workOrder,
                                      WorkflowRound round, WorkOrderStatus outcome,
                                      WorkflowRoundStatus roundStatus) {
        if (!isActiveReview(workOrder, round) || workOrder.cancellationRequested()) {
            ignored(event, event.eventType() + " 事件对应的工单已变化或正在取消");
            return;
        }
        changes.applyReviewOutcomeInCurrentTransaction(event.tenantId(), workOrder.id(),
                workOrder.rowVersion(), event.operatorId(), outcome);
        completeRound(event, workOrder, round, roundStatus);
        processed(event, outcome == WorkOrderStatus.RETURNED
                ? "已退回申请人修改" : "已拒绝并结束办理");
    }

    private void consumeTerminated(WorkflowLifecycleEvent event, WorkOrder workOrder,
                                   WorkflowRound round) {
        if (!isActiveReview(workOrder, round) || !workOrder.cancellationRequested()) {
            ignored(event, "TERMINATED 事件没有匹配的取消请求");
            return;
        }
        changes.applyCancellationConfirmationInCurrentTransaction(event.tenantId(), workOrder.id(),
                workOrder.rowVersion(), event.operatorId());
        completeRound(event, workOrder, round, WorkflowRoundStatus.TERMINATED);
        processed(event, "已确认工作流终止并取消工单");
    }

    private boolean isActiveReview(WorkOrder workOrder, WorkflowRound round) {
        return workOrder.status() == WorkOrderStatus.IN_REVIEW
                && round.status() == WorkflowRoundStatus.STARTED;
    }

    private void completeRound(WorkflowLifecycleEvent event, WorkOrder workOrder,
                               WorkflowRound round, WorkflowRoundStatus nextStatus) {
        if (!store.completeStartedWorkflowRound(event.tenantId(), workOrder.id(), round.roundNo(),
                nextStatus, event.occurredAt())) {
            throw conflict("审批轮次状态已变化");
        }
    }

    private boolean matches(WorkOrder workOrder, WorkflowRound round, WorkflowLifecycleEvent event) {
        WorkflowBusinessContext context = event.context();
        return workOrder.id() == round.workOrderId()
                && workOrder.tenantId() == event.tenantId()
                && workOrder.currentBusinessRound() == context.businessRound()
                && Objects.equals(workOrder.currentWorkflowDefinitionId(), round.workflowDefinitionId())
                && Objects.equals(workOrder.currentWorkflowVersionId(), round.workflowVersionId())
                && Objects.equals(workOrder.currentWorkflowInstanceId(), event.instanceId())
                && Objects.equals(round.workflowInstanceId(), event.instanceId())
                && round.roundNo() == context.businessRound()
                && equalsDigest(workOrder.currentPayloadDigest(), context.dataDigest())
                && equalsDigest(round.payloadDigest(), context.dataDigest())
                && String.valueOf(workOrder.id()).equals(context.businessKey());
    }

    private long validateAndWorkOrderId(WorkflowLifecycleEvent event) {
        if (event == null || event.context() == null || event.eventType() == null || event.occurredAt() == null
                || event.eventId() == null || event.eventId().isBlank() || event.tenantId() <= 0
                || event.instanceId() <= 0 || event.operatorId() <= 0
                || !supports(event.context().businessType())
                || !NetworkWorkOrderSubmissionService.MODULE_CODE.equals(event.context().moduleCode())
                || event.context().businessRound() <= 0
                || event.context().dataDigest() == null
                || event.context().dataDigest().length() != 64) {
            throw conflict("工作流生命周期事件无效");
        }
        try {
            long workOrderId = Long.parseLong(event.context().businessKey());
            if (workOrderId <= 0) {
                throw new NumberFormatException("非正数");
            }
            return workOrderId;
        } catch (NumberFormatException exception) {
            throw conflict("工作流事件业务键不是有效的网络专项工单编号");
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
