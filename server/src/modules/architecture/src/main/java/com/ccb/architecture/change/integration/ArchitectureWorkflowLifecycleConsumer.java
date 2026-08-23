package com.ccb.architecture.change.integration;

import com.ccb.architecture.change.model.SubsystemChangeModels.ApplicationStatus;
import com.ccb.architecture.change.model.SubsystemChangeModels.ChangeApplication;
import com.ccb.architecture.change.model.SubsystemChangeModels.WorkflowReceiptStart;
import com.ccb.architecture.change.model.SubsystemChangeModels.WorkflowReceiptStatus;
import com.ccb.architecture.change.model.SubsystemChangeModels.WorkflowRound;
import com.ccb.architecture.change.model.SubsystemChangeModels.WorkflowRoundStatus;
import com.ccb.architecture.change.persistence.SubsystemChangeStore;
import com.ccb.architecture.change.service.ArchitectureSubsystemSubmissionService;
import com.ccb.architecture.change.service.SubsystemChangeService;
import com.ccb.architecture.change.service.SubsystemChangeService.ReviewOutcome;
import com.ccb.architecture.change.service.SubsystemPublicationService;
import com.ccb.architecture.change.service.SubsystemPublicationService.ApprovalCommand;
import com.ccb.common.exception.BusinessException;
import com.ccb.common.exception.ErrorCode;
import com.ccb.security.model.AuthUser;
import com.ccb.workflow.integration.WorkflowBusinessContext;
import com.ccb.workflow.integration.WorkflowLifecycleConsumer;
import com.ccb.workflow.integration.WorkflowLifecycleEvent;
import com.ccb.workflow.integration.WorkflowLifecycleEventType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;

/**
 * 架构子系统工单的可信工作流生命周期消费者。
 *
 * <p>事件只携带流程身份，不携带可编辑业务值；批准发布始终重新读取已提交草稿。
 * 回执、轮次和业务状态共享同一事务，发布失败时回执也回滚，从而允许平台安全重试。</p>
 */
@Component
public class ArchitectureWorkflowLifecycleConsumer implements WorkflowLifecycleConsumer {
    public static final String SUBSCRIBER_KEY = "architecture.subsystem.change.lifecycle.v1";

    private final SubsystemChangeStore store;
    private final SubsystemChangeService changes;
    private final SubsystemPublicationService publication;
    private final LongSupplier idSupplier;

    @Autowired
    public ArchitectureWorkflowLifecycleConsumer(SubsystemChangeStore store,
                                                 SubsystemChangeService changes,
                                                 SubsystemPublicationService publication) {
        this(store, changes, publication,
                () -> System.currentTimeMillis() * 1_000 + ThreadLocalRandom.current().nextInt(1_000));
    }

    ArchitectureWorkflowLifecycleConsumer(SubsystemChangeStore store,
                                          SubsystemChangeService changes,
                                          SubsystemPublicationService publication,
                                          LongSupplier idSupplier) {
        this.store = Objects.requireNonNull(store, "工单存储不能为空");
        this.changes = Objects.requireNonNull(changes, "工单服务不能为空");
        this.publication = Objects.requireNonNull(publication, "发布服务不能为空");
        this.idSupplier = Objects.requireNonNull(idSupplier, "标识生成器不能为空");
    }

    @Override
    public String subscriberKey() {
        return SUBSCRIBER_KEY;
    }

    @Override
    public boolean supports(String businessType) {
        return ArchitectureSubsystemSubmissionService.BUSINESS_TYPE.equals(businessType);
    }

    @Override
    @Transactional
    public void consume(WorkflowLifecycleEvent event) {
        long applicationId = validateAndApplicationId(event);
        ChangeApplication application = store.lockApplication(event.tenantId(), applicationId)
                .orElseThrow(() -> conflict("工作流事件关联的架构子系统工单不存在"));
        if (!store.beginReceipt(new WorkflowReceiptStart(nextId(), event.tenantId(), event.eventId(),
                SUBSCRIBER_KEY, applicationId, event.context().businessRound(), event.instanceId(),
                event.eventType().name()))) {
            return;
        }

        WorkflowRound round = store.lockWorkflowRoundByInstance(event.tenantId(), event.instanceId())
                .orElseThrow(() -> conflict("工作流事件关联的审批轮次不存在"));
        if (!matches(application, round, event)
                || !store.isLatestWorkflowRound(event.tenantId(), application.id(), round.roundNo())) {
            ignored(event, "事件不匹配当前实例、轮次或摘要");
            return;
        }

        switch (event.eventType()) {
            case STARTED -> consumeStarted(event, application, round);
            case APPROVED -> consumeApproved(event, application, round);
            case RETURNED -> consumeReviewOutcome(event, application, round,
                    ReviewOutcome.RETURNED, WorkflowRoundStatus.RETURNED);
            case REJECTED -> consumeReviewOutcome(event, application, round,
                    ReviewOutcome.REJECTED, WorkflowRoundStatus.REJECTED);
            case TERMINATED -> consumeTerminated(event, application, round);
        }
    }

    private void consumeStarted(WorkflowLifecycleEvent event, ChangeApplication application, WorkflowRound round) {
        if (!isActiveReview(application, round)) {
            ignored(event, "STARTED 事件对应的工单或轮次已完成");
            return;
        }
        processed(event, "已确认当前审批轮次启动");
    }

    private void consumeApproved(WorkflowLifecycleEvent event, ChangeApplication application, WorkflowRound round) {
        if (!isActiveReview(application, round) || application.cancellationRequested()) {
            ignored(event, "APPROVED 事件对应的工单已变化或正在取消");
            return;
        }
        publication.approve(new ApprovalCommand(application.id(), round.roundNo(), application.rowVersion(),
                event.instanceId(), event.context().dataDigest()), workflowOperator(event));
        completeRound(event, application, round, WorkflowRoundStatus.APPROVED);
        processed(event, "已批准并原子发布架构子系统变更");
    }

    private void consumeReviewOutcome(WorkflowLifecycleEvent event, ChangeApplication application,
                                      WorkflowRound round, ReviewOutcome outcome,
                                      WorkflowRoundStatus roundStatus) {
        if (!isActiveReview(application, round) || application.cancellationRequested()) {
            ignored(event, event.eventType() + " 事件对应的工单已变化或正在取消");
            return;
        }
        changes.applyReviewOutcomeInCurrentTransaction(event.tenantId(), application.id(),
                application.rowVersion(), event.operatorId(), outcome);
        completeRound(event, application, round, roundStatus);
        processed(event, outcome == ReviewOutcome.RETURNED
                ? "已退回申请人修改" : "已拒绝并释放未发布资源");
    }

    private void consumeTerminated(WorkflowLifecycleEvent event, ChangeApplication application,
                                   WorkflowRound round) {
        if (!isActiveReview(application, round) || !application.cancellationRequested()) {
            ignored(event, "TERMINATED 事件没有匹配的取消请求");
            return;
        }
        changes.applyCancellationConfirmationInCurrentTransaction(event.tenantId(), application.id(),
                application.rowVersion(), event.instanceId(), event.operatorId());
        completeRound(event, application, round, WorkflowRoundStatus.TERMINATED);
        processed(event, "已确认工作流终止并取消工单");
    }

    private boolean isActiveReview(ChangeApplication application, WorkflowRound round) {
        return application.status() == ApplicationStatus.IN_REVIEW
                && round.status() == WorkflowRoundStatus.STARTED;
    }

    private void completeRound(WorkflowLifecycleEvent event, ChangeApplication application,
                               WorkflowRound round, WorkflowRoundStatus nextStatus) {
        if (!store.completeStartedWorkflowRound(event.tenantId(), application.id(), round.roundNo(),
                nextStatus, event.occurredAt())) {
            throw conflict("审批轮次状态已变化");
        }
    }

    private boolean matches(ChangeApplication application, WorkflowRound round, WorkflowLifecycleEvent event) {
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
                || !ArchitectureSubsystemSubmissionService.MODULE_CODE.equals(event.context().moduleCode())
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
            throw conflict("工作流事件业务键不是有效的架构子系统工单编号");
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

    private AuthUser workflowOperator(WorkflowLifecycleEvent event) {
        return new AuthUser(event.operatorId(), event.tenantId(), "workflow", "", "工作流审批",
                0L, true);
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
